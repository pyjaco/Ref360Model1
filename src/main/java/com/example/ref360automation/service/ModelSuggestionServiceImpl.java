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

    // Basic regex patterns for type detection
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\d+$");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^-?\d*\.?\d+([eE][-+]?\d+)?$");
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

        // For simplicity, the overall model name could be fixed or derived
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
        // Attempt to clean up source name for entity name
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
            // Could add more metadata here, e.g., "isNullable", "maxLength"
            attributes.add(attribute);
        }
        entity.setAttributes(attributes);
        logger.debug("Entity {} suggested with attributes: {}", entityName, attributes);
        return entity;
    }

    private String cleanSourceName(String rawName) {
        // Remove extensions like .csv, .xlsx, .xls
        String name = rawName.replaceAll("\.(csv|xlsx|xls)$", "");
        // Replace underscores or hyphens with spaces, then capitalize
        name = name.replaceAll("[_\-]", " ");
        // Remove "excel_data" if it's a remnant from our workaround
        name = name.replaceAll("excel data", "");
        // Basic plural to singular (very naive)
        if (name.endsWith("s") && !name.endsWith("ss")) { // Avoid changing "address" to "addres"
            name = name.substring(0, name.length() -1);
        }
        // Capitalize first letter of each word
        String[] parts = name.trim().split("\s+");
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
        // Replace spaces, special characters (except underscore) with underscore, or camelCase
        // For now, just trim and ensure it's a valid Java-like identifier (simple version)
        String cleaned = rawHeader.trim().replaceAll("\s+", "_").replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "attr_" + cleaned; // Ensure it doesn't start with a digit or is empty
        }
        return cleaned;
    }

    private String detectColumnType(String header, List<Map<String, String>> dataRows) {
        if (dataRows == null || dataRows.isEmpty()) {
            return "String"; // Default if no data to inspect
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
                continue; // Skip empty values for type detection, consider them nullable
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

            // If none of the types match, it's likely a String, no need to check further for this column
            if (!allInteger && !allDouble && !allBoolean && !allDate) break;
        }

        if (allInteger) return "Integer";
        if (allDouble) return "Double"; // Or "Numeric", "Decimal"
        if (allBoolean) return "Boolean";
        if (allDate) return "Date"; // Or "Timestamp" if time components are common

        return "String"; // Default type
    }

    private boolean isDate(String value) {
        if (value == null) return false;
        for (String pattern : DATE_PATTERNS) {
            try {
                // Attempt to parse with strictness (SimpleDateFormat is not ideal for validation but okay for suggestion)
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
