# Reference 360 Model Automation Tool

## 1. Overview

This application helps automate the creation of data models in Reference 360. It provides two main modules:

*   **Module 1: Suggest Model from Folder:** Analyzes a folder containing CSV and Excel files to suggest a data model. Users can then review this suggested model, select entities, and (eventually) deploy them to Reference 360 via its API.
*   **Module 2: Define Model from Spreadsheet:** Allows users to define a data model explicitly in an Excel spreadsheet. This defined model can then be reviewed, and selected entities can be (eventually) deployed.

The application is built using Java and Spring Boot.

## 2. Prerequisites

*   **Java Development Kit (JDK):** Version 11 or higher.
*   **Apache Maven:** Version 3.6 or higher (for building the project).
*   **Reference 360 Instance:** Access to a Reference 360 instance with API capabilities (for actual deployment).

## 3. Configuration

Currently, the Reference 360 API connection details are placeholders within the application code.

*   **API Configuration File:** `src/main/java/com/example/ref360automation/config/R360ApiConfig.java`
    *   `apiUrl`: The base URL of your Reference 360 API.
    *   `username`: The username for API authentication.
    *   `password`: The password for API authentication.

**Note:** For a production environment, these details should be externalized into `src/main/resources/application.properties` or managed via environment variables or a secure secrets management system.

Example `application.properties` entries (currently commented out):
```properties
# r360.api.url=https://your-ref360-instance.com/api
# r360.api.username=your-api-user
# r360.api.password=your-api-password
```

## 4. How to Use

### Module 1: Suggest Model from Folder

1.  **Start the Application:** (See "Building and Running" section below).
2.  **Access the Wizard:** Open your web browser and navigate to `http://localhost:8080/wizard/start`.
3.  **Specify Folder Path:**
    *   Enter the absolute path to the folder on the server where your data files (CSV, XLS, XLSX) are located.
    *   Example: `/path/to/data_files` or `C:/data_files`.
    *   For testing with sample data included in the project, you can use a path like `src/test/resources/sample_data` (ensure the application has read access to this path from its running location).
    *   Click "Suggest Model".
4.  **Review and Select Model:**
    *   The application will parse the files, suggest a model structure (entities and attributes with inferred types), and display it.
    *   Each suggested entity will have a checkbox. By default, all entities are selected.
    *   Review the suggested entities and their attributes.
    *   Uncheck any entities you do not wish to deploy.
    *   Click "Submit Selected Entities for Deployment".
5.  **Confirmation:**
    *   A confirmation page will indicate that the deployment has been initiated (currently, this uses a mock API service).

### Module 2: Define Model from Spreadsheet

1.  **Start the Application.**
2.  **Access the Wizard:** Open your web browser and navigate to `http://localhost:8080/wizard/upload-definition`.
3.  **Prepare Your Spreadsheet:**
    *   Create an Excel file (.xls or .xlsx).
    *   Each **sheet** in the file will represent an **Entity**. The sheet name will be used as the entity name.
    *   In each sheet, the **first row must contain headers**: `Attribute Name` in the first column and `Data Type` in the second column.
    *   Subsequent rows define the attributes for that entity:
        *   Column 1: The name of the attribute.
        *   Column 2: The data type (e.g., String, Integer, Double, Boolean, Date).
    *   Example for a sheet named "Customers":
        | Attribute Name | Data Type |
        |----------------|-----------|
        | CustomerID     | String    |
        | CompanyName    | String    |
        | ContactEmail   | String    |
        | LoyaltyScore   | Integer   |
4.  **Upload Spreadsheet:**
    *   Click "Choose File" (or similar, depending on your browser) and select your prepared Excel file.
    *   Click "Upload and Review Model".
5.  **Review and Select Model:**
    *   The application will parse your spreadsheet and display the defined model.
    *   This review and selection process is identical to Step 4 in Module 1. Select the entities you wish to deploy.
    *   Click "Submit Selected Entities for Deployment".
6.  **Confirmation:**
    *   A confirmation page will indicate that the deployment has been initiated (mocked).

## 5. Building and Running the Application

1.  **Clone the Repository:**
    ```bash
    # git clone <repository_url>
    # cd ref360automation
    ```
2.  **Build the Project:**
    Use Maven to build the project:
    ```bash
    mvn clean install
    ```
3.  **Run the Application:**
    You can run the application using the Spring Boot Maven plugin:
    ```bash
    mvn spring-boot:run
    ```
    Alternatively, you can run the packaged JAR file (after building):
    ```bash
    java -jar target/ref360automation-0.0.1-SNAPSHOT.jar
    ```
4.  The application will start, typically on `http://localhost:8080`.

## 6. Project Structure Highlights

*   `src/main/java`: Main Java source code.
    *   `com.example.ref360automation`: Base package.
        *   `config`: Configuration classes (e.g., `R360ApiConfig`).
        *   `controller`: Spring MVC controllers.
            *   `api`: REST API controllers (e.g., `FileProcessingController` for testing).
            *   `ui`: Controllers for the web UI (e.g., `WizardController`).
        *   `fileutils`: Utilities for file parsing (e.g., `FileParsingService`).
        *   `model`: Data model classes (e.g., `ReferenceModel`, `Entity`, `RawDataContainer`).
        *   `service`: Business logic services (e.g., `ModelSuggestionService`, `R360ApiService`, `ModelDefinitionParserService`).
*   `src/main/resources`: Application resources.
    *   `application.properties`: Spring Boot configuration file.
    *   `static`: Static web resources (CSS, JS, images - currently none).
    *   `templates`: Thymeleaf HTML templates for the UI.
        *   `wizard`: Templates for the modeling wizard.
*   `src/test/java`: Unit and integration tests.
*   `src/test/resources`: Test resources (e.g., `sample_data` for testing file parsing).
*   `pom.xml`: Maven project configuration file.

## 7. Further Development / TODO

*   Implement actual Reference 360 API calls in `R360ApiServiceImpl` (authentication, JSON payload creation, HTTP requests).
*   Externalize API configuration properly.
*   Enhance UI: Add styling, improve user feedback, allow model modification during review.
*   More robust error handling and validation.
*   Expand data type detection and allow user overrides.
*   Add more comprehensive tests (controller tests, integration tests).
*   Consider security aspects for file uploads and API credentials.
