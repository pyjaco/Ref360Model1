package com.example.ref360automation.service;

import com.example.ref360automation.model.Entity;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ModelSuggestionServiceImpl implements ModelSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(ModelSuggestionServiceImpl.class);
    private static final int MAX_ROWS_FOR_TYPE_DETECTION = 100; // Sample size for type detection

    // Basic regex patterns for type detection - CORRECTED
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^-?\\d*\\.?\\d+([eE][-+]?\\d+)?$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(true|false|yes|no|0|1|t|f|y|n)$", Pattern.CASE_INSENSITIVE);
    // Add more date patterns as needed
    private static final String[] DATE_PATTERNS = {
        "yyyy-MM-dd", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd",
        "yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy HH:mm:ss"
    };


    @Override
    public ReferenceModel suggestModel(List<RawDataContainer> rawDataContainers) {
        if (rawDataContainers == null || rawDataContainers.isEmpty()) {
            logger.warn("No raw data provided for model suggestion.");
            return null; // Or return an empty model
        }

        ReferenceModel suggestedModel = new ReferenceModel("SuggestedDataModel");
        List<Entity> entities = new ArrayList<>();

        for (RawDataContainer dataContainer : rawDataContainers) {
            if (dataContainer.getHeaders() == null || dataContainer.getHeaders().isEmpty()) {
                logger.warn("Skipping data container '{}' as it has no headers.", dataContainer.getSourceName());
                continue;
            }
            Entity entity = suggestEntity(dataContainer);
            if (entity != null) {
                entities.add(entity);
            }
        }
        suggestedModel.setEntities(entities);
        logger.info("Suggested model '{}' with {} entities.", suggestedModel.getModelName(), entities.size());
        return suggestedModel;
    }

    private Entity suggestEntity(RawDataContainer dataContainer) {
        String rawSourceName = dataContainer.getSourceName();
        String entityName = cleanSourceName(rawSourceName);
        Entity entity = new Entity(entityName);
        logger.info("Suggesting entity: {} from source: {}", entityName, rawSourceName);

        List<Map<String, String>> attributes = new ArrayList<>();
        List<String> headers = dataContainer.getHeaders();
        List<Map<String, String>> dataRows = dataContainer.getDataRows();

        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                logger.warn("Skipping empty or null header in source: {}", rawSourceName);
                continue;
            }
            String attributeName = cleanAttributeName(header);
            String attributeType = detectColumnType(header, dataRows);

            Map<String, String> attribute = new HashMap<>();
            attribute.put("name", attributeName);
            attribute.put("type", attributeType);
            attributes.add(attribute);
        }
        entity.setAttributes(attributes);
        logger.debug("Entity {} suggested with attributes: {}", entityName, attributes);
        return entity;
    }

    private String cleanSourceName(String rawName) {
        // Remove extensions like .csv, .xlsx, .xls
        // This regex uses \. which is correct for String.replaceAll's regex input
        String name = rawName.replaceAll("(?i)\\.(csv|xlsx|xls)$", "");
        // This regex uses \- which is correct for String.replaceAll's regex input
        name = name.replaceAll("[_\\-]", " ");
        name = name.replaceAll("excel data", "");
        if (name.endsWith("s") && !name.endsWith("ss")) {
            name = name.substring(0, name.length() -1);
        }
        // This regex uses \s which is correct for String.split()'s regex input
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

    private String cleanAttributeName(String rawHeader) {
        // CORRECTED regex for \s+
        String cleaned = rawHeader.trim().replaceAll("\\s+", "_"); // Replace whitespace with underscore
        cleaned = cleaned.replaceAll("[-@]", "_"); // Replace hyphen and @ with underscore
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9_]", ""); // Remove any remaining non-alphanumeric (except underscore)
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "attr_" + cleaned;
        }
        return cleaned;
    }

    private String detectColumnType(String header, List<Map<String, String>> dataRows) {
        if (dataRows == null || dataRows.isEmpty()) {
            return "String";
        }

        boolean allInteger = true;
        boolean allDouble = true;
        boolean allBoolean = true;
        boolean allDate = true;
        int rowsToScan = Math.min(dataRows.size(), MAX_ROWS_FOR_TYPE_DETECTION);

        for (int i = 0; i < rowsToScan; i++) {
            Map<String, String> row = dataRows.get(i);
            String value = row.get(header);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            value = value.trim();

            if (allInteger && !INTEGER_PATTERN.matcher(value).matches()) {
                allInteger = false;
            }
            if (allDouble && !DOUBLE_PATTERN.matcher(value).matches()) {
                allDouble = false;
            }
            if (allBoolean && !BOOLEAN_PATTERN.matcher(value).matches()) {
                allBoolean = false;
            }
            if (allDate && !isDate(value)) {
                allDate = false;
            }

            if (!allInteger && !allDouble && !allBoolean && !allDate) break;
        }

        if (allInteger) return "Integer";
        if (allDouble) return "Double";
        if (allBoolean) return "Boolean";
        if (allDate) return "Date";

        return "String";
    }

    private boolean isDate(String value) {
        if (value == null) return false;
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                sdf.parse(value);
                return true;
            } catch (ParseException e) {
                // Continue to next pattern
            }
        }
        return false;
    }
}
