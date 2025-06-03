package com.example.ref360automation.service;

import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.r360.model.R360ModelImport; // New model
import java.util.List;

public interface ModelSuggestionService {
    // com.example.ref360automation.model.ReferenceModel suggestModel(List<RawDataContainer> rawDataContainers); // Old signature - commented out from instruction
    R360ModelImport suggestR360Model(List<RawDataContainer> rawDataContainers, String defaultRdsName); // New signature
}
