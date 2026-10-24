package com.joutvhu.jmstoolbox.mcp;

import com.joutvhu.jmstoolbox.mcp.server.McpServer;

public class Main {
    public static void main(String[] args) {
        McpServer server = new McpServer();
        server.start();
    }
}
