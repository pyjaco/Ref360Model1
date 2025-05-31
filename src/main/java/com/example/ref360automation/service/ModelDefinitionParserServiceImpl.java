package com.example.ref360automation.service;

import com.example.ref360automation.model.Entity;
import com.example.ref360automation.model.ReferenceModel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelDefinitionParserServiceImpl implements ModelDefinitionParserService {

    private static final Logger logger = LoggerFactory.getLogger(ModelDefinitionParserServiceImpl.class);

    // Define expected column headers for clarity and validation
    private static final String ATTRIBUTE_NAME_HEADER = "Attribute Name";
    private static final String DATA_TYPE_HEADER = "Data Type";

    @Override
    public ReferenceModel parseModelDefinition(MultipartFile multipartFile) throws IOException, IllegalArgumentException {
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String fileName = multipartFile.getOriginalFilename();
        logger.info("Parsing model definition from spreadsheet: {}", fileName);

        ReferenceModel definedModel = new ReferenceModel("DefinedDataModel_" + removeFileExtension(fileName));
        List<Entity> entities = new ArrayList<>();

        try (InputStream is = multipartFile.getInputStream()) {
            Workbook workbook;
            if (fileName != null && fileName.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(is);
            } else if (fileName != null && fileName.toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(is);
            } else {
                throw new IllegalArgumentException("Invalid file format. Please upload an Excel file (XLS or XLSX).");
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String entityName = sheet.getSheetName();
                if (entityName == null || entityName.trim().isEmpty()) {
                    logger.warn("Skipping sheet with no name (index {}).", i);
                    continue;
                }
                entityName = cleanEntityName(entityName);
                logger.info("Processing sheet as entity: {}", entityName);

                Entity entity = new Entity(entityName);
                List<Map<String, String>> attributes = new ArrayList<>();

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    logger.warn("Skipping sheet '{}' as it has no header row.", sheet.getSheetName());
                    continue;
                }

                // Validate header row (optional but good practice)
                String header1 = getCellStringValue(headerRow.getCell(0)).trim();
                String header2 = getCellStringValue(headerRow.getCell(1)).trim();

                if (!ATTRIBUTE_NAME_HEADER.equalsIgnoreCase(header1) || !DATA_TYPE_HEADER.equalsIgnoreCase(header2)) {
                    logger.warn("Sheet '{}' has incorrect headers. Expected '{}' and '{}', but got '{}' and '{}'. Skipping.",
                            sheet.getSheetName(), ATTRIBUTE_NAME_HEADER, DATA_TYPE_HEADER, header1, header2);
                    continue;
                }

                for (int j = 1; j <= sheet.getLastRowNum(); j++) { // Start from second row (data)
                    Row dataRow = sheet.getRow(j);
                    if (dataRow == null) continue;

                    String attributeName = getCellStringValue(dataRow.getCell(0)).trim();
                    String attributeType = getCellStringValue(dataRow.getCell(1)).trim();

                    if (attributeName.isEmpty()) { // Skip if attribute name is blank
                        logger.trace("Skipping row {} in sheet '{}' due to empty attribute name.", j + 1, sheet.getSheetName());
                        continue;
                    }
                    if (attributeType.isEmpty()) { // Default to String if type is blank
                        attributeType = "String";
                        logger.trace("Attribute type for '{}' in sheet '{}' is empty, defaulting to String.", attributeName, sheet.getSheetName());
                    }

                    Map<String, String> attribute = new HashMap<>();
                    attribute.put("name", attributeName);
                    attribute.put("type", attributeType);
                    attributes.add(attribute);
                }

                if (attributes.isEmpty()) {
                    logger.warn("No attributes defined for entity '{}' from sheet '{}'.", entityName, sheet.getSheetName());
                }
                entity.setAttributes(attributes);
                entities.add(entity);
            }
            workbook.close();
        }
        definedModel.setEntities(entities);
        if (entities.isEmpty()) {
             logger.warn("No entities could be parsed from the spreadsheet {}.", fileName);
             // Optionally throw an exception or return a model indicating no entities found
        }
        logger.info("Successfully parsed model definition from {}. Entities found: {}", fileName, entities.size());
        return definedModel;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        // Using DataFormatter to get cell value as String, regardless of actual cell type
        DataFormatter dataFormatter = new DataFormatter();
        return dataFormatter.formatCellValue(cell);
    }

    private String removeFileExtension(String filename) {
        if (filename == null) return "DefaultModel";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    private String cleanEntityName(String rawName) {
        // Similar to ModelSuggestionServiceImpl's cleaning, but perhaps simpler
        String name = rawName.replaceAll("[_\\-]", " ");
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
            }
        }
        String cleanedName = sb.toString();
        return cleanedName.isEmpty() ? "UnnamedEntity" : cleanedName;
    }
}
