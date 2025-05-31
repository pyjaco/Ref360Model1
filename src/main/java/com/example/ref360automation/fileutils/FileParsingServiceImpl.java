package com.example.ref360automation.fileutils;

import com.example.ref360automation.model.RawDataContainer;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook; // For XLSX
import org.apache.poi.hssf.usermodel.HSSFWorkbook; // For XLS
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
public class FileParsingServiceImpl implements FileParsingService {

    private static final Logger logger = LoggerFactory.getLogger(FileParsingServiceImpl.class);

    @Override
    public List<File> listSupportedFiles(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            logger.error("Provided path is not a directory: {}", directoryPath);
            return new ArrayList<>();
        }

        File[] files = directory.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".csv") ||
            name.toLowerCase().endsWith(".xls") ||
            name.toLowerCase().endsWith(".xlsx")
        );

        if (files == null) {
            logger.warn("No files found or error reading directory: {}", directoryPath);
            return new ArrayList<>();
        }
        logger.info("Found {} supported files in directory: {}", files.length, directoryPath);
        return Arrays.asList(files);
    }

    @Override
    public List<RawDataContainer> parseFiles(List<File> files) {
        List<RawDataContainer> allData = new ArrayList<>();
        for (File file : files) {
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".csv")) {
                RawDataContainer csvData = parseCsvFile(file);
                if (csvData != null) {
                    allData.add(csvData);
                }
            } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                allData.addAll(parseExcelFile(file));
            }
        }
        return allData;
    }

    @Override
    public RawDataContainer parseCsvFile(File file) {
        logger.info("Parsing CSV file: {}", file.getAbsolutePath());
        RawDataContainer container = new RawDataContainer(file.getName());
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> dataRows = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headerLine = reader.readNext();
            if (headerLine == null) {
                logger.warn("CSV file is empty or has no header: {}", file.getName());
                return null;
            }
            for (String header : headerLine) {
                headers.add(header.trim());
            }
            container.setHeaders(headers);

            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    if (i < nextLine.length) {
                        row.put(headers.get(i), nextLine[i]);
                    } else {
                        row.put(headers.get(i), ""); // Handle rows with fewer columns than headers
                    }
                }
                dataRows.add(row);
            }
            container.setDataRows(dataRows);
            logger.info("Successfully parsed CSV file: {}. Headers: {}, Rows: {}", file.getName(), headers.size(), dataRows.size());

        } catch (IOException | CsvValidationException e) {
            logger.error("Error parsing CSV file {}: {}", file.getName(), e.getMessage(), e);
            return null;
        }
        return container;
    }

    @Override
    public List<RawDataContainer> parseExcelFile(File file) {
        logger.info("Parsing Excel file: {}", file.getAbsolutePath());
        List<RawDataContainer> sheetDataContainers = new ArrayList<>();
        String fileName = file.getName();

        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook;
            if (fileName.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (fileName.toLowerCase().endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                logger.warn("Unsupported Excel file format: {}", fileName);
                return sheetDataContainers; // Empty list
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                logger.info("Processing sheet: {}", sheet.getSheetName());
                RawDataContainer container = new RawDataContainer(fileName + "_" + sheet.getSheetName());
                List<String> headers = new ArrayList<>();
                List<Map<String, String>> dataRows = new ArrayList<>();

                Row headerRow = sheet.getRow(0); // Assuming header is in the first row
                if (headerRow == null) {
                    logger.warn("Sheet '{}' in file '{}' is empty or has no header row.", sheet.getSheetName(), fileName);
                    continue; // Skip this sheet
                }

                for (Cell cell : headerRow) {
                    headers.add(getCellStringValue(cell).trim());
                }
                container.setHeaders(headers);

                for (int j = 1; j <= sheet.getLastRowNum(); j++) { // Start from second row (data)
                    Row dataRow = sheet.getRow(j);
                    if (dataRow == null) continue; // Skip empty rows

                    Map<String, String> rowMap = new HashMap<>();
                    boolean rowHasData = false;
                    for (int k = 0; k < headers.size(); k++) {
                        Cell cell = dataRow.getCell(k, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String cellValue = getCellStringValue(cell);
                        rowMap.put(headers.get(k), cellValue);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            rowHasData = true;
                        }
                    }
                    if(rowHasData) { // Only add row if it has some data
                       dataRows.add(rowMap);
                    }
                }
                container.setDataRows(dataRows);
                if (!headers.isEmpty() || !dataRows.isEmpty()){
                    sheetDataContainers.add(container);
                    logger.info("Successfully parsed sheet: {}. Headers: {}, Rows: {}", sheet.getSheetName(), headers.size(), dataRows.size());
                } else {
                    logger.info("Sheet {} is empty, skipping.", sheet.getSheetName());
                }
            }
            workbook.close();
        } catch (IOException e) {
            logger.error("Error parsing Excel file {}: {}", fileName, e.getMessage(), e);
        }
        return sheetDataContainers;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString(); // Or format as needed
                } else {
                    // Format as string to avoid ".0" for whole numbers, handle potential scientific notation
                    DataFormatter dataFormatter = new DataFormatter();
                    return dataFormatter.formatCellValue(cell);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Evaluating formula cells, could be complex depending on functions used
                // For simplicity, try to get the cached formula result value
                try {
                    DataFormatter dataFormatter = new DataFormatter();
                    return dataFormatter.formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator());
                } catch (Exception e) {
                     logger.warn("Could not evaluate formula cell: {}", e.getMessage());
                     return cell.getCellFormula(); // return formula itself as a fallback
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
