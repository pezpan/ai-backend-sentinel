# Testing & Manual Validation del SentinelMcpServer

**Versión:** 1.0.0  
**Fecha:** 2026-02-04  
**Propósito:** Guía para probar el servidor MCP en modo manual

---

## 🚀 Iniciar el Servidor

### Compilar y Empaquetar

```bash
cd C:\Datos\proyectos\ai-backend-sentinel

# Compilar
mvn clean compile

# Empaquetar (crea JAR con todas las dependencias)
mvn package -DskipTests
```

### Ejecutar el Servidor

```bash
# Opción 1: JAR sombreado (recomendado)
java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer

# Salida esperada en stderr:
# ╔════════════════════════════════════════════════╗
# ║     Sentinel Backend AI - MCP Server v1.0.0    ║
# ║        Análisis de Arquitectura Java            ║
# ╚════════════════════════════════════════════════╝
# [main] INFO  SentinelMcpServer - Iniciando SentinelMcpServer
# [main] INFO  SentinelMcpServer - Registrando herramientas desde ProjectMcpTools
# ...
# [main] INFO  SentinelMcpServer - Servidor MCP configurado. Iniciando escucha en STDIO...
```

> ℹ️ El servidor ahora está escuchando en STDIO. Permanece bloqueante esperando mensajes JSON-RPC.

---

## 🧪 Test Manual con curl / nc

### Opción 1: Usar `nc` (netcat) - NO RECOMENDADO (El servidor usa STDIO, no TCP)

```bash
# El servidor NO escucha en un puerto TCP, escucha en STDIO
# Por lo tanto, nc no funcionará directamente
```

### Opción 2: Pipe directo (RECOMENDADO)

```bash
# En otra terminal, crear archivo de entrada JSON
cat > test_request.json << 'EOF'
{"jsonrpc": "2.0", "method": "tools/list", "id": 1}
EOF

# Enviar al servidor via pipe
cat test_request.json | java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer
```

**Salida esperada (en stdout, JSON-RPC):**
```json
{"jsonrpc":"2.0","result":{"tools":[{"name":"readProjectStructure","description":"readProjectStructure","inputSchema":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}},{"name":"readJavaFile","description":"readJavaFile","inputSchema":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}]},"id":1}
```

### Opción 3: Script de Prueba Bash

```bash
#!/bin/bash

# Crear archivo temporal con solicitudes
cat > requests.jsonl << 'EOF'
{"jsonrpc": "2.0", "method": "tools/list", "id": 1}
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readProjectStructure", "arguments": {"path": "."}}, "id": 2}
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readJavaFile", "arguments": {"path": "pom.xml"}}, "id": 3}
EOF

# Ejecutar servidor y enviar solicitudes
(cat requests.jsonl; sleep 1) | java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer 2>/tmp/server.log

# Ver logs
cat /tmp/server.log
```

---

## 📋 Casos de Test

### Test 1: tools/list (Listar Herramientas)

**Entrada:**
```json
{"jsonrpc": "2.0", "method": "tools/list", "id": 1}
```

**Salida esperada:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [
      {
        "name": "readProjectStructure",
        "description": "readProjectStructure",
        "inputSchema": {
          "type": "object",
          "properties": {
            "path": {"type": "string"}
          },
          "required": ["path"]
        }
      },
      {
        "name": "readJavaFile",
        "description": "readJavaFile",
        "inputSchema": {
          "type": "object",
          "properties": {
            "path": {"type": "string"}
          },
          "required": ["path"]
        }
      }
    ]
  },
  "id": 1
}
```

**Validación:**
- ✅ `jsonrpc` = "2.0"
- ✅ `result.tools` contiene 2 herramientas
- ✅ `id` = 1 (echo de la solicitud)

---

### Test 2: readProjectStructure (Éxito)

**Entrada:**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readProjectStructure", "arguments": {"path": "."}}, "id": 2}
```

**Salida esperada (fragmento):**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "AGENTS.md\nARCHITECTURE.md\nINTEGRATION.md\npom.xml\nsrc\n..."
      }
    ]
  },
  "id": 2
}
```

**Validación:**
- ✅ `result.content[0].type` = "text"
- ✅ Contiene archivos reales del proyecto
- ✅ `id` = 2

---

### Test 3: readJavaFile (Éxito)

**Entrada:**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readJavaFile", "arguments": {"path": "pom.xml"}}, "id": 3}
```

**Salida esperada (fragmento):**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n  ..."
      }
    ]
  },
  "id": 3
}
```

**Validación:**
- ✅ Contenido XML válido
- ✅ No hay truncamiento

---

### Test 4: Path Traversal (Seguridad)

**Entrada (ATAQUE):**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readJavaFile", "arguments": {"path": "../../../../../../etc/passwd"}}, "id": 4}
```

**Salida esperada (ERROR):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32000,
    "message": "Security violation: Path traversal detectado: ../../../../../../etc/passwd"
  },
  "id": 4
}
```

**Validación:**
- ✅ Código de error = -32000 (Server error)
- ✅ Mensaje indica violación de seguridad
- ✅ Archivo NO fue leído (seguridad funciona)

---

### Test 5: Archivo Binario (Seguridad)

**Entrada (ATAQUE):**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readJavaFile", "arguments": {"path": "target/classes/com/sentinel/arch/SentinelMain.class"}}, "id": 5}
```

**Salida esperada (ERROR):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params: Solo se permite la lectura de archivos de código o configuración."
  },
  "id": 5
}
```

**Validación:**
- ✅ Código de error = -32602 (Invalid params)
- ✅ Extensión `.class` bloqueada

---

### Test 6: Parámetro Faltante (Validación)

**Entrada (INVÁLIDA):**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "readProjectStructure"}, "id": 6}
```

**Salida esperada (ERROR):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params: Missing parameter: path"
  },
  "id": 6
}
```

**Validación:**
- ✅ Código de error = -32602
- ✅ Mensaje es descriptivo

---

### Test 7: Herramienta No Existe

**Entrada (INVÁLIDA):**
```json
{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "nonexistentTool", "arguments": {}}, "id": 7}
```

**Salida esperada (ERROR):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32601,
    "message": "Tool not found: nonexistentTool"
  },
  "id": 7
}
```

**Validación:**
- ✅ Código de error = -32601 (Method not found)

---

### Test 8: JSON Malformado

**Entrada (INVÁLIDA):**
```
{invalid json
```

**Salida esperada (ERROR):**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Internal error: ..."
  },
  "id": null
}
```

**Validación:**
- ✅ Código de error = -32603 (Internal error)

---

## 🔍 Monitoreo de Logs

Los logs se escriben en **stderr**, no interfieren con JSON-RPC en stdout.

### Ver logs en tiempo real

```bash
# Terminal 1: Iniciar servidor (redirigir stderr a archivo)
java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer 2>/tmp/mcp-server.log &

# Terminal 2: Monitorear logs
tail -f /tmp/mcp-server.log

# Salida esperada:
# 11:57:23.456 [main] INFO  SentinelMcpServer - ╔════════════════════════════════════════════════╗
# 11:57:23.457 [main] INFO  SentinelMcpServer - ║     Sentinel Backend AI - MCP Server v1.0.0    ║
# 11:57:23.460 [main] INFO  SentinelMcpServer - Iniciando SentinelMcpServer
# 11:57:23.461 [main] DEBUG SentinelMcpServer - Registrando herramientas desde ProjectMcpTools
# 11:57:23.462 [main] INFO  SentinelMcpServer - Registrando herramienta: readProjectStructure - ...
# 11:57:23.463 [main] INFO  SentinelMcpServer - Registrando herramienta: readJavaFile - ...
```

---

## 🧬 Script de Test Automatizado (Python)

```python
#!/usr/bin/env python3
"""
Test script para validar SentinelMcpServer
"""

import json
import subprocess
import sys
import time

def send_request(request_dict):
    """Envía solicitud JSON-RPC al servidor"""
    request_json = json.dumps(request_dict)
    print(f"→ Enviando: {request_json}")
    
    # Iniciar servidor y enviar solicitud
    proc = subprocess.Popen(
        [
            "java", "-cp", 
            "target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar",
            "com.sentinel.arch.mcp.server.SentinelMcpServer"
        ],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    stdout, stderr = proc.communicate(input=request_json + "\n", timeout=5)
    
    if stdout.strip():
        response = json.loads(stdout.strip())
        print(f"← Respuesta: {json.dumps(response, indent=2)}")
        return response
    else:
        print(f"✗ Sin respuesta. Logs:\n{stderr}")
        return None

def test_tools_list():
    """Test: Listar herramientas"""
    print("\n" + "="*50)
    print("TEST 1: tools/list")
    print("="*50)
    
    response = send_request({
        "jsonrpc": "2.0",
        "method": "tools/list",
        "id": 1
    })
    
    assert response is not None, "Sin respuesta"
    assert response["jsonrpc"] == "2.0", "jsonrpc incorrecto"
    assert "result" in response, "Sin result"
    assert len(response["result"]["tools"]) == 2, "Debe haber 2 herramientas"
    print("✓ PASÓ")

def test_read_structure():
    """Test: Leer estructura del proyecto"""
    print("\n" + "="*50)
    print("TEST 2: readProjectStructure")
    print("="*50)
    
    response = send_request({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": "readProjectStructure",
            "arguments": {"path": "."}
        },
        "id": 2
    })
    
    assert response is not None, "Sin respuesta"
    assert "result" in response, "Sin result"
    assert "pom.xml" in response["result"]["content"][0]["text"], "pom.xml no encontrado"
    print("✓ PASÓ")

def test_path_traversal_blocked():
    """Test: Path traversal bloqueado"""
    print("\n" + "="*50)
    print("TEST 3: Path Traversal (Seguridad)")
    print("="*50)
    
    response = send_request({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": "readJavaFile",
            "arguments": {"path": "../../etc/passwd"}
        },
        "id": 3
    })
    
    assert response is not None, "Sin respuesta"
    assert "error" in response, "Debería haber error"
    assert response["error"]["code"] == -32000, "Código de error incorrecto"
    assert "Path traversal" in response["error"]["message"], "Mensaje incorrecto"
    print("✓ PASÓ - Ataque bloqueado correctamente")

if __name__ == "__main__":
    try:
        test_tools_list()
        test_read_structure()
        test_path_traversal_blocked()
        
        print("\n" + "="*50)
        print("✓ TODOS LOS TESTS PASARON")
        print("="*50)
    except AssertionError as e:
        print(f"\n✗ TEST FALLIDO: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\n✗ ERROR: {e}")
        sys.exit(1)
```

**Ejecutar tests:**
```bash
python3 test_mcp_server.py
```

---

## 🔧 Troubleshooting

### Problema: "STDIO Port already in use"

**Causa:** Otro servidor está escuchando  
**Solución:**
```bash
# Encontrar proceso
lsof -i :3000  # (Si el servidor usara TCP)

# Para STDIO, buscar procesos Java
ps aux | grep java

# Matar proceso
kill -9 <PID>
```

### Problema: "ClassNotFoundException"

**Causa:** JAR incompleto o mal ejecutado  
**Solución:**
```bash
# Usar JAR sombreado
java -cp target/sentinel-backend-ai-1.0.0-SNAPSHOT-all.jar \
    com.sentinel.arch.mcp.server.SentinelMcpServer
```

### Problema: Sin respuesta JSON

**Causa:** El servidor espera JSON en cada línea  
**Solución:**
```bash
# Asegurar que cada solicitud termina con NEWLINE
echo '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}' | ...
#      ^                                                   ^ NEWLINE importante
```

---

## 📊 Métricas de Performance

### Esperado

| Operación | Tiempo | Notas |
|-----------|--------|-------|
| Startup | < 2s | JVM warm-up |
| tools/list | < 50ms | Solo reflection |
| readProjectStructure (.) | < 100ms | Files.walk() |
| readJavaFile (pequeño) | < 10ms | < 1MB |
| readJavaFile (grande) | < 500ms | Archivo 5MB |
| Path validation | < 5ms | Regex simple |

---

## ✅ Checklist de Validación

- [ ] Servidor compila sin errores
- [ ] JAR se crea exitosamente
- [ ] Servidor inicia sin excepciones
- [ ] tools/list retorna 2 herramientas
- [ ] readProjectStructure funciona
- [ ] readJavaFile funciona
- [ ] Path traversal bloqueado (-32000)
- [ ] Archivo binario rechazado (-32602)
- [ ] Parámetro faltante reportado (-32602)
- [ ] Logs van a stderr (no contamina stdout)
- [ ] CTRL+C cierra servidor limpiamente

---

**Siguiente:** Integración con cliente MCP (Cursor, IntelliJ)
