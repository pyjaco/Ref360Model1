package com.example.ref360automation.model;

import java.util.List;
import java.util.Map;

public class Entity {
    private String name;
    private List<Map<String, String>> attributes; // e.g. [{"name": "id", "type": "string"}, {"name": "value", "type": "integer"}]

    public Entity(String name) {
        this.name = name;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Map<String, String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Map<String, String>> attributes) {
        this.attributes = attributes;
    }
}
