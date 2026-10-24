package com.joutvhu.jmstoolbox.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutvhu.jmstoolbox.mcp.config.JtbConfigParser;
import com.joutvhu.jmstoolbox.mcp.config.model.ConnectionConfig;
import com.joutvhu.jmstoolbox.mcp.config.model.QManagerDef;
import com.joutvhu.jmstoolbox.mcp.driver.JmsDriverRegistry;
import com.joutvhu.jmstoolbox.mcp.jms.JmsService;
import com.joutvhu.jmstoolbox.mcp.jms.model.JmsMessageDto;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class McpServer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JtbConfigParser configParser = new JtbConfigParser();
    private final JmsService jmsService = new JmsService();

    private boolean isReadOnly() {
        String ro = System.getenv("READ_ONLY");
        if (ro == null) return true; // Default safe read-only
        return !"false".equalsIgnoreCase(ro.trim());
    }

    public void start() {
        System.err.println("[McpServer] Starting JMSToolBox MCP Server using Java MCP SDK 2.x...");

        // Parse configurations
        configParser.parse();

        CountDownLatch closeLatch = new CountDownLatch(1);

        InputStream wrappedIn = new FilterInputStream(System.in) {
            private boolean atLineStart = true;

            @Override
            public int read() throws IOException {
                while (true) {
                    int b = super.read();
                    if (b == -1) {
                        closeLatch.countDown();
                        return -1;
                    }
                    if (atLineStart && (b == '\r' || b == '\n' || b == ' ' || b == '\t')) {
                        continue;
                    }
                    if (b == '\n') {
                        atLineStart = true;
                    } else if (b != '\r') {
                        atLineStart = false;
                    }
                    return b;
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len <= 0) return 0;
                int first = read();
                if (first == -1) return -1;
                b[off] = (byte) first;
                int count = 1;
                if (first == '\n') {
                    return 1;
                }
                while (count < len) {
                    int next = read();
                    if (next == -1) {
                        break;
                    }
                    b[off + count] = (byte) next;
                    count++;
                    if (next == '\n') {
                        break;
                    }
                }
                return count;
            }

            @Override
            public void close() throws IOException {
                super.close();
                closeLatch.countDown();
            }
        };

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                wrappedIn,
                System.out
        ) {
            @Override
            public void setSessionFactory(io.modelcontextprotocol.spec.McpServerSession.Factory factory) {
                super.setSessionFactory(transport -> {
                    io.modelcontextprotocol.spec.McpServerTransport wrappedTransport = new io.modelcontextprotocol.spec.McpServerTransport() {
                        @Override
                        public reactor.core.publisher.Mono<Void> sendMessage(JSONRPCMessage message) {
                            return transport.sendMessage(message);
                        }

                        @Override
                        public <T> T unmarshalFrom(Object data, io.modelcontextprotocol.json.TypeRef<T> typeRef) {
                            return transport.unmarshalFrom(data, typeRef);
                        }

                        @Override
                        public reactor.core.publisher.Mono<Void> closeGracefully() {
                            closeLatch.countDown();
                            return transport.closeGracefully();
                        }

                        @Override
                        public void close() {
                            closeLatch.countDown();
                            transport.close();
                        }
                    };
                    return factory.create(wrappedTransport);
                });
            }
        };

        String serverVersion = McpServer.class.getPackage() != null ? McpServer.class.getPackage().getImplementationVersion() : null;
        if (serverVersion == null || serverVersion.trim().isEmpty()) {
            serverVersion = "1.0.0";
        }

        Implementation serverInfo = Implementation.builder("jmstoolbox-mcp", serverVersion)
                .title("JMSToolBox Universal JMS MCP Server")
                .description("Model Context Protocol server for JMS brokers with dynamic driver loading, supporting IBM MQ, ActiveMQ, Artemis, RabbitMQ, WebLogic, TIBCO, etc.")
                .websiteUrl("https://github.com/joutvhu/jmstoolbox-mcp")
                .build();

        String instructions = "Access and interact with JMS brokers configured in JMSToolBox (IBM MQ, ActiveMQ, RabbitMQ, TIBCO, WebLogic, etc.). Use 'list_connections' first, then 'connect' to broker, 'browse_messages' to inspect queue without consuming, and 'get_queue_depth' to check backlog.";

        McpSyncServer syncServer = io.modelcontextprotocol.server.McpServer.sync(transportProvider)
                .serverInfo(serverInfo)
                .instructions(instructions)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(
                        buildListConnectionsTool(),
                        buildConnectTool(),
                        buildDisconnectTool(),
                        buildGetActiveConnectionsTool(),
                        buildListQueuesTool(),
                        buildBrowseMessagesTool(),
                        buildGetQueueDepthTool(),
                        buildSendMessageTool(),
                        buildConsumeMessageTool()
                )
                .build();

        System.err.println("[McpServer] JMSToolBox MCP Server running. Awaiting requests...");

        Thread shutdownHook = new Thread(() -> {
            closeLatch.countDown();
            try {
                jmsService.disconnectAll();
            } catch (Exception ignored) {}
            try {
                syncServer.close();
            } catch (Exception ignored) {}
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            closeLatch.await();
        } catch (InterruptedException e) {
            System.err.println("[McpServer] Server interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (Exception ignored) {}
            try {
                jmsService.disconnectAll();
            } catch (Exception ignored) {}
            try {
                syncServer.close();
            } catch (Exception ignored) {}
        }
    }

    private SyncToolSpecification buildListConnectionsTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Collections.emptyMap()
        );

        Tool tool = Tool.builder("list_connections", schema)
                .description("List all configured JMS connections from JMSToolBox config.xml")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    configParser.parse(); // Reload if updated
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (ConnectionConfig c : configParser.getConnections()) {
                        list.add(c.toSummary());
                    }
                    try {
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list))))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildConnectTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Name of the connection from list_connections")
                ),
                "required", List.of("connection_name")
        );

        Tool tool = Tool.builder("connect", schema)
                .description("Establish connection to a JMS broker")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String name = (String) request.arguments().get("connection_name");
                    ConnectionConfig config = configParser.findConnection(name);
                    if (config == null) {
                        return errorResult("Connection not found: " + name);
                    }
                    QManagerDef qmDef = configParser.getQManagerDefs().get(config.getQManagerDef());
                    try {
                        jmsService.connect(config, qmDef);
                        return successResult("Connected successfully to " + name);
                    } catch (Exception e) {
                        return errorResult("Failed to connect to " + name + ": " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildDisconnectTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Name of the connection to disconnect")
                ),
                "required", List.of("connection_name")
        );

        Tool tool = Tool.builder("disconnect", schema)
                .description("Disconnect from a JMS broker")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String name = (String) request.arguments().get("connection_name");
                    jmsService.disconnect(name);
                    return successResult("Disconnected from " + name);
                })
                .build();
    }

    private SyncToolSpecification buildGetActiveConnectionsTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Collections.emptyMap()
        );

        Tool tool = Tool.builder("get_active_connections", schema)
                .description("Show currently active connected brokers")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(mapper.writeValueAsString(jmsService.getActiveConnectionNames()))))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildListQueuesTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Name of the connection")
                ),
                "required", List.of("connection_name")
        );

        Tool tool = Tool.builder("list_queues", schema)
                .description("List discovered queues or destinations configured for the connection")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String name = (String) request.arguments().get("connection_name");
                    ConnectionConfig config = configParser.findConnection(name);
                    if (config == null) {
                        return errorResult("Connection not found: " + name);
                    }
                    if (!jmsService.isConnected(name)) {
                        return errorResult("Broker '" + name + "' is not connected. Call 'connect' first.");
                    }

                    // Extract queues defined in session properties if any
                    List<String> queues = new ArrayList<>();
                    for (Map.Entry<String, String> prop : config.getProperties().entrySet()) {
                        if (prop.getKey().toLowerCase().contains("queue") || prop.getKey().toLowerCase().contains("destination")) {
                            queues.add(prop.getValue());
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("connection", name);
                    result.put("configured_queues", queues);
                    result.put("note", "You can browse or query depth on any valid queue name directly using 'browse_messages' or 'get_queue_depth'.");

                    try {
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return errorResult(e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildBrowseMessagesTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Connection name"),
                        "queue_name", Map.of("type", "string", "description", "Destination queue name"),
                        "max_messages", Map.of("type", "integer", "description", "Maximum messages to browse (default 10)")
                ),
                "required", List.of("connection_name", "queue_name")
        );

        Tool tool = Tool.builder("browse_messages", schema)
                .description("Read messages from a queue WITHOUT consuming them (Safe/Non-destructive)")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String connName = (String) request.arguments().get("connection_name");
                    String queueName = (String) request.arguments().get("queue_name");
                    Number max = (Number) request.arguments().getOrDefault("max_messages", 10);
                    try {
                        List<JmsMessageDto> msgs = jmsService.browseMessages(connName, queueName, max.intValue());
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msgs))))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return errorResult("Browse failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildGetQueueDepthTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Connection name"),
                        "queue_name", Map.of("type", "string", "description", "Destination queue name")
                ),
                "required", List.of("connection_name", "queue_name")
        );

        Tool tool = Tool.builder("get_queue_depth", schema)
                .description("Check the number of messages currently in a queue")
                .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    String connName = (String) request.arguments().get("connection_name");
                    String queueName = (String) request.arguments().get("queue_name");
                    try {
                        int depth = jmsService.getQueueDepth(connName, queueName);
                        return successResult("Queue depth for '" + queueName + "': " + depth);
                    } catch (Exception e) {
                        return errorResult("Get queue depth failed: " + e.getMessage());
                    }
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    private SyncToolSpecification buildSendMessageTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Connection name"),
                        "queue_name", Map.of("type", "string", "description", "Destination queue name"),
                        "body", Map.of("type", "string", "description", "Message text / JSON / XML body"),
                        "properties", Map.of("type", "object", "description", "Optional JMS properties (key-value)")
                ),
                "required", List.of("connection_name", "queue_name", "body")
        );

        Tool tool = Tool.builder("send_message", schema)
                .description("Send a message to a queue (Blocked if READ_ONLY=true)")
                .annotations(ToolAnnotations.builder().readOnlyHint(false).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    if (isReadOnly()) {
                        return errorResult("Tool 'send_message' is blocked because server is running in READ_ONLY mode. Set READ_ONLY=false to enable.");
                    }
                    String connName = (String) request.arguments().get("connection_name");
                    String queueName = (String) request.arguments().get("queue_name");
                    String body = (String) request.arguments().get("body");
                    Map<String, Object> msgProps = (Map<String, Object>) request.arguments().get("properties");

                    try {
                        String msgId = jmsService.sendMessage(connName, queueName, body, msgProps);
                        return successResult("Message sent successfully. JMSMessageID: " + msgId);
                    } catch (Exception e) {
                        return errorResult("Send message failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private SyncToolSpecification buildConsumeMessageTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "connection_name", Map.of("type", "string", "description", "Connection name"),
                        "queue_name", Map.of("type", "string", "description", "Destination queue name"),
                        "timeout_ms", Map.of("type", "integer", "description", "Wait timeout in milliseconds (default 1000)")
                ),
                "required", List.of("connection_name", "queue_name")
        );

        Tool tool = Tool.builder("consume_message", schema)
                .description("Dequeue and consume a message (DESTRUCTIVE - removes from queue, Blocked if READ_ONLY=true)")
                .annotations(ToolAnnotations.builder().readOnlyHint(false).build())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    if (isReadOnly()) {
                        return errorResult("Tool 'consume_message' is blocked because server is running in READ_ONLY mode. Set READ_ONLY=false to enable.");
                    }
                    String connName = (String) request.arguments().get("connection_name");
                    String queueName = (String) request.arguments().get("queue_name");
                    Number timeout = (Number) request.arguments().getOrDefault("timeout_ms", 1000);

                    try {
                        JmsMessageDto msg = jmsService.consumeMessage(connName, queueName, timeout.longValue());
                        if (msg == null) {
                            return successResult("No message received within timeout (" + timeout + " ms).");
                        }
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg))))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return errorResult("Consume message failed: " + e.getMessage());
                    }
                })
                .build();
    }

    private CallToolResult successResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(false)
                .build();
    }

    private CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent("Error: " + message)))
                .isError(true)
                .build();
    }
}
