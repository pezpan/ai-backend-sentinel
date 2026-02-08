package com.sentinel.arch.ollama;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Configuración y verificación de conexión con Ollama (modelo local).
 * "Hello World" de LangChain4j contra qwen2.5-coder:3b en localhost:11434.
 */
public class OllamaConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "qwen2.5-coder:3b";

    private final String baseUrl;
    private final String modelName;

    public OllamaConfig() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public OllamaConfig(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    /**
     * Comprueba si Ollama está disponible y el modelo responde.
     */
    public boolean isAvailable() {
        try {
            var response = createModel().chat("Responde solo: OK");
            log.debug("Ollama health check response: {}", response);
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.warn("Ollama no disponible en {} (modelo {}): {}", baseUrl, modelName, e.getMessage());
            return false;
        }
    }

    /**
     * Crea el modelo de chat para uso en el agente.
     */
    public ChatModel createModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(10)) // Timeout extendido para tareas complejas
                .temperature(0.0)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }
}
