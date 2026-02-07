package com.sentinel.arch.mcp;

import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service Interconnection Discovery Tool
 * 
 * Discovers service interconnections by analyzing source code for protocol fingerprints
 * and mapping inbound/outbound connections.
 */
public class ServiceInterconnectionDiscovery {

    private static final Set<String> JAVA_FILE_EXTENSIONS = Set.of(".java", ".kt", ".scala");
    private static final Set<String> CONFIG_FILE_EXTENSIONS = Set.of(".yml", ".yaml", ".properties", ".xml");
    
    // Inbound protocol patterns
    private static final Pattern REST_CONTROLLER_PATTERN = Pattern.compile("@RestController|@Controller");
    private static final Pattern REQUEST_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@GetMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@PostMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@PutMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@DeleteMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@PatchMapping\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']|@RequestMapping\\s*\\([\"']([^\"']*)[\"']|@GetMapping\\s*\\([\"']([^\"']*)[\"']|@PostMapping\\s*\\([\"']([^\"']*)[\"']|@PutMapping\\s*\\([\"']([^\"']*)[\"']|@DeleteMapping\\s*\\([\"']([^\"']*)[\"']");
    private static final Pattern GRPC_SERVICE_PATTERN = Pattern.compile("@GrpcService|extends\\s+\\w+ImplBase|extends\\s+Abstract\\w+Impl");
    private static final Pattern KAFKA_LISTENER_PATTERN = Pattern.compile("@KafkaListener\\s*\\([^)]*topics\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern RABBIT_LISTENER_PATTERN = Pattern.compile("@RabbitListener\\s*\\([^)]*queues\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern JMS_LISTENER_PATTERN = Pattern.compile("@JmsListener\\s*\\([^)]*destination\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern STREAM_LISTENER_PATTERN = Pattern.compile("@StreamListener\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']");
    
    // Outbound protocol patterns
    private static final Pattern FEIGN_CLIENT_PATTERN = Pattern.compile("@FeignClient\\s*\\([^)]*name\\s*=\\s*[\"']([^\"']*)[\"']|@FeignClient\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern WEB_CLIENT_PATTERN = Pattern.compile("WebClient\\.create\\s*\\([\"']([^\"']*)[\"']|WebClient\\.builder\\(\\)");
    private static final Pattern REST_TEMPLATE_PATTERN = Pattern.compile("restTemplate\\.getForObject\\s*\\([\"']([^\"']*)[^)]+|restTemplate\\.exchange\\s*\\([\"']([^\"']*)[^)]+");
    private static final Pattern KAFKA_TEMPLATE_SEND_PATTERN = Pattern.compile("kafkaTemplate\\.send\\s*\\([^\"']*\"([^\"]+)\"|KafkaTemplate\\.send\\s*\\([^\"']*\"([^\"]+)\"");
    private static final Pattern STREAM_BRIDGE_SEND_PATTERN = Pattern.compile("streamBridge\\.send\\s*\\([^\"']*\"([^\"]+)\"|StreamBridge\\.send\\s*\\([^\"']*\"([^\"]+)\"");
    private static final Pattern RABBIT_TEMPLATE_PATTERN = Pattern.compile("rabbitTemplate\\.convertAndSend\\s*\\([^\"']*\"([^\"]+)\"|RabbitTemplate\\.convertAndSend\\s*\\([^\"']*\"([^\"]+)\"");
    
    // Configuration patterns
    private static final Pattern APPLICATION_YML_SERVICE_URLS = Pattern.compile("(?:http://|https://)[^\\s\"'/]+[^\\s\"'/?#]+");
    
    /**
     * Discovers service interconnections by analyzing source code for protocol fingerprints
     * and mapping inbound/outbound connections.
     * 
     * @param projectPath The path to the project to analyze
     * @return A structured summary of service interconnections in JSON-like format
     * @throws IOException If there's an error reading files
     */
    @Tool("Discovers service interconnections by analyzing source code for protocol fingerprints and mapping inbound/outbound connections")
    public String discover_service_interconnections(String projectPath) throws IOException {
        Path rootPath = Paths.get(projectPath);
        
        if (!Files.exists(rootPath)) {
            System.err.println("Error: Project path does not exist: " + projectPath);
            return "{\"error\": \"Project path does not exist: " + projectPath + "\"}";
        }
        
        // Discover inbound services (entry points)
        List<InboundService> inboundServices = discoverInboundServices(rootPath);
        
        // Discover outbound services (external dependencies)
        List<OutboundService> outboundServices = discoverOutboundServices(rootPath);
        
        // Generate structured JSON-like summary
        return generateInterconnectionSummary(inboundServices, outboundServices);
    }
    
    /**
     * Discovers inbound services (entry points to the application)
     */
    private List<InboundService> discoverInboundServices(Path rootPath) throws IOException {
        List<InboundService> inboundServices = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(path -> JAVA_FILE_EXTENSIONS.contains(getFileExtension(path.toString())))
                 .filter(path -> !path.toString().contains("/target/") && !path.toString().contains("/build/"))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         String fileName = path.getFileName().toString();
                         
                         // Check for REST controllers
                         if (REST_CONTROLLER_PATTERN.matcher(content).find()) {
                             List<String> mappings = extractRequestMappingValues(content);
                             for (String mapping : mappings) {
                                 inboundServices.add(new InboundService("REST", mapping, fileName));
                             }
                         }
                         
                         // Check for gRPC services
                         Matcher grpcMatcher = GRPC_SERVICE_PATTERN.matcher(content);
                         while (grpcMatcher.find()) {
                             inboundServices.add(new InboundService("gRPC", "Service Methods", fileName));
                         }
                         
                         // Check for Kafka listeners
                         Matcher kafkaMatcher = KAFKA_LISTENER_PATTERN.matcher(content);
                         while (kafkaMatcher.find()) {
                             String topic = kafkaMatcher.group(1);
                             inboundServices.add(new InboundService("Messaging-Kafka", topic, fileName));
                         }
                         
                         // Check for Rabbit listeners
                         Matcher rabbitMatcher = RABBIT_LISTENER_PATTERN.matcher(content);
                         while (rabbitMatcher.find()) {
                             String queue = rabbitMatcher.group(1);
                             inboundServices.add(new InboundService("Messaging-RabbitMQ", queue, fileName));
                         }
                         
                         // Check for JMS listeners
                         Matcher jmsMatcher = JMS_LISTENER_PATTERN.matcher(content);
                         while (jmsMatcher.find()) {
                             String destination = jmsMatcher.group(1);
                             inboundServices.add(new InboundService("Messaging-JMS", destination, fileName));
                         }
                         
                         // Check for Stream listeners
                         Matcher streamMatcher = STREAM_LISTENER_PATTERN.matcher(content);
                         while (streamMatcher.find()) {
                             String binding = streamMatcher.group(1);
                             inboundServices.add(new InboundService("Messaging-Stream", binding, fileName));
                         }
                     } catch (IOException e) {
                         System.err.println("Error reading file: " + path + ", Error: " + e.getMessage());
                     }
                 });
        }
        
        return inboundServices;
    }
    
    /**
     * Discovers outbound services (external dependencies)
     */
    private List<OutboundService> discoverOutboundServices(Path rootPath) throws IOException {
        List<OutboundService> outboundServices = new ArrayList<>();
        
        // Search for outbound calls in Java files
        try (Stream<Path> paths = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(path -> JAVA_FILE_EXTENSIONS.contains(getFileExtension(path.toString())))
                 .filter(path -> !path.toString().contains("/target/") && !path.toString().contains("/build/"))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         String fileName = path.getFileName().toString();
                         
                         // Extract Feign clients
                         Matcher feignMatcher = FEIGN_CLIENT_PATTERN.matcher(content);
                         while (feignMatcher.find()) {
                             String serviceName = feignMatcher.group(1) != null ? feignMatcher.group(1) : feignMatcher.group(2);
                             outboundServices.add(new OutboundService(serviceName, "REST", "FeignClient", fileName));
                         }
                         
                         // Extract WebClient usage
                         Matcher webClientMatcher = WEB_CLIENT_PATTERN.matcher(content);
                         while (webClientMatcher.find()) {
                             String url = webClientMatcher.group(1);
                             if (url != null) {
                                 outboundServices.add(new OutboundService(url, "REST", "WebClient", fileName));
                             } else {
                                 outboundServices.add(new OutboundService("External Services", "REST", "WebClient", fileName));
                             }
                         }
                         
                         // Extract RestTemplate usage
                         Matcher restTemplateMatcher = REST_TEMPLATE_PATTERN.matcher(content);
                         while (restTemplateMatcher.find()) {
                             String url = restTemplateMatcher.group(1) != null ? restTemplateMatcher.group(1) : restTemplateMatcher.group(2);
                             if (url != null) {
                                 outboundServices.add(new OutboundService(url, "REST", "RestTemplate", fileName));
                             } else {
                                 outboundServices.add(new OutboundService("External Services", "REST", "RestTemplate", fileName));
                             }
                         }
                         
                         // Extract Kafka template usage
                         Matcher kafkaSendMatcher = KAFKA_TEMPLATE_SEND_PATTERN.matcher(content);
                         while (kafkaSendMatcher.find()) {
                             String topic = kafkaSendMatcher.group(1) != null ? kafkaSendMatcher.group(1) : kafkaSendMatcher.group(2);
                             outboundServices.add(new OutboundService(topic, "Messaging-Kafka", "KafkaTemplate", fileName));
                         }
                         
                         // Extract Stream bridge usage
                         Matcher streamBridgeMatcher = STREAM_BRIDGE_SEND_PATTERN.matcher(content);
                         while (streamBridgeMatcher.find()) {
                             String destination = streamBridgeMatcher.group(1) != null ? streamBridgeMatcher.group(1) : streamBridgeMatcher.group(2);
                             outboundServices.add(new OutboundService(destination, "Messaging-Stream", "StreamBridge", fileName));
                         }
                         
                         // Extract Rabbit template usage
                         Matcher rabbitMatcher = RABBIT_TEMPLATE_PATTERN.matcher(content);
                         while (rabbitMatcher.find()) {
                             String queue = rabbitMatcher.group(1) != null ? rabbitMatcher.group(1) : rabbitMatcher.group(2);
                             outboundServices.add(new OutboundService(queue, "Messaging-RabbitMQ", "RabbitTemplate", fileName));
                         }
                     } catch (IOException e) {
                         System.err.println("Error reading file: " + path + ", Error: " + e.getMessage());
                     }
                 });
        }
        
        // Search for service URLs in configuration files
        try (Stream<Path> paths = Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(path -> CONFIG_FILE_EXTENSIONS.contains(getFileExtension(path.toString())))
                 .filter(path -> !path.toString().contains("/target/") && !path.toString().contains("/build/"))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         String fileName = path.getFileName().toString();
                         
                         // Look for URLs in configuration files
                         Matcher urlMatcher = APPLICATION_YML_SERVICE_URLS.matcher(content);
                         while (urlMatcher.find()) {
                             String url = urlMatcher.group(0);
                             outboundServices.add(new OutboundService(url, "Configuration", "Service URL", fileName));
                         }
                     } catch (IOException e) {
                         System.err.println("Error reading config file: " + path + ", Error: " + e.getMessage());
                     }
                 });
        }
        
        return outboundServices;
    }
    
    /**
     * Extracts request mapping values from content
     */
    private List<String> extractRequestMappingValues(String content) {
        List<String> mappings = new ArrayList<>();
        Matcher matcher = REQUEST_MAPPING_PATTERN.matcher(content);
        
        while (matcher.find()) {
            // Check each capturing group to find which one matched
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    mappings.add(group);
                    break; // Only add the first non-null group
                }
            }
        }
        
        if (mappings.isEmpty()) {
            mappings.add("Generic REST endpoints");
        }
        
        return mappings;
    }
    
    /**
     * Generates a structured JSON-like summary of service interconnections
     */
    private String generateInterconnectionSummary(List<InboundService> inboundServices, 
                                                 List<OutboundService> outboundServices) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"discovery_summary\": {\n");
        sb.append("    \"inbound_services\": [\n");
        
        for (int i = 0; i < inboundServices.size(); i++) {
            InboundService service = inboundServices.get(i);
            sb.append("      {\n");
            sb.append("        \"protocol\": \"").append(service.protocol).append("\",\n");
            sb.append("        \"endpoint\": \"").append(service.endpoint).append("\",\n");
            sb.append("        \"source_file\": \"").append(service.sourceFile).append("\"\n");
            sb.append("      }");
            if (i < inboundServices.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");
        
        sb.append("    \"outbound_services\": [\n");
        for (int i = 0; i < outboundServices.size(); i++) {
            OutboundService service = outboundServices.get(i);
            sb.append("      {\n");
            sb.append("        \"target_service\": \"").append(service.targetService).append("\",\n");
            sb.append("        \"protocol\": \"").append(service.protocol).append("\",\n");
            sb.append("        \"type\": \"").append(service.type).append("\",\n");
            sb.append("        \"source_file\": \"").append(service.sourceFile).append("\"\n");
            sb.append("      }");
            if (i < outboundServices.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n");
        
        sb.append("    \"connection_mapping\": {\n");
        sb.append("      \"inbound\": [\n");
        for (int i = 0; i < inboundServices.size(); i++) {
            InboundService service = inboundServices.get(i);
            sb.append("        \"Inbound: ").append(service.protocol).append(" -> ").append(service.endpoint).append("\"");
            if (i < inboundServices.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("      ],\n");
        sb.append("      \"outbound\": [\n");
        for (int i = 0; i < outboundServices.size(); i++) {
            OutboundService service = outboundServices.get(i);
            sb.append("        \"Outbound: ").append(service.targetService).append(" via ").append(service.protocol).append("\"");
            if (i < outboundServices.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    /**
     * Helper method to extract file extension
     */
    private String getFileExtension(String pathStr) {
        int dotIndex = pathStr.lastIndexOf('.');
        return dotIndex > 0 ? pathStr.substring(dotIndex) : "";
    }
    
    /**
     * Inner class to represent an inbound service
     */
    private static class InboundService {
        final String protocol;
        final String endpoint;
        final String sourceFile;
        
        InboundService(String protocol, String endpoint, String sourceFile) {
            this.protocol = protocol;
            this.endpoint = endpoint;
            this.sourceFile = sourceFile;
        }
    }
    
    /**
     * Inner class to represent an outbound service
     */
    private static class OutboundService {
        final String targetService;
        final String protocol;
        final String type;
        final String sourceFile;
        
        OutboundService(String targetService, String protocol, String type, String sourceFile) {
            this.targetService = targetService;
            this.protocol = protocol;
            this.type = type;
            this.sourceFile = sourceFile;
        }
    }
}