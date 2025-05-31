package com.example.ref360automation.controller;

import com.example.ref360automation.fileutils.FileParsingService;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel; // Import ReferenceModel
import com.example.ref360automation.service.ModelSuggestionService; // Import ModelSuggestionService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Collections;
import java.util.List;

@RestController
public class FileProcessingController {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingController.class);
    private final FileParsingService fileParsingService;
    private final ModelSuggestionService modelSuggestionService; // Added

    @Autowired
    public FileProcessingController(FileParsingService fileParsingService, ModelSuggestionService modelSuggestionService) { // Added
        this.fileParsingService = fileParsingService;
        this.modelSuggestionService = modelSuggestionService; // Added
    }

    // Example: GET /api/parse-folder?path=/path/to/your/data_files
    @GetMapping("/api/parse-folder")
    public List<RawDataContainer> parseFolder(@RequestParam String path) {
        logger.info("Received request to parse folder: {}", path);
        File testDir = new File(path);
        if (!testDir.exists() || !testDir.isDirectory()) {
             logger.warn("Test directory {} not found. Ensure it exists with sample files.", path);
             return Collections.emptyList(); // Return empty if path is bad
        }

        List<File> files = fileParsingService.listSupportedFiles(path);
        if (files.isEmpty()) {
            logger.info("No supported files found in path: {}", path);
            return Collections.emptyList();
        }
        return fileParsingService.parseFiles(files);
    }

    // New endpoint for suggesting model
    // Example: GET /api/suggest-model?path=/path/to/your/data_files
    @GetMapping("/api/suggest-model")
    public ReferenceModel suggestModelFromPath(@RequestParam String path) {
        logger.info("Received request to suggest model from folder: {}", path);
        File testDir = new File(path);
        if (!testDir.exists() || !testDir.isDirectory()) {
             logger.warn("Test directory {} not found. Ensure it exists with sample files.", path);
             ReferenceModel emptyModel = new ReferenceModel("EmptyModelDueToError");
             emptyModel.setEntities(Collections.emptyList());
             return emptyModel;
        }

        List<File> files = fileParsingService.listSupportedFiles(path);
        if (files.isEmpty()) {
            logger.info("No supported files found in path: {} for model suggestion.", path);
            ReferenceModel emptyModel = new ReferenceModel("EmptyModelNoFiles");
            emptyModel.setEntities(Collections.emptyList());
            return emptyModel;
        }
        List<RawDataContainer> rawData = fileParsingService.parseFiles(files);
        if (rawData.isEmpty()) {
            logger.info("No data could be parsed from files in path: {}", path);
            ReferenceModel emptyModel = new ReferenceModel("EmptyModelNoDataParsed");
            emptyModel.setEntities(Collections.emptyList());
            return emptyModel;
        }
        return modelSuggestionService.suggestModel(rawData);
    }
}
