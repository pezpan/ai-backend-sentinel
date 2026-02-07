package com.sentinel.arch.agent;

import dev.langchain4j.service.SystemMessage;

public interface SentinelAgent {
    @SystemMessage("""
        Eres 'Sentinel-Arch', un arquitecto senior de Java. 
        Tu objetivo es analizar el código local usando las herramientas proporcionadas.
        1. Explora la estructura del proyecto.
        2. Lee los archivos clave (pom.xml, controladores, servicios).
        3. Genera un reporte en Markdown con: Resumen, Puntos de Mejora y un Diagrama Mermaid del flujo.
        """)
    String analyze(String userPrompt);
}