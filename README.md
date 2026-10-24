# JMSToolBox MCP Server

Model Context Protocol (MCP) server for JMS messaging brokers, built with Java 17 and the official Java MCP SDK (`io.modelcontextprotocol.sdk:mcp-core`), packaged as a universal cross-platform CLI tool via npm.

Supports all JMS brokers supported by [JMSToolBox](https://github.com/jmstoolbox/jmstoolbox) (**IBM MQ**, **ActiveMQ**, **Artemis**, **RabbitMQ**, **TIBCO EMS**, **Solace**, **Oracle WebLogic**, **SonicMQ**, etc.) by directly leveraging JMSToolBox's configuration and dynamic JAR driver loading.

---

## 🌟 Key Features

- **Universal JMS Compatibility:** Loads broker client JARs on-demand using dedicated `URLClassLoader`s, eliminating native C client dependencies.
- **Auto-Discovery across Platforms:**
  - Auto-detects JMSToolBox installation directory on **Windows**, **macOS**, and **Linux** to locate bundled vendor driver JARs.
  - Auto-detects JMSToolBox configuration files from standard locations across operating systems (`~/.jtb/JMSToolBox/config.xml`, `APPDATA`, `Application Support`, `XDG_CONFIG_HOME`).
- **Native Password Decryption:** Decrypts encrypted passwords stored in JMSToolBox `config.xml` directly.
- **Safe Queue Browsing:** Non-destructive queue inspection with complete headers, properties, and message payloads.
- **Safety Safeguards:** Destructive tools (`send_message`, `consume_message`) are blocked by default unless `READ_ONLY=false` is set.
- **NPM Package & npx Support:** Can be installed globally via npm or run directly with Node.js.

---

## 🛠 Available Tools

| Tool | Description | Safe? |
|------|-------------|:----:|
| `list_connections` | List all configured connections from JMSToolBox `config.xml` | ✅ |
| `connect` | Connect to a broker using its dynamic ConnectionFactory | ✅ |
| `disconnect` | Disconnect and clean up resources | ✅ |
| `get_active_connections`| View currently connected brokers | ✅ |
| `browse_messages` | Browse messages without consuming (reads headers + body) | ✅ |
| `get_queue_depth` | Count total messages pending in a queue | ✅ |
| `send_message` | Send text/JSON/XML message to a queue | ⚠️ (Requires `READ_ONLY=false`) |
| `consume_message` | Dequeue/consume message from a queue | ⚠️ (Requires `READ_ONLY=false`) |

---

## 📦 Installation & Packaging

### Method 1: Global NPM Install (Recommended for Local Dev)
Run the automated build & install script:
```bash
npm run publish:local
```
This builds the fat JAR using Maven, packages the npm tarball, and installs `jmstoolbox-mcp` globally.

### Method 2: Manual Maven Build
```bash
mvn clean package
```
Generates `target/jmstoolbox-mcp.jar`.

---

## ⚙️ MCP Configuration

Add to your `.kiro/settings/mcp.json` or Claude Desktop configuration:

### Option A: Using NPM binary (if installed globally)
```json
{
  "mcpServers": {
    "jmstoolbox": {
      "command": "npx",
      "args": ["-y", "jmstoolbox-mcp"],
      "env": {
        "READ_ONLY": "true"
      },
      "disabled": false,
      "autoApprove": [
        "list_connections",
        "connect",
        "disconnect",
        "get_active_connections",
        "browse_messages",
        "get_queue_depth"
      ]
    }
  }
}
```

### Option B: Using direct launcher script
```json
{
  "mcpServers": {
    "jmstoolbox": {
      "command": "F:\\Documents\\Library\\AI\\jmstoolbox-mcp\\jmstoolbox-mcp.cmd",
      "args": [],
      "env": {
        "READ_ONLY": "true"
      },
      "disabled": false,
      "autoApprove": [
        "list_connections",
        "connect",
        "disconnect",
        "get_active_connections",
        "browse_messages",
        "get_queue_depth"
      ]
    }
  }
}
```

---

## 🔧 Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `READ_ONLY` | Safety flag (`true`/`false`). When `true`, prevents destructive actions (`send_message`, `consume_message`). | `true` |
| `JMS_CONFIG_PATH` | Explicit path to `config.xml`. | Auto-detected (`~/.jtb/JMSToolBox/config.xml`) |
| `JTB_HOME` | Explicit path to JMSToolBox installation directory. | Auto-detected (`Program Files`, `/Applications`, `/opt`, etc.) |
| `JTB_KEY` | Custom encryption key if customized in JMSToolBox. | JMSToolBox default key |
