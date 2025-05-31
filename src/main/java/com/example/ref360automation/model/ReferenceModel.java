package com.example.ref360automation.model;

import java.util.List;
import java.util.Map;

// A very basic representation of a model.
// This will need to be significantly expanded based on Reference 360 API requirements.
public class ReferenceModel {

    private String modelName;
    private List<Entity> entities;

    public ReferenceModel(String modelName) {
        this.modelName = modelName;
    }

    // Getters and Setters
    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }
}
