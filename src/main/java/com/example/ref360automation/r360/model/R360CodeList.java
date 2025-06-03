package com.example.ref360automation.r360.model;

import java.util.ArrayList;
import java.util.List;

public class R360CodeList {
    private String id; // Optional for creation
    private String termId; // ID of parent RDS
    private String name;
    private String internalId;
    private String alias;
    private String description;
    private boolean hierarchical;
    private List<R360CodeValueField> codeValueFields = new ArrayList<>(); // Can be empty if all inherited
    private String rdsName; // From example, useful for context
    // private R360BusinessIdDef businessIdDef;
    // private R360AssetWorkflowConfiguration assetWorkflowConfiguration;
    // private R360DqValidationInfo dqValidationInfo;
    // private R360AssetStakeholders assetStakeholders;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInternalId() { return internalId; }
    public void setInternalId(String internalId) { this.internalId = internalId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isHierarchical() { return hierarchical; }
    public void setHierarchical(boolean hierarchical) { this.hierarchical = hierarchical; }
    public List<R360CodeValueField> getCodeValueFields() { return codeValueFields; }
    public void setCodeValueFields(List<R360CodeValueField> codeValueFields) { this.codeValueFields = codeValueFields; }
    public String getRdsName() { return rdsName; }
    public void setRdsName(String rdsName) { this.rdsName = rdsName; }
}
