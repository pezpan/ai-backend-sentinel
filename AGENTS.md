# Sentinel-Arch — Proveedor MCP de capacidades de análisis

## Identidad

**Nombre:** Sentinel-Arch  
**Rol:** Proveedor de capacidades MCP para análisis de arquitectura y código Java.  
**Contexto:** Servidor MCP local que expone herramientas de análisis de microservicios Java corporativos (por ejemplo bajo VPN), utilizando LangChain4j + Ollama para el razonamiento y el SDK oficial MCP para la comunicación con IDEs (Cursor, IntelliJ, etc.).

## Objetivo

Ofrecer, vía MCP, un conjunto de herramientas especializadas para:

- Entender la estructura de proyectos Java (mono-repos, microservicios, multi-módulo).
- Analizar la arquitectura (capas, dependencias, puntos de integración).
*- Generar documentación y diagramas (Mermaid) que sirvan como entrega inmediata a equipos de desarrollo y arquitectura.*

## Capacidades del servidor MCP

- **Exposición de herramientas (tools):**  
  - Exploración de estructura de proyecto (directorios, módulos, servicios).  
  - Lectura de archivos clave (`pom.xml`, controladores, servicios, configuraciones).  
  - Análisis de arquitectura usando LangChain4j + modelo local (`qwen2.5-coder:3b` vía Ollama).
- **Integración con IDEs / clientes MCP:**  
  - El servidor escucha por **STDIO** y habla JSON-RPC siguiendo el estándar MCP.  
  - Los clientes (Cursor, IntelliJ, etc.) descubren automáticamente las herramientas expuestas.
- **Documentación y diagramas:**  
  - Generación de resúmenes de arquitectura en Markdown.  
  - Diagramas en **Mermaid** (secuencia, componentes, C4 nivel alto).
- **Clean Code / arquitectura:**  
  - Revisión de convenciones, acoplamiento, cohesión, patrones y oportunidades de refactor.

## Restricciones

- No ejecutar código de usuario ni escribir en el sistema de archivos salvo que la herramienta lo especifique explícitamente.
- Mantenerse dentro del directorio de trabajo configurado por el cliente MCP (p. ej. raíz del proyecto).
- No asumir tecnologías no presentes: las sugerencias y diagnósticos deben basarse en el código real.

## Formato de interacción

- **Protocolo:** Model Context Protocol (MCP) sobre STDIO.
- **Mensajes:** JSON-RPC con herramientas, recursos y (futuro) prompts/ completions.
- **Respuestas:** Markdown estructurado, con secciones claras y, cuando aplique, bloques Mermaid.

## Herramientas MCP previstas

Estas herramientas se implementan en el backend (paquetes `com.sentinel.arch.mcp` y `com.sentinel.arch.mcp.server`) usando el SDK Java MCP:

- **`analyze_project`**  
  - **Descripción:** Analiza la arquitectura de un proyecto Java dado un directorio raíz.  
  - **Parámetros clave:** `rootPath` (ruta absoluta o relativa).  
  - **Salida:** Resumen de arquitectura + diagrama Mermaid.

- **`list_project_structure`**  
  - **Descripción:** Lista archivos y carpetas de un directorio para entender la estructura del microservicio.  
  - **Relación con código actual:** Evoluciona la lógica de `ProjectMcpTools.readProjectStructure`.

- **`read_code_file`**  
  - **Descripción:** Lee contenido de archivos de código / configuración (`.java`, `.xml`, `.md`, etc.) con validación básica de seguridad.  
  - **Relación con código actual:** Evoluciona la lógica de `ProjectMcpTools.readJavaFile`.

LangChain4j y Ollama se usan internamente para “interpretar” la información que estas herramientas devuelven y para construir respuestas ricas para el cliente MCP.

## Invocación desde un cliente MCP

El servidor se distribuye como JAR ejecutable (`sentinel-backend-ai`) y se lanza típicamente así:

```bash
java -jar sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar
```

Un cliente MCP (como Cursor) lo configura como servidor basado en **STDIO**, de forma que:

- Envía peticiones JSON-RPC por `stdin`.
- Recibe respuestas y notificaciones por `stdout`.

La configuración concreta en Cursor/IntelliJ se hace en sus respectivos archivos de configuración MCP, apuntando al comando anterior y al directorio raíz del proyecto.

---

*Al modificar las herramientas MCP, el modelo de análisis o el flujo de interacción con el IDE, actualizar también `ARCHITECTURE.md` para que refleje las capacidades y el flujo actuales del servidor MCP.*
