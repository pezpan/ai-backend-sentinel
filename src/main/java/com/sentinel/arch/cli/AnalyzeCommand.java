package com.sentinel.arch.cli;

import com.sentinel.arch.agent.SentinelAgent;
import com.sentinel.arch.mcp.ProjectMcpTools;
import com.sentinel.arch.ollama.OllamaConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "analyze", description = "Analiza la arquitectura de un proyecto local")
public class AnalyzeCommand implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, description = "Ruta absoluta del proyecto Java", required = true)
    private String projectPath;

    @Override
    public Integer call() {
        System.out.println("🚀 Iniciando Sentinel-Arch sobre: " + projectPath);

        try {
            // 1. Obtener el modelo de Ollama
            OllamaConfig ollamaConfig = new OllamaConfig();
            ChatModel model = ollamaConfig.createModel();

            // 2. Construir el Agente con capacidades MCP (Tools)
            SentinelAgent agent = AiServices.builder(SentinelAgent.class)
                    .chatModel(model)
                    .tools(new ProjectMcpTools())
                    .build();

            System.out.println("🧠 El agente está analizando el contexto... (esto puede tardar unos segundos)");

            // 3. Ejecutar la tarea
            // Le damos una instrucción inicial, el agente usará las herramientas para cumplirla
            String report = agent.analyze("Analiza el microservicio en la ruta: " + projectPath 
                    + ". Identifica la arquitectura y genera un diagrama Mermaid.");

            // 4. Mostrar resultado
            System.out.println("\n--- REPORTE DE ARQUITECTURA SENTINEL ---");
            System.out.println(report);
            System.out.println("------------------------------------------");

            return 0;
        } catch (Exception e) {
            System.err.println("❌ Error durante el análisis: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}