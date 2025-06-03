package com.example.ref360automation.service;

import java.util.List;

public interface R360TypeDetectionService {
    /**
     * Suggests an R360 data type (e.g., "String", "Integer", "Decimal", "Boolean", "Date")
     * for a given column of sample data, potentially using NER and other heuristics.
     *
     * @param columnData A list of string values from a single column.
     * @return The suggested R360 data type as a String.
     */
    String suggestR360DataType(List<String> columnData);
}
