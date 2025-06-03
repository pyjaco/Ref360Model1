package com.example.ref360automation.r360.model;

public class R360RelatedCodeListInfo {
    private String codeListId;
    private String codeListName;
    private String codeListInternalId;
    private String termId; // RDS ID
    private String termName; // RDS Name

    // Getters and Setters
    public String getCodeListId() { return codeListId; }
    public void setCodeListId(String codeListId) { this.codeListId = codeListId; }
    public String getCodeListName() { return codeListName; }
    public void setCodeListName(String codeListName) { this.codeListName = codeListName; }
    public String getCodeListInternalId() { return codeListInternalId; }
    public void setCodeListInternalId(String codeListInternalId) { this.codeListInternalId = codeListInternalId; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName; }
}
