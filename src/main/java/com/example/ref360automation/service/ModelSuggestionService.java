package com.example.ref360automation.service;

import com.example.ref360automation.model.RawDataContainer;
import com.example.ref360automation.model.ReferenceModel;
import java.util.List;

public interface ModelSuggestionService {
    /**
     * Suggests a ReferenceModel based on a list of RawDataContainers.
     * Each RawDataContainer (from a file or sheet) will typically become an Entity.
     * @param rawDataContainers List of data parsed from files/sheets.
     * @return A suggested ReferenceModel.
     */
    ReferenceModel suggestModel(List<RawDataContainer> rawDataContainers);
}
