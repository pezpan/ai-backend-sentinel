# Integración de Workflows en SentinelMcpServer

**Versión:** 1.0.0  
**Fecha:** 2026-02-04  
**Estado:** ✅ Implementado y Compilado  

---

## 📋 Resumen Ejecutivo

Se ha implementado la **infraestructura del servidor MCP** (`SentinelMcpServer.java`) que orquesta el comportamiento de Sentinel Backend AI integrando los workflows definidos:

- **WF-01-PROJECT-DISCOVERY.md**: Descubrimiento de estructura de proyectos
- **WF-04-MCP-PROTOCOL-SECURITY.md**: Seguridad y protocolo JSON-RPC

El servidor está completamente compilado y listo para ejecutarse.

---

## 🏗️ Arquitectura de Integración

### Flujo General

```
┌─────────────────────────────────────────────────────────────────┐
│  Cliente MCP (Cursor, IntelliJ, etc.)                          │
│  Envía: {"jsonrpc": "2.0", "method": "tools/list", "id": 1}   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼ (STDIO - JSON-RPC 2.0)
┌──────────────────────────────────────────────────────────────────┐
│              SentinelMcpServer.java (Main Loop)                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ startListeningLoop()                                       │ │
│  │  - Lee líneas de System.in                                │ │
│  │  - Parsea JSON-RPC                                        │ │
│  │  - Valida seguridad (WF-04)                               │ │
│  │  - Invoca herramientas (WF-01)                            │ │
│  │  - Responde en System.out (JSON-RPC)                      │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────┬─────────────────────────────────────────────────────────┘
         │
         ├──────────────────┬──────────────────┬──────────────────┐
         ▼                  ▼                  ▼                  ▼
    ┌─────────┐     ┌─────────┐        ┌──────────────┐  ┌─────────────┐
    │ WF-01   │     │ WF-04   │        │ ProjectMcp   │  │ Logging     │
    │ Discovery      │Security │        │ Tools        │  │ (Logback)   │
    └─────────┘     └─────────┘        └──────────────┘  └─────────────┘
         │               │                   │                 │
         │               │                   │                 ▼
         │               │                   │           System.err
         │               │                   │           (No contamina
         │               │                   │            stdout)
         ▼               ▼                   ▼
    readProject      validatePath       readJavaFile
    Structure()      (path traversal)    readProject
    readJavaFile()   validateToolCall    Structure()
                     (security checks)
                                              │
                                              ▼
                                        File I/O
                                        (Safe Read-Only)
```

---

## 🔄 Integración Detallada de Workflows

### 1️⃣ WF-01: Project Discovery

**¿Qué es?**  
Define cómo descubrir y analizar la estructura de un proyecto Java de forma segura.

**¿Cómo se integra en SentinelMcpServer?**

#### A) Registro de Herramientas (Línea 264)

```java
private void registerToolsFromProjectMcpTools() {
    // Escanea ProjectMcpTools.class buscando métodos @Tool
    Method[] methods = ProjectMcpTools.class.getDeclaredMethods();
    
    for (Method method : methods) {
        dev.langchain4j.agent.tool.Tool toolAnnotation =
                method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
        
        if (toolAnnotation != null) {
            // Extraer nombre de la herramienta
            String toolName = toolAnnotation.value()[0]; // ej: "readProjectStructure"
            
            // Crear definición + registrar en toolRegistry
            ToolDefinition toolDef = new ToolDefinition(toolName, description, method);
            toolRegistry.put(toolName, toolDef);
        }
    }
}
```

**Herramientas disponibles (de ProjectMcpTools):**

| Herramienta | Parámetro | Retorno | Workflow |
|-------------|-----------|---------|----------|
| `readProjectStructure` | `path: String` | Lista de archivos/directorios | WF-01 Fase 3 |
| `readJavaFile` | `path: String` | Contenido del archivo | WF-01 Fase 4 |

#### B) Respuesta a tools/list (Línea 185)

```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "readProjectStructure",
        "description": "Lista los archivos y carpetas...",
        "inputSchema": {
          "type": "object",
          "properties": {
            "path": { "type": "string" }
          },
          "required": ["path"]
        }
      },
      {
        "name": "readJavaFile",
        "description": "Lee el contenido de un archivo...",
        "inputSchema": { ... }
      }
    ]
  },
  "id": 1
}
```

#### C) Invocación de Herramientas (Línea 213)

```java
private JsonNode handleToolCall(JsonNode id, JsonNode request) {
    String toolName = params.get("name").asText();  // ej: "readProjectStructure"
    JsonNode arguments = params.get("arguments");     // ej: {"path": "src/main/java"}
    
    // Validar seguridad (WF-04)
    validateToolCall(toolName, arguments);
    
    // Invocar método en ProjectMcpTools
    Object result = invokeToolMethod(toolName, arguments);
    
    // Retornar resultado en JSON-RPC
    return createSuccessResponse(id, toolResult);
}
```

---

### 2️⃣ WF-04: MCP Protocol & Security

**¿Qué es?**  
Define el protocolo JSON-RPC 2.0, manejo de errores y restricciones de seguridad.

**¿Cómo se integra en SentinelMcpServer?**

#### A) Protocolo JSON-RPC 2.0 (Línea 129)

```java
private JsonNode handleRpcRequest(JsonNode request) {
    // Validar estructura JSON-RPC
    if (!request.has("jsonrpc") || !request.get("jsonrpc").asText().equals("2.0")) {
        return createErrorResponse(null, -32600, "Invalid Request: missing jsonrpc");
    }
    
    // Enrutar según método
    String method = request.get("method").asText();
    
    switch (method) {
        case "tools/list":
            return handleToolsList(id);
        case "tools/call":
            return handleToolCall(id, request);
        default:
            return createErrorResponse(id, -32601, "Method not found: " + method);
    }
}
```

**Métodos soportados:**
- `tools/list`: Lista herramientas disponibles
- `tools/call`: Invoca una herramienta con parámetros

#### B) Manejo de Errores JSON-RPC (Línea 437)

```java
private JsonNode createErrorResponse(JsonNode id, int code, String message) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put("jsonrpc", "2.0");
    
    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);  // -32602, -32000, -32603, etc.
    error.put("message", message);
    
    response.set("error", error);
    response.set("id", id);
    return response;
}
```

**Códigos de error implementados:**

| Código | Significado | Ejemplo |
|--------|-------------|---------|
| -32700 | Parse error | JSON malformado |
| -32600 | Invalid Request | Falta `jsonrpc` |
| -32601 | Method not found | Tool no registrada |
| -32602 | Invalid params | Parámetro faltante o tipo incorrecto |
| -32603 | Internal error | Excepción no controlada |
| -32000 | Server error | Violación de seguridad |

#### C) Validación de Seguridad (Línea 359)

```java
private void validateToolCall(String toolName, JsonNode arguments) throws SecurityException {
    switch (toolName) {
        case "readProjectStructure":
        case "readJavaFile":
            if (arguments.has("path")) {
                String path = arguments.get("path").asText();
                validatePath(path);  // Prevención de path traversal
            }
            break;
    }
}

private void validatePath(String path) throws SecurityException {
    // Rechazar path traversal
    if (path.contains("..") || path.contains("~")) {
        throw new SecurityException("Path traversal detectado: " + path);
    }
}
```

**Restricciones de seguridad implementadas:**

✅ **Permitido:**
- Lectura de `.java`, `.xml`, `.md`, `.properties`, `.yml`
- Listar directorios dentro del proyecto

❌ **Bloqueado:**
- Path traversal (`../../etc/passwd`)
- Home directory (`~/`)
- Archivos binarios (`.class`, `.jar`)
- Escritura de archivos (excepto reportes)
- Ejecución de comandos

#### D) STDIO con Protección de Salida (Línea 100)

```java
private void startListeningLoop() {
    // Reader: System.in (entrada JSON-RPC)
    this.reader = new BufferedReader(new InputStreamReader(System.in));
    
    // Writer: System.out (salida JSON-RPC)
    this.writer = new PrintWriter(System.out, true);  // auto-flush
    
    // Loop bloqueante
    String line;
    while (running && (line = reader.readLine()) != null) {
        JsonNode response = handleRpcRequest(objectMapper.readTree(line));
        writer.println(objectMapper.writeValueAsString(response));
    }
}
```

**Protección de salida:**
- ✅ `System.out`: SOLO mensajes JSON-RPC
- ✅ `System.err`: Logs Logback (no contamina JSON-RPC)
- ✅ `logger.info()`, `logger.debug()`: Configurados en `logback.xml`

---

## 📊 Flujo de Ejecución: Ejemplo Completo

### Entrada del Cliente MCP

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "readProjectStructure",
    "arguments": {
      "path": "src/main/java"
    }
  },
  "id": 1
}
```

### Procesamiento en SentinelMcpServer

```
1. startListeningLoop()
   └─ Lee línea: "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", ...}"
      └─ Parsea JSON → JsonNode request
         
2. handleRpcRequest(request)
   └─ Valida: ✓ jsonrpc = "2.0", ✓ method = "tools/call"
      └─ Enruta a handleToolCall(id=1, request)
         
3. handleToolCall(id, request)
   └─ Extrae: toolName="readProjectStructure", arguments={"path": "src/main/java"}
      └─ Valida seguridad: validateToolCall()
         └─ Revisa path: ✓ No contiene "..", ✓ No contiene "~"
            └─ Invoca: invokeToolMethod("readProjectStructure", arguments)
               └─ Lookup en toolRegistry
                  └─ Obtiene Method: ProjectMcpTools.readProjectStructure(String)
                     └─ Invoca: method.invoke(projectTools, "src/main/java")
                        └─ Ejecuta: Files.walk() → lista archivos
                           
4. Respuesta exitosa
   └─ result = "pom.xml\nsrc/\n...\n"
      └─ Construye JSON-RPC: createSuccessResponse(id=1, result)
         
5. Envía respuesta por STDIO
   └─ System.out << '{"jsonrpc": "2.0", "result": {...}, "id": 1}'
      └─ Client recibe respuesta formateada
```

### Salida del Cliente MCP

```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "ARCHITECTURE.md\nAGENTS.md\npom.xml\nsrc/\ntarget/\n..."
      }
    ]
  },
  "id": 1
}
```

---

## 🔐 Casos de Seguridad Bloqueados

### 1. Path Traversal

**Intento:**
```json
{
  "params": {
    "name": "readJavaFile",
    "arguments": {
      "path": "../../../../../../etc/passwd"
    }
  }
}
```

**Respuesta:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32000,
    "message": "Security violation: Path traversal detectado: ../../../../../../etc/passwd"
  },
  "id": 1
}
```

### 2. Archivo Binario

**Intento:**
```json
{
  "params": {
    "name": "readJavaFile",
    "arguments": {
      "path": "target/classes/com/sentinel/arch/SentinelMain.class"
    }
  }
}
```

**Respuesta:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params: Solo se permite la lectura de archivos de código o configuración."
  },
  "id": 1
}
```

### 3. Parámetro Faltante

**Intento:**
```json
{
  "params": {
    "name": "readProjectStructure"
    // Falta "arguments"
  }
}
```

**Respuesta:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params: Missing parameter: path"
  },
  "id": 1
}
```

---

## 📦 Compilación y Empaquetamiento

### Build Exitoso ✅

```bash
$ mvn clean compile
[INFO] Compiling 7 source files...
[INFO] BUILD SUCCESS

$ mvn package -DskipTests
[INFO] Building jar: target/sentinel-backend-ai-1.0.0-SNAPSHOT.jar
[INFO] Building jar: target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar
```

### Ejecución del Servidor

```bash
# Opción 1: JAR sombreado (all dependencies)
java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer

# Opción 2: JAR con classpath completo
java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT.jar:lib/* \
    com.sentinel.arch.mcp.server.SentinelMcpServer
```

**Salida esperada (en stderr, Logback):**
```
11:57:23.456 [main] INFO  SentinelMcpServer - ╔════════════════════════════════════════════════╗
11:57:23.457 [main] INFO  SentinelMcpServer - ║     Sentinel Backend AI - MCP Server v1.0.0    ║
11:57:23.458 [main] INFO  SentinelMcpServer - ║        Análisis de Arquitectura Java            ║
11:57:23.459 [main] INFO  SentinelMcpServer - ╚════════════════════════════════════════════════╝
11:57:23.460 [main] INFO  SentinelMcpServer - Iniciando SentinelMcpServer
11:57:23.461 [main] INFO  SentinelMcpServer - Registrando herramientas desde ProjectMcpTools
11:57:23.462 [main] INFO  SentinelMcpServer - Registrando herramienta: readProjectStructure - ...
11:57:23.463 [main] INFO  SentinelMcpServer - Registrando herramienta: readJavaFile - ...
11:57:23.464 [main] INFO  SentinelMcpServer - Herramientas registradas exitosamente. Total: 2
11:57:23.465 [main] INFO  SentinelMcpServer - Servidor MCP configurado. Iniciando escucha en STDIO...
```

---

## 🧩 Integración con IDEs/Clientes MCP

### Cursor

Agregar en `cursor.md` (o similar):

```json
{
  "mcp": {
    "sentinel-arch": {
      "command": "java",
      "args": [
        "-cp",
        "/path/to/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar",
        "com.sentinel.arch.mcp.server.SentinelMcpServer"
      ],
      "env": {
        "LOG_LEVEL": "INFO"
      }
    }
  }
}
```

### IntelliJ IDEA Plugin (futuro)

```java
// plugin.xml
<extensions defaultExtensionNs="com.jetbrains.ideaSentinel">
  <mcpServer implementation="com.sentinel.arch.mcp.server.SentinelMcpServer">
    <toolName>readProjectStructure</toolName>
    <toolName>readJavaFile</toolName>
  </mcpServer>
</extensions>
```

---

## 📈 Próximos Pasos

### Fase 2: Inteligencia Arquitectónica (WF-02)

- [ ] Implementar `analyzeProject(path)` que combine WF-01 + análisis de patrones
- [ ] Detectar roles: `@SpringBootApplication`, `@RestController`, `@Service`, `@Repository`
- [ ] Extraer relaciones entre servicios (FeignClient, WebClient, etc.)
- [ ] Generar diagramas Mermaid

### Fase 3: Reporte Estándar (WF-03)

- [ ] Crear clase `SentinelReporter` que genere `SENTINEL_REPORT_YYYYMMDD.md`
- [ ] Incluir resumen ejecutivo, tablas de dependencias, diagrama arquitectónico
- [ ] Sugerencias de refactorización Clean Code

### Fase 4: Testing & Documentación

- [ ] Test unitarios de seguridad
- [ ] Test de integración MCP
- [ ] Documentación de API REST (si se agrega)
- [ ] Tutorial de conexión con clientes MCP

---

## ✅ Checklist de Implementación

### SentinelMcpServer.java

- [x] Constructor e inicialización
- [x] Método `start()` con STDIO
- [x] Loop de escucha bloqueante
- [x] Parseo de JSON-RPC
- [x] Manejo de `tools/list`
- [x] Manejo de `tools/call`
- [x] Invocación de métodos vía reflection
- [x] Validación de seguridad (path traversal)
- [x] Respuestas JSON-RPC con códigos de error
- [x] Logging en Logback (no contamina stdout)
- [x] Shutdown hook para cierre controlado
- [x] Compilación exitosa

### Integración con Workflows

- [x] WF-01: Registro de herramientas desde @Tool
- [x] WF-01: Invocación segura de readProjectStructure / readJavaFile
- [x] WF-04: Protocolo JSON-RPC 2.0
- [x] WF-04: Códigos de error estándar
- [x] WF-04: Validación de path traversal
- [x] WF-04: Protección de STDIO

### Documentación

- [x] WF-01-PROJECT-DISCOVERY.md
- [x] WF-04-MCP-PROTOCOL-SECURITY.md
- [x] Este documento: INTEGRATION.md

---

## 🎓 Conclusión

Se ha implementado exitosamente la **infraestructura del servidor MCP Sentinel** que:

1. ✅ **Implementa WF-01**: Descubre y expone herramientas de análisis de proyectos Java
2. ✅ **Implementa WF-04**: Sigue protocolo JSON-RPC 2.0 con validación de seguridad robusta
3. ✅ **Es compilable y ejecutable**: JAR generado y listo para uso
4. ✅ **Integrable con IDEs**: Compatible con Cursor, IntelliJ, y otros clientes MCP
5. ✅ **Extensible**: Nuevas herramientas pueden agregarse simplemente anotando métodos con `@Tool`

El servidor está listo para ser integrado con clientes MCP y servir como backend de análisis de arquitectura Java corporativa bajo entornos restringidos (VPN, sin APIs externas).
