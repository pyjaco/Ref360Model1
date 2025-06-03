package com.example.ref360automation.service;

// import com.example.ref360automation.model.ReferenceModel; // Old import
import com.example.ref360automation.r360.model.R360ModelImport; // New import
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ModelDefinitionParserService {
    /**
     * Parses an uploaded spreadsheet (Excel file) that defines a data model.
     * Each sheet is expected to represent an entity, with columns for attribute name and type.
     * @param file The uploaded Excel file (XLS or XLSX).
     * @param defaultRdsName The name to use for the parent Reference Data Set.
     * @return A R360ModelImport representing the model defined in the spreadsheet.
     * @throws IOException If there's an error reading the file.
     * @throws IllegalArgumentException If the file format is invalid or content is not as expected.
     */
    R360ModelImport parseModelDefinition(MultipartFile file, String defaultRdsName) throws IOException, IllegalArgumentException;
}
