package com.sentinel.arch;

import com.sentinel.arch.cli.AnalyzeCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Comando raíz de Sentinel. Delega en subcomandos (analyze, etc.).
 */
@Command(
        name = "sentinel",
        description = "Agente Sentinel-Arch: análisis de microservicios Java con IA local (LangChain4j + Ollama + MCP)",
        mixinStandardHelpOptions = true,
        subcommands = { AnalyzeCommand.class }
)
public class SentinelCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.err);
    }
}
