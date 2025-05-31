package com.example.ref360automation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class R360ApiConfig {

    // These would be typically loaded from application.properties
    // For example: @Value("${r360.api.url}")
    private String apiUrl = "https://your-ref360-instance.com/api"; // Placeholder
    private String username = "your-username"; // Placeholder
    private String password = "your-password"; // Placeholder

    // Getters
    public String getApiUrl() {
        return apiUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    // Setters can be added if properties are mutable or for testing
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
