package com.example.ref360automation.service;

import com.example.ref360automation.model.ReferenceModel;

public interface R360ApiService {
    boolean login(String username, String password);
    boolean deployModel(ReferenceModel model);
    // Add other methods as needed, e.g., for fetching existing models, checking status, etc.
}
