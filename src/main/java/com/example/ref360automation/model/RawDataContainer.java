package com.example.ref360automation.model;

import java.util.List;
import java.util.Map;

public class RawDataContainer {
    private String sourceName; // e.g., filename or sheet name
    private List<String> headers;
    private List<Map<String, String>> dataRows; // List of rows, where each row is a map of header:value

    public RawDataContainer(String sourceName) {
        this.sourceName = sourceName;
    }

    // Getters and Setters
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<Map<String, String>> getDataRows() {
        return dataRows;
    }

    public void setDataRows(List<Map<String, String>> dataRows) {
        this.dataRows = dataRows;
    }
}
