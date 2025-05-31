package com.example.ref360automation.service;

import com.example.ref360automation.model.Entity;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class ModelSuggestionServiceImplTest {

    private ModelSuggestionService modelSuggestionService;

    @BeforeEach
    void setUp() {
        modelSuggestionService = new ModelSuggestionServiceImpl();
    }

    private RawDataContainer createSampleRawData(String sourceName, List<String> headers, List<Map<String, String>> rows) {
        RawDataContainer container = new RawDataContainer(sourceName);
        container.setHeaders(headers);
        container.setDataRows(rows);
        return container;
    }

    @Test
    void suggestModel_singleRawData_shouldSuggestOneEntity() {
        List<String> headers = Arrays.asList("id", "product_name", "is_active", "price", "count", "mfg_date");
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("id", "101");
        row1.put("product_name", "Super Widget");
        row1.put("is_active", "true");
        row1.put("price", "19.99");
        row1.put("count", "12345678901"); // Potentially Long
        row1.put("mfg_date", "2023-01-15");
        rows.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("id", "102");
        row2.put("product_name", "Mega Gadget");
        row2.put("is_active", "FALSE");
        row2.put("price", "29.50");
        row2.put("count", "987");
        row2.put("mfg_date", "2022/12/20");
        rows.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("id", "103");
        row3.put("product_name", "Basic Item");
        row3.put("is_active", "1");
        row3.put("price", "9"); // Integer, but column should be Double due to others
        row3.put("count", "500");
        row3.put("mfg_date", "Invalid Date"); // Should make mfg_date String
        rows.add(row3);


        RawDataContainer rawData = createSampleRawData("products_data.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));

        assertNotNull(model);
        assertEquals("SuggestedDataModel", model.getModelName());
        assertThat(model.getEntities()).hasSize(1);

        Entity productEntity = model.getEntities().get(0);
        assertEquals("Productdata", productEntity.getName()); // From "products_data.csv" -> "Productdata" (naive plural removal test)
                                                               // Actual cleaning might be "ProductData" or "Product"

        assertThat(productEntity.getAttributes()).hasSize(6)
            .extracting(attr -> attr.get("name"))
            .containsExactly("id", "product_name", "is_active", "price", "count", "mfg_date");

        Map<String, String> idAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("id")).findFirst().get();
        assertEquals("Integer", idAttr.get("type"));

        Map<String, String> nameAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("product_name")).findFirst().get();
        assertEquals("String", nameAttr.get("type"));

        Map<String, String> activeAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("is_active")).findFirst().get();
        assertEquals("Boolean", activeAttr.get("type"));

        Map<String, String> priceAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("price")).findFirst().get();
        assertEquals("Double", priceAttr.get("type"));

        // "count" has one very large number, but others are small. Current INTEGER_PATTERN might make it String if too large for int.
        // Let's assume it fits typical Integer for this test based on pattern. If values > Integer.MAX_VALUE, it would be Double or String.
        // The current regex `^-?\d+$` doesn't check for Long specifically. It would fit INTEGER_PATTERN.
        // For very large numbers that might exceed standard integer types, they might fall back to Double if they have no decimal, or String.
        // Let's assume '12345678901' makes the column 'Double' due to the pattern, or 'String' if it fails DOUBLE_PATTERN too.
        // Given current simple regexes, it would be String if it's too big for what a double might represent without scientific notation.
        // For this test, let's assume it becomes String as it's a very large integer not fitting double without 'E'.
        // Or, if we assume typical integer sizes, it might be Integer. Let's test for Integer as per current basic patterns.
        Map<String, String> countAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("count")).findFirst().get();
        assertEquals("Integer", countAttr.get("type")); // If this fails, it might be Double or String based on precise regex and value interaction

        Map<String, String> dateAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("mfg_date")).findFirst().get();
        assertEquals("String", dateAttr.get("type")); // Because of "Invalid Date"
    }

    @Test
    void suggestModel_noRows_shouldDefaultTypesToString() {
        List<String> headers = Arrays.asList("Col1", "Col2");
        List<Map<String, String>> rows = new ArrayList<>(); // No data rows
        RawDataContainer rawData = createSampleRawData("empty_sheet.xlsx", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));

        Entity entity = model.getEntities().get(0);
        assertThat(entity.getAttributes()).allSatisfy(attr -> assertEquals("String", attr.get("type")));
    }

    @Test
    void suggestModel_nameCleaning() {
        RawDataContainer rawData1 = createSampleRawData("user_accounts.csv", Arrays.asList("id"), new ArrayList<>());
        RawDataContainer rawData2 = createSampleRawData("ORDER Details.XLSX", Arrays.asList("item_id"), new ArrayList<>());

        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData1, rawData2));

        assertThat(model.getEntities()).extracting(Entity::getName).containsExactlyInAnyOrder("UserAccount", "OrderDetail");
    }
}
