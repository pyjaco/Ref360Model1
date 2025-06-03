package com.example.ref360automation.service;

import com.example.ref360automation.r360.model.*; // Import new R360 POJOs
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
import java.util.HashMap; // Added import
import java.util.List;
import java.util.Map; // Added import
import java.util.stream.Collectors;

@Service
public class ModelDefinitionParserServiceImpl implements ModelDefinitionParserService {

    private static final Logger logger = LoggerFactory.getLogger(ModelDefinitionParserServiceImpl.class);

    private static final String ATTRIBUTE_NAME_HEADER = "Attribute Name";
    private static final String DATA_TYPE_HEADER = "Data Type";
    private static final String IS_MANDATORY_HEADER = "Is Mandatory"; // Optional header

    @Override
    public R360ModelImport parseModelDefinition(MultipartFile multipartFile, String defaultRdsName) throws IOException, IllegalArgumentException {
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String originalFileName = multipartFile.getOriginalFilename();
        logger.info("Parsing model definition from spreadsheet: {}", originalFileName);

        R360ModelImport r360Model = new R360ModelImport();

        R360ReferenceDataSet rds = new R360ReferenceDataSet();
        String rdsName = cleanAssetName(defaultRdsName.isEmpty() ? removeFileExtension(originalFileName, "DefaultRDS") : defaultRdsName);
        rds.setName(rdsName);
        rds.setInternalId(generateInternalId(rdsName));
        rds.setAlias(generateAlias(rdsName));
        rds.setDescription("Reference Data Set defined from spreadsheet: " + originalFileName);
        rds.setHierarchical(false); // Default, can be updated if any CL is hierarchical
        rds.setLevels(1);

        List<R360CodeValueField> rdsGlobalFields = new ArrayList<>();

        try (InputStream is = multipartFile.getInputStream()) {
            Workbook workbook;
            if (originalFileName != null && originalFileName.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(is);
            } else if (originalFileName != null && originalFileName.toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(is);
            } else {
                throw new IllegalArgumentException("Invalid file format. Please upload an Excel file (XLS or XLSX).");
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                if (sheetName == null || sheetName.trim().isEmpty()) {
                    logger.warn("Skipping sheet with no name (index {}).", i);
                    continue;
                }

                String clName = cleanAssetName(sheetName);
                logger.info("Processing sheet '{}' as Code List '{}'", sheetName, clName);

                R360CodeList codeList = new R360CodeList();
                codeList.setName(clName);
                codeList.setInternalId(generateInternalId(clName));
                codeList.setAlias(generateAlias(clName));
                codeList.setTermId(rds.getInternalId()); // Link to parent RDS
                codeList.setRdsName(rds.getName());
                codeList.setDescription("Code List defined from sheet: " + sheetName);
                codeList.setHierarchical(false); // Default, TODO: add heuristic for hierarchies from sheet structure

                List<R360CodeValueField> codeValueFields = new ArrayList<>();
                Row headerRowObj = sheet.getRow(0);
                if (headerRowObj == null) {
                    logger.warn("Skipping sheet '{}' as it has no header row.", sheetName);
                    continue;
                }

                // Map headers to column indices
                Map<String, Integer> headerMap = new HashMap<>();
                for (Cell cell : headerRowObj) {
                    headerMap.put(getCellStringValue(cell).trim(), cell.getColumnIndex());
                }

                if (!headerMap.containsKey(ATTRIBUTE_NAME_HEADER) || !headerMap.containsKey(DATA_TYPE_HEADER)) {
                    logger.warn("Sheet '{}' has incorrect headers. Expected '{}' and '{}'. Found: {}. Skipping.",
                            sheetName, ATTRIBUTE_NAME_HEADER, DATA_TYPE_HEADER, headerMap.keySet());
                    continue;
                }

                for (int j = 1; j <= sheet.getLastRowNum(); j++) { // Start from second row (data)
                    Row dataRow = sheet.getRow(j);
                    if (dataRow == null) continue;

                    String attributeNameVal = getCellStringValue(dataRow.getCell(headerMap.get(ATTRIBUTE_NAME_HEADER))).trim();
                    String attributeTypeVal = getCellStringValue(dataRow.getCell(headerMap.get(DATA_TYPE_HEADER))).trim();
                    boolean isMandatoryVal = false;
                    if (headerMap.containsKey(IS_MANDATORY_HEADER)) {
                         String mandatoryStr = getCellStringValue(dataRow.getCell(headerMap.get(IS_MANDATORY_HEADER))).trim();
                         isMandatoryVal = "true".equalsIgnoreCase(mandatoryStr) || "yes".equalsIgnoreCase(mandatoryStr) || "1".equals(mandatoryStr);
                    }


                    if (attributeNameVal.isEmpty()) {
                        logger.trace("Skipping row {} in sheet '{}' due to empty attribute name.", j + 1, sheetName);
                        continue;
                    }
                    if (attributeTypeVal.isEmpty()) {
                        attributeTypeVal = "String"; // Default if type is blank
                        logger.trace("Attribute type for '{}' in sheet '{}' is empty, defaulting to String.", attributeNameVal, sheetName);
                    }

                    R360CodeValueField field = new R360CodeValueField();
                    field.setName(cleanAttributeName(attributeNameVal));
                    field.getLabels().add(new R360Label("en", attributeNameVal));
                    field.setDatatype(attributeTypeVal); // Consider validating/normalizing this type
                    field.setMandatory(isMandatoryVal);
                    field.setOrigin("TERM");

                    // TODO: Add logic for "Reference" datatype if specified in sheet
                    // Example: if (attributeTypeVal.startsWith("Reference(")) parse relatedTermId etc.

                    codeValueFields.add(field);
                }

                if (codeValueFields.isEmpty()) {
                    logger.warn("No attributes defined for Code List '{}' from sheet '{}'.", clName, sheetName);
                }
                codeList.setCodeValueFields(codeValueFields);
                r360Model.getCodeLists().add(codeList);

                // Aggregate fields for RDS
                codeValueFields.forEach(cvf -> {
                    if (rdsGlobalFields.stream().noneMatch(f -> f.getName().equals(cvf.getName()))) {
                        R360CodeValueField rdsFieldCopy = new R360CodeValueField();
                        rdsFieldCopy.setName(cvf.getName());
                        rdsFieldCopy.setDatatype(cvf.getDatatype());
                        rdsFieldCopy.setMandatory(false); // RDS fields are often not mandatory themselves at RDS level
                        rdsFieldCopy.setOrigin(cvf.getOrigin());
                        rdsFieldCopy.setLabels(new ArrayList<>(cvf.getLabels()));
                        // Copy reference details if any, though this is simplified
                        if ("Reference".equals(cvf.getDatatype())) {
                            rdsFieldCopy.setRelatedTermId(cvf.getRelatedTermId());
                            rdsFieldCopy.setDisplayColumns(new ArrayList<>(cvf.getDisplayColumns()));
                        }
                        rdsGlobalFields.add(rdsFieldCopy);
                    }
                });
            }
            workbook.close();
        }

        rds.setCodeValueFields(rdsGlobalFields);
        if (!r360Model.getCodeLists().isEmpty()) {
            rds.setDefaultList(r360Model.getCodeLists().get(0).getInternalId());
        }
        r360Model.getReferenceDataSets().add(rds);

        if (r360Model.getCodeLists().isEmpty()) {
             logger.warn("No Code Lists could be parsed from the spreadsheet {}.", originalFileName);
        }
        logger.info("Successfully parsed model definition from {}. Found {} Code Lists under RDS '{}'.",
                     originalFileName, r360Model.getCodeLists().size(), rds.getName());
        return r360Model;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter dataFormatter = new DataFormatter();
        return dataFormatter.formatCellValue(cell);
    }

    private String removeFileExtension(String filename, String defaultName) {
        if (filename == null || filename.isEmpty()) return defaultName;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    private String cleanAssetName(String rawName) { // Renamed from cleanEntityName for clarity
        if (rawName == null || rawName.trim().isEmpty()) return "UnnamedAsset";
        String name = rawName.replaceAll("(?i)\\.(csv|xlsx|xls)$", "");
        name = name.replaceAll("[_\\-]", " ");
        name = name.replaceAll("excel data", "").trim(); // Added from ModelSuggestionServiceImpl for consistency

        String[] parts = name.split("\\s+"); // Uses \\s+ from ModelSuggestionServiceImpl
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
            }
        }
        String cleanedName = sb.toString();
        // Refined plural 's' removal from ModelSuggestionServiceImpl
        if (cleanedName.endsWith("s") &&
            !cleanedName.endsWith("ss") &&
            cleanedName.length() > 2 &&
            Character.isLowerCase(cleanedName.charAt(cleanedName.length() - 2))) {
            cleanedName = cleanedName.substring(0, cleanedName.length() - 1);
        }
        return cleanedName.isEmpty() ? "UnnamedAsset" : cleanedName;
    }

    private String cleanAttributeName(String rawHeader) {
        if (rawHeader == null || rawHeader.trim().isEmpty()) return "unnamed_attr";
        String cleaned = rawHeader.trim().replaceAll("\\s+", "_");
        cleaned = cleaned.replaceAll("[-@]", "_");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "attr_" + cleaned;
        }
        return cleaned;
    }

    private String generateInternalId(String name) {
        if (name == null || name.isEmpty()) return "defaultIntId";
        return name.toLowerCase().replaceAll("\\s+", "") + "IntId";
    }

    private String generateAlias(String name) {
        if (name == null || name.isEmpty()) return "defaultAls";
        return name.toLowerCase().replaceAll("\\s+", "") + "Als";
    }
}
