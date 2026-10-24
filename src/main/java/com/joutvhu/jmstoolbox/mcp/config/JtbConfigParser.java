package com.joutvhu.jmstoolbox.mcp.config;

import com.joutvhu.jmstoolbox.mcp.config.model.ConnectionConfig;
import com.joutvhu.jmstoolbox.mcp.config.model.QManagerDef;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JtbConfigParser {
    private final Map<String, QManagerDef> qManagerDefs = new HashMap<>();
    private final List<ConnectionConfig> connections = new ArrayList<>();

    public static File findConfigFile() {
        // 1. Check custom environment variable
        String envPath = System.getenv("JMS_CONFIG_PATH");
        if (envPath != null && !envPath.trim().isEmpty()) {
            File f = new File(envPath.trim());
            if (f.exists()) return f;
        }

        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();

        // 2. Standard user directory: ~/.jtb/JMSToolBox/config.xml (Cross-platform default for JMSToolBox)
        List<Path> candidatePaths = new ArrayList<>();
        candidatePaths.add(Paths.get(userHome, ".jtb", "JMSToolBox", "config.xml"));

        // Windows specific roaming/local fallback locations if any
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                candidatePaths.add(Paths.get(appData, "JMSToolBox", "config.xml"));
                candidatePaths.add(Paths.get(appData, ".jtb", "JMSToolBox", "config.xml"));
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                candidatePaths.add(Paths.get(localAppData, "JMSToolBox", "config.xml"));
            }
        } else if (os.contains("mac")) {
            // macOS standard application support
            candidatePaths.add(Paths.get(userHome, "Library", "Application Support", "JMSToolBox", "config.xml"));
            candidatePaths.add(Paths.get(userHome, "Library", "JMSToolBox", "config.xml"));
        } else {
            // Linux / Unix XDG config
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isEmpty()) {
                candidatePaths.add(Paths.get(xdgConfig, "jmstoolbox", "config.xml"));
            }
            candidatePaths.add(Paths.get(userHome, ".config", "jmstoolbox", "config.xml"));
            candidatePaths.add(Paths.get(userHome, ".local", "share", "JMSToolBox", "config.xml"));
        }

        for (Path p : candidatePaths) {
            File f = p.toFile();
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        return null;
    }

    public void parse() {
        File configFile = findConfigFile();
        if (configFile == null || !configFile.exists()) {
            System.err.println("[JtbConfigParser] JMSToolBox config.xml not found. Expected at ~/.jtb/JMSToolBox/config.xml");
            return;
        }
        parse(configFile);
    }

    public void parse(File configFile) {
        qManagerDefs.clear();
        connections.clear();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // Disable DTD / XXE for safety
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(configFile);
            doc.getDocumentElement().normalize();

            // 1. Parse qManagerDef
            NodeList qmList = doc.getElementsByTagName("qManagerDef");
            for (int i = 0; i < qmList.getLength(); i++) {
                Element qmElem = (Element) qmList.item(i);
                QManagerDef qm = new QManagerDef();
                qm.setId(qmElem.getAttribute("id"));
                qm.setName(qmElem.getAttribute("name"));

                NodeList jarList = qmElem.getElementsByTagName("jar");
                for (int j = 0; j < jarList.getLength(); j++) {
                    Element jarElem = (Element) jarList.item(j);
                    String jarPath = jarElem.getTextContent();
                    if (jarPath != null && !jarPath.trim().isEmpty()) {
                        qm.getJars().add(jarPath.trim());
                    }
                }
                qManagerDefs.put(qm.getId(), qm);
            }

            // 2. Parse sessionDef
            NodeList sessionList = doc.getElementsByTagName("sessionDef");
            for (int i = 0; i < sessionList.getLength(); i++) {
                Element sessElem = (Element) sessionList.item(i);
                ConnectionConfig conn = new ConnectionConfig();
                conn.setName(sessElem.getAttribute("name"));
                conn.setQManagerDef(sessElem.getAttribute("qManagerDef"));
                conn.setSessionType(sessElem.getAttribute("sessionType"));
                conn.setFolder(sessElem.getAttribute("folder"));

                conn.setHost(getTagValue(sessElem, "host"));
                String portStr = getTagValue(sessElem, "port");
                if (portStr != null && !portStr.isEmpty()) {
                    try {
                        conn.setPort(Integer.parseInt(portStr));
                    } catch (NumberFormatException ignored) {}
                }

                conn.setHost2(getTagValue(sessElem, "host2"));
                String port2Str = getTagValue(sessElem, "port2");
                if (port2Str != null && !port2Str.isEmpty()) {
                    try {
                        conn.setPort2(Integer.parseInt(port2Str));
                    } catch (NumberFormatException ignored) {}
                }

                conn.setUserid(getTagValue(sessElem, "userid"));
                conn.setPasswordEncrypted(getTagValue(sessElem, "password"));

                // Parse properties
                NodeList propsList = sessElem.getElementsByTagName("properties");
                if (propsList.getLength() > 0) {
                    Element propsElem = (Element) propsList.item(0);
                    NodeList propList = propsElem.getElementsByTagName("property");
                    for (int k = 0; k < propList.getLength(); k++) {
                        Element propElem = (Element) propList.item(k);
                        String pName = propElem.getAttribute("name");
                        String pValue = propElem.getAttribute("value");
                        if (pName != null && !pName.isEmpty()) {
                            conn.getProperties().put(pName, pValue);
                        }
                    }
                }

                connections.add(conn);
            }

            System.err.println("[JtbConfigParser] Parsed " + qManagerDefs.size() + " QManagers, " + connections.size() + " sessions from " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[JtbConfigParser] Error parsing config.xml: " + e.getMessage());
        }
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() > 0 && nl.item(0).getFirstChild() != null) {
            return nl.item(0).getTextContent().trim();
        }
        return null;
    }

    public Map<String, QManagerDef> getQManagerDefs() {
        return qManagerDefs;
    }

    public List<ConnectionConfig> getConnections() {
        return connections;
    }

    public ConnectionConfig findConnection(String name) {
        if (name == null) return null;
        for (ConnectionConfig c : connections) {
            if (name.equalsIgnoreCase(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
