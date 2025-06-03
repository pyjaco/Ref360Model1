package com.example.ref360automation.controller;

import com.example.ref360automation.fileutils.FileParsingService;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.r360.model.*; // Import R360 POJOs
import com.example.ref360automation.service.ModelDefinitionParserService;
import com.example.ref360automation.service.ModelSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// import org.springframework.web.multipart.MultipartFile; // Not used in this version of test endpoint

import java.io.File;
// import java.io.IOException; // Not used in this version of test endpoint
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
public class FileProcessingController {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingController.class);
    private final FileParsingService fileParsingService;
    private final ModelSuggestionService modelSuggestionService;
    private final ModelDefinitionParserService modelDefinitionParserService; // Injected

    @Autowired
    public FileProcessingController(FileParsingService fileParsingService,
                                    ModelSuggestionService modelSuggestionService,
                                    ModelDefinitionParserService modelDefinitionParserService) { // Added parser service
        this.fileParsingService = fileParsingService;
        this.modelSuggestionService = modelSuggestionService;
        this.modelDefinitionParserService = modelDefinitionParserService;
    }

    @GetMapping("/api/parse-folder")
    public List<RawDataContainer> parseFolder(@RequestParam String path) {
        logger.info("Received request to parse folder: {}", path);
        File testDir = new File(path);
        if (!testDir.exists() || !testDir.isDirectory()) {
             logger.warn("Test directory {} not found. Ensure it exists with sample files.", path);
             return Collections.emptyList();
        }

        List<File> files = fileParsingService.listSupportedFiles(path);
        if (files.isEmpty()) {
            logger.info("No supported files found in path: {}", path);
            return Collections.emptyList();
        }
        return fileParsingService.parseFiles(files);
    }

    @GetMapping("/api/suggest-model")
    public R360ModelImport suggestModelFromPath(@RequestParam String path) {
        logger.info("Received request to suggest model from folder: {}", path);
        File testDir = new File(path);
        R360ModelImport emptyR360Model = new R360ModelImport();
        emptyR360Model.setVersion("3.9"); // Default version

        if (!testDir.exists() || !testDir.isDirectory()) {
             logger.warn("Test directory {} not found. Returning empty model.", path);
             return emptyR360Model;
        }

        List<File> files = fileParsingService.listSupportedFiles(path);
        if (files.isEmpty()) {
            logger.info("No supported files found in path: {} for model suggestion. Returning empty model.", path);
            return emptyR360Model;
        }
        List<RawDataContainer> rawData = fileParsingService.parseFiles(files);
        if (rawData.isEmpty()) {
            logger.info("No data could be parsed from files in path: {}. Returning empty model.", path);
            return emptyR360Model;
        }

        String rdsNameFromPath = testDir.getName();
        return modelSuggestionService.suggestR360Model(rawData, rdsNameFromPath);
    }

    // New endpoint to test serialization of a model structure similar to what ModelDefinitionParserService would create
    @GetMapping("/api/test-defined-model-serialization")
    public R360ModelImport getTestDefinedModel(@RequestParam(defaultValue = "TestRDS") String rdsName) {
        logger.info("Received request for test defined model serialization with RDS name: {}", rdsName);

        R360ModelImport modelImport = new R360ModelImport();

        R360ReferenceDataSet rds = new R360ReferenceDataSet();
        rds.setName(rdsName);
        rds.setInternalId(rdsName.toLowerCase() + "IntId");
        rds.setAlias(rdsName.toLowerCase() + "Als");
        rds.setDescription("Test RDS for " + rdsName);

        // Simulating ProductCategories CodeList
        R360CodeList clProductCategories = new R360CodeList();
        clProductCategories.setName("ProductCategories");
        clProductCategories.setInternalId("productcategoriesIntId");
        clProductCategories.setAlias("productcategoriesAls");
        clProductCategories.setTermId(rds.getInternalId());
        clProductCategories.setRdsName(rds.getName());

        R360CodeValueField catId = new R360CodeValueField();
        catId.setName("CategoryID");
        catId.getLabels().add(new R360Label("en", "Category ID"));
        catId.setDatatype("String");
        catId.setMandatory(true);
        catId.setOrigin("TERM");
        clProductCategories.getCodeValueFields().add(catId);

        R360CodeValueField catName = new R360CodeValueField();
        catName.setName("CategoryName");
        catName.getLabels().add(new R360Label("en", "Category Name"));
        catName.setDatatype("String");
        catName.setMandatory(true);
        catName.setOrigin("TERM");
        clProductCategories.getCodeValueFields().add(catName);

        R360CodeValueField catDesc = new R360CodeValueField();
        catDesc.setName("Description");
        catDesc.getLabels().add(new R360Label("en", "Description"));
        catDesc.setDatatype("String");
        catDesc.setMandatory(false);
        catDesc.setOrigin("TERM");
        clProductCategories.getCodeValueFields().add(catDesc);

        // Simulating Regions CodeList
        R360CodeList clRegions = new R360CodeList();
        clRegions.setName("Regions");
        clRegions.setInternalId("regionsIntId");
        clRegions.setAlias("regionsAls");
        clRegions.setTermId(rds.getInternalId());
        clRegions.setRdsName(rds.getName());

        R360CodeValueField regCode = new R360CodeValueField();
        regCode.setName("RegionCode");
        regCode.getLabels().add(new R360Label("en", "Region Code"));
        regCode.setDatatype("String");
        regCode.setMandatory(false); // Assuming false if not specified
        regCode.setOrigin("TERM");
        clRegions.getCodeValueFields().add(regCode);

        R360CodeValueField regName = new R360CodeValueField();
        regName.setName("RegionName");
        regName.getLabels().add(new R360Label("en", "Region Name"));
        regName.setDatatype("String");
        regName.setMandatory(false);
        regName.setOrigin("TERM");
        clRegions.getCodeValueFields().add(regName);

        R360CodeValueField salesMgrId = new R360CodeValueField();
        salesMgrId.setName("SalesManagerID");
        salesMgrId.getLabels().add(new R360Label("en", "Sales Manager ID"));
        salesMgrId.setDatatype("Integer");
        salesMgrId.setMandatory(false);
        salesMgrId.setOrigin("TERM");
        clRegions.getCodeValueFields().add(salesMgrId);

        modelImport.getCodeLists().add(clProductCategories);
        modelImport.getCodeLists().add(clRegions);

        // Add fields to RDS (collecting unique ones)
        List<R360CodeValueField> rdsFields = new ArrayList<>();
        modelImport.getCodeLists().forEach(cl -> {
            cl.getCodeValueFields().forEach(clField -> {
                if (rdsFields.stream().noneMatch(rdsField -> rdsField.getName().equals(clField.getName()))) {
                    R360CodeValueField rdsFieldCopy = new R360CodeValueField();
                    rdsFieldCopy.setName(clField.getName());
                    rdsFieldCopy.setDatatype(clField.getDatatype());
                    rdsFieldCopy.setMandatory(false); // At RDS level, fields might not be mandatory for all CLs
                    rdsFieldCopy.setOrigin(clField.getOrigin());
                    rdsFieldCopy.setLabels(new ArrayList<>(clField.getLabels()));
                    rdsFields.add(rdsFieldCopy);
                }
            });
        });
        rds.setCodeValueFields(rdsFields);
        if (!modelImport.getCodeLists().isEmpty()){
            rds.setDefaultList(modelImport.getCodeLists().get(0).getInternalId());
        }

        modelImport.getReferenceDataSets().add(rds);
        return modelImport;
    }
}
