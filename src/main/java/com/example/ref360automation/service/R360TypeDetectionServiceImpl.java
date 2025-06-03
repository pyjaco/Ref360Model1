package com.example.ref360automation.service;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct; // For Spring-managed initialization
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.text.ParseException;


@Service
public class R360TypeDetectionServiceImpl implements R360TypeDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(R360TypeDetectionServiceImpl.class);
    private static final Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    private Map<String, NameFinderME> nameFinders = new HashMap<>();
    private boolean nerModelsAvailable = false;

    // Regex patterns (corrected from ModelSuggestionServiceImpl)
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^-?\\d*\\.?\\d+([eE][-+]?\\d+)?$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(true|false|yes|no|0|1|t|f|y|n)$", Pattern.CASE_INSENSITIVE);
    private static final String[] DATE_FORMATS = { // Renamed from DATE_PATTERNS for clarity
        "yyyy-MM-dd", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd",
        "yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy HH:mm:ss", "M/d/yy", "dd-MMM-yyyy"
    };
    private static final int MAX_ROWS_FOR_REGEX_TYPE_DETECTION = 100;


    @PostConstruct // Ensures models are loaded after bean creation
    public void initializeModels() {
        String[] modelTypes = {"date", "money", "percentage", "location", "person"};
        String modelBasePath = "src/test/resources/opennlp_models/en-ner-";
        int loadedCount = 0;

        for (String type : modelTypes) {
            String modelPath = modelBasePath + type + ".bin";
            // Try loading from classpath first, then fallback to FileInputStream for broader compatibility
            InputStream modelIn = null;
            try {
                modelIn = getClass().getClassLoader().getResourceAsStream("opennlp_models/en-ner-" + type + ".bin");
                if (modelIn == null) { // Fallback for environments where classpath loading might be tricky
                     logger.warn("Could not load NER model for type '{}' from classpath. Trying FileInputStream from '{}'", type, modelPath);
                     modelIn = new FileInputStream(modelPath);
                }

                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                nameFinders.put(type, new NameFinderME(model));
                logger.info("Successfully loaded NER model: {}", type);
                loadedCount++;
            } catch (IOException e) {
                logger.warn("Could not load NER model for type '{}' from path '{}'. NER for this type will be skipped. Reason: {}", type, modelPath, e.getMessage());
            } finally {
                if (modelIn != null) {
                    try { modelIn.close(); } catch (IOException e) { logger.error("Error closing model input stream", e); }
                }
            }
        }
        if (loadedCount > 0) {
            nerModelsAvailable = true;
            logger.info("{} NER models loaded successfully.", loadedCount);
        } else {
            logger.warn("No OpenNLP NER models were loaded. Type detection will rely solely on regex and heuristics.");
        }
    }

    @Override
    public String suggestR360DataType(List<String> columnData) {
        if (columnData == null || columnData.isEmpty()) {
            return "String"; // Default for empty columns
        }

        List<String> nonNullSamples = columnData.stream()
                                               .filter(s -> s != null && !s.trim().isEmpty())
                                               .collect(java.util.stream.Collectors.toList());

        if (nonNullSamples.isEmpty()) {
            return "String"; // All nulls or empty strings
        }

        // 1. NER-based detection (if models are available)
        if (nerModelsAvailable) {
            Map<String, Integer> aggregateEntityCounts = new HashMap<>();
            for (String modelType : nameFinders.keySet()) {
                NameFinderME nameFinder = nameFinders.get(modelType);
                if (nameFinder == null) continue;

                int entityCount = 0;
                for (String text : nonNullSamples) {
                    String[] tokens = tokenizer.tokenize(text);
                    Span[] nameSpans = nameFinder.find(tokens);
                    if (nameSpans.length > 0) {
                        entityCount++;
                    }
                }
                nameFinder.clearAdaptiveData();
                if (entityCount > 0) {
                     aggregateEntityCounts.put(modelType, entityCount);
                }
            }

            // Heuristic: if a specific entity type is found in > 50% of non-empty samples
            double thresholdRatio = 0.5;
            int thresholdCount = (int) Math.ceil(thresholdRatio * nonNullSamples.size());

            if (aggregateEntityCounts.getOrDefault("date", 0) >= thresholdCount) return "Date";
            if (aggregateEntityCounts.getOrDefault("money", 0) >= thresholdCount ||
                aggregateEntityCounts.getOrDefault("percentage", 0) >= thresholdCount) return "Decimal";
            // Location/Person are usually Strings, this can be a confirmation or a fallback later
        }

        // 2. Regex and Heuristic based detection (primary or fallback)
        boolean allMatchInteger = true;
        boolean allMatchDouble = true;
        boolean allMatchBoolean = true;
        boolean allMatchDate = true;

        int rowsToScan = Math.min(nonNullSamples.size(), MAX_ROWS_FOR_REGEX_TYPE_DETECTION);

        for (int i = 0; i < rowsToScan; i++) {
            String value = nonNullSamples.get(i).trim();

            if (allMatchInteger && !INTEGER_PATTERN.matcher(value).matches()) allMatchInteger = false;
            if (allMatchDouble && !DOUBLE_PATTERN.matcher(value).matches()) allMatchDouble = false;
            if (allMatchBoolean && !BOOLEAN_PATTERN.matcher(value).matches()) allMatchBoolean = false;
            if (allMatchDate && !isParsableDate(value)) allMatchDate = false;

            if (!allMatchInteger && !allMatchDouble && !allMatchBoolean && !allMatchDate && !nerModelsAvailable) {
                // If no NER and all regex failed for this sample, it's likely String for the column
                break;
            }
        }

        if (allMatchDate) return "Date"; // Regex date check can be more robust for varied formats
        if (allMatchInteger) return "Integer";
        if (allMatchDouble) return "Decimal";
        if (allMatchBoolean) return "Boolean";

        // If NER suggested location/person strongly earlier, that's a good String.
        // Otherwise, default to String.
        if (nerModelsAvailable) {
             double thresholdRatio = 0.5;
             int thresholdCount = (int) Math.ceil(thresholdRatio * nonNullSamples.size());
             // Re-check NER counts for string-like entities if no other type matched strongly
             Map<String, Integer> tempCounts = new HashMap<>();
             if (nameFinders.containsKey("location")) tempCounts.putAll(detectEntitiesInSampleForType(nonNullSamples, "location"));
             if (nameFinders.containsKey("person")) tempCounts.putAll(detectEntitiesInSampleForType(nonNullSamples, "person"));

             if (tempCounts.getOrDefault("location", 0) >= thresholdCount ||
                 tempCounts.getOrDefault("person", 0) >= thresholdCount) {
                 return "String"; // Confirmed as textual content by NER
             }
        }

        return "String"; // Ultimate fallback
    }

    // Helper for NER detection for a specific type, used in fallback String check
    private Map<String, Integer> detectEntitiesInSampleForType(List<String> sampleData, String entityTypeToFind) {
        Map<String, Integer> entityCounts = new HashMap<>();
        NameFinderME nameFinder = nameFinders.get(entityTypeToFind);
        if (nameFinder == null) return entityCounts;

        for (String text : sampleData) {
            String[] tokens = tokenizer.tokenize(text);
            Span[] nameSpans = nameFinder.find(tokens);
            if (nameSpans.length > 0) {
                 entityCounts.put(entityTypeToFind, entityCounts.getOrDefault(entityTypeToFind, 0) + 1);
            }
        }
        nameFinder.clearAdaptiveData();
        return entityCounts;
    }

    private boolean isParsableDate(String value) {
        if (value == null) return false;
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                sdf.parse(value);
                return true;
            } catch (ParseException e) {
                // try next format
            }
        }
        return false;
    }
}
