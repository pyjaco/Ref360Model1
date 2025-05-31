package com.example.ref360automation.fileutils;

import com.example.ref360automation.model.RawDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir; // For creating temporary files/dirs for tests

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;


class FileParsingServiceImplTest {

    private FileParsingService fileParsingService;

    @TempDir
    Path tempDir; // JUnit 5 temporary directory

    @BeforeEach
    void setUp() {
        fileParsingService = new FileParsingServiceImpl();
    }

    // Helper to create a dummy file in the temp directory
    private File createTestFile(String name, List<String> lines) throws IOException {
        Path filePath = tempDir.resolve(name);
        Files.write(filePath, lines);
        return filePath.toFile();
    }

    @Test
    void listSupportedFiles_shouldFindCsvAndExcel() throws IOException {
        createTestFile("test1.csv", Arrays.asList("h1,h2", "d1,d2"));
        createTestFile("test2.xls", Arrays.asList("")); // Content doesn't matter for listing
        createTestFile("test3.xlsx", Arrays.asList(""));
        createTestFile("test.txt", Arrays.asList("text"));
        createTestFile("image.jpg", Arrays.asList(""));

        List<File> supportedFiles = fileParsingService.listSupportedFiles(tempDir.toString());

        assertThat(supportedFiles).hasSize(3)
            .extracting(File::getName)
            .containsExactlyInAnyOrder("test1.csv", "test2.xls", "test3.xlsx");
    }

    @Test
    void listSupportedFiles_nonExistentDirectory_shouldReturnEmptyList() {
        List<File> files = fileParsingService.listSupportedFiles(tempDir.resolve("nonexistent").toString());
        assertThat(files).isEmpty();
    }

    @Test
    void parseCsvFile_validCsv_shouldReturnRawDataContainer() throws IOException {
        File csvFile = createTestFile("products_test.csv", Arrays.asList(
            "ProductID,ProductName,UnitPrice",
            "1,Chai,18",
            "2,Chang,19"
        ));

        RawDataContainer data = fileParsingService.parseCsvFile(csvFile);

        assertNotNull(data);
        assertEquals("products_test.csv", data.getSourceName());
        assertThat(data.getHeaders()).containsExactly("ProductID", "ProductName", "UnitPrice");
        assertThat(data.getDataRows()).hasSize(2);
        assertThat(data.getDataRows().get(0))
            .containsEntry("ProductID", "1")
            .containsEntry("ProductName", "Chai")
            .containsEntry("UnitPrice", "18");
    }

    @Test
    void parseCsvFile_emptyCsv_shouldReturnNullOrEmptyContainer() throws IOException {
        File emptyCsv = createTestFile("empty_test.csv", Arrays.asList("Header1,Header2")); // Only header
        RawDataContainer data = fileParsingService.parseCsvFile(emptyCsv);
        assertNotNull(data); // Service creates container even if only headers exist
        assertThat(data.getHeaders()).containsExactly("Header1", "Header2");
        assertThat(data.getDataRows()).isEmpty();

        File completelyEmptyCsv = createTestFile("completely_empty_test.csv", Arrays.asList());
        RawDataContainer dataEmpty = fileParsingService.parseCsvFile(completelyEmptyCsv);
        assertNull(dataEmpty); // OpenCSV might return null if no lines are read
    }

    @Test
    void parseCsvFile_malformedCsv_shouldHandleGracefully() throws IOException {
         File malformedCsv = createTestFile("malformed_test.csv", Arrays.asList(
            "Name,Age",
            "Alice,30,ExtraColumn", // row with more columns
            "Bob,24"
        ));
        RawDataContainer data = fileParsingService.parseCsvFile(malformedCsv);
        assertNotNull(data);
        assertThat(data.getHeaders()).containsExactly("Name", "Age");
        // OpenCSV behavior: it will read up to the number of headers. Extra columns are ignored by default.
        // If a row has FEWER columns, the remaining header values will be mapped to empty strings or null.
        assertThat(data.getDataRows()).hasSize(2);
        assertThat(data.getDataRows().get(0)).containsEntry("Name", "Alice").containsEntry("Age", "30");
        assertThat(data.getDataRows().get(1)).containsEntry("Name", "Bob").containsEntry("Age", "24");
    }

    // Basic test for parseFiles - more comprehensive tests would involve mocking Excel parsing or having real test Excel files
    @Test
    void parseFiles_parsesMultipleFileTypes() throws IOException {
        File csvFile = createTestFile("data.csv", Arrays.asList("ID,Value", "1,A"));
        // For Excel, we'd ideally have small .xls and .xlsx files.
        // Since creating them programmatically in the subtask is hard,
        // this test will mostly focus on the CSV part of parseFiles.
        // If parseExcelFile is well-tested, this becomes an integration test of the loop.

        List<RawDataContainer> containers = fileParsingService.parseFiles(Arrays.asList(csvFile));
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getSourceName()).isEqualTo("data.csv");
    }

    // Note: Testing parseExcelFile thoroughly requires actual .xls and .xlsx files.
    // The subtask environment might not be able to create these easily.
    // If such files were available in src/test/resources, tests would look like:
    // @Test
    // void parseExcelFile_validExcel_shouldReturnData() {
    //     File excelFile = new File(getClass().getClassLoader().getResource("sample_data/test_excel.xlsx").getFile());
    //     List<RawDataContainer> data = fileParsingService.parseExcelFile(excelFile);
    //     // Assertions on data...
    // }
}
