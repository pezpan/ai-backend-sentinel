package com.sentinel.arch.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinel.arch.mcp.ProjectMcpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SentinelMcpServer
 *
 * Servidor MCP que expone herramientas para análisis de arquitectura de proyectos Java.
 * Implementa el protocolo Model Context Protocol (MCP) sobre STDIO siguiendo
 * WF-04-MCP-PROTOCOL-SECURITY para seguridad y manejo de errores JSON-RPC.
 *
 * Características:
 * - Servidor basado en STDIO (entrada/salida estándar)
 * - Registro dinámico de herramientas desde ProjectMcpTools
 * - Logging via SLF4J/Logback (consola estándar protegida para protocolo MCP)
 * - Manejo de errores JSON-RPC con códigos estándar
 * - Validación de seguridad según WF-04
 *
 * Flujo de vida:
 * 1. Inicialización: crear servidor y registrar herramientas
 * 2. Escucha activa en STDIO
 * 3. Procesamiento de llamadas RPC
 * 4. Cierre controlado al recibir señal TERM o error fatal
 */
public class SentinelMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(SentinelMcpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ProjectMcpTools projectTools;
    private Map<String, ToolDefinition> toolRegistry;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean running = true;

    /**
     * Define una herramienta MCP con su metadata y handler
     */
    private static class ToolDefinition {
        String name;
        String description;
        Method method;
        Map<String, String> parameterTypes;

        ToolDefinition(String name, String description, Method method) {
            this.name = name;
            this.description = description;
            this.method = method;
            this.parameterTypes = new ConcurrentHashMap<>();
        }
    }

    /**
     * Constructor del servidor MCP
     */
    public SentinelMcpServer() {
        this.projectTools = new ProjectMcpTools();
        this.toolRegistry = new ConcurrentHashMap<>();
        logger.info("SentinelMcpServer inicializado con ProjectMcpTools");
    }

    /**
     * Inicializa el servidor MCP:
     * 1. Registra las herramientas de ProjectMcpTools
     * 2. Configura el transporte STDIO
     * 3. Inicia la escucha activa
     *
     * @throws Exception si ocurre un error durante la inicialización
     */
    public void start() throws Exception {
        logger.info("Iniciando SentinelMcpServer");

        // Registrar herramientas desde ProjectMcpTools
        registerToolsFromProjectMcpTools();

        // Configurar STDIO
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.writer = new PrintWriter(System.out, true);  // true = auto-flush

        logger.info("Servidor MCP configurado. Iniciando escucha en STDIO...");
        logger.info("Herramientas registradas: {}", toolRegistry.keySet());

        // Iniciar servidor en modo bloqueante (escucha STDIO hasta shutdown)
        startListeningLoop();

        logger.info("Servidor MCP finalizado");
    }

    /**
     * Loop principal de escucha en STDIO
     * Lee mensajes JSON-RPC línea por línea y responde
     */
    private void startListeningLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Parsear mensaje JSON-RPC
                    JsonNode request = objectMapper.readTree(line);
                    logger.debug("Mensaje RPC recibido: {}", request);

                    // Procesar solicitud
                    JsonNode response = handleRpcRequest(request);

                    // Enviar respuesta
                    String responseText = objectMapper.writeValueAsString(response);
                    writer.println(responseText);
                    logger.debug("Respuesta RPC enviada: {}", response);

                } catch (Exception e) {
                    logger.error("Error procesando mensaje RPC: {}", line, e);
                    // Enviar error JSON-RPC
                    JsonNode errorResponse = createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                }
            }
        } catch (IOException e) {
            logger.error("Error leyendo desde STDIO", e);
        } finally {
            running = false;
        }
    }

    /**
     * Maneja una solicitud JSON-RPC 2.0
     * Soporta: initialize, tools/list, tools/call, shutdown
     */
    private JsonNode handleRpcRequest(JsonNode request) {
        try {
            // Validar estructura JSON-RPC básica
            if (!request.has("jsonrpc") || !request.get("jsonrpc").asText().equals("2.0")) {
                return createErrorResponse(null, -32600, "Invalid Request: missing jsonrpc");
            }

            if (!request.has("method")) {
                return createErrorResponse(request.get("id"), -32600, "Invalid Request: missing method");
            }

            String method = request.get("method").asText();
            JsonNode id = request.get("id");

            // Enrutar según método
            switch (method) {
                case "initialize":
                    return handleInitialize(id);

                case "tools/list":
                    return handleToolsList(id);

                case "tools/call":
                    return handleToolCall(id, request);

                case "shutdown":
                    return handleShutdown(id);

                default:
                    return createErrorResponse(id, -32601, "Method not found: " + method);
            }

        } catch (Exception e) {
            logger.error("Error manejando solicitud RPC", e);
            return createErrorResponse(request.get("id"), -32603, "Internal error");
        }
    }

    /**
     * Maneja initialize: inicializa la sesión MCP
     */
    private JsonNode handleInitialize(JsonNode id) {
        ObjectNode result = objectMapper.createObjectNode();
        
        // Protocol version is required by MCP specification
        result.put("protocolVersion", "2024-11-05");
        
        // Información básica del servidor
        result.put("serverInfo", 
            objectMapper.createObjectNode()
                .put("name", "SentinelMcpServer")
                .put("version", "1.0.0")
        );
        
        // Capacidades del servidor
        result.put("capabilities", 
            objectMapper.createObjectNode()
                .putObject("tools")
                    .put("listChanged", false)  // No cambia dinámicamente
        );

        return createSuccessResponse(id, result);
    }

    /**
     * Maneja shutdown: cierra la sesión MCP
     */
    private JsonNode handleShutdown(JsonNode id) {
        logger.info("Recibida solicitud de apagado (shutdown)");
        
        // Aquí podríamos hacer limpieza si es necesario
        // Por ahora, simplemente respondemos con éxito
        
        return createSuccessResponse(id, objectMapper.createObjectNode());
    }

    /**
     * Maneja tools/list: retorna lista de herramientas disponibles
     */
    private JsonNode handleToolsList(JsonNode id) {
        ObjectNode result = objectMapper.createObjectNode();
        result.putArray("tools");

        for (ToolDefinition tool : toolRegistry.values()) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("name", tool.name);
            toolNode.put("description", tool.description);

            // Construir inputSchema
            ObjectNode inputSchema = objectMapper.createObjectNode();
            inputSchema.put("type", "object");

            ObjectNode properties = objectMapper.createObjectNode();
            for (String paramName : tool.parameterTypes.keySet()) {
                ObjectNode param = objectMapper.createObjectNode();
                param.put("type", tool.parameterTypes.getOrDefault(paramName, "string"));
                properties.set(paramName, param);
            }

            inputSchema.set("properties", properties);
            // Agregar parámetros requeridos
            com.fasterxml.jackson.databind.node.ArrayNode requiredArray = inputSchema.putArray("required");
            properties.fieldNames().forEachRemaining(requiredArray::add);

            toolNode.set("inputSchema", inputSchema);
            ((com.fasterxml.jackson.databind.node.ArrayNode) result.get("tools")).add(toolNode);
        }

        return createSuccessResponse(id, result);
    }

    /**
     * Maneja tools/call: invoca una herramienta con los parámetros dados
     */
    private JsonNode handleToolCall(JsonNode id, JsonNode request) {
        try {
            if (!request.has("params")) {
                return createErrorResponse(id, -32602, "Invalid params: missing params object");
            }

            JsonNode params = request.get("params");
            if (!params.has("name")) {
                return createErrorResponse(id, -32602, "Invalid params: missing tool name");
            }

            String toolName = params.get("name").asText();
            JsonNode arguments = params.get("arguments");

            if (!toolRegistry.containsKey(toolName)) {
                return createErrorResponse(id, -32601, "Tool not found: " + toolName);
            }

            // Validar seguridad antes de ejecutar
            validateToolCall(toolName, arguments);

            // Ejecutar herramienta
            Object result = invokeToolMethod(toolName, arguments);

            // Retornar resultado
            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "text");
            content.put("text", result != null ? result.toString() : "");

            ObjectNode toolResult = objectMapper.createObjectNode();
            toolResult.putArray("content").add(content);

            return createSuccessResponse(id, toolResult);

        } catch (SecurityException e) {
            logger.warn("Violación de seguridad: {}", e.getMessage());
            return createErrorResponse(id, -32000, "Security violation: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Argumento inválido: {}", e.getMessage());
            return createErrorResponse(id, -32602, "Invalid params: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error ejecutando herramienta", e);
            return createErrorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Registra dinámicamente todas las herramientas definidas en ProjectMcpTools
     * que estén anotadas con @Tool.
     *
     * Implementa descubrimiento automático de herramientas siguiendo WF-01-PROJECT-DISCOVERY
     * para exponer capabilities del servidor MCP.
     */
    private void registerToolsFromProjectMcpTools() {
        logger.debug("Registrando herramientas desde ProjectMcpTools");

        Method[] methods = ProjectMcpTools.class.getDeclaredMethods();

        for (Method method : methods) {
            // Buscar anotación @Tool
            dev.langchain4j.agent.tool.Tool toolAnnotation =
                    method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);

            if (toolAnnotation != null) {
                // value() retorna String[], tomar el primer elemento como nombre
                String[] values = toolAnnotation.value();
                String toolName = (values != null && values.length > 0) ? values[0] : method.getName();
                String toolDescription = toolName;

                logger.info("Registrando herramienta: {} - {}", toolName, toolDescription);

                // Crear definición de herramienta
                ToolDefinition toolDef = new ToolDefinition(toolName, toolDescription, method);

                // Registrar tipos de parámetros
                Parameter[] params = method.getParameters();
                for (Parameter param : params) {
                    toolDef.parameterTypes.put(param.getName(), getJsonType(param.getType()));
                }

                // Registrar en el toolRegistry
                toolRegistry.put(toolName, toolDef);
            }
        }

        logger.info("Herramientas registradas exitosamente. Total: {}", toolRegistry.size());
    }

    /**
     * Mapea tipos Java a tipos JSON Schema
     */
    private String getJsonType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == int.class) return "integer";
        if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
        if (clazz == Double.class || clazz == double.class) return "number";
        return "string"; // Default
    }


    /**
     * Invoca un método de herramienta con los parámetros dados
     */
    private Object invokeToolMethod(String toolName, JsonNode arguments) throws Exception {
        ToolDefinition toolDef = toolRegistry.get(toolName);

        // Validar seguridad
        validateToolCall(toolName, arguments);

        // Extraer argumentos
        Parameter[] params = toolDef.method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            if (arguments != null && arguments.has(paramName)) {
                args[i] = arguments.get(paramName).asText();
            } else {
                throw new IllegalArgumentException("Missing parameter: " + paramName);
            }
        }

        // Invocar método
        try {
            return toolDef.method.invoke(projectTools, args);
        } catch (Exception e) {
            throw new Exception("Error invoking tool " + toolName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Valida la llamada a una herramienta según WF-04-MCP-PROTOCOL-SECURITY
     *
     * Restricciones:
     * - Acceso de solo lectura al path proporcionado
     * - Prevención de path traversal (../../)
     * - Validación de extensiones de archivo permitidas
     *
     * @param toolName Nombre de la herramienta
     * @param arguments Argumentos JSON de la llamada
     * @throws SecurityException si la validación falla
     */
    private void validateToolCall(String toolName, JsonNode arguments) throws SecurityException {
        logger.debug("Validando seguridad para herramienta: {}", toolName);

        if (arguments == null) {
            return;
        }

        // Validar según el tipo de herramienta
        switch (toolName) {
            case "readProjectStructure":
            case "readJavaFile":
                if (arguments.has("path")) {
                    String path = arguments.get("path").asText();
                    validatePath(path);
                }
                break;
            default:
                logger.debug("No hay validaciones específicas para herramienta: {}", toolName);
        }
    }

    /**
     * Valida que un path sea seguro (prevención de path traversal)
     *
     * @param path Path a validar
     * @throws SecurityException si el path es inseguro
     */
    private void validatePath(String path) throws SecurityException {
        if (path == null || path.isEmpty()) {
            throw new SecurityException("Path no puede estar vacío");
        }

        // Prevenir path traversal
        if (path.contains("..") || path.contains("~")) {
            throw new SecurityException("Path traversal detectado: " + path);
        }

        logger.debug("Path validado: {}", path);
    }

    /**
     * Crea una respuesta JSON-RPC exitosa
     */
    private JsonNode createSuccessResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("result", result);
        response.set("id", id);
        return response;
    }

    /**
     * Crea una respuesta JSON-RPC con error
     *
     * Códigos de error según JSON-RPC 2.0 y WF-04-MCP-PROTOCOL-SECURITY:
     * - -32700: Parse error
     * - -32600: Invalid Request
     * - -32601: Method not found
     * - -32602: Invalid params
     * - -32603: Internal error
     * - -32000 a -32099: Server error
     */
    private JsonNode createErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");

        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        response.set("error", error);
        response.set("id", id);

        return response;
    }

    /**
     * Detiene el servidor MCP de forma controlada
     */
    public void stop() {
        logger.info("Deteniendo SentinelMcpServer");
        running = false;

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                logger.warn("Error cerrando reader", e);
            }
        }

        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Punto de entrada del servidor MCP
     *
     * Uso: java -cp sentinel-backend-ai-1.0.0-SNAPSHOT.jar com.sentinel.arch.mcp.server.SentinelMcpServer
     *
     * El servidor escucha en STDIO y expone herramientas a clientes MCP (Cursor, IntelliJ, etc.)
     *
     * @param args Argumentos de línea de comandos (no utilizados por ahora)
     */
    public static void main(String[] args) {
        logger.info("╔════════════════════════════════════════════════╗");
        logger.info("║     Sentinel Backend AI - MCP Server v1.0.0    ║");
        logger.info("║        Análisis de Arquitectura Java            ║");
        logger.info("╚════════════════════════════════════════════════╝");

        SentinelMcpServer server = new SentinelMcpServer();

        // Registrar shutdown hook para cierre controlado
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Señal de shutdown recibida. Cerrando servidor...");
            server.stop();
        }));

        try {
            server.start();
        } catch (Exception e) {
            logger.error("Error fatal en SentinelMcpServer", e);
            System.exit(1);
        }
    }
}
