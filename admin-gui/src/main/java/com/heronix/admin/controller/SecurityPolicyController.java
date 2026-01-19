package com.heronix.admin.controller;

import com.heronix.admin.model.SecurityPolicyDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for security policy configuration view.
 */
@Slf4j
public class SecurityPolicyController implements Initializable {

    // Password Policy
    @FXML private Spinner<Integer> minPasswordLengthSpinner;
    @FXML private Spinner<Integer> passwordExpirySpinner;
    @FXML private Spinner<Integer> passwordHistorySpinner;
    @FXML private CheckBox requireUppercaseCheckbox;
    @FXML private CheckBox requireLowercaseCheckbox;
    @FXML private CheckBox requireNumbersCheckbox;
    @FXML private CheckBox requireSpecialCheckbox;

    // Login Security
    @FXML private Spinner<Integer> maxLoginAttemptsSpinner;
    @FXML private Spinner<Integer> lockoutDurationSpinner;
    @FXML private CheckBox requireTwoFactorCheckbox;

    // Session Settings
    @FXML private Spinner<Integer> sessionTimeoutSpinner;
    @FXML private Spinner<Integer> maxConcurrentSessionsSpinner;
    @FXML private CheckBox allowRememberMeCheckbox;
    @FXML private Spinner<Integer> rememberMeDaysSpinner;

    private final ApiClient apiClient = ApiClient.getInstance();
    private SecurityPolicyDTO currentPolicy;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSpinners();
        loadSecurityPolicy();
    }

    private void setupSpinners() {
        minPasswordLengthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(4, 32, 8));
        passwordExpirySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 365, 0));
        passwordHistorySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 24, 5));
        maxLoginAttemptsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 5));
        lockoutDurationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440, 30));
        sessionTimeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 1440, 60));
        maxConcurrentSessionsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 0));
        rememberMeDaysSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 90, 30));
    }

    private void loadSecurityPolicy() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getSecurityPolicy();
            } catch (Exception e) {
                log.error("Failed to load security policy", e);
                return null;
            }
        }).thenAccept(policy -> {
            Platform.runLater(() -> {
                if (policy != null) {
                    currentPolicy = policy;
                    populateFields(policy);
                } else {
                    showError("Failed to load security policy");
                }
            });
        });
    }

    private void populateFields(SecurityPolicyDTO policy) {
        // Password Policy
        minPasswordLengthSpinner.getValueFactory().setValue(policy.getMinPasswordLength());
        passwordExpirySpinner.getValueFactory().setValue(policy.getPasswordExpiryDays());
        passwordHistorySpinner.getValueFactory().setValue(policy.getPasswordHistoryCount());
        requireUppercaseCheckbox.setSelected(policy.isRequireUppercase());
        requireLowercaseCheckbox.setSelected(policy.isRequireLowercase());
        requireNumbersCheckbox.setSelected(policy.isRequireNumbers());
        requireSpecialCheckbox.setSelected(policy.isRequireSpecialChars());

        // Login Security
        maxLoginAttemptsSpinner.getValueFactory().setValue(policy.getMaxLoginAttempts());
        lockoutDurationSpinner.getValueFactory().setValue(policy.getLockoutDurationMinutes());
        requireTwoFactorCheckbox.setSelected(policy.isRequireTwoFactor());

        // Session Settings
        sessionTimeoutSpinner.getValueFactory().setValue(policy.getSessionTimeoutMinutes());
        maxConcurrentSessionsSpinner.getValueFactory().setValue(policy.getMaxConcurrentSessions());
        allowRememberMeCheckbox.setSelected(policy.isAllowRememberMe());
        rememberMeDaysSpinner.getValueFactory().setValue(policy.getRememberMeDays());
    }

    private SecurityPolicyDTO buildPolicyFromFields() {
        SecurityPolicyDTO policy = new SecurityPolicyDTO();
        if (currentPolicy != null) {
            policy.setId(currentPolicy.getId());
        }

        // Password Policy
        policy.setMinPasswordLength(minPasswordLengthSpinner.getValue());
        policy.setPasswordExpiryDays(passwordExpirySpinner.getValue());
        policy.setPasswordHistoryCount(passwordHistorySpinner.getValue());
        policy.setRequireUppercase(requireUppercaseCheckbox.isSelected());
        policy.setRequireLowercase(requireLowercaseCheckbox.isSelected());
        policy.setRequireNumbers(requireNumbersCheckbox.isSelected());
        policy.setRequireSpecialChars(requireSpecialCheckbox.isSelected());

        // Login Security
        policy.setMaxLoginAttempts(maxLoginAttemptsSpinner.getValue());
        policy.setLockoutDurationMinutes(lockoutDurationSpinner.getValue());
        policy.setRequireTwoFactor(requireTwoFactorCheckbox.isSelected());

        // Session Settings
        policy.setSessionTimeoutMinutes(sessionTimeoutSpinner.getValue());
        policy.setMaxConcurrentSessions(maxConcurrentSessionsSpinner.getValue());
        policy.setAllowRememberMe(allowRememberMeCheckbox.isSelected());
        policy.setRememberMeDays(rememberMeDaysSpinner.getValue());

        return policy;
    }

    @FXML
    private void handleSave() {
        SecurityPolicyDTO policy = buildPolicyFromFields();

        CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateSecurityPolicy(policy);
                Platform.runLater(() -> {
                    currentPolicy = policy;
                    showInfo("Success", "Security policy updated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to save security policy: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleResetDefaults() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset to Defaults");
        confirm.setHeaderText("Are you sure you want to reset to default security settings?");
        confirm.setContentText("This will reset all security policy settings to their default values.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Set default values
            minPasswordLengthSpinner.getValueFactory().setValue(8);
            passwordExpirySpinner.getValueFactory().setValue(0);
            passwordHistorySpinner.getValueFactory().setValue(5);
            requireUppercaseCheckbox.setSelected(true);
            requireLowercaseCheckbox.setSelected(true);
            requireNumbersCheckbox.setSelected(true);
            requireSpecialCheckbox.setSelected(false);

            maxLoginAttemptsSpinner.getValueFactory().setValue(5);
            lockoutDurationSpinner.getValueFactory().setValue(30);
            requireTwoFactorCheckbox.setSelected(false);

            sessionTimeoutSpinner.getValueFactory().setValue(60);
            maxConcurrentSessionsSpinner.getValueFactory().setValue(0);
            allowRememberMeCheckbox.setSelected(true);
            rememberMeDaysSpinner.getValueFactory().setValue(30);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
