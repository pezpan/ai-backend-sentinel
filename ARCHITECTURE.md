# Arquitectura del proyecto Sentinel Backend AI

## Visión general

**Sentinel Backend AI** es un agente de IA que se ejecuta en local y analiza microservicios (u otros proyectos Java) usando un modelo de lenguaje local (Ollama) y **herramientas tipo MCP** (Tools) para acceder al sistema de archivos y al código. Las herramientas se implementan con la API de Tools de LangChain4j (`@Tool` en `ProjectMcpTools`).

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        sentinel-backend-ai (JVM 21)                          │
│  ┌─────────────┐   ┌──────────────────┐   ┌─────────────────────────────┐   │
│  │   Picocli   │──▶│ AnalyzeCommand   │──▶│ OllamaConfig → ChatModel     │   │
│  │  (CLI)      │   │ -p/--path <dir>  │   │ (LangChain4j / Ollama)        │   │
│  └─────────────┘   └────────┬─────────┘   └──────────────┬────────────────┘   │
│                              │                            │                   │
│                              │   ┌────────────────────────▼────────────────┐   │
│                              │   │ AiServices.builder(SentinelAgent.class)  │   │
│                              │   │   .chatLanguageModel(model)             │   │
│                              │   │   .tools(ProjectMcpTools)                │   │
│                              │   └────────────────────────┬────────────────┘   │
│                              │                            │                   │
│                              │   ┌────────────────────────▼────────────────┐   │
│                              └──▶│ ProjectMcpTools (@Tool)                  │   │
│                                  │  - readProjectStructure(path)           │   │
│                                  │  - readJavaFile(path)                    │   │
│                                  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                                    │
                                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Ollama (localhost:11434) — Modelo: qwen2.5-coder:7b                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Uso de herramientas (estilo MCP)

- En este proyecto, la idea de **MCP** (dar al LLM acceso controlado al sistema de archivos) se materializa con **LangChain4j Tools**: una clase `ProjectMcpTools` con métodos anotados con `@Tool` que el LLM puede invocar durante el análisis.
- **Herramientas disponibles:** `readProjectStructure(path)` (listar contenido de un directorio) y `readJavaFile(path)` (leer contenido de archivos `.java`, `.xml`, `.md`). El LLM recibe la ruta del proyecto en el prompt (`analyze -p <dir>`) y decide qué rutas pasar a cada herramienta.
- Todo corre en proceso (sin servidor MCP externo), lo que permite uso en entornos restringidos (VPN, sin APIs externas).

## Componentes principales

| Componente           | Clase / tecnología   | Responsabilidad                                              |
|---------------------|----------------------|--------------------------------------------------------------|
| Entrada              | Picocli              | CLI: `sentinel analyze -p/--path <dir>`                     |
| Comando analyze      | `AnalyzeCommand`     | Crea OllamaConfig, ChatModel, AiServices + SentinelAgent + Tools |
| Agente               | `SentinelAgent`      | Interfaz con `@SystemMessage` y `analyze(prompt)`; implementada por AiServices |
| Modelo local         | `OllamaConfig`       | Configuración y creación de `ChatModel` (Ollama qwen2.5-coder:7b) |
| Herramientas (MCP-style) | `ProjectMcpTools` | `@Tool` readProjectStructure, readJavaFile                   |
| Logging              | SLF4J/Logback        | Trazas y depuración                                          |
| Serialización        | Jackson              | JSON (y futuro uso en mensajes/tools si se extiende)          |

## Estructura de paquetes

| Paquete                 | Contenido                                                                 |
|-------------------------|---------------------------------------------------------------------------|
| `com.sentinel.arch`     | `SentinelMain`, `SentinelCommand` (punto de entrada y comando raíz)       |
| `com.sentinel.arch.cli` | `AnalyzeCommand` (comando `analyze -p/--path`)                            |
| `com.sentinel.arch.agent` | `SentinelAgent` (interfaz del agente con @SystemMessage)               |
| `com.sentinel.arch.ollama` | `OllamaConfig` (URL, modelo, creación de ChatModel)                   |
| `com.sentinel.arch.mcp` | `ProjectMcpTools` (herramientas @Tool para estructura y lectura de archivos) |
| `com.sentinel.arch.mcp.server` | **✅ IMPLEMENTADO:** `SentinelMcpServer` - Servidor MCP sobre STDIO (JSON-RPC 2.0) |

## Workflows del agente (ciclo de vida)

El servidor Sentinel-Arch orquesta su comportamiento mediante **workflows versionados** ubicados en `.sentinel/workflows/`:

- `WF-01-PROJECT-DISCOVERY.md`  
  Define cómo se descubre el proyecto:
  - Escaneo de `pom.xml` y módulos Maven.
  - Recorrido de paquetes bajo `src/main/java`.
  - Reglas de exclusión estrictas (`target/`, `.git/`, `.idea/`, `*.class`, etc.).
  - Generación de una representación jerárquica simplificada (JSON + vista textual).

- `WF-02-ARCH-INTELLIGENCE.md`  
  Define la inteligencia arquitectónica:
  - Detección de patrones (`@SpringBootApplication`, `@RestController`, `@Service`, `@Repository`, `@FeignClient`, etc.).
  - Extracción de relaciones entre servicios (FeignClient, RestTemplate/WebClient).
  - Reglas de generación de diagramas **Mermaid `graph TD`** con convenciones de nodos y aristas.

- `WF-03-SENTINEL-REPORTING.md`  
  Estandariza el reporte final:
  - Estructura de reporte Markdown (header con timestamp, resumen ejecutivo, tablas de dependencias, diagrama de arquitectura, sugerencias de refactorización Clean Code).
  - Convención de nombres `SENTINEL_REPORT_YYYYMMDD[_NN].md`.
  - Trazabilidad entre datos de entrada y salida.

- `WF-04-MCP-PROTOCOL-SECURITY.md`  
  Fija las reglas de seguridad y errores:
  - Uso de códigos de error JSON-RPC estándar (`-32600`, `-32601`, `-32602`, `-32603`) y errores de servidor (`-32000`–`-32099`).
  - Operación bajo STDIO con **acceso de solo lectura** al path proporcionado, salvo para escribir reportes de `WF-03`.
  - Validación de paths, prevención de path traversal y logging controlado.

### Flujo de alto nivel

1. **Descubrimiento** (`WF-01`): se analiza `rootPath`, se construye el árbol de módulos y paquetes excluyendo artefactos generados.
2. **Inteligencia arquitectónica** (`WF-02`): se detectan roles (controllers, services, repositories) y relaciones entre ellos, produciendo un grafo lógico + Mermaid.
3. **Reporte** (`WF-03`): se ensamblan los hallazgos en un reporte `SENTINEL_REPORT_YYYYMMDD.md`.
4. **Seguridad / protocolo** (`WF-04`): todos los pasos anteriores se ejecutan respetando las reglas JSON-RPC y las restricciones de acceso al filesystem.

Este ciclo de vida debe ser seguido por cualquier nueva Tool MCP de alto nivel que pretenda analizar un proyecto completo (por ejemplo, `analyze_project`).

---

*Al añadir o cambiar componentes, comandos, herramientas o workflows, actualizar este archivo y `AGENTS.md` para que la documentación siga alineada con el código y los procesos del agente.*
