#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/jmstoolbox-mcp.jar"
if [ ! -f "$JAR_PATH" ]; then
    JAR_PATH="$SCRIPT_DIR/target/jmstoolbox-mcp.jar"
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] Could not find jmstoolbox-mcp.jar in $SCRIPT_DIR" >&2
    echo "Please make sure jmstoolbox-mcp.jar is placed in the same directory as this script, or run 'mvn package' first." >&2
    exit 1
fi

JAVA_CMD=""

# 1. Check macOS JMSToolBox bundle JRE
if [ -d "/Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin" ]; then
    JAVA_CMD="/Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin/java"
elif [ -d "/Applications/JMSToolBox.app/Contents/Eclipse/jre/bin" ]; then
    JAVA_CMD="/Applications/JMSToolBox.app/Contents/Eclipse/jre/bin/java"
elif [ -d "/Applications/JMSToolBox.app/Contents/jre/bin" ]; then
    JAVA_CMD="/Applications/JMSToolBox.app/Contents/jre/bin/java"
elif [ -d "$HOME/Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin" ]; then
    JAVA_CMD="$HOME/Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin/java"
elif [ -d "$HOME/Applications/JMSToolBox.app/Contents/jre/bin" ]; then
    JAVA_CMD="$HOME/Applications/JMSToolBox.app/Contents/jre/bin/java"
fi

# 2. Check Linux JMSToolBox JRE
if [ -z "$JAVA_CMD" ]; then
    for path in \
        "/opt/jmstoolbox/jre/bin/java" \
        "/opt/JMSToolBox/jre/bin/java" \
        "/usr/local/jmstoolbox/jre/bin/java" \
        "/usr/share/jmstoolbox/jre/bin/java" \
        "$HOME/jmstoolbox/jre/bin/java" \
        "$HOME/.local/share/jmstoolbox/jre/bin/java"; do
        if [ -x "$path" ]; then
            JAVA_CMD="$path"
            break
        fi
    done
fi

# 3. Check JAVA_HOME
if [ -z "$JAVA_CMD" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# 4. Fallback to system java
if [ -z "$JAVA_CMD" ]; then
    JAVA_CMD="java"
fi

exec "$JAVA_CMD" -jar "$JAR_PATH" "$@"
