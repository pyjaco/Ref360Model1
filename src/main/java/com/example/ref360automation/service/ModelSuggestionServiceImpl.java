package com.example.ref360automation.service;

import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.r360.model.*; // Import all new R360 POJOs
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern; // Added for Pattern class
import java.util.stream.Collectors;

@Service
public class ModelSuggestionServiceImpl implements ModelSuggestionService { // Keep interface for now, or change if method signature changes

    private static final Logger logger = LoggerFactory.getLogger(ModelSuggestionServiceImpl.class);
    private final R360TypeDetectionService typeDetectionService;

    // Pre-compile patterns for efficiency and clarity
    private static final Pattern ID_PATTERN_REGEX = Pattern.compile("(?i).*id$|.*key$|.*code$");
    private static final Pattern PARENT_ID_PATTERN_REGEX_P1 = Pattern.compile("(?i)parent.*id$");
    private static final Pattern PARENT_ID_PATTERN_REGEX_P2 = Pattern.compile("(?i)parent.*key$");
    private static final Pattern PARENT_ID_PATTERN_REGEX_P3 = Pattern.compile("(?i)parent.*code$");
    // private static final Pattern PARENT_ID_PATTERN_REGEX_MGR = Pattern.compile("(?i)mgr.*id$"); // Will use direct string compare for this


    @Autowired
    public ModelSuggestionServiceImpl(R360TypeDetectionService typeDetectionService) {
        this.typeDetectionService = typeDetectionService;
    }

    public R360ModelImport suggestR360Model(List<RawDataContainer> rawDataContainers, String defaultRdsName) {
        if (rawDataContainers == null || rawDataContainers.isEmpty()) {
            logger.warn("No raw data provided for model suggestion.");
            return createEmptyR360ModelImport();
        }

        R360ModelImport r360Model = new R360ModelImport();
        R360ReferenceDataSet rds = new R360ReferenceDataSet();
        rds.setName(cleanSourceNameForAsset(defaultRdsName.isEmpty() ? "DefaultRDS" : defaultRdsName));
        rds.setInternalId(generateInternalId(rds.getName()));
        rds.setAlias(generateAlias(rds.getName()));
        rds.setDescription("Reference Data Set generated from input files.");
        rds.setHierarchical(false);
        rds.setLevels(1);

        List<R360CodeValueField> rdsFields = new ArrayList<>();

        for (RawDataContainer dataContainer : rawDataContainers) {
            if (dataContainer.getHeaders() == null || dataContainer.getHeaders().isEmpty()) {
                logger.warn("Skipping data container '{}' as it has no headers.", dataContainer.getSourceName());
                continue;
            }

            R360CodeList codeList = convertRawDataToCodeList(dataContainer, rds.getInternalId(), rds.getName());
            r360Model.getCodeLists().add(codeList);

            if (codeList.getCodeValueFields() != null) {
                codeList.getCodeValueFields().forEach(clField -> {
                    if (rdsFields.stream().noneMatch(rdsField -> rdsField.getName().equals(clField.getName()))) {
                        R360CodeValueField rdsFieldCopy = new R360CodeValueField();
                        rdsFieldCopy.setName(clField.getName());
                        rdsFieldCopy.setDatatype(clField.getDatatype());
                        rdsFieldCopy.setMandatory(clField.isMandatory());
                        rdsFieldCopy.setOrigin(clField.getOrigin());
                        rdsFieldCopy.setLabels(new ArrayList<>(clField.getLabels()));
                        if ("Reference".equals(clField.getDatatype())) {
                             rdsFieldCopy.setRelatedTermId(clField.getRelatedTermId());
                             rdsFieldCopy.setDisplayColumns(new ArrayList<>(clField.getDisplayColumns()));
                        }
                        rdsFields.add(rdsFieldCopy);
                    }
                });
            }
            if (codeList.isHierarchical() && !rds.isHierarchical()){
                rds.setHierarchical(true);
            }
        }

        rds.setCodeValueFields(rdsFields);
        if (!r360Model.getCodeLists().isEmpty()) {
             rds.setDefaultList(r360Model.getCodeLists().get(0).getInternalId());
        }
        r360Model.getReferenceDataSets().add(rds);

        logger.info("Suggested R360 model with {} RDS, {} CodeLists.",
                     r360Model.getReferenceDataSets().size(), r360Model.getCodeLists().size());
        return r360Model;
    }

    private R360CodeList convertRawDataToCodeList(RawDataContainer dataContainer, String parentRdsInternalId, String parentRdsName) {
        R360CodeList codeList = new R360CodeList();
        String clName = cleanSourceNameForAsset(dataContainer.getSourceName());
        codeList.setName(clName);
        codeList.setInternalId(generateInternalId(clName));
        codeList.setAlias(generateAlias(clName));
        codeList.setTermId(parentRdsInternalId);
        codeList.setRdsName(parentRdsName);
        codeList.setDescription("Code List generated from source: " + dataContainer.getSourceName());

        List<String> headers = dataContainer.getHeaders();
        List<Map<String, String>> dataRows = dataContainer.getDataRows();
        List<R360CodeValueField> codeValueFields = new ArrayList<>();

        Optional<String> idColumnName = headers.stream().filter(h -> h != null && ID_PATTERN_REGEX.matcher(h).matches()).findFirst();

        Optional<String> parentIdColumnName = headers.stream().filter(h -> {
            if (h == null) return false;
            if ("ManagerID".equalsIgnoreCase(h)) { // Direct check for ManagerID
                logger.info("Header '{}' matches 'ManagerID' (case-insensitive).", h);
                return true;
            }
            // Check other parent patterns using regex
            return PARENT_ID_PATTERN_REGEX_P1.matcher(h).matches() ||
                   PARENT_ID_PATTERN_REGEX_P2.matcher(h).matches() ||
                   PARENT_ID_PATTERN_REGEX_P3.matcher(h).matches();
        }).findFirst();

        logger.info("For CodeList {}: Found idColumnName: {}, parentIdColumnName: {}", clName, idColumnName, parentIdColumnName);

        if(idColumnName.isPresent() && parentIdColumnName.isPresent() && !idColumnName.get().equalsIgnoreCase(parentIdColumnName.get())){
            codeList.setHierarchical(true);
            logger.info("CodeList {} identified as potentially hierarchical due to columns: {} and {}", clName, idColumnName.get(), parentIdColumnName.get());
        } else {
            codeList.setHierarchical(false);
            logger.info("CodeList {} NOT marked as hierarchical. idCol: {}, parentIdCol: {}", clName, idColumnName.orElse("null"), parentIdColumnName.orElse("null"));
        }

        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) continue;

            R360CodeValueField field = new R360CodeValueField();
            String cleanedAttributeName = cleanAttributeName(header);
            field.setName(cleanedAttributeName);
            field.getLabels().add(new R360Label("en", header.trim()));
            field.setOrigin("TERM");

            List<String> columnData = dataRows.stream()
                                             .map(row -> row.get(header))
                                             .collect(Collectors.toList());

            field.setDatatype(typeDetectionService.suggestR360DataType(columnData));

            long nonNullCount = columnData.stream().filter(s -> s != null && !s.trim().isEmpty()).count();
            if (!columnData.isEmpty() && (double)nonNullCount / columnData.size() > 0.95) {
                field.setMandatory(true);
            } else {
                field.setMandatory(false);
            }
            codeValueFields.add(field);
        }
        codeList.setCodeValueFields(codeValueFields);
        return codeList;
    }

    private R360ModelImport createEmptyR360ModelImport() {
        R360ModelImport emptyModel = new R360ModelImport();
        return emptyModel;
    }

    private String cleanSourceNameForAsset(String rawName) {
        String name = rawName.replaceAll("(?i)\\.(csv|xlsx|xls)$", "");
        name = name.replaceAll("[_\\-]", " ");
        name = name.replaceAll("excel data", "").trim();

        String[] parts = name.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
            }
        }
        String cleanedName = sb.toString();
        if (cleanedName.endsWith("s") &&
            !cleanedName.endsWith("ss") &&
            cleanedName.length() > 2 &&
            Character.isLowerCase(cleanedName.charAt(cleanedName.length() - 2))) {
            cleanedName = cleanedName.substring(0, cleanedName.length() - 1);
        }
        return cleanedName.isEmpty() ? "UnnamedAsset" : cleanedName;
    }

    private String cleanAttributeName(String rawHeader) {
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
