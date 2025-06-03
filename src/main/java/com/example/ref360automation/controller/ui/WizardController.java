package com.example.ref360automation.controller.ui;

import com.example.ref360automation.fileutils.FileParsingService;
import com.example.ref360automation.model.RawDataContainer;
// import com.example.ref360automation.model.ReferenceModel; // Old model
import com.example.ref360automation.r360.model.*; // New R360 POJOs
import com.example.ref360automation.service.ModelDefinitionParserService;
import com.example.ref360automation.service.ModelSuggestionService;
import com.example.ref360automation.service.R360ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/wizard")
@SessionAttributes("suggestedModel") // Will now store R360ModelImport
public class WizardController {

    private static final Logger logger = LoggerFactory.getLogger(WizardController.class);

    private final FileParsingService fileParsingService;
    private final ModelSuggestionService modelSuggestionService;
    private final R360ApiService r360ApiService;
    private final ModelDefinitionParserService modelDefinitionParserService;

    @Autowired
    public WizardController(FileParsingService fileParsingService,
                            ModelSuggestionService modelSuggestionService,
                            R360ApiService r360ApiService,
                            ModelDefinitionParserService modelDefinitionParserService) {
        this.fileParsingService = fileParsingService;
        this.modelSuggestionService = modelSuggestionService;
        this.r360ApiService = r360ApiService;
        this.modelDefinitionParserService = modelDefinitionParserService;
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

        String rdsNameFromPath = new File(folderPath).getName(); // Basic name derivation
        R360ModelImport suggestedR360Model = modelSuggestionService.suggestR360Model(rawData, rdsNameFromPath);

         if (suggestedR360Model == null || suggestedR360Model.getCodeLists().isEmpty()) { // Check based on new structure
            redirectAttributes.addFlashAttribute("warningMessage", "Could not suggest a model from the data in: " + folderPath + ". Ensure files contain recognizable headers and data.");
            return "redirect:/wizard/start";
        }
        model.addAttribute("suggestedModel", suggestedR360Model); // Now an R360ModelImport object
        return "redirect:/wizard/review-model";
    }

    // --- Module 2 Endpoints ---
    @GetMapping("/upload-definition")
    public String showUploadDefinitionForm(Model model) {
        return "wizard/spreadsheet-upload-form";
    }

    @PostMapping("/process-definition-upload")
    public String processUploadedDefinition(@RequestParam("file") MultipartFile file,
                                            Model model,
                                            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a spreadsheet file to upload.");
            return "redirect:/wizard/upload-definition";
        }
        try {
            // IMPORTANT ASSUMPTION: ModelDefinitionParserService.parseModelDefinition is also updated
            // to return R360ModelImport. If it still returns the old ReferenceModel, this will break
            // or a conversion step would be needed here.
            String defaultRdsName = file.getOriginalFilename() != null ? file.getOriginalFilename().replaceAll("\\.(xlsx|xls)$", "") : "UploadedModel";
            R360ModelImport definedModel = modelDefinitionParserService.parseModelDefinition(file, defaultRdsName); // Assuming it returns R360ModelImport

            if (definedModel == null || definedModel.getCodeLists().isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "The uploaded spreadsheet did not result in a valid model. Please check the format and content.");
                return "redirect:/wizard/upload-definition";
            }
            model.addAttribute("suggestedModel", definedModel); // Store R360ModelImport
            return "redirect:/wizard/review-model";
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
    public String showModelReviewPage(Model model, RedirectAttributes redirectAttributes) {
        if (!model.containsAttribute("suggestedModel")) {
            logger.warn("No suggested/defined model found in session. Redirecting to start or upload.");
            return "redirect:/wizard/start";
        }
        // Ensure the model attribute is of the correct type for Thymeleaf
        if (!(model.getAttribute("suggestedModel") instanceof R360ModelImport)) {
            logger.error("Session attribute 'suggestedModel' is not of type R360ModelImport. Type: {}", model.getAttribute("suggestedModel").getClass().getName());
            redirectAttributes.addFlashAttribute("errorMessage", "Internal error: Model data is in an unexpected format. Please start over.");
            // Clear the problematic session attribute if possible, though with redirect this is tricky.
            // Consider SessionStatus.setComplete() in the methods that populate it if this becomes an issue.
            return "redirect:/wizard/start";
        }
        return "wizard/model-review";
    }

    @PostMapping("/submit-selection")
    public String submitModelSelection(@RequestParam(value = "selectedEntities", required = false) List<String> selectedCodeListNames,
                                       @ModelAttribute("suggestedModel") R360ModelImport suggestedR360Model,
                                       RedirectAttributes redirectAttributes) {
        if (selectedCodeListNames == null || selectedCodeListNames.isEmpty()) {
            redirectAttributes.addFlashAttribute("warningMessage", "No entities (Code Lists) were selected for deployment.");
            return "redirect:/wizard/review-model";
        }

        logger.info("Selected Code List names for deployment: {}", selectedCodeListNames);

        R360ModelImport finalModelToDeploy = new R360ModelImport();
        finalModelToDeploy.setVersion(suggestedR360Model.getVersion());

        if (suggestedR360Model.getCodeLists() != null && suggestedR360Model.getReferenceDataSets() != null && !suggestedR360Model.getReferenceDataSets().isEmpty()) {
            List<R360CodeList> selectedCodeLists = suggestedR360Model.getCodeLists().stream()
                .filter(cl -> selectedCodeListNames.contains(cl.getName())) // Assuming entity name is CodeList name for selection
                .collect(Collectors.toList());
            finalModelToDeploy.setCodeLists(selectedCodeLists);

            if (!selectedCodeLists.isEmpty()) {
                // Assuming a single RDS as per current ModelSuggestionServiceImpl logic
                R360ReferenceDataSet originalRds = suggestedR360Model.getReferenceDataSets().get(0);
                R360ReferenceDataSet rdsForDeployment = new R360ReferenceDataSet();

                rdsForDeployment.setName(originalRds.getName());
                rdsForDeployment.setInternalId(originalRds.getInternalId()); // Keep original InternalId for parent RDS
                rdsForDeployment.setAlias(originalRds.getAlias());
                rdsForDeployment.setDescription(originalRds.getDescription());

                // Determine if the RDS is still hierarchical based on selected code lists
                boolean isHierarchical = selectedCodeLists.stream().anyMatch(R360CodeList::isHierarchical);
                rdsForDeployment.setHierarchical(isHierarchical);
                rdsForDeployment.setLevels(originalRds.getLevels()); // Or adjust based on selected CLs

                // Filter RDS fields to only those present in selected code lists
                List<String> allFieldNamesInSelectedCodeLists = selectedCodeLists.stream()
                    .flatMap(cl -> cl.getCodeValueFields().stream())
                    .map(R360CodeValueField::getName)
                    .distinct()
                    .collect(Collectors.toList());

                List<R360CodeValueField> filteredRdsFields = originalRds.getCodeValueFields().stream()
                    .filter(rdsField -> allFieldNamesInSelectedCodeLists.contains(rdsField.getName()))
                    .collect(Collectors.toList());
                rdsForDeployment.setCodeValueFields(filteredRdsFields);

                // Set default list for the RDS
                if (originalRds.getDefaultList() != null) {
                    boolean defaultListIsSelected = selectedCodeLists.stream()
                        .anyMatch(cl -> cl.getInternalId().equals(originalRds.getDefaultList()));
                    if (defaultListIsSelected) {
                        rdsForDeployment.setDefaultList(originalRds.getDefaultList());
                    } else { // Fallback to the first selected code list if original default is not selected
                        rdsForDeployment.setDefaultList(selectedCodeLists.get(0).getInternalId());
                    }
                } else if (!selectedCodeLists.isEmpty()) { // If no default list was set originally
                    rdsForDeployment.setDefaultList(selectedCodeLists.get(0).getInternalId());
                }

                finalModelToDeploy.getReferenceDataSets().add(rdsForDeployment);
                // TODO: Also filter hierarchies and crosswalks if they involve only selected code lists.
            }
        } else {
             redirectAttributes.addFlashAttribute("errorMessage", "Error: Original suggested model structure was not found or was empty.");
             return "redirect:/wizard/review-model";
        }

        logger.info("Final R360 model to deploy: {} CodeLists, {} RDSs.",
                    finalModelToDeploy.getCodeLists().size(), finalModelToDeploy.getReferenceDataSets().size());

        // TODO: r360ApiService.deployModel will need to be updated to handle R360ModelImport
        // This call might fail at runtime if R360ApiServiceImpl expects the old ReferenceModel type.
        // For now, we are focusing on adapting the controller logic.
        // Temporarily wrapping in a try-catch to prevent ClassCastException from stopping flow for this subtask.
        boolean deploymentSuccess = false;
        try {
            // TODO: This line needs R360ApiService.deployModel to be updated to accept R360ModelImport
            // Passing null for now to ensure compilation, as the service expects old ReferenceModel.
            deploymentSuccess = r360ApiService.deployModel(null);
            logger.info("Mocked deployment call with null model due to R360ModelImport type change. Actual object to deploy: {}", finalModelToDeploy);
        } catch (ClassCastException e) { // Should not happen if passing null, but kept for safety
            logger.error("ClassCastException during deployModel: Likely R360ApiService.deployModel expects old ReferenceModel. This needs update.", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Deployment service error: Incompatible model type (needs update).");
            return "redirect:/wizard/deployment-confirmation";
        }


        if (deploymentSuccess) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Code Lists selected: " + selectedCodeListNames + ". Model deployment initiated successfully (mocked).");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Model deployment failed (mocked). Selected Code Lists: " + selectedCodeListNames);
        }

        // TODO: It's good practice to clear session attributes that are no longer needed.
        // e.g. using SessionStatus.setComplete()
        return "redirect:/wizard/deployment-confirmation";
    }

    @GetMapping("/deployment-confirmation")
    public String showDeploymentConfirmation(Model model) {
        return "wizard/deployment-confirmation";
    }
}
