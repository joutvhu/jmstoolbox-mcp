package com.joutvhu.jmstoolbox.mcp.config.model;

import com.joutvhu.jmstoolbox.mcp.config.JtbPasswordDecryptor;

import java.util.HashMap;
import java.util.Map;

public class ConnectionConfig {
    private String name;
    private String qManagerDef;
    private String host;
    private int port;
    private String host2;
    private int port2;
    private String host3;
    private int port3;

    private String userid;
    private String passwordEncrypted;
    private String sessionType;
    private String folder;
    private Map<String, String> properties = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQManagerDef() {
        return qManagerDef;
    }

    public void setQManagerDef(String qManagerDef) {
        this.qManagerDef = qManagerDef;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost2() {
        return host2;
    }

    public void setHost2(String host2) {
        this.host2 = host2;
    }

    public int getPort2() {
        return port2;
    }

    public void setPort2(int port2) {
        this.port2 = port2;
    }

    public String getHost3() {
        return host3;
    }

    public void setHost3(String host3) {
        this.host3 = host3;
    }

    public int getPort3() {
        return port3;
    }

    public void setPort3(int port3) {
        this.port3 = port3;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String getPassword() {
        if (name != null) {
            String envKey = "JMS_PASSWORD_" + name.toUpperCase().replace("-", "_").replace(" ", "_");
            String envVal = System.getenv(envKey);
            if (envVal != null && !envVal.trim().isEmpty()) {
                return envVal;
            }
        }
        return JtbPasswordDecryptor.decrypt(passwordEncrypted);
    }

    public Map<String, Object> toSummary() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("qManagerDef", qManagerDef);
        map.put("host", host);
        map.put("port", port);
        map.put("userid", userid);
        map.put("sessionType", sessionType);
        map.put("folder", folder);

        // Mask sensitive properties if any
        Map<String, String> safeProps = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String k = entry.getKey().toLowerCase();
            if (k.contains("password") || k.contains("secret") || k.contains("key")) {
                safeProps.put(entry.getKey(), "******");
            } else {
                safeProps.put(entry.getKey(), entry.getValue());
            }
        }
        map.put("properties", safeProps);
        return map;
    }
}
