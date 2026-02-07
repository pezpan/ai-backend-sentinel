## WF-01 PROJECT DISCOVERY

### 1. Objetivo

Definir un **protocolo determinista y repetible** para el descubrimiento inicial de un proyecto Java, de forma que cualquier ejecución del servidor MCP produzca una **vista jerárquica simple y consistente** de:

- Artefacto principal (raíz Maven).
- Módulos / submódulos.
- Paquetes Java relevantes.

Este workflow se invoca típicamente desde una tool MCP de alto nivel (`analyze_project` o similar) antes de cualquier análisis semántico.

### 2. Alcance

- Proyectos **Maven** (con archivo `pom.xml` en la raíz analizada).
- Estructuras estándar `src/main/java`, `src/test/java`, `src/main/resources`, etc.
- Solo lectura del sistema de archivos.

### 3. Reglas de exclusión (ignore)

Durante el escaneo se **deben excluir** explícitamente:

- Directorios:
  - `target/`
  - `.git/`
  - `.idea/`
  - Cualquier subdirectorio que coincida con patrón de salida de build (`*/build/`, `*/out/`), si en el futuro se soportan otros sistemas.
- Archivos:
  - `*.class`
  - Archivos binarios grandes no relevantes (`*.jar`, `*.war`, `*.ear`) salvo que se solicite expresamente lo contrario.

Las reglas de exclusión se aplican **antes** de construir la representación jerárquica, de modo que estos elementos nunca aparezcan en la salida.

### 4. Protocolo de escaneo

El flujo **SIEMPRE** seguirá este orden:

1. **Validación de entrada**
   - Verificar que el path recibido (`rootPath`) existe y es un **directorio**.
   - Si no es válido, devolver error JSON-RPC con código `-32602` (Invalid params) y descripción clara.

2. **Localización de `pom.xml`**
   - Buscar `pom.xml` en `rootPath`.
   - Si no existe:
     - El workflow continúa, pero marca el proyecto como **“no-Maven”** en la salida.
   - Si existe:
     - Parsear mínimamente:
       - `<groupId>`
       - `<artifactId>`
       - `<version>`
       - Módulos (`<modules>` si aplica).

3. **Descubrimiento de módulos**
   - Si hay `<modules>`, para cada módulo:
     - Construir ruta `rootPath/<moduleName>`.
     - Verificar existencia de `pom.xml` hijo.
     - Registrar jerarquía padre → hijo en la salida.

4. **Descubrimiento de estructura de paquetes**
   - Para cada módulo (incluido el raíz):
     - Localizar `src/main/java`.
     - Recorrer recursivamente aplicando **reglas de exclusión**.
     - Construir la jerarquía de paquetes a partir de la ruta relativa a `src/main/java`, por ejemplo:
       - `src/main/java/com/example/order/api/OrderController.java`
       - se representa como:
         - paquete `com.example.order.api`
         - archivo `OrderController.java`.

### 5. Formato de la representación jerárquica

La **salida lógica** de este workflow es un árbol simplificado, que puede emitirse como:

- **JSON estructurado** (recomendado para MCP):

```json
{
  "root": {
    "path": "<rootPath>",
    "maven": {
      "groupId": "com.example",
      "artifactId": "orders-service",
      "version": "1.0.0",
      "modules": [
        "orders-api",
        "orders-core"
      ],
      "missing": false
    },
    "modules": [
      {
        "name": "orders-api",
        "path": "<rootPath>/orders-api",
        "packages": [
          {
            "name": "com.example.orders.api",
            "files": [
              "OrderController.java",
              "HealthController.java"
            ]
          }
        ]
      }
    ]
  }
}
```

- Opcionalmente, una **vista textual jerárquica** (útil para logs o debugging):

```text
orders-service (com.example:orders-service:1.0.0)
  ├─ module: orders-api
  │   ├─ com.example.orders.api
  │   │   ├─ OrderController.java
  │   │   └─ HealthController.java
  └─ module: orders-core
      └─ com.example.orders.core
          └─ OrderService.java
```

### 6. Reglas de consistencia

- El orden de módulos y paquetes **debe ser determinista** (orden alfabético).
- Los paths devueltos deben ser **absolutos** o claramente relativos al `rootPath` (pero no mezclados).
- Si el proyecto no es Maven (`pom.xml` ausente), el campo `maven.missing` se establece en `true` y se podría omitir `groupId`, `artifactId` y `version`.

### 7. Consideraciones para Tools MCP

- Las tools de alto nivel (`analyze_project`) **no deben** reimplementar este flujo; deben delegar explícitamente en una implementación interna alineada con este documento.
- Cualquier cambio en este workflow debe versionarse y reflejarse en `ARCHITECTURE.md` para mantener la trazabilidad del comportamiento del agente.

