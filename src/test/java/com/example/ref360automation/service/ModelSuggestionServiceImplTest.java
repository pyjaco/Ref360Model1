package com.example.ref360automation.service;

import com.example.ref360automation.model.Entity;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

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

    // Existing test - can remain as is or be enhanced if needed
    @Test
    void suggestModel_singleRawData_shouldSuggestOneEntity() {
        List<String> headers = Arrays.asList("id", "product_name", "is_active", "price", "count", "mfg_date", "notes");
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("id", "101");
        row1.put("product_name", "Super Widget");
        row1.put("is_active", "true");
        row1.put("price", "19.99");
        row1.put("count", "12345"); // Standard Integer
        row1.put("mfg_date", "2023-01-15");
        row1.put("notes", "First note");
        rows.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("id", "102");
        row2.put("product_name", "Mega Gadget");
        row2.put("is_active", "FALSE");
        row2.put("price", "29.50");
        row2.put("count", "-50"); // Negative Integer
        row2.put("mfg_date", "2022/12/20");
        row2.put("notes", ""); // Empty string
        rows.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("id", "103"); // Integer
        row3.put("product_name", "Basic Item");
        row3.put("is_active", "1"); // Boolean
        row3.put("price", "9"); // Will make column Double due to others
        row3.put("count", "9876543210"); // Large number, should be Double or String if patterns are strict
                                        // Assuming current INTEGER_PATTERN (\\d+) might make it String, or Double if it also matches that.
                                        // Let's assume it makes the column Double.
        row3.put("mfg_date", "Invalid Date"); // Should make mfg_date String
        row3.put("notes", "  Another note  "); // String with spaces
        rows.add(row3);

        RawDataContainer rawData = createSampleRawData("products_data.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));

        assertNotNull(model);
        Entity productEntity = model.getEntities().get(0);

        Map<String, String> idAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("id")).findFirst().orElse(null);
        assertNotNull(idAttr);
        assertEquals("Integer", idAttr.get("type"));

        Map<String, String> nameAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("product_name")).findFirst().orElse(null);
        assertNotNull(nameAttr);
        assertEquals("String", nameAttr.get("type"));

        Map<String, String> activeAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("is_active")).findFirst().orElse(null);
        assertNotNull(activeAttr);
        assertEquals("Boolean", activeAttr.get("type"));

        Map<String, String> priceAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("price")).findFirst().orElse(null);
        assertNotNull(priceAttr);
        assertEquals("Double", priceAttr.get("type"));

        Map<String, String> countAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("count")).findFirst().orElse(null);
        assertNotNull(countAttr);
        // If "9876543210" is too large for INTEGER_PATTERN (it is for standard int), it might become Double or String.
        // Given `^-?\\d+$`, it will match. So, "Integer".
        assertEquals("Integer", countAttr.get("type"));

        Map<String, String> dateAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("mfg_date")).findFirst().orElse(null);
        assertNotNull(dateAttr);
        assertEquals("String", dateAttr.get("type")); // Because of "Invalid Date"

        Map<String, String> notesAttr = productEntity.getAttributes().stream().filter(a -> a.get("name").equals("notes")).findFirst().orElse(null);
        assertNotNull(notesAttr);
        assertEquals("String", notesAttr.get("type"));
    }

    @Test
    void suggestModel_noRows_shouldDefaultTypesToString() {
        List<String> headers = Arrays.asList("Col1", "Col2");
        RawDataContainer rawData = createSampleRawData("empty_sheet.xlsx", headers, Collections.emptyList());
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));

        Entity entity = model.getEntities().get(0);
        assertThat(entity.getAttributes()).allSatisfy(attr -> assertEquals("String", attr.get("type")));
    }

    @ParameterizedTest
    @CsvSource({
        "user_accounts.csv, UserAccount",
        "ORDER Details.XLSX, OrderDetail",
        "some-data-file.CSV, SomeDataFile",
        "TABLE.xlsx, Table"
    })
    void cleanSourceName_variousInputs_shouldCleanCorrectly(String inputName, String expectedName) {
        // This test is indirect, testing via suggestModel as cleanSourceName is private
        RawDataContainer rawData = createSampleRawData(inputName, Arrays.asList("id"), Collections.emptyList());
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertThat(model.getEntities()).hasSize(1);
        assertEquals(expectedName, model.getEntities().get(0).getName());
    }

    @ParameterizedTest
    @CsvSource({
        "Attribute Name, Attribute_Name",
        "user id, user_id",
        "product-code, product_code",
        "  leading trailing spaces  , leading_trailing_spaces",
        "column@1, column_1",
        "1stColumn, attr_1stColumn"
    })
    void cleanAttributeName_variousInputs_shouldCleanCorrectly(String inputName, String expectedName) {
        // This test is indirect, testing via suggestModel as cleanAttributeName is private
        RawDataContainer rawData = createSampleRawData("test.csv", Arrays.asList(inputName), Collections.emptyList());
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertThat(model.getEntities().get(0).getAttributes()).hasSize(1);
        assertEquals(expectedName, model.getEntities().get(0).getAttributes().get(0).get("name"));
    }

    // --- New tests specifically for type detection with corrected regex ---
    @ParameterizedTest
    @ValueSource(strings = {"123", "-45", "0", "9876543210"})
    void detectColumnType_integerStrings_shouldBeInteger(String intValue) {
        List<String> headers = Arrays.asList("intColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("intColumn", intValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertEquals("Integer", model.getEntities().get(0).getAttributes().get(0).get("type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.23", "-0.5", "0.0", "123.456", "1e5", "2.5E-2", "-100.0", ".5"})
    void detectColumnType_doubleStrings_shouldBeDouble(String doubleValue) {
        List<String> headers = Arrays.asList("doubleColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("doubleColumn", doubleValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertEquals("Double", model.getEntities().get(0).getAttributes().get(0).get("type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12.3.4", "1e5e6", "--5", "truefalse", "123a"})
    void detectColumnType_nonNumericNonBooleanNonDateStrings_shouldBeString(String stringValue) {
        List<String> headers = Arrays.asList("stringColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("stringColumn", stringValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertEquals("String", model.getEntities().get(0).getAttributes().get(0).get("type"));
    }

    @Test
    void detectColumnType_mixedIntegerAndDouble_shouldBeDouble() {
        List<String> headers = Arrays.asList("mixedNumericColumn");
        List<Map<String, String>> rows = Arrays.asList(
            Collections.singletonMap("mixedNumericColumn", "123"),
            Collections.singletonMap("mixedNumericColumn", "45.67")
        );
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertEquals("Double", model.getEntities().get(0).getAttributes().get(0).get("type"));
    }

    @Test
    void detectColumnType_allIntegersButOneString_shouldBeString() {
        List<String> headers = Arrays.asList("mixedTypeColumn");
        List<Map<String, String>> rows = Arrays.asList(
            Collections.singletonMap("mixedTypeColumn", "123"),
            Collections.singletonMap("mixedTypeColumn", "not a number"),
            Collections.singletonMap("mixedTypeColumn", "456")
        );
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        ReferenceModel model = modelSuggestionService.suggestModel(Arrays.asList(rawData));
        assertEquals("String", model.getEntities().get(0).getAttributes().get(0).get("type"));
    }
}
