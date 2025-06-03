package com.example.ref360automation.service;

import com.example.ref360automation.r360.model.R360CodeList;
import com.example.ref360automation.r360.model.R360CodeValueField;
import com.example.ref360automation.r360.model.R360ModelImport;
import com.example.ref360automation.r360.model.R360ReferenceDataSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class ModelDefinitionParserServiceImplTest {

    private ModelDefinitionParserService modelDefinitionParserService;

    @BeforeEach
    void setUp() {
        modelDefinitionParserService = new ModelDefinitionParserServiceImpl();
    }

    // Helper method to create a minimal XLSX byte array from a map of sheet names to CSV-like string data
    private byte[] createMockExcelBytes(Map<String, List<List<String>>> sheetsData) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        for (Map.Entry<String, List<List<String>>> entry : sheetsData.entrySet()) {
            Sheet sheet = workbook.createSheet(entry.getKey());
            List<List<String>> sheetData = entry.getValue();
            for (int i = 0; i < sheetData.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> rowData = sheetData.get(i);
                for (int j = 0; j < rowData.size(); j++) {
                    Cell cell = row.createCell(j);
                    // Trim cell value before setting, to mimic real data entry
                    cell.setCellValue(rowData.get(j) != null ? rowData.get(j).trim() : null);
                }
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        workbook.close();
        return bos.toByteArray();
    }

    // Simpler helper for single sheet from header string and list of string arrays for rows
    private byte[] createSingleSheetExcelBytes(String sheetName, String csvHeader, List<String[]> csvRows) throws IOException {
        Map<String, List<List<String>>> sheetsData = new HashMap<>();
        List<List<String>> sheetContent = new ArrayList<>();
        // Add header row by splitting the csvHeader string
        sheetContent.add(Arrays.stream(csvHeader.split(","))
                               .map(String::trim) // Trim each header
                               .collect(Collectors.toList()));
        // Add data rows
        for (String[] rowArray : csvRows) {
            sheetContent.add(Arrays.stream(rowArray)
                                   .map(s -> s != null ? s.trim() : null) // Trim each cell in data rows
                                   .collect(Collectors.toList()));
        }
        sheetsData.put(sheetName, sheetContent);
        return createMockExcelBytes(sheetsData);
    }


    @Test
    void parseModelDefinition_emptyFile_shouldThrowException() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            modelDefinitionParserService.parseModelDefinition(emptyFile, "TestRDS"));
        assertEquals("Uploaded file is empty.", exception.getMessage());
    }

    @Test
    void parseModelDefinition_invalidFileType_shouldThrowException() throws IOException {
        MockMultipartFile textFile = new MockMultipartFile("file", "test.txt", "text/plain", "This is not an excel file.".getBytes());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            modelDefinitionParserService.parseModelDefinition(textFile, "TestRDS"));
        assertEquals("Invalid file format. Please upload an Excel file (XLS or XLSX).", exception.getMessage());
    }

    @Test
    void parseModelDefinition_validSingleSheetExcel_createsCorrectR360Model() throws IOException {
        String sheetName = "ProductCategories";
        // Ensure headers are exactly "Attribute Name", "Data Type", "Is Mandatory"
        String csvHeader = "Attribute Name,Data Type,Is Mandatory";
        List<String[]> csvRows = Arrays.asList(
            new String[]{"CategoryID", "String", "true"},
            new String[]{"CategoryName", "String", "true"},
            new String[]{"Description", "String", "false"}
        );
        byte[] excelBytes = createSingleSheetExcelBytes(sheetName, csvHeader, csvRows);

        MockMultipartFile mockFile = new MockMultipartFile("file", "TestModelDef.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        R360ModelImport result = modelDefinitionParserService.parseModelDefinition(mockFile, "MyProductRDS");

        assertNotNull(result);
        assertThat(result.getReferenceDataSets()).hasSize(1);
        R360ReferenceDataSet rds = result.getReferenceDataSets().get(0);
        assertEquals("Myproductrd", rds.getName());
        assertEquals("myproductrdIntId", rds.getInternalId());
        assertEquals("myproductrdAls", rds.getAlias());

        assertThat(result.getCodeLists()).hasSize(1);
        R360CodeList cl = result.getCodeLists().get(0);
        assertEquals("Productcategorie", cl.getName());
        assertEquals(rds.getInternalId(), cl.getTermId());
        assertEquals("productcategorieIntId", cl.getInternalId());


        assertThat(cl.getCodeValueFields()).hasSize(3);

        R360CodeValueField catId = cl.getCodeValueFields().stream().filter(f -> f.getName().equals("CategoryID")).findFirst().orElseThrow();
        assertEquals("String", catId.getDatatype());
        assertTrue(catId.isMandatory());
        assertEquals("TERM", catId.getOrigin());
        assertThat(catId.getLabels()).hasSize(1).allMatch(l -> l.getValue().equals("CategoryID") && l.getLanguage().equals("en"));

        R360CodeValueField catDesc = cl.getCodeValueFields().stream().filter(f -> f.getName().equals("Description")).findFirst().orElseThrow();
        assertEquals("String", catDesc.getDatatype());
        assertFalse(catDesc.isMandatory());

        assertThat(rds.getCodeValueFields()).hasSize(3);
        assertTrue(rds.getCodeValueFields().stream().anyMatch(f->f.getName().equals("CategoryID")));
        assertTrue(rds.getCodeValueFields().stream().anyMatch(f->f.getName().equals("CategoryName")));
        assertTrue(rds.getCodeValueFields().stream().anyMatch(f->f.getName().equals("Description")));
    }

    @Test
    void parseModelDefinition_multiSheetExcel_createsCorrectModel() throws IOException {
        Map<String, List<List<String>>> sheetsData = new HashMap<>();
        sheetsData.put("Products", Arrays.asList(
            Arrays.asList("Attribute Name", "Data Type", "Is Mandatory"), // Header
            Arrays.asList("ProductID", "Integer", "true"),
            Arrays.asList("ProductName", "String", "true"),
            Arrays.asList("UnitPrice", "Decimal", "false")
        ));
        sheetsData.put("Suppliers", Arrays.asList(
            Arrays.asList("Attribute Name", "Data Type"), // No "Is Mandatory" column
            Arrays.asList("SupplierID", "Integer"),
            Arrays.asList("SupplierName", "String")
        ));
        byte[] excelBytes = createMockExcelBytes(sheetsData);
        MockMultipartFile mockFile = new MockMultipartFile("file", "MultiSheet.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        R360ModelImport result = modelDefinitionParserService.parseModelDefinition(mockFile, "MainData");

        assertNotNull(result);
        assertThat(result.getReferenceDataSets()).hasSize(1);
        R360ReferenceDataSet rds = result.getReferenceDataSets().get(0);
        assertEquals("Maindata", rds.getName());

        assertThat(result.getCodeLists()).hasSize(2);

        R360CodeList clProduct = result.getCodeLists().stream().filter(cl -> cl.getName().equals("Product")).findFirst().orElseThrow();
        assertEquals("productIntId", clProduct.getInternalId());
        assertEquals(rds.getInternalId(), clProduct.getTermId());
        assertThat(clProduct.getCodeValueFields()).hasSize(3);
        R360CodeValueField unitPriceField = clProduct.getCodeValueFields().stream().filter(f -> f.getName().equals("UnitPrice")).findFirst().orElseThrow();
        assertEquals("Decimal", unitPriceField.getDatatype());
        assertFalse(unitPriceField.isMandatory());

        R360CodeList clSupplier = result.getCodeLists().stream().filter(cl -> cl.getName().equals("Supplier")).findFirst().orElseThrow();
        assertEquals("supplierIntId", clSupplier.getInternalId());
        assertThat(clSupplier.getCodeValueFields()).hasSize(2);
        R360CodeValueField supplierNameField = clSupplier.getCodeValueFields().stream().filter(f -> f.getName().equals("SupplierName")).findFirst().orElseThrow();
        assertEquals("String", supplierNameField.getDatatype());
        assertFalse(supplierNameField.isMandatory()); // Defaults to false as column is missing

        List<String> rdsFieldNames = rds.getCodeValueFields().stream().map(R360CodeValueField::getName).collect(Collectors.toList());
        assertThat(rdsFieldNames).containsExactlyInAnyOrder("ProductID", "ProductName", "UnitPrice", "SupplierID", "SupplierName");
    }
}
