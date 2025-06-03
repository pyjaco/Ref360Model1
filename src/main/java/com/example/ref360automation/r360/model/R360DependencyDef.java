package com.example.ref360automation.r360.model;

import java.util.ArrayList;
import java.util.List;

public class R360DependencyDef {
    private String termId;
    private List<String> displayColumns = new ArrayList<>();

    // Getters and Setters
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public List<String> getDisplayColumns() { return displayColumns; }
    public void setDisplayColumns(List<String> displayColumns) { this.displayColumns = displayColumns; }
}
