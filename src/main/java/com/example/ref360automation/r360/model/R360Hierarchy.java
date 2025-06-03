package com.example.ref360automation.r360.model;

// import com.fasterxml.jackson.annotation.JsonProperty; // If needed for specific naming

public class R360Hierarchy {
    private String id;
    private String name;
    private String internalId;
    private String alias;
    private String description;
    // @JsonProperty("codeListRelations") // Example if Jackson specific naming is needed
    private R360CodeListRelations codeListRelations;
    // private R360AssetStakeholders stakeholderAssignments;
    // private R360AssetWorkflowConfiguration assetWorkflowConfiguration;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInternalId() { return internalId; }
    public void setInternalId(String internalId) { this.internalId = internalId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public R360CodeListRelations getCodeListRelations() { return codeListRelations; }
    public void setCodeListRelations(R360CodeListRelations codeListRelations) { this.codeListRelations = codeListRelations; }
}
