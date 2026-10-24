package com.joutvhu.jmstoolbox.mcp.config.model;

import java.util.ArrayList;
import java.util.List;

public class QManagerDef {
    private String id;
    private String name;
    private List<String> jars = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getJars() {
        return jars;
    }

    public void setJars(List<String> jars) {
        this.jars = jars;
    }
}
