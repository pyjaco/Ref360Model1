package com.example.ref360automation.r360.model;

public class R360Label {
    private String language = "en";
    private String value;

    public R360Label() {}
    public R360Label(String language, String value) {
        this.language = language;
        this.value = value;
    }
    // Getters and Setters
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
