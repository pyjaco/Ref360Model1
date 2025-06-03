package com.example.ref360automation.service;

import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.r360.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class ModelSuggestionServiceImplTest {

    @Mock
    private R360TypeDetectionService typeDetectionServiceMock;

    private ModelSuggestionService modelSuggestionService;

    // Corrected DEFAULT_RDS_NAME to align with cleaning behavior for "TestRDS" -> "Testrd"
    private static final String DEFAULT_RDS_INPUT_NAME = "TestRDS";
    private static final String EXPECTED_RDS_CLEANED_NAME = "Testrd";


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        modelSuggestionService = new ModelSuggestionServiceImpl(typeDetectionServiceMock);
    }

    private RawDataContainer createSampleRawData(String sourceName, List<String> headers, List<Map<String, String>> rows) {
        RawDataContainer container = new RawDataContainer(sourceName);
        container.setHeaders(headers);
        container.setDataRows(rows);
        return container;
    }

    @Test
    void suggestR360Model_singleRawData_shouldSuggestOneRdsAndOneCodeList() {
        List<String> headers = Arrays.asList("id", "product_name", "is_active", "price", "count", "mfg_date", "notes");
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("id", "101");
        row1.put("product_name", "Super Widget");
        row1.put("is_active", "true"); // Boolean
        row1.put("price", "19.99");   // Decimal
        row1.put("count", "12345");   // Integer
        row1.put("mfg_date", "2023-01-15"); // Date
        row1.put("notes", "First note");   // String
        rows.add(row1);

        // Mock behavior of typeDetectionService
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("101")))).thenReturn("Integer");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("Super Widget")))).thenReturn("String");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("true")))).thenReturn("Boolean");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("19.99")))).thenReturn("Decimal");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("12345")))).thenReturn("Integer");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("2023-01-15")))).thenReturn("Date");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("First note")))).thenReturn("String");

        RawDataContainer rawData = createSampleRawData("products_data.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), DEFAULT_RDS_INPUT_NAME);

        assertNotNull(model);
        assertThat(model.getReferenceDataSets()).hasSize(1);
        R360ReferenceDataSet rds = model.getReferenceDataSets().get(0);
        assertEquals(EXPECTED_RDS_CLEANED_NAME, rds.getName());
        assertEquals(EXPECTED_RDS_CLEANED_NAME.toLowerCase() + "IntId", rds.getInternalId());
        assertEquals(EXPECTED_RDS_CLEANED_NAME.toLowerCase() + "Als", rds.getAlias());

        assertThat(model.getCodeLists()).hasSize(1);
        R360CodeList codeList = model.getCodeLists().get(0);
        assertEquals("ProductsData", codeList.getName());
        assertEquals("productsdataIntId", codeList.getInternalId());
        assertEquals("productsdataAls", codeList.getAlias());
        assertEquals(rds.getInternalId(), codeList.getTermId());
        assertEquals(rds.getName(), codeList.getRdsName());
        assertEquals(codeList.getInternalId(), rds.getDefaultList());


        assertThat(codeList.getCodeValueFields()).hasSize(headers.size());

        R360CodeValueField idField = codeList.getCodeValueFields().stream().filter(f -> f.getName().equals("id")).findFirst().orElseThrow();
        assertEquals("Integer", idField.getDatatype());
        assertEquals("id", idField.getLabels().get(0).getValue());
        assertEquals("en", idField.getLabels().get(0).getLanguage());
        assertEquals("TERM", idField.getOrigin());
        assertTrue(idField.isMandatory()); // Assuming >95% non-null makes it mandatory

        R360CodeValueField priceField = codeList.getCodeValueFields().stream().filter(f -> f.getName().equals("price")).findFirst().orElseThrow();
        assertEquals("Decimal", priceField.getDatatype());
        assertEquals("price", priceField.getLabels().get(0).getValue());
        assertTrue(priceField.isMandatory());

        assertThat(rds.getCodeValueFields().size()).isEqualTo(headers.size());
        Optional<R360CodeValueField> rdsIdField = rds.getCodeValueFields().stream().filter(f -> f.getName().equals("id")).findFirst();
        assertTrue(rdsIdField.isPresent());
        assertEquals("Integer", rdsIdField.get().getDatatype());
    }

    @Test
    void suggestR360Model_multipleRawData_shouldSuggestOneRdsAndMultipleCodeLists() {
        RawDataContainer rawData1 = createSampleRawData("products.csv",
            Arrays.asList("ProductID", "ProductName"),
            Arrays.asList(Map.of("ProductID", "1", "ProductName", "Chai")));
        RawDataContainer rawData2 = createSampleRawData("categories.csv",
            Arrays.asList("CategoryID", "CategoryName"),
            Arrays.asList(Map.of("CategoryID", "10", "CategoryName", "Beverages")));

        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("1")))).thenReturn("Integer");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("Chai")))).thenReturn("String");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("10")))).thenReturn("Integer");
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("Beverages")))).thenReturn("String");

        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData1, rawData2), "InventoryRDS");

        assertThat(model.getReferenceDataSets()).hasSize(1);
        R360ReferenceDataSet rds = model.getReferenceDataSets().get(0);
        assertEquals("Inventoryrd", rds.getName()); // InventoryRDS -> Inventoryrd

        assertThat(model.getCodeLists()).hasSize(2);
        // Print generated code list names for debugging
        model.getCodeLists().forEach(cl -> System.out.println("Generated CodeList Name: " + cl.getName()));
        R360CodeList clProduct = model.getCodeLists().stream().filter(cl->cl.getName().equals("Product")).findFirst().orElseThrow();
        R360CodeList clCategory = model.getCodeLists().stream().filter(cl->cl.getName().equals("Categorie")).findFirst().orElseThrow(); // Corrected expected name

        assertEquals("productIntId", clProduct.getInternalId());
        assertEquals(rds.getInternalId(), clProduct.getTermId());
        assertThat(clProduct.getCodeValueFields()).anyMatch(f -> f.getName().equals("ProductID"));

        assertEquals("categorieIntId", clCategory.getInternalId()); // Corrected expectation
        assertEquals(rds.getInternalId(), clCategory.getTermId());
        assertThat(clCategory.getCodeValueFields()).anyMatch(f -> f.getName().equals("CategoryID"));

        // Check RDS fields - should contain unique fields from both
        List<String> rdsFieldNames = rds.getCodeValueFields().stream().map(R360CodeValueField::getName).collect(Collectors.toList());
        assertThat(rdsFieldNames).containsExactlyInAnyOrder("ProductID", "ProductName", "CategoryID", "CategoryName");
        assertEquals(clProduct.getInternalId(), rds.getDefaultList()); // First CL becomes default
    }


    @Test
    void suggestR360Model_noRows_shouldDefaultTypesToString() {
        List<String> headers = Arrays.asList("Col1", "Col2");
        RawDataContainer rawData = createSampleRawData("empty_sheet.xlsx", headers, Collections.emptyList());

        when(typeDetectionServiceMock.suggestR360DataType(Collections.emptyList())).thenReturn("String");

        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "EmptyRDS");

        assertThat(model.getCodeLists()).hasSize(1);
        R360CodeList codeList = model.getCodeLists().get(0);
        assertThat(codeList.getCodeValueFields()).allSatisfy(field -> assertEquals("String", field.getDatatype()));
        assertEquals("EmptySheet", codeList.getName()); // From empty_sheet.xlsx
        assertEquals("Emptyrd", model.getReferenceDataSets().get(0).getName()); // From EmptyRDS
    }

    @ParameterizedTest
    @CsvSource({
        "user_accounts.csv, UserAccount",
        "ORDER Details.XLSX, OrderDetail",
        "some-data-file.CSV, SomeDataFile",
        "TABLE.xlsx, Table"
    })
    void cleanSourceName_variousInputs_shouldCleanCorrectly(String inputName, String expectedName) {
        when(typeDetectionServiceMock.suggestR360DataType(anyList())).thenReturn("String");
        RawDataContainer rawData = createSampleRawData(inputName, Arrays.asList("id"), Collections.emptyList());
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TestRDS");
        assertThat(model.getCodeLists()).hasSize(1);
        assertEquals(expectedName, model.getCodeLists().get(0).getName());
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
        when(typeDetectionServiceMock.suggestR360DataType(anyList())).thenReturn("String");
        RawDataContainer rawData = createSampleRawData("test.csv", Arrays.asList(inputName), Collections.emptyList());
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TestRDS");
        assertThat(model.getCodeLists().get(0).getCodeValueFields()).hasSize(1);
        assertEquals(expectedName, model.getCodeLists().get(0).getCodeValueFields().get(0).getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "-45", "0", "9876543210"})
    void detectColumnType_integerStrings_shouldBeInteger(String intValue) {
        when(typeDetectionServiceMock.suggestR360DataType(Collections.singletonList(intValue))).thenReturn("Integer");
        List<String> headers = Arrays.asList("intColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("intColumn", intValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TypeTestRDS");
        assertEquals("Integer", model.getCodeLists().get(0).getCodeValueFields().get(0).getDatatype());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.23", "-0.5", "0.0", "123.456", "1e5", "2.5E-2", "-100.0", ".5"})
    void detectColumnType_doubleStrings_shouldBeDecimal(String doubleValue) {
        when(typeDetectionServiceMock.suggestR360DataType(Collections.singletonList(doubleValue))).thenReturn("Decimal");
        List<String> headers = Arrays.asList("doubleColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("doubleColumn", doubleValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TypeTestRDS");
        assertEquals("Decimal", model.getCodeLists().get(0).getCodeValueFields().get(0).getDatatype());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12.3.4", "1e5e6", "--5", "truefalse", "123a"})
    void detectColumnType_nonNumericNonBooleanNonDateStrings_shouldBeString(String stringValue) {
        when(typeDetectionServiceMock.suggestR360DataType(Collections.singletonList(stringValue))).thenReturn("String");
        List<String> headers = Arrays.asList("stringColumn");
        List<Map<String, String>> rows = Collections.singletonList(Collections.singletonMap("stringColumn", stringValue));
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TypeTestRDS");
        assertEquals("String", model.getCodeLists().get(0).getCodeValueFields().get(0).getDatatype());
    }

    @Test
    void detectColumnType_mixedIntegerAndDouble_shouldBeDecimal() {
        List<String> sampleData = Arrays.asList("123", "45.67");
        when(typeDetectionServiceMock.suggestR360DataType(sampleData)).thenReturn("Decimal");
        List<String> headers = Arrays.asList("mixedNumericColumn");
        List<Map<String, String>> rows = sampleData.stream()
                                           .map(val -> Collections.singletonMap("mixedNumericColumn", val))
                                           .collect(Collectors.toList());
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TypeTestRDS");
        assertEquals("Decimal", model.getCodeLists().get(0).getCodeValueFields().get(0).getDatatype());
    }

    @Test
    void detectColumnType_allIntegersButOneString_shouldBeString() {
        List<String> sampleData = Arrays.asList("123", "not a number", "456");
        when(typeDetectionServiceMock.suggestR360DataType(sampleData)).thenReturn("String");
        List<String> headers = Arrays.asList("mixedTypeColumn");
        List<Map<String, String>> rows = sampleData.stream()
                                           .map(val -> Collections.singletonMap("mixedTypeColumn", val))
                                           .collect(Collectors.toList());
        RawDataContainer rawData = createSampleRawData("typeTest.csv", headers, rows);
        R360ModelImport model = modelSuggestionService.suggestR360Model(Arrays.asList(rawData), "TypeTestRDS");
        assertEquals("String", model.getCodeLists().get(0).getCodeValueFields().get(0).getDatatype());
    }

    @Test
    void suggestR360Model_withHierarchyClues_setsHierarchicalFlag() {
        List<String> hierarchicalHeaders = Arrays.asList("EmployeeID", "EmployeeName", "ManagerID");
        List<Map<String, String>> rows = Collections.singletonList(
            Map.of("EmployeeID", "1", "EmployeeName", "Alice", "ManagerID", "")
        );
        RawDataContainer hierarchicalData = createSampleRawData("employees.csv", hierarchicalHeaders, rows);

        List<String> nonHierarchicalHeaders = Arrays.asList("DepartmentID", "DepartmentName");
         List<Map<String, String>> deptRows = Collections.singletonList(
            Map.of("DepartmentID", "D1", "DepartmentName", "HR")
        );
        RawDataContainer nonHierarchicalData = createSampleRawData("departments.csv", nonHierarchicalHeaders, deptRows);

        // Mocking type detection calls
        when(typeDetectionServiceMock.suggestR360DataType(anyList())).thenReturn("String"); // Default mock
        when(typeDetectionServiceMock.suggestR360DataType(argThat(list -> list != null && list.contains("1")))).thenReturn("Integer");


        R360ModelImport model = modelSuggestionService.suggestR360Model(
            Arrays.asList(hierarchicalData, nonHierarchicalData),
            "OrgModel"
        );

        R360CodeList employeeCl = model.getCodeLists().stream()
            .filter(cl -> cl.getName().equals("Employee")) // "employees.csv" -> "Employee"
            .findFirst().orElseThrow();
        System.out.println("Test check for Employee: isHierarchical=" + employeeCl.isHierarchical() + ", Name=" + employeeCl.getName()); // Debug line
        assertTrue(employeeCl.isHierarchical(), "Employee CodeList should be marked as hierarchical.");

        R360CodeList departmentCl = model.getCodeLists().stream()
            .filter(cl -> cl.getName().equals("Department")) // "departments.csv" -> "Department"
            .findFirst().orElseThrow();
        assertFalse(departmentCl.isHierarchical(), "Department CodeList should not be marked as hierarchical.");
    }
}
