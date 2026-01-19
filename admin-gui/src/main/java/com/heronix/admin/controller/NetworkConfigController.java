package com.heronix.admin.controller;

import com.heronix.admin.model.NetworkConfigDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for network configuration view.
 */
@Slf4j
public class NetworkConfigController implements Initializable {

    // Server settings
    @FXML private TextField serverHostField;
    @FXML private Spinner<Integer> serverPortSpinner;
    @FXML private CheckBox sslEnabledCheckbox;
    @FXML private VBox sslConfigBox;
    @FXML private TextField sslKeystorePathField;

    // WebSocket settings
    @FXML private Spinner<Integer> websocketPortSpinner;
    @FXML private Spinner<Integer> websocketMaxConnectionsSpinner;
    @FXML private Spinner<Integer> websocketHeartbeatSpinner;
    @FXML private Spinner<Integer> websocketMaxSizeSpinner;

    // Proxy settings
    @FXML private CheckBox proxyEnabledCheckbox;
    @FXML private VBox proxyConfigBox;
    @FXML private TextField proxyHostField;
    @FXML private Spinner<Integer> proxyPortSpinner;
    @FXML private TextField proxyUsernameField;

    // CORS settings
    @FXML private CheckBox corsEnabledCheckbox;
    @FXML private VBox corsConfigBox;
    @FXML private TextField corsAllowedOriginsField;
    @FXML private TextField corsAllowedMethodsField;
    @FXML private TextField corsAllowedHeadersField;

    // Rate limiting
    @FXML private CheckBox rateLimitEnabledCheckbox;
    @FXML private VBox rateLimitConfigBox;
    @FXML private Spinner<Integer> rateLimitRequestsSpinner;
    @FXML private Spinner<Integer> rateLimitBurstSpinner;

    private final ApiClient apiClient = ApiClient.getInstance();
    private NetworkConfigDTO currentConfig;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSpinners();
        setupToggleBindings();
        loadNetworkConfig();
    }

    private void setupSpinners() {
        serverPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 9680));
        websocketPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 9681));
        websocketMaxConnectionsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100000, 10000));
        websocketHeartbeatSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 300, 30));
        websocketMaxSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10240, 512));
        proxyPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 8080));
        rateLimitRequestsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 10000, 100));
        rateLimitBurstSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 50));
    }

    private void setupToggleBindings() {
        sslEnabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            sslConfigBox.setVisible(newVal);
            sslConfigBox.setManaged(newVal);
        });

        proxyEnabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            proxyConfigBox.setVisible(newVal);
            proxyConfigBox.setManaged(newVal);
        });

        corsEnabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            corsConfigBox.setVisible(newVal);
            corsConfigBox.setManaged(newVal);
        });

        rateLimitEnabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            rateLimitConfigBox.setVisible(newVal);
            rateLimitConfigBox.setManaged(newVal);
        });
    }

    private void loadNetworkConfig() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getNetworkConfig();
            } catch (Exception e) {
                log.error("Failed to load network config", e);
                return null;
            }
        }).thenAccept(config -> {
            Platform.runLater(() -> {
                if (config != null) {
                    currentConfig = config;
                    populateFields(config);
                } else {
                    showError("Failed to load network configuration");
                }
            });
        });
    }

    private void populateFields(NetworkConfigDTO config) {
        // Server settings
        serverHostField.setText(config.getServerHost() != null ? config.getServerHost() : "0.0.0.0");
        serverPortSpinner.getValueFactory().setValue(config.getServerPort() > 0 ? config.getServerPort() : 9680);
        sslEnabledCheckbox.setSelected(config.isSslEnabled());
        sslKeystorePathField.setText(config.getSslKeyStorePath() != null ? config.getSslKeyStorePath() : "");

        // WebSocket settings
        websocketPortSpinner.getValueFactory().setValue(config.getWebsocketPort() > 0 ? config.getWebsocketPort() : 9681);
        websocketMaxConnectionsSpinner.getValueFactory().setValue(config.getWebsocketMaxConnections() > 0 ? config.getWebsocketMaxConnections() : 10000);
        websocketHeartbeatSpinner.getValueFactory().setValue(config.getWebsocketHeartbeatInterval() > 0 ? config.getWebsocketHeartbeatInterval() : 30);
        websocketMaxSizeSpinner.getValueFactory().setValue(config.getWebsocketMessageMaxSize() > 0 ? config.getWebsocketMessageMaxSize() : 512);

        // Proxy settings
        proxyEnabledCheckbox.setSelected(config.isProxyEnabled());
        proxyHostField.setText(config.getProxyHost() != null ? config.getProxyHost() : "");
        proxyPortSpinner.getValueFactory().setValue(config.getProxyPort() > 0 ? config.getProxyPort() : 8080);
        proxyUsernameField.setText(config.getProxyUsername() != null ? config.getProxyUsername() : "");

        // CORS settings
        corsEnabledCheckbox.setSelected(config.isCorsEnabled());
        corsAllowedOriginsField.setText(config.getCorsAllowedOrigins() != null ? config.getCorsAllowedOrigins() : "*");
        corsAllowedMethodsField.setText(config.getCorsAllowedMethods() != null ? config.getCorsAllowedMethods() : "GET, POST, PUT, DELETE");
        corsAllowedHeadersField.setText(config.getCorsAllowedHeaders() != null ? config.getCorsAllowedHeaders() : "*");

        // Rate limiting
        rateLimitEnabledCheckbox.setSelected(config.isRateLimitEnabled());
        rateLimitRequestsSpinner.getValueFactory().setValue(config.getRateLimitRequestsPerMinute() > 0 ? config.getRateLimitRequestsPerMinute() : 100);
        rateLimitBurstSpinner.getValueFactory().setValue(config.getRateLimitBurstSize() > 0 ? config.getRateLimitBurstSize() : 50);
    }

    private NetworkConfigDTO buildConfigFromFields() {
        NetworkConfigDTO config = new NetworkConfigDTO();
        if (currentConfig != null) {
            config.setId(currentConfig.getId());
        }

        // Server settings
        config.setServerHost(serverHostField.getText().trim());
        config.setServerPort(serverPortSpinner.getValue());
        config.setSslEnabled(sslEnabledCheckbox.isSelected());
        config.setSslKeyStorePath(sslKeystorePathField.getText().trim());

        // WebSocket settings
        config.setWebsocketPort(websocketPortSpinner.getValue());
        config.setWebsocketMaxConnections(websocketMaxConnectionsSpinner.getValue());
        config.setWebsocketHeartbeatInterval(websocketHeartbeatSpinner.getValue());
        config.setWebsocketMessageMaxSize(websocketMaxSizeSpinner.getValue());

        // Proxy settings
        config.setProxyEnabled(proxyEnabledCheckbox.isSelected());
        config.setProxyHost(proxyHostField.getText().trim());
        config.setProxyPort(proxyPortSpinner.getValue());
        config.setProxyUsername(proxyUsernameField.getText().trim());

        // CORS settings
        config.setCorsEnabled(corsEnabledCheckbox.isSelected());
        config.setCorsAllowedOrigins(corsAllowedOriginsField.getText().trim());
        config.setCorsAllowedMethods(corsAllowedMethodsField.getText().trim());
        config.setCorsAllowedHeaders(corsAllowedHeadersField.getText().trim());

        // Rate limiting
        config.setRateLimitEnabled(rateLimitEnabledCheckbox.isSelected());
        config.setRateLimitRequestsPerMinute(rateLimitRequestsSpinner.getValue());
        config.setRateLimitBurstSize(rateLimitBurstSpinner.getValue());

        return config;
    }

    @FXML
    private void handleBrowseKeystore() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select SSL Keystore");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Keystore Files", "*.jks", "*.p12", "*.pfx"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(sslKeystorePathField.getScene().getWindow());
        if (file != null) {
            sslKeystorePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleSave() {
        NetworkConfigDTO config = buildConfigFromFields();

        // Validation
        if (config.getServerPort() == config.getWebsocketPort()) {
            showError("Server port and WebSocket port must be different");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateNetworkConfig(config);
                Platform.runLater(() -> {
                    currentConfig = config;
                    showInfo("Success", "Network configuration saved.\nNote: Some changes may require a server restart to take effect.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to save network config: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleResetDefaults() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset to Defaults");
        confirm.setHeaderText("Are you sure you want to reset to default network settings?");
        confirm.setContentText("This will reset all network settings to their default values.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Server settings
            serverHostField.setText("0.0.0.0");
            serverPortSpinner.getValueFactory().setValue(9680);
            sslEnabledCheckbox.setSelected(false);
            sslKeystorePathField.setText("");

            // WebSocket settings
            websocketPortSpinner.getValueFactory().setValue(9681);
            websocketMaxConnectionsSpinner.getValueFactory().setValue(10000);
            websocketHeartbeatSpinner.getValueFactory().setValue(30);
            websocketMaxSizeSpinner.getValueFactory().setValue(512);

            // Proxy settings
            proxyEnabledCheckbox.setSelected(false);
            proxyHostField.setText("");
            proxyPortSpinner.getValueFactory().setValue(8080);
            proxyUsernameField.setText("");

            // CORS settings
            corsEnabledCheckbox.setSelected(true);
            corsAllowedOriginsField.setText("*");
            corsAllowedMethodsField.setText("GET, POST, PUT, DELETE, OPTIONS");
            corsAllowedHeadersField.setText("*");

            // Rate limiting
            rateLimitEnabledCheckbox.setSelected(false);
            rateLimitRequestsSpinner.getValueFactory().setValue(100);
            rateLimitBurstSpinner.getValueFactory().setValue(50);
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
