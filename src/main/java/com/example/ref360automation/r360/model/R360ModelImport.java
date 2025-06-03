package com.example.ref360automation.r360.model;

import java.util.ArrayList;
import java.util.List;

public class R360ModelImport {
    private String version = "3.9"; // Default as per example
    private List<R360ReferenceDataSet> referenceDataSets = new ArrayList<>();
    private List<R360CodeList> codeLists = new ArrayList<>();
    private List<R360Crosswalk> crosswalks = new ArrayList<>();
    private List<R360Hierarchy> hierarchies = new ArrayList<>();
    // private R360Enums enums; // Add if enums structure is complex, else Map<String, List<R360EnumEntry>>

    // Getters and Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public List<R360ReferenceDataSet> getReferenceDataSets() { return referenceDataSets; }
    public void setReferenceDataSets(List<R360ReferenceDataSet> referenceDataSets) { this.referenceDataSets = referenceDataSets; }
    public List<R360CodeList> getCodeLists() { return codeLists; }
    public void setCodeLists(List<R360CodeList> codeLists) { this.codeLists = codeLists; }
    public List<R360Crosswalk> getCrosswalks() { return crosswalks; }
    public void setCrosswalks(List<R360Crosswalk> crosswalks) { this.crosswalks = crosswalks; }
    public List<R360Hierarchy> getHierarchies() { return hierarchies; }
    public void setHierarchies(List<R360Hierarchy> hierarchies) { this.hierarchies = hierarchies; }
}
