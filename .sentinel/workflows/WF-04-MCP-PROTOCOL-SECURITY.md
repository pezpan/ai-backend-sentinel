## WF-04 MCP PROTOCOL SECURITY

### 1. Objetivo

Establecer las **normas de seguridad y manejo de errores** para el servidor MCP Sentinel-Arch, asegurando que:

- Respeta el estándar JSON-RPC 2.0 en sus errores.
- Opera bajo transporte **STDIO** con el menor privilegio posible.
- Limita las operaciones sobre el sistema de archivos a **solo lectura**, salvo la escritura explícita de reportes según `WF-03`.

### 2. Manejo de errores JSON-RPC

El servidor MCP debe devolver errores en el formato JSON-RPC 2.0 estándar:

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32601,
    "message": "Method not found",
    "data": {
      "details": "Tool 'unknown_tool' is not registered"
    }
  },
  "id": "123"
}
```

#### 2.1 Códigos estándar obligatorios

Se deben utilizar, al menos, los siguientes códigos JSON-RPC:

- `-32600` — **Invalid Request**  
  La petición no es un objeto JSON-RPC válido (falta `jsonrpc`, `method`, `id`, etc.).

- `-32601` — **Method not found**  
  El método/tool solicitado no existe o no está habilitado.

- `-32602` — **Invalid params**  
  Los parámetros de entrada no cumplen el esquema esperado (faltan campos obligatorios, tipos incorrectos, etc.).

- `-32603` — **Internal error**  
  Error interno no previsto (excepciones no controladas, fallos de IO, etc.).

#### 2.2 Códigos de servidor específicos

Para errores propios del servidor Sentinel-Arch se recomienda usar el rango **`-32000` a `-32099`** (reservado para errores de servidor):

- `-32000` — **Server error** genérico (fallback).
- `-32010` — **Access denied** (operación fuera del path permitido o intento de escritura no autorizada).
- `-32011` — **Read-only violation** (intento de operación de escritura distinta a la generación de reportes).

El campo `data` del objeto `error` debe incluir detalles útiles para depuración, pero **nunca** información sensible (paths internos del sistema, variables de entorno, secretos, etc.).

### 3. Modelo de seguridad de filesystem

El servidor MCP debe operarse bajo el principio de **mínimo privilegio**:

1. **Path raíz controlado**
   - Toda operación sobre el sistema de archivos (lectura o escritura) debe estar acotada al **path raíz** proporcionado por el cliente MCP (por ejemplo, la raíz del proyecto).
   - Se debe normalizar y validar el path para evitar:
     - Traversal (`..`, `../..`, etc.).
     - Accesos fuera del directorio autorizado.
   - Si se detecta un intento de acceso fuera del root, devolver error `-32010` (Access denied).

2. **Permisos de lectura**
   - Por defecto, todas las tools (`list_project_structure`, `read_code_file`, etc.) deben usar **solo lectura** (`read-only`) sobre el filesystem.
   - No se debe permitir **borrar**, **mover** o **sobrescribir** archivos del proyecto.

3. **Única excepción de escritura: reportes**
   - La única escritura permitida es la generación de reportes según `WF-03-SENTINEL-REPORTING`:
     - Archivos `SENTINEL_REPORT_YYYYMMDD[_NN].md`.
   - La ubicación de los reportes debe ser:
     - Bajo el `rootPath` del proyecto, o
     - En un directorio de trabajo configurado explícitamente para tal fin.
   - Cualquier otra operación de escritura se considera violación de política y debe:
     - Ser bloqueada.
     - Registrar log de advertencia.
     - Devolver error `-32011` (Read-only violation).

### 4. Transporte STDIO

El servidor Sentinel-Arch se ejecuta como un proceso iniciado por el cliente MCP (p. ej. Cursor), comunicándose por:

- **Entrada estándar (`stdin`)** — recibe peticiones JSON-RPC.
- **Salida estándar (`stdout`)** — envía respuestas y notificaciones JSON-RPC.
- **Salida de error (`stderr`)** — reservada para logs y diagnósticos humanos; no debe usarse para protocolo.

Requisitos:

- El proceso no debe abrir puertos de red adicionales por sí mismo para atender MCP.
- Cualquier otro transporte (HTTP/SSE, etc.) debe configurarse explícitamente y fuera del alcance de este workflow.

### 5. Logs y observabilidad

- Los logs deben:
  - Evitar registrar contenido completo de archivos de código salvo a nivel DEBUG y solo si está claramente activado.
  - Nunca incluir secretos potenciales (tokens, passwords, etc.) si se encuentran accidentalmente.
- Es recomendable etiquetar logs con:
  - ID de petición JSON-RPC (`id`) si está disponible.
  - Método/tool invocado.
  - `rootPath` o contexto de proyecto (siempre que no exponga información sensible).

### 6. Integración con otros workflows

- `WF-01-PROJECT-DISCOVERY` y `WF-02-ARCH-INTELLIGENCE` deben:
  - Respetar las restricciones de lectura y path.
  - Propagar errores JSON-RPC respetando los códigos definidos en este workflow.
- `WF-03-SENTINEL-REPORTING`:
  - Es el único autorizado a realizar escrituras y debe seguir las reglas aquí descritas.

### 7. Trazabilidad de incidentes

- Ante errores graves (por ejemplo, `-32603` repetidos o violaciones de política), se recomienda:
  - Registrar un log con nivel WARNING/ERROR.
  - Opcionalmente, incluir un identificador de correlación (trace ID) que permita investigar el incidente a posteriori.

