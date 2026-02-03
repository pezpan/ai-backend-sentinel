# Arquitectura del proyecto Sentinel Backend AI

## Visión general

**Sentinel Backend AI** es un agente de IA que se ejecuta en local y analiza microservicios (u otros proyectos Java) usando un modelo de lenguaje local (Ollama) y el **Model Context Protocol (MCP)** para acceder al sistema de archivos y al código.

```
┌─────────────────────────────────────────────────────────────────┐
│                     sentinel-backend-ai (JVM 21)                  │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────────────┐   │
│  │   Picocli   │──▶│ AnalyzeCommand│──▶│ OllamaConfig / LLM   │   │
│  │  (CLI)      │   │ --path <dir>  │   │ (LangChain4j)        │   │
│  └─────────────┘   └──────┬───────┘   └──────────┬───────────┘   │
│                           │                      │               │
│                           │   ┌──────────────────▼───────────┐   │
│                           └──▶│  MCP (Model Context Protocol) │   │
│                               │  - Sistema de archivos local   │   │
│                               │  - Contexto para el LLM       │   │
│                               └──────────────────┬───────────┘   │
└──────────────────────────────────────────────────┼───────────────┘
                                                    │
                                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  Ollama (localhost:11434)                                        │
│  Modelo: qwen2.5-coder:7b                                        │
└─────────────────────────────────────────────────────────────────┘
```

## Uso de MCP

- **MCP** permite exponer capacidades (por ejemplo, lectura de archivos, listado de directorios) como “herramientas” que el LLM puede invocar durante una conversación o un análisis.
- En este proyecto, MCP se usa para **conectar el LLM con el sistema de archivos local**: el agente puede explorar el `<directorio>` pasado con `analyze --path` sin que el usuario tenga que pegar manualmente todo el código en el prompt.
- El backend actúa como cliente MCP y orquesta las llamadas entre el modelo (Ollama vía LangChain4j) y los recursos (p. ej. servidor MCP de sistema de archivos), de modo que el análisis sea repetible y seguro en entornos corporativos (VPN, sin salida a APIs externas si no se desea).

## Componentes principales

| Componente        | Tecnología   | Responsabilidad                          |
|-------------------|-------------|-------------------------------------------|
| Entrada           | Picocli     | CLI: `sentinel analyze --path <dir>`      |
| Orquestación      | Java 21     | Comandos, flujo de análisis, inyección   |
| LLM local         | LangChain4j + Ollama | Chat y (futuro) embeddings          |
| Acceso a código   | MCP         | Herramientas para leer/explorar archivos  |
| Logging           | SLF4J/Logback | Trazas y depuración                     |
| Serialización     | Jackson     | JSON y mensajes MCP                      |

## Estructura de paquetes (resumen)

- `com.sentinel.arch` — Punto de entrada (`SentinelMain`), comando raíz.
- `com.sentinel.arch.cli` — Comandos Picocli (p. ej. `AnalyzeCommand`).
- `com.sentinel.arch.ollama` — Configuración y verificación de Ollama (`OllamaConfig`).

La integración MCP y el flujo completo de análisis (enviar contexto de archivos al LLM, parsear respuestas, generar informes) se irán añadiendo en capas sobre esta base.
