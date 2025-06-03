package com.example.ref360automation.prototype;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class NerPrototype {

    private static final Tokenizer tokenizer = SimpleTokenizer.INSTANCE;
    private Map<String, NameFinderME> nameFinders = new HashMap<>();
    private boolean modelsLoadedSuccessfully = false;

    public NerPrototype() {
        String[] modelTypes = {"date", "location", "person", "money", "percentage"};
        String modelBasePath = "src/test/resources/opennlp_models/en-ner-"; // Adjusted for clarity

        int loadedCount = 0;
        for (String type : modelTypes) {
            String modelPath = modelBasePath + type + ".bin";
            try (InputStream modelIn = new FileInputStream(modelPath)) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                nameFinders.put(type, new NameFinderME(model));
                System.out.println("Successfully loaded NER model: " + type + " from " + modelPath);
                loadedCount++;
            } catch (IOException e) {
                System.err.println("WARNING: Could not load NER model for type '" + type + "' from path '" + modelPath + "'. Reason: " + e.getMessage());
                System.err.println("This type will not be detectable by the prototype.");
            }
        }
        if (loadedCount > 0) {
            modelsLoadedSuccessfully = true;
        } else {
             System.err.println("CRITICAL WARNING: No OpenNLP NER models were loaded. The prototype will have limited functionality.");
        }
    }

    public Map<String, Integer> detectEntitiesInSample(List<String> sampleData, String entityTypeToFind) {
        Map<String, Integer> entityCounts = new HashMap<>();
        NameFinderME nameFinder = nameFinders.get(entityTypeToFind);

        if (nameFinder == null) {
            // This specific model wasn't loaded, return empty counts for this type
            return entityCounts;
        }

        for (String text : sampleData) {
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            String[] tokens = tokenizer.tokenize(text);
            Span[] nameSpans = nameFinder.find(tokens);
            for (Span s : nameSpans) {
                // Use the specific entityTypeToFind as key, as we are calling one model at a time
                entityCounts.put(entityTypeToFind, entityCounts.getOrDefault(entityTypeToFind, 0) + 1);
            }
        }
        nameFinder.clearAdaptiveData();
        return entityCounts;
    }

    public String suggestR360TypeFromColumnData(List<String> columnData) {
        if (!modelsLoadedSuccessfully && nameFinders.isEmpty()) { // Check if ANY model loaded
            System.err.println("No NER models available for type suggestion. Defaulting to String.");
            return "String";
        }

        Map<String, Integer> aggregateEntityCounts = new HashMap<>();
        int validSamples = (int) columnData.stream().filter(s -> s != null && !s.trim().isEmpty()).count();
        if (validSamples == 0) return "String"; // Or based on header if no data

        for (String modelType : nameFinders.keySet()) { // Iterate only over loaded models
            Map<String, Integer> countsForType = detectEntitiesInSample(columnData, modelType);
            aggregateEntityCounts.putAll(countsForType);
        }

        // Simple heuristic: if a specific entity type is found in > 50% of non-empty samples
        double thresholdRatio = 0.5;
        int thresholdCount = (int) Math.ceil(thresholdRatio * validSamples);

        if (aggregateEntityCounts.getOrDefault("date", 0) >= thresholdCount) {
            return "Date";
        }
        if (aggregateEntityCounts.getOrDefault("money", 0) >= thresholdCount ||
            aggregateEntityCounts.getOrDefault("percentage", 0) >= thresholdCount) {
            return "Decimal";
        }
        // If location or person are dominant, it's a strong indicator of String,
        // but doesn't differentiate from a generic string without further rules.
        // For now, this helps confirm it's not a more specific type like Date/Decimal.
        if (aggregateEntityCounts.getOrDefault("location", 0) >= thresholdCount ||
            aggregateEntityCounts.getOrDefault("person", 0) >= thresholdCount) {
            return "String (NER Confirmed Textual)";
        }

        // Fallback if no strong NER signal, could integrate regex checks here too
        return "String (Default)";
    }

    public static void main(String[] args) {
        // Create an instance to attempt model loading
        NerPrototype prototype = new NerPrototype();

        // Proceed with tests only if at least one model was loaded
        if (!prototype.modelsLoadedSuccessfully && prototype.nameFinders.isEmpty()) {
            System.out.println("\nNo NER models were loaded. Aborting prototype's main method execution.");
            System.out.println("Please ensure OpenNLP .bin models (e.g., en-ner-date.bin) are present in 'src/test/resources/opennlp_models/'");
            return;
        }
        System.out.println("\nStarting NER Prototype Evaluation (functionality depends on loaded models):");

        List<String> dateColumn = Arrays.asList("01/15/2023", "The meeting is Tomorrow", "Next Tuesday at 3pm", "2024-03-10", null, "5th May 2024");
        List<String> locationColumn = Arrays.asList("London", "Paris", "New York City", "Berlin", "", "The event is in Tokyo");
        List<String> moneyColumn = Arrays.asList("$19.99", "20 EUR", "£50 and 50p", "¥1000", "100.50", "€ 75.25", "Fifty Dollars");
        List<String> personColumn = Arrays.asList("John Doe", "Dr. Jane Smith", "Peter O'Malley", "Unknown", "Smith, John");
        List<String> percentageColumn = Arrays.asList("10%", "0.5%", "Interest rate is 5 percent", "25 % discount");
        List<String> stringColumn = Arrays.asList("Product ABC", "SKU-12345", "This is a free text description.", "REF009");


        System.out.println("\n--- Date Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(dateColumn));

        System.out.println("\n--- Location Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(locationColumn));

        System.out.println("\n--- Money Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(moneyColumn));

        System.out.println("\n--- Person Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(personColumn));

        System.out.println("\n--- Percentage Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(percentageColumn));

        System.out.println("\n--- Generic String Column Analysis ---");
        System.out.println("Suggested R360 Type: " + prototype.suggestR360TypeFromColumnData(stringColumn));
    }
}
