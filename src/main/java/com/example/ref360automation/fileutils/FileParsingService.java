package com.example.ref360automation.fileutils;

import com.example.ref360automation.model.RawDataContainer; // We'll define this next
import java.io.File;
import java.util.List;

public interface FileParsingService {
    /**
     * Lists all supported files (CSV, XLS, XLSX) in a given directory.
     * @param directoryPath The path to the directory.
     * @return A list of File objects.
     */
    List<File> listSupportedFiles(String directoryPath);

    /**
     * Parses a list of files and extracts data.
     * @param files The list of files to parse.
     * @return A list of RawDataContainer objects, each representing data from one file or one sheet.
     */
    List<RawDataContainer> parseFiles(List<File> files);

    /**
     * Parses a single CSV file.
     * @param file The CSV file to parse.
     * @return A RawDataContainer holding the data.
     */
    RawDataContainer parseCsvFile(File file);

    /**
     * Parses a single Excel file (XLS or XLSX).
     * Excel files can have multiple sheets, so this might return multiple RawDataContainers.
     * @param file The Excel file to parse.
     * @return A list of RawDataContainer objects, one for each sheet with data.
     */
    List<RawDataContainer> parseExcelFile(File file);
}
