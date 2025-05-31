package com.example.ref360automation.service;

import com.example.ref360automation.model.ReferenceModel;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ModelDefinitionParserService {
    /**
     * Parses an uploaded spreadsheet (Excel file) that defines a data model.
     * Each sheet is expected to represent an entity, with columns for attribute name and type.
     * @param file The uploaded Excel file (XLS or XLSX).
     * @return A ReferenceModel representing the model defined in the spreadsheet.
     * @throws IOException If there's an error reading the file.
     * @throws IllegalArgumentException If the file format is invalid or content is not as expected.
     */
    ReferenceModel parseModelDefinition(MultipartFile file) throws IOException, IllegalArgumentException;
}
