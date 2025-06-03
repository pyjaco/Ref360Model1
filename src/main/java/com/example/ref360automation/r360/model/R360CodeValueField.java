package com.example.ref360automation.r360.model;

import java.util.ArrayList;
import java.util.List;

public class R360CodeValueField {
    private String name;
    private List<R360Label> labels = new ArrayList<>();
    private String origin = "TERM";
    private String datatype; // e.g., String, Integer, Decimal, Boolean, Date, Reference
    private boolean mandatory;
    private String relatedTermId;    // For datatype "Reference"
    private List<String> displayColumns = new ArrayList<>(); // For datatype "Reference"
    // dependencyDef might be implied by relatedTermId and displayColumns at this level

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<R360Label> getLabels() { return labels; }
    public void setLabels(List<R360Label> labels) { this.labels = labels; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDatatype() { return datatype; }
    public void setDatatype(String datatype) { this.datatype = datatype; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public String getRelatedTermId() { return relatedTermId; }
    public void setRelatedTermId(String relatedTermId) { this.relatedTermId = relatedTermId; }
    public List<String> getDisplayColumns() { return displayColumns; }
    public void setDisplayColumns(List<String> displayColumns) { this.displayColumns = displayColumns; }
}
