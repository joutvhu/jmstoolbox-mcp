package com.joutvhu.jmstoolbox.mcp.config;

import com.joutvhu.jmstoolbox.mcp.config.model.ConnectionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class JtbConfigParserTest {

    @Test
    public void testPasswordDecryption() {
        // Plaintext test
        assertEquals("secret123", JtbPasswordDecryptor.decrypt("secret123"));
        assertNull(JtbPasswordDecryptor.decrypt(null));

        // Test Encrypt and Decrypt symmetry matching JMSToolBox
        String original = "mySecretPassword#2026!";
        String encrypted = JtbPasswordDecryptor.encrypt(original);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("{##}"));

        String decrypted = JtbPasswordDecryptor.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testParseSampleConfig(@TempDir Path tempDir) throws Exception {
        File sampleXml = tempDir.resolve("config.xml").toFile();
        try (FileWriter writer = new FileWriter(sampleXml)) {
            writer.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <config>
                    <qManagerDef id="org.titou10.jtb.qm.activemq.ActiveMQQManager" name="Apache Active MQ">
                        <jar>lib/activemq-all.jar</jar>
                    </qManagerDef>
                    <sessionDef name="test-amq" qManagerDef="org.titou10.jtb.qm.activemq.ActiveMQQManager" sessionType="DEV" folder="Local">
                        <host>localhost</host>
                        <port>61616</port>
                        <userid>admin</userid>
                        <password>admin</password>
                        <properties>
                            <property name="brokerURL" value="tcp://localhost:61616" kind="STRING"/>
                        </properties>
                    </sessionDef>
                </config>
                """);
        }

        JtbConfigParser parser = new JtbConfigParser();
        parser.parse(sampleXml);

        assertEquals(1, parser.getQManagerDefs().size());
        assertEquals(1, parser.getConnections().size());

        ConnectionConfig conn = parser.findConnection("test-amq");
        assertNotNull(conn);
        assertEquals("localhost", conn.getHost());
        assertEquals(61616, conn.getPort());
        assertEquals("admin", conn.getUserid());
        assertEquals("admin", conn.getPassword());
        assertEquals("tcp://localhost:61616", conn.getProperties().get("brokerURL"));
    }
}
