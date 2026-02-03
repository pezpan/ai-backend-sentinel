package com.sentinel.arch;

import com.sentinel.arch.cli.AnalyzeCommand;
import picocli.CommandLine;

/**
 * Punto de entrada del agente Sentinel-Arch.
 * Usa Picocli para la interfaz de línea de comandos.
 */
public class SentinelMain {

    public static void main(String[] args) {
        var exitCode = new CommandLine(new SentinelCommand())
                .execute(args);
        System.exit(exitCode);
    }
}
