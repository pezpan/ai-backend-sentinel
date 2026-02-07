package com.sentinel.arch.cli;

import com.sentinel.arch.mcp.ServiceInterconnectionDiscovery;
import com.sentinel.arch.ollama.OllamaConfig;
import dev.langchain4j.model.chat.ChatModel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "audit", description = "Audits the service interconnections of a project and generates an audit report")
public class AuditCommand implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, description = "Ruta absoluta del proyecto Java a auditar", required = true)
    private String projectPath;

    @Override
    public Integer call() {
        System.out.println("🔍 Iniciando auditoría de interconexiones de servicios en: " + projectPath);

        try {
            // Validate project path exists
            if (!Files.exists(Paths.get(projectPath))) {
                System.err.println("❌ Error: La ruta del proyecto no existe: " + projectPath);
                return 1;
            }

            // 1. Run the discover_service_interconnections logic
            System.out.println("🔍 Descubriendo interconexiones de servicios...");
            ServiceInterconnectionDiscovery discovery = new ServiceInterconnectionDiscovery();
            String discoveryResult = discovery.discover_service_interconnections(projectPath);

            System.out.println("✅ Descubrimiento completado. Enviando resultados a Ollama para generar el informe de auditoría...");

            // 2. Connect to Ollama API and send the discovery result
            OllamaConfig ollamaConfig = new OllamaConfig();
            ChatModel model = ollamaConfig.createModel();

            // 3. Create system prompt for audit report generation
            String systemPrompt = """
                Eres un experto en arquitectura de microservicios y seguridad. 
                Tu tarea es generar un informe de auditoría detallado basado en el análisis de interconexiones de servicios.
                
                El informe debe incluir:
                1. Una descripción general de la arquitectura de microservicios
                2. Un análisis de las interconexiones encontradas (inbound y outbound)
                3. Posibles riesgos de seguridad identificados
                4. Recomendaciones para mejorar la seguridad y la arquitectura
                5. Un resumen ejecutivo
                
                El formato del informe debe ser en Markdown con secciones claras y bien organizadas.
                """;

            // 4. Send to Ollama and get the response
            String userPrompt = systemPrompt + "\n\nPor favor, genera un informe de auditoría completo basado en esta información de interconexiones de servicios:\n\n" + discoveryResult;
            String auditReport = model.chat(userPrompt);

            // 5. Save the generated audit report to AUDIT_REPORT.md
            String reportFileName = "AUDIT_REPORT.md";
            Files.write(Paths.get(reportFileName), auditReport.getBytes("UTF-8"));

            System.out.println("📄 Informe de auditoría generado exitosamente en: " + reportFileName);
            System.out.println("\n--- RESUMEN DEL INFORME DE AUDITORÍA ---");
            System.out.println(auditReport.substring(0, Math.min(auditReport.length(), 500)) + "...");
            System.out.println("----------------------------------------");

            return 0;
        } catch (Exception e) {
            System.err.println("❌ Error durante la auditoría: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}