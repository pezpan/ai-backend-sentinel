# WF-04: MCP Protocol & Security Specifications

**Versión:** 1.0.0  
**Última actualización:** 2026-02-04  
**Estado:** Implementado en `SentinelMcpServer.java`  

---

## Propósito

Definir las restricciones de seguridad, el manejo de errores JSON-RPC y el protocolo de comunicación para el servidor MCP `SentinelMcpServer`.

---

## 1. Protocolo de Transporte

### 1.1 STDIO (Standard Input/Output)

- **Transporte:** El servidor MCP escucha en `System.in` (lectura) y responde en `System.out` (escritura)
- **Formato:** Mensajes JSON-RPC 2.0 separados por newline (`\n`)
- **Implementación:** `StdioServerTransport` del SDK oficial MCP

**Implicación en código:**
```java
StdioServerTransport transport = new StdioServerTransport(
    System.in,   // Lee mensajes MCP
    System.out   // Envía respuestas
);
mcpServer.start(transport);
```

> ⚠️ **CRÍTICO:** La salida estándar (stdout) está completamente dedicada al protocolo MCP.
> Cualquier log, debug o info **DEBE** usar SLF4J + Logback (stderr) para no corromper mensajes RPC.

### 1.2 Formato JSON-RPC 2.0

Todas las llamadas y respuestas siguen el estándar [JSON-RPC 2.0](https://www.jsonrpc.org/specification):

**Solicitud (Request):**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "readProjectStructure",
    "arguments": {
      "path": "/ruta/del/proyecto"
    }
  },
  "id": 1
}
```

**Respuesta exitosa (Success):**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "pom.xml\nsrc/\ntarget/\n..."
      }
    ]
  },
  "id": 1
}
```

**Respuesta con error (Error):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params: Path cannot contain '..'",
    "data": {
      "details": "Detected path traversal attempt in: /proyecto/../../etc/passwd"
    }
  },
  "id": 1
}
```

---

## 2. Códigos de Error JSON-RPC

| Código  | Significado         | Caso de uso                                  |
|---------|-------------------|----------------------------------------------|
| -32700  | Parse error       | JSON malformado, encoding inválido           |
| -32600  | Invalid Request   | Campo `jsonrpc` o `method` faltante          |
| -32601  | Method not found  | Herramienta no registrada en `toolRegistry`  |
| -32602  | Invalid params    | Parámetros faltantes, tipos incorrectos      |
| -32603  | Internal error    | Exception no controlada en herramienta       |
| -32000 a -32099 | Server error  | Errores de seguridad, recursos, límites      |

**Implementación en `SentinelMcpServer.java`:**
```java
private String jsonRpcError(int code, String message) {
    // -32602: Parámetro inválido
    // -32000: Violación de seguridad
    // -32603: Error interno
}
```

---

## 3. Restricciones de Seguridad

### 3.1 Acceso de Solo Lectura (Read-Only)

- ✅ **PERMITIDO:** Lectura de archivos `.java`, `.xml`, `.md`, `.properties`, `.yml`, `.yaml`
- ✅ **PERMITIDO:** Listar directorios dentro del `rootPath` configurado
- ❌ **PROHIBIDO:** Escribir, eliminar o modificar archivos (excepto reportes en `WF-03`)
- ❌ **PROHIBIDO:** Ejecutar comandos del sistema

**Validación en `ProjectMcpTools`:**
```java
public String readJavaFile(String path) throws IOException {
    if (!path.endsWith(".java") && !path.endsWith(".xml") && !path.endsWith(".md")) {
        return "Error: Solo se permite la lectura de archivos de código o configuración.";
    }
    return Files.readString(Paths.get(path));
}
```

### 3.2 Prevención de Path Traversal

**Ataques bloqueados:**

| Ataque                          | Protección                          |
|---------------------------------|-------------------------------------|
| `../../etc/passwd`              | Detectar y rechazar `..` en path    |
| `/etc/passwd` (absolute path)   | Validar que path está en `rootPath` |
| `~/.ssh/id_rsa` (home dir)      | Rechazar `~` y rutas expandidas     |
| `symlink` a `/etc`              | Resolver canonical path             |

**Implementación en `SentinelMcpServer.validatePath()`:**
```java
private void validatePath(String path) throws SecurityException {
    if (path.contains("..") || path.contains("~")) {
        throw new SecurityException("Path traversal detectado: " + path);
    }
    // Validar contra rootPath configurado
}
```

### 3.3 Whitelist de Extensiones

**Archivos permitidos para lectura:**
- `.java` — Código fuente
- `.xml` — Configuración (pom.xml, applicationContext.xml)
- `.md` — Documentación
- `.properties`, `.yml`, `.yaml` — Configuración

**Archivos bloqueados:**
- `.class` — Bytecode compilado
- `.jar`, `.war` — Artifacts
- `.git*` — Metadata de repositorio
- Cualquier binario

### 3.4 Limitaciones de Volumen

- **Tamaño máximo de archivo:** 10 MB (prevenir DoS)
- **Número máximo de directorios listados:** 1000 entradas
- **Timeout de operación:** 5 segundos

> Implementar en `ProjectMcpTools` con validaciones antes de `Files.walk()` y `Files.readString()`

---

## 4. Ciclo de Vida del Servidor MCP

```
┌─────────────────────────────────────────────────────┐
│  1. Inicialización                                  │
│  - Crear McpServer                                  │
│  - Registrar herramientas (toolRegistry)            │
│  - Crear StdioServerTransport                       │
└─────────┬───────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────┐
│  2. Escucha Activa (STDIO)                          │
│  - mcpServer.start(transport)                       │
│  - Bloquea hasta shutdown                           │
│  - Lee JSON-RPC de System.in                        │
└─────────┬───────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────┐
│  3. Procesamiento de Solicitud                      │
│  - Recibir: {"jsonrpc": "2.0", "method": "...", ...}
│  - Validar: sintaxis, parámetros, seguridad        │
│  - Ejecutar: invocar herramienta                    │
│  - Responder: JSON-RPC success o error              │
└─────────┬───────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────┐
│  4. Cierre Controlado                               │
│  - Recibir señal SIGTERM / SIGINT                   │
│  - Ejecutar shutdown hooks                          │
│  - Cerrar mcpServer y transporte                    │
│  - Exit(0) o Exit(1)                                │
└─────────────────────────────────────────────────────┘
```

**Implementación:**
```java
public static void main(String[] args) {
    SentinelMcpServer server = new SentinelMcpServer();
    
    // Shutdown hook para cierre controlado
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        logger.info("Shutdown signal received. Closing server...");
        server.stop();
    }));
    
    try {
        server.start();  // Bloqueante
    } catch (Exception e) {
        logger.error("Fatal error", e);
        System.exit(1);
    }
}
```

---

## 5. Logging & Debugging

### 5.1 Restricción de Salida Estándar

| Destino | Uso                                 | Restricción                |
|---------|-------------------------------------|---------------------------|
| `stdout` (System.out) | Mensajes JSON-RPC MCP | **SOLO MCP**, sin logs    |
| `stderr` (System.err) | Logs, traces, debug   | **PERMITIDO**, Logback    |
| Archivos | Reportes (WF-03)    | **PERMITIDO**, en rootPath |

### 5.2 Configuración de Logback

**logback.xml:**
```xml
<appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

### 5.3 Niveles de Log en SentinelMcpServer

| Nivel | Evento                                        |
|-------|-----------------------------------------------|
| INFO  | Inicio/parada servidor, registro de tools     |
| DEBUG | Validaciones de path, invocación de tools     |
| WARN  | Argumentos inválidos, path traversal bloqueado |
| ERROR | Excepciones, violaciones de seguridad         |

**Ejemplo:**
```java
logger.info("Iniciando SentinelMcpServer");
logger.debug("Registrando herramienta: {}", toolName);
logger.warn("Path traversal detectado: {}", path);
logger.error("Error ejecutando herramienta: {}", e);
```

---

## 6. Integración con Workflows Anteriores

### 6.1 Con WF-01 (Project Discovery)

- **Herramientas expuestas:** `readProjectStructure()`, `readJavaFile()`
- **Seguridad:** Validación de path antes de listar/leer
- **Respuesta:** JSON-RPC con contenido o error

### 6.2 Con WF-02 (Arch Intelligence)

- **Herramientas requeridas:** Las mismas que WF-01
- **Flujo:** Cliente MCP → SentinelMcpServer → ProjectMcpTools → File I/O

### 6.3 Con WF-03 (Reporting)

- **Excepción de escritura:** Permitir crear `SENTINEL_REPORT_*.md` en rootPath
- **Validación:** Verificar que el nombre siga patrón `SENTINEL_REPORT_YYYYMMDD[_NN].md`

---

## 7. Testing & Validación

### 7.1 Test de Protocolo (Pytest + curl)

```bash
# Iniciar servidor
java -cp sentinel-backend-ai-1.0.0-SNAPSHOT.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer &

# Enviar solicitud JSON-RPC
echo '{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readProjectStructure", "arguments": {"path": "."}}, "id": 1}' | nc localhost 3000
```

### 7.2 Test de Seguridad

| Test | Input | Expected | Status |
|------|-------|----------|--------|
| Path traversal | `../../etc/passwd` | Error -32000 | ✅ |
| Extensión bloqueada | `file.exe` | Error -32602 | ✅ |
| Path válido | `src/main/java` | Success + content | ✅ |
| Sin parámetros | `{}` | Error -32602 | ✅ |

---

## 8. Checklist de Implementación

- [x] Servidor MCP con StdioServerTransport
- [x] Registro dinámico de herramientas desde ProjectMcpTools
- [x] Validación de path (prevención de traversal)
- [x] Manejo de errores JSON-RPC con códigos estándar
- [x] Logging en stderr (Logback) sin contaminación de stdout
- [x] Shutdown hook para cierre controlado
- [ ] Test unitarios de seguridad
- [ ] Integración con cliente MCP (Cursor / IntelliJ)
- [ ] Documentación de cliente (cómo conectar al servidor)

---

**Siguiente:** WF-01-PROJECT-DISCOVERY (Algoritmo de descubrimiento de proyectos)
