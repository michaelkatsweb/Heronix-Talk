package com.heronix.admin.controller;

import com.heronix.admin.AdminApplication;
import com.heronix.admin.model.AuthResponse;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Controller for the login view.
 */
@Slf4j
public class LoginController implements Initializable {

    @FXML
    private TextField serverUrlField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox rememberMeCheckbox;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    private HBox loadingBox;

    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private static final String PREF_SERVER_URL = "serverUrl";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_REMEMBER_ME = "rememberMe";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Load saved preferences
        String savedServerUrl = prefs.get(PREF_SERVER_URL, "http://localhost:9680");
        String savedUsername = prefs.get(PREF_USERNAME, "");
        boolean savedRememberMe = prefs.getBoolean(PREF_REMEMBER_ME, false);

        serverUrlField.setText(savedServerUrl);

        if (savedRememberMe) {
            usernameField.setText(savedUsername);
            rememberMeCheckbox.setSelected(true);
        }

        // Set focus to appropriate field
        Platform.runLater(() -> {
            if (savedRememberMe && !savedUsername.isEmpty()) {
                passwordField.requestFocus();
            } else {
                usernameField.requestFocus();
            }
        });
    }

    @FXML
    private void handleLogin() {
        String serverUrl = serverUrlField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validate inputs
        if (serverUrl.isEmpty()) {
            showError("Please enter the server URL");
            serverUrlField.requestFocus();
            return;
        }

        if (username.isEmpty()) {
            showError("Please enter your username");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password");
            passwordField.requestFocus();
            return;
        }

        // Show loading state
        setLoadingState(true);
        hideError();

        // Configure API client
        ApiClient apiClient = ApiClient.getInstance();
        apiClient.setBaseUrl(serverUrl);

        // Perform login asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.login(username, password);
            } catch (Exception e) {
                log.error("Login failed", e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }).thenAccept(response -> {
            Platform.runLater(() -> {
                setLoadingState(false);
                handleLoginResponse(response, username, serverUrl);
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                setLoadingState(false);
                String message = throwable.getCause() != null
                        ? throwable.getCause().getMessage()
                        : throwable.getMessage();

                if (message.contains("Connection refused") || message.contains("Failed to connect")) {
                    showError("Cannot connect to server. Please check the server URL and ensure the server is running.");
                } else {
                    showError("Login failed: " + message);
                }
            });
            return null;
        });
    }

    private void handleLoginResponse(AuthResponse response, String username, String serverUrl) {
        if (response.isSuccess()) {
            // Save preferences
            prefs.put(PREF_SERVER_URL, serverUrl);
            prefs.putBoolean(PREF_REMEMBER_ME, rememberMeCheckbox.isSelected());

            if (rememberMeCheckbox.isSelected()) {
                prefs.put(PREF_USERNAME, username);
            } else {
                prefs.remove(PREF_USERNAME);
            }

            log.info("Login successful for user: {}", username);

            // Check if user has admin permissions
            if (response.getUser() != null && response.getUser().getPermissions() != null) {
                boolean hasAdminAccess = response.getUser().getPermissions().stream()
                        .anyMatch(p -> p.startsWith("admin.") || p.equals("*"));

                if (!hasAdminAccess) {
                    showError("Access denied. You do not have administrator privileges.");
                    ApiClient.getInstance().logout();
                    return;
                }
            }

            // Navigate to dashboard
            try {
                AdminApplication.showDashboard();
            } catch (Exception e) {
                log.error("Failed to load dashboard", e);
                showError("Failed to load dashboard: " + e.getMessage());
            }
        } else {
            showError(response.getMessage() != null ? response.getMessage() : "Authentication failed");
        }
    }

    private void setLoadingState(boolean loading) {
        loginButton.setDisable(loading);
        loginButton.setVisible(!loading);
        loginButton.setManaged(!loading);
        loadingBox.setVisible(loading);
        loadingBox.setManaged(loading);
        serverUrlField.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        rememberMeCheckbox.setDisable(loading);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
