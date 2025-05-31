package com.example.ref360automation.controller.ui;

import com.example.ref360automation.fileutils.FileParsingService;
import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel;
import com.example.ref360automation.service.ModelDefinitionParserService; // New import
import com.example.ref360automation.service.ModelSuggestionService;
import com.example.ref360automation.service.R360ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // New import
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException; // New import
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/wizard")
@SessionAttributes("suggestedModel")
public class WizardController {

    private static final Logger logger = LoggerFactory.getLogger(WizardController.class);

    private final FileParsingService fileParsingService;
    private final ModelSuggestionService modelSuggestionService;
    private final R360ApiService r360ApiService;
    private final ModelDefinitionParserService modelDefinitionParserService; // New service

    @Autowired
    public WizardController(FileParsingService fileParsingService,
                            ModelSuggestionService modelSuggestionService,
                            R360ApiService r360ApiService,
                            ModelDefinitionParserService modelDefinitionParserService) { // Inject new service
        this.fileParsingService = fileParsingService;
        this.modelSuggestionService = modelSuggestionService;
        this.r360ApiService = r360ApiService;
        this.modelDefinitionParserService = modelDefinitionParserService; // Initialize
    }

    // --- Module 1 Endpoints ---
    @GetMapping("/start")
    public String showFolderInputForm(Model model) {
        return "wizard/folder-input";
    }

    @PostMapping("/process-folder")
    public String processFolder(@RequestParam String folderPath, Model model, RedirectAttributes redirectAttributes) {
        logger.info("Processing folder path from UI: {}", folderPath);
        File testDir = new File(folderPath);
        if (!testDir.exists() || !testDir.isDirectory()) {
             logger.warn("Directory not found: {}", folderPath);
             redirectAttributes.addFlashAttribute("errorMessage", "Directory not found: " + folderPath + ". Please ensure the path is correct and accessible.");
             return "redirect:/wizard/start";
        }

        List<File> files = fileParsingService.listSupportedFiles(folderPath);
        if (files.isEmpty()) {
            redirectAttributes.addFlashAttribute("infoMessage", "No supported (CSV, XLS, XLSX) files found in: " + folderPath);
            return "redirect:/wizard/start";
        }
        List<RawDataContainer> rawData = fileParsingService.parseFiles(files);
        if (rawData.isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage", "No data could be parsed from the files in: " + folderPath + ". Check file formats and content.");
            return "redirect:/wizard/start";
        }
        ReferenceModel suggested_model = modelSuggestionService.suggestModel(rawData);
         if (suggested_model == null || suggested_model.getEntities() == null || suggested_model.getEntities().isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage", "Could not suggest a model from the data in: " + folderPath + ". Ensure files contain recognizable headers and data.");
            return "redirect:/wizard/start";
        }
        model.addAttribute("suggestedModel", suggested_model);
        return "redirect:/wizard/review-model";
    }

    // --- Module 2 Endpoints ---
    @GetMapping("/upload-definition")
    public String showUploadDefinitionForm(Model model) {
        return "wizard/spreadsheet-upload-form"; // New HTML page
    }

    @PostMapping("/process-definition-upload")
    public String processUploadedDefinition(@RequestParam("file") MultipartFile file,
                                            Model model, // To add to session attributes
                                            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a spreadsheet file to upload.");
            return "redirect:/wizard/upload-definition";
        }
        try {
            ReferenceModel definedModel = modelDefinitionParserService.parseModelDefinition(file);
            if (definedModel == null || definedModel.getEntities() == null || definedModel.getEntities().isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "The uploaded spreadsheet did not result in a valid model. Please check the format and content.");
                return "redirect:/wizard/upload-definition";
            }
            // Put the parsed model into the session attribute, similar to Module 1's suggested model
            model.addAttribute("suggestedModel", definedModel);
            return "redirect:/wizard/review-model"; // Redirect to the same review page
        } catch (IOException e) {
            logger.error("Error processing uploaded spreadsheet: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error reading or processing the spreadsheet: " + e.getMessage());
            return "redirect:/wizard/upload-definition";
        } catch (IllegalArgumentException e) {
            logger.error("Invalid spreadsheet format or content: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid spreadsheet: " + e.getMessage());
            return "redirect:/wizard/upload-definition";
        }
    }

    // --- Common Wizard Endpoints ---
    @GetMapping("/review-model")
    public String showModelReviewPage(Model model) {
        if (!model.containsAttribute("suggestedModel")) {
            logger.warn("No suggested/defined model found in session. Redirecting to start or upload.");
            // Could redirect to a generic start page or decide based on context
            return "redirect:/wizard/start"; // Or a new landing page for choosing module 1 or 2
        }
        // "suggestedModel" will be used by model-review.html, regardless of how it got there (suggestion or definition)
        return "wizard/model-review";
    }

    @PostMapping("/submit-selection")
    public String submitModelSelection(@RequestParam(value = "selectedEntities", required = false) List<String> selectedEntityNames,
                                       @ModelAttribute("suggestedModel") ReferenceModel suggestedModel,
                                       RedirectAttributes redirectAttributes) {
        if (selectedEntityNames == null || selectedEntityNames.isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage", "No entities were selected for deployment.");
            return "redirect:/wizard/review-model";
        }
        ReferenceModel finalModelToDeploy = new ReferenceModel(suggestedModel.getModelName() + "_Selected");
        if (suggestedModel.getEntities() != null) {
            List<com.example.ref360automation.model.Entity> entitiesToDeploy = suggestedModel.getEntities().stream()
                .filter(entity -> selectedEntityNames.contains(entity.getName()))
                .collect(Collectors.toList());
            finalModelToDeploy.setEntities(entitiesToDeploy);
        } else {
             redirectAttributes.addFlashAttribute("errorMessage", "Error: Original model was not found in session.");
             return "redirect:/wizard/review-model";
        }
        boolean deploymentSuccess = r360ApiService.deployModel(finalModelToDeploy);
        if (deploymentSuccess) {
            redirectAttributes.addFlashAttribute("successMessage", "Entities selected: " + selectedEntityNames + ". Model deployment initiated successfully (mocked).");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Model deployment failed (mocked). Selected entities: " + selectedEntityNames);
        }
        return "redirect:/wizard/deployment-confirmation";
    }

    @GetMapping("/deployment-confirmation")
    public String showDeploymentConfirmation(Model model) {
        return "wizard/deployment-confirmation";
    }
}
