# WF-01: Project Discovery & Structure Analysis

**Versión:** 1.0.0  
**Última actualización:** 2026-02-04  
**Estado:** Implementado parcialmente en `ProjectMcpTools.java`  

---

## Propósito

Define el algoritmo para descubrir, analizar y representar la estructura de un proyecto Java (mono-repo, microservicio, multi-módulo) de forma segura y eficiente.

---

## 1. Fases del Descubrimiento

```
┌────────────────────────────────────┐
│ 1. Entrada: rootPath              │
│    (ej: /ruta/del/proyecto)       │
└────────────────────┬───────────────┘
                     │
                     ▼
┌────────────────────────────────────┐
│ 2. Análisis de pom.xml             │
│    - Detectar: ArtifactId, version │
│    - Packaging: jar, war, pom      │
│    - Módulos (si es multi-módulo)  │
└────────────────────┬───────────────┘
                     │
                     ▼
┌────────────────────────────────────┐
│ 3. Escaneo de Estructura           │
│    - src/main/java                 │
│    - src/test/java                 │
│    - src/main/resources            │
│    - Excluir: target, .git, etc    │
└────────────────────┬───────────────┘
                     │
                     ▼
┌────────────────────────────────────┐
│ 4. Análisis de Paquetes            │
│    - Detectar com.sentinel.arch    │
│    - Construir árbol jerárquico    │
│    - Contar clases por paquete     │
└────────────────────┬───────────────┘
                     │
                     ▼
┌────────────────────────────────────┐
│ 5. Generación de Representación    │
│    - JSON                          │
│    - Texto (tree-like view)        │
│    - Metadata (dependencias, etc)  │
└────────────────────────────────────┘
```

---

## 2. Reglas de Exclusión

**Directorios excluidos (no escanear):**
```
target/
.git/
.idea/
.vscode/
node_modules/
dist/
build/
*.class
*.jar
*.war
```

**Archivos ignorados en análisis:**
```
.DS_Store
Thumbs.db
*.log
*.tmp
*.swp
```

**Lógica en `ProjectMcpTools.readProjectStructure()`:**
```java
return Files.walk(Paths.get(path), 1)
    .filter(p -> !p.getFileName().toString().startsWith("."))
    .filter(p -> !p.getFileName().toString().equals("target"))
    .filter(p -> !p.getFileName().toString().equals("build"))
    .map(p -> p.getFileName().toString())
    .collect(Collectors.joining("\n"));
```

---

## 3. Estructura de Datos: Proyecto

### 3.1 Representación JSON

```json
{
  "project": {
    "name": "sentinel-backend-ai",
    "version": "1.0.0-SNAPSHOT",
    "packaging": "jar",
    "description": "Agente de IA local con LangChain4j, Ollama y MCP",
    "rootPath": "/home/user/projects/ai-backend-sentinel",
    "scanTimestamp": "2026-02-04T10:30:45Z",
    "modules": [],
    "packages": {
      "com": {
        "sentinel": {
          "arch": {
            "classCount": 7,
            "subPackages": {
              "agent": {
                "classCount": 1
              },
              "cli": {
                "classCount": 1
              },
              "mcp": {
                "classCount": 3,
                "subPackages": {
                  "server": {
                    "classCount": 1
                  }
                }
              },
              "ollama": {
                "classCount": 1
              }
            }
          }
        }
      }
    },
    "dependencies": {
      "runtime": [
        {
          "groupId": "dev.langchain4j",
          "artifactId": "langchain4j",
          "version": "1.10.0"
        }
      ],
      "test": []
    },
    "statistics": {
      "totalClasses": 7,
      "totalPackages": 5,
      "javaFilesCount": 7,
      "configFilesCount": 2,
      "largestPackage": "com.sentinel.arch"
    }
  }
}
```

### 3.2 Representación Textual (Tree View)

```
sentinel-backend-ai/
├── pom.xml (1.8 KB)
├── ARCHITECTURE.md
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── sentinel/
│       │           └── arch/
│       │               ├── SentinelMain.java
│       │               ├── SentinelCommand.java
│       │               ├── agent/
│       │               │   └── SentinelAgent.java (1 archivo)
│       │               ├── cli/
│       │               │   └── AnalyzeCommand.java (1 archivo)
│       │               ├── mcp/
│       │               │   ├── ProjectMcpTools.java (1 archivo)
│       │               │   └── server/
│       │               │       └── SentinelMcpServer.java (1 archivo)
│       │               └── ollama/
│       │                   └── OllamaConfig.java (1 archivo)
│       └── resources/
│           ├── logback.xml
│           └── META-INF/
└── target/ [EXCLUDED]

Total: 7 Java files | 5 packages | 2 config files
```

---

## 4. Algoritmo Detallado

### 4.1 Fase 1: Detectar pom.xml

```java
File pomFile = new File(rootPath, "pom.xml");
if (!pomFile.exists()) {
    // No es un proyecto Maven, retornar error o estructura básica
    return discoverNonMavenProject(rootPath);
}

// Parsear pom.xml (usando SAX o DOM parser)
DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
Document doc = builder.parse(pomFile);

String projectName = doc.getElementsByTagName("artifactId").item(0).getTextContent();
String projectVersion = doc.getElementsByTagName("version").item(0).getTextContent();
String packaging = doc.getElementsByTagName("packaging").item(0).getTextContent();
```

### 4.2 Fase 2: Escanear Directorios

```java
Path sourcePath = Paths.get(rootPath, "src", "main", "java");

if (!Files.exists(sourcePath)) {
    return error("Source directory not found: " + sourcePath);
}

// Usar Files.walk() para recorrer recursivamente
Map<String, PackageInfo> packages = new HashMap<>();

Files.walk(sourcePath)
    .filter(Files::isRegularFile)
    .filter(p -> p.toString().endsWith(".java"))
    .forEach(javaFile -> {
        // Extraer nombre del paquete desde el path
        String packageName = extractPackageNameFromPath(javaFile);
        packages.computeIfAbsent(packageName, k -> new PackageInfo())
                .incrementClassCount();
    });
```

### 4.3 Fase 3: Construir Árbol de Paquetes

```java
// Convertir lista plana de paquetes en árbol jerárquico
// Ej: com.sentinel.arch.mcp.server → com > sentinel > arch > mcp > server

Map<String, Map<String, ?>> packageTree = buildPackageHierarchy(packages);

// Resultado:
// {
//   "com": {
//     "sentinel": {
//       "arch": {
//         "classCount": 7,
//         "mcp": { ... },
//         "cli": { ... }
//       }
//     }
//   }
// }
```

### 4.4 Fase 4: Generar Representación Final

```java
// JSON (para clientes MCP)
ObjectMapper mapper = new ObjectMapper();
String jsonOutput = mapper.writerWithDefaultPrettyPrinter()
    .writeValueAsString(discoveryResult);

// Texto (para logs y reportes)
String textOutput = generateTreeView(packageTree);

// Retornar ambas representaciones
return new DiscoveryResult(jsonOutput, textOutput, statistics);
```

---

## 5. Herramientas MCP Requeridas

### 5.1 `readProjectStructure(path)`

**Función:** Lista los archivos y carpetas de un directorio (sin recursión profunda).

**Entrada:**
```json
{
  "path": "."
}
```

**Salida exitosa:**
```json
{
  "type": "text",
  "text": "ARCHITECTURE.md\nAGENTS.md\npom.xml\nsrc/\ntarget/"
}
```

**Salida con error (path traversal):**
```json
{
  "error": {
    "code": -32000,
    "message": "Security violation: Path traversal detected"
  }
}
```

**Implementación actual (ProjectMcpTools.java):**
```java
@Tool("Lista los archivos y carpetas de un directorio para entender la estructura del microservicio")
public String readProjectStructure(String path) throws IOException {
    return Files.walk(Paths.get(path), 1)
            .map(p -> p.getFileName().toString())
            .collect(Collectors.joining("\n"));
}
```

### 5.2 `readJavaFile(path)`

**Función:** Lee el contenido completo de un archivo Java/XML/Markdown.

**Entrada:**
```json
{
  "path": "src/main/java/com/sentinel/arch/SentinelMain.java"
}
```

**Salida exitosa:**
```json
{
  "type": "text",
  "text": "package com.sentinel.arch;\n\npublic class SentinelMain { ... }"
}
```

**Validaciones:**
- ✅ Extensión permitida (.java, .xml, .md)
- ❌ Rechazar .class, .jar, .exe
- ❌ Prevenir path traversal

**Implementación actual (ProjectMcpTools.java):**
```java
@Tool("Lee el contenido de un archivo Java específico para analizar su arquitectura")
public String readJavaFile(String path) throws IOException {
    if (!path.endsWith(".java") && !path.endsWith(".xml") && !path.endsWith(".md")) {
        return "Error: Solo se permite la lectura de archivos de código o configuración.";
    }
    return Files.readString(Paths.get(path));
}
```

---

## 6. Casos de Uso

### 6.1 Analizar un Proyecto Monolítico

```
Input: /home/user/myapp
├── pom.xml (packaging: jar)
├── src/main/java/com/mycompany/app
│   ├── controller/
│   ├── service/
│   └── repository/
└── ...

Output (JSON): Árbol completo con estructura de paquetes y conteo de clases
Output (Text): Vista de árbol formateada para terminal
```

### 6.2 Analizar un Multi-Módulo Maven

```
Input: /home/user/myplatform
├── pom.xml (packaging: pom, modules: [module1, module2, module3])
├── module1/
│   ├── pom.xml (packaging: jar)
│   └── src/main/java/...
├── module2/
│   ├── pom.xml (packaging: jar)
│   └── src/main/java/...
└── module3/
    ├── pom.xml (packaging: war)
    └── src/main/java/...

Output: Descubrimiento recursivo de módulos, estructura jerárquica
```

### 6.3 Detectar Proyectos No-Maven

```
Input: /home/user/gradle-project
├── build.gradle
├── src/main/java/
├── src/test/java/
└── ...

Output: Fallback a escaneo simple de directorios (sin pom.xml)
```

---

## 7. Implementación en ProjectMcpTools

**Estado actual:** Implementadas funciones básicas de lectura

**Pendiente:**
- [ ] Análisis de pom.xml (parseo XML)
- [ ] Construcción de árbol de paquetes
- [ ] Generación de JSON + vista textual
- [ ] Caché de resultados (optimizar reintentos)
- [ ] Soporte para proyectos Gradle

**Versión mejorada planificada:**
```java
public class ProjectMcpTools {
    
    @Tool("Descubre la estructura completa de un proyecto Java")
    public String analyzeProject(String rootPath) {
        // Fase 1: Detectar pom.xml
        // Fase 2: Escanear directorios
        // Fase 3: Construir árbol
        // Fase 4: Generar JSON + texto
        return discoveryResult;
    }
    
    // Métodos privados de soporte
    private ProjectMetadata parsePom(File pomFile) { ... }
    private Map<String, PackageInfo> scanJavaClasses(Path sourcePath) { ... }
    private Map<String, ?> buildPackageHierarchy(Map<String, PackageInfo> packages) { ... }
    private String generateJsonRepresentation(Map<String, ?> packageTree) { ... }
    private String generateTextRepresentation(Map<String, ?> packageTree) { ... }
}
```

---

## 8. Testing

### 8.1 Test de Estructura

```java
@Test
void testReadProjectStructure() {
    ProjectMcpTools tools = new ProjectMcpTools();
    String result = tools.readProjectStructure(".");
    
    assertTrue(result.contains("pom.xml"));
    assertTrue(result.contains("src"));
    assertFalse(result.contains("target"));  // Excluido
}
```

### 8.2 Test de Seguridad

```java
@Test
void testPathTraversalBlocked() {
    ProjectMcpTools tools = new ProjectMcpTools();
    String result = tools.readJavaFile("../../etc/passwd");
    
    assertTrue(result.contains("Error") || result.contains("denied"));
}
```

---

## 9. Integración con SentinelMcpServer

El servidor MCP (`SentinelMcpServer.java`) expone las herramientas definidas aquí:

1. **Registro automático:** `registerToolsFromProjectMcpTools()`
2. **Validación de seguridad:** `validateToolCall()` según WF-04
3. **Manejo de errores:** JSON-RPC con códigos estándar

**Flujo:**
```
Cliente MCP (Cursor/IntelliJ)
    ↓ {"jsonrpc": "2.0", "method": "tools/call", ...}
    ↓
SentinelMcpServer.handleToolCall()
    ↓
ProjectMcpTools.readProjectStructure(path)
    ↓
Validación de path (WF-04)
    ↓
Files.walk() / Files.readString()
    ↓ {"jsonrpc": "2.0", "result": { "content": [...] }}
    ↓
Cliente MCP (resultado formateado)
```

---

## 10. Checklist de Implementación

- [x] Herramienta `readProjectStructure()`
- [x] Herramienta `readJavaFile()` con validación de extensiones
- [ ] Parseo de pom.xml
- [ ] Construcción de árbol de paquetes (JSON + texto)
- [ ] Exclusión de directorios (target, .git, etc.)
- [ ] Test unitarios
- [ ] Documentación de API MCP
- [ ] Integración con SentinelMcpServer

---

**Siguiente:** WF-02-ARCH-INTELLIGENCE (Análisis de patrones arquitectónicos)
