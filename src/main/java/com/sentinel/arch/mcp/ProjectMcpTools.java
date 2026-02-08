package com.sentinel.arch.mcp;

import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;

public class ProjectMcpTools {

    @Tool("Lista los archivos y carpetas de un directorio para entender la estructura del microservicio")
    public String readProjectStructure(String path) throws IOException {
        return Files.walk(Paths.get(path), 1)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.joining("\n"));
    }

    @Tool("Lee el contenido de un archivo Java específico para analizar su arquitectura")
    public String readJavaFile(String path) throws IOException {
        // Validación básica de seguridad (solo leer .java o .xml)
        if (!path.endsWith(".java") && !path.endsWith(".xml") && !path.endsWith(".md")) {
            return "Error: Solo se permite la lectura de archivos de código o configuración.";
        }
        return Files.readString(Paths.get(path));
    }

    @Tool("Escribe un reporte de arquitectura en un archivo dentro del proyecto")
    public String write_architecture_report(String fileName, String content) throws IOException {
        // Validación de seguridad para asegurar que el archivo se crea dentro del directorio del proyecto
        Path basePath = Paths.get("").toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(fileName).normalize();

        // Verificar que el path resultante esté dentro del directorio base
        if (!targetPath.startsWith(basePath)) {
            return "Error: La ruta especificada está fuera del directorio del proyecto.";
        }

        // Verificar que el archivo tenga una extensión permitida (por seguridad)
        String fileExtension = getFileExtension(fileName);
        if (!isValidFileExtension(fileExtension)) {
            return "Error: Extensión de archivo no permitida. Solo se permiten .md, .txt, .json, .yaml, .yml.";
        }

        // Escribir el contenido en el archivo
        Files.write(targetPath, content.getBytes("UTF-8"));
        return "Reporte de arquitectura guardado exitosamente en: " + targetPath.toString();
    }

    // Delegate the new discovery tool to the ServiceInterconnectionDiscovery class
    private final ServiceInterconnectionDiscovery discovery = new ServiceInterconnectionDiscovery();

    @Tool("Discovers service interconnections by analyzing source code for protocol fingerprints and mapping inbound/outbound connections")
    public String discover_service_interconnections(String projectPath) throws IOException {
        return discovery.discover_service_interconnections(projectPath);
    }

    // Delegate the Maven project structure analyzer tool
    private final MavenProjectStructureAnalyzer structureAnalyzer = new MavenProjectStructureAnalyzer();

    @Tool("Analyzes Maven project structure to identify modules, their dependencies, and proprietary frameworks")
    public String get_project_structure(String projectPath) throws IOException {
        return structureAnalyzer.get_project_structure(projectPath);
    }

    @Tool("Analyzes Maven project structure to identify modules, their dependencies, and proprietary frameworks with custom organization pattern")
    public String get_project_structure(String projectPath, String orgPattern) throws IOException {
        return structureAnalyzer.get_project_structure(projectPath, orgPattern);
    }

    // Delegate the architectural signatures extractor tool
    private final ArchitecturalSignaturesExtractor signaturesExtractor = new ArchitecturalSignaturesExtractor();

    @Tool("Extracts architectural signatures from Java files including annotations, class names, and outbound calls")
    public String extract_architectural_signatures(String projectPath) throws IOException {
        return signaturesExtractor.extract_architectural_signatures(projectPath);
    }

    // Delegate the master architecture report generator tool
    private final MasterArchitectureReportGenerator reportGenerator = new MasterArchitectureReportGenerator();

    @Tool("Generates a master architecture report by cross-referencing STRUCTURE.json and SIGNATURES.json")
    public String generate_master_arch_report(String structureJsonPath, String signaturesJsonPath) throws IOException {
        return reportGenerator.generate_master_arch_report(structureJsonPath, signaturesJsonPath);
    }

    /**
     * Obtiene la extensión del archivo
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex).toLowerCase();
        }
        return "";
    }

    /**
     * Valida si la extensión del archivo es permitida
     */
    private boolean isValidFileExtension(String extension) {
        return extension.equals(".md") ||
               extension.equals(".txt") ||
               extension.equals(".json") ||
               extension.equals(".yaml") ||
               extension.equals(".yml");
    }
}