package com.sentinel.arch.cli;

import com.sentinel.arch.ollama.OllamaConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Comando: analyze --path &lt;directorio&gt;
 * Analiza un directorio de microservicios con el agente de IA.
 */
@Command(
        name = "analyze",
        description = "Analiza un directorio de código (microservicios Java) usando el modelo local"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Option(
            names = { "-p", "--path" },
            required = true,
            description = "Ruta al directorio a analizar (proyecto o raíz de microservicios)"
    )
    private Path path;

    @Override
    public Integer call() {
        System.out.println("Sentinel-Arch: analizando path = " + path.toAbsolutePath());

        var ollama = new OllamaConfig();
        if (!ollama.isAvailable()) {
            System.err.println("Ollama no está disponible en localhost:11434. Comprueba que el servicio esté en marcha.");
            return 1;
        }

        // TODO: integrar MCP y flujo de análisis real
        System.out.println("Conexión con Ollama OK. Análisis de '" + path + "' pendiente de implementación (MCP).");
        return 0;
    }
}
