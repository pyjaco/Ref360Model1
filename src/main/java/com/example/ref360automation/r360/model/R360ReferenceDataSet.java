package com.example.ref360automation.r360.model;

import java.util.ArrayList;
import java.util.List;

public class R360ReferenceDataSet {
    private String id; // Optional for creation, used for updates
    private String name;
    private String internalId;
    private String alias;
    private String description;
    private boolean hierarchical;
    private int levels = 1;
    private String defaultList; // ID of a code list
    private List<R360CodeValueField> codeValueFields = new ArrayList<>();
    private R360DependencyDef dependencyDef; // As seen in JSON example for rds2
    // private R360AssetStakeholders assetStakeholders; // Simplified for now

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
    public boolean isHierarchical() { return hierarchical; }
    public void setHierarchical(boolean hierarchical) { this.hierarchical = hierarchical; }
    public int getLevels() { return levels; }
    public void setLevels(int levels) { this.levels = levels; }
    public String getDefaultList() { return defaultList; }
    public void setDefaultList(String defaultList) { this.defaultList = defaultList; }
    public List<R360CodeValueField> getCodeValueFields() { return codeValueFields; }
    public void setCodeValueFields(List<R360CodeValueField> codeValueFields) { this.codeValueFields = codeValueFields; }
    public R360DependencyDef getDependencyDef() { return dependencyDef; }
    public void setDependencyDef(R360DependencyDef dependencyDef) { this.dependencyDef = dependencyDef; }
}
