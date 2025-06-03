package com.example.ref360automation.r360.model;

public class R360Crosswalk {
    private String id;
    private String internalId;
    private String alias;
    private String description;
    private String status;
    private String sourceCodelistId;
    private String targetCodelistId;
    // private R360AssetStakeholders assetStakeholders;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInternalId() { return internalId; }
    public void setInternalId(String internalId) { this.internalId = internalId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceCodelistId() { return sourceCodelistId; }
    public void setSourceCodelistId(String sourceCodelistId) { this.sourceCodelistId = sourceCodelistId; }
    public String getTargetCodelistId() { return targetCodelistId; }
    public void setTargetCodelistId(String targetCodelistId) { this.targetCodelistId = targetCodelistId; }
}
