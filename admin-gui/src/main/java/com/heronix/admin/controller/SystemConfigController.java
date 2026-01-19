package com.heronix.admin.controller;

import com.heronix.admin.model.SystemConfigDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Controller for system configuration view.
 */
@Slf4j
public class SystemConfigController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private TableView<SystemConfigDTO> configTable;
    @FXML private TableColumn<SystemConfigDTO, String> keyColumn;
    @FXML private TableColumn<SystemConfigDTO, String> valueColumn;
    @FXML private TableColumn<SystemConfigDTO, String> categoryColumn;
    @FXML private TableColumn<SystemConfigDTO, String> descriptionColumn;
    @FXML private TableColumn<SystemConfigDTO, Boolean> encryptedColumn;
    @FXML private TableColumn<SystemConfigDTO, Void> actionsColumn;
    @FXML private Label configCountLabel;

    private final ApiClient apiClient = ApiClient.getInstance();
    private final ObservableList<SystemConfigDTO> configList = FXCollections.observableArrayList();
    private List<SystemConfigDTO> allConfigs = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        loadConfigurations();
    }

    private void setupTable() {
        // Configure columns
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("configKey"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("configValue"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Encrypted column with icon
        encryptedColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean encrypted, boolean empty) {
                super.updateItem(encrypted, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    SystemConfigDTO config = getTableView().getItems().get(getIndex());
                    Label icon = new Label(config.isEncrypted() ? "🔒" : "");
                    setGraphic(icon);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Value column - mask encrypted values
        valueColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    SystemConfigDTO config = getTableView().getItems().get(getIndex());
                    if (config.isEncrypted()) {
                        setText("••••••••");
                    } else {
                        setText(value.length() > 50 ? value.substring(0, 47) + "..." : value);
                    }
                }
            }
        });

        // Actions column
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox buttons = new HBox(5, editBtn, deleteBtn);

            {
                buttons.setAlignment(Pos.CENTER);
                editBtn.getStyleClass().add("btn-small");
                deleteBtn.getStyleClass().addAll("btn-small", "btn-danger");

                editBtn.setOnAction(e -> {
                    SystemConfigDTO config = getTableView().getItems().get(getIndex());
                    showEditDialog(config);
                });

                deleteBtn.setOnAction(e -> {
                    SystemConfigDTO config = getTableView().getItems().get(getIndex());
                    handleDeleteConfig(config);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });

        configTable.setItems(configList);

        // Category filter
        categoryFilter.setOnAction(e -> filterConfigs());
    }

    private void loadConfigurations() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllSystemConfigs();
            } catch (Exception e) {
                log.error("Failed to load configurations", e);
                return Collections.<SystemConfigDTO>emptyList();
            }
        }).thenAccept(configs -> {
            Platform.runLater(() -> {
                allConfigs = new ArrayList<>(configs);
                configList.clear();
                configList.addAll(configs);
                updateConfigCount();
                updateCategoryFilter();
            });
        });
    }

    private void updateCategoryFilter() {
        Set<String> categories = allConfigs.stream()
                .map(SystemConfigDTO::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add("All Categories");
        filterOptions.addAll(categories);

        categoryFilter.setItems(FXCollections.observableArrayList(filterOptions));
        categoryFilter.getSelectionModel().selectFirst();
    }

    private void updateConfigCount() {
        configCountLabel.setText(configList.size() + " configurations");
    }

    @FXML
    private void handleSearch() {
        filterConfigs();
    }

    private void filterConfigs() {
        String search = searchField.getText().toLowerCase().trim();
        String category = categoryFilter.getValue();

        List<SystemConfigDTO> filtered = allConfigs.stream()
                .filter(c -> search.isEmpty() ||
                        (c.getConfigKey() != null && c.getConfigKey().toLowerCase().contains(search)) ||
                        (c.getDescription() != null && c.getDescription().toLowerCase().contains(search)))
                .filter(c -> category == null || "All Categories".equals(category) ||
                        (c.getCategory() != null && c.getCategory().equals(category)))
                .toList();

        configList.clear();
        configList.addAll(filtered);
        updateConfigCount();
    }

    @FXML
    private void handleAddConfig() {
        Dialog<SystemConfigDTO> dialog = new Dialog<>();
        dialog.setTitle("Add Configuration");
        dialog.setHeaderText("Create a new configuration entry");

        ButtonType saveButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);

        TextField keyField = new TextField();
        keyField.setPromptText("config.key.name");
        TextField valueField = new TextField();
        valueField.setPromptText("Configuration value");
        TextField categoryField = new TextField();
        categoryField.setPromptText("Category (e.g., general, mail, storage)");
        TextArea descriptionField = new TextArea();
        descriptionField.setPromptText("Description");
        descriptionField.setPrefRowCount(2);
        CheckBox encryptedCheckbox = new CheckBox("Encrypt value (for sensitive data)");

        content.getChildren().addAll(
                new Label("Key:"), keyField,
                new Label("Value:"), valueField,
                new Label("Category:"), categoryField,
                new Label("Description:"), descriptionField,
                encryptedCheckbox
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String key = keyField.getText().trim();
                if (key.isEmpty()) {
                    showError("Configuration key is required");
                    return null;
                }

                SystemConfigDTO config = new SystemConfigDTO();
                config.setConfigKey(key);
                config.setConfigValue(valueField.getText().trim());
                config.setCategory(categoryField.getText().trim());
                config.setDescription(descriptionField.getText().trim());
                config.setEncrypted(encryptedCheckbox.isSelected());

                createConfig(config);
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showEditDialog(SystemConfigDTO config) {
        Dialog<SystemConfigDTO> dialog = new Dialog<>();
        dialog.setTitle("Edit Configuration");
        dialog.setHeaderText("Edit: " + config.getConfigKey());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);

        Label keyLabel = new Label(config.getConfigKey());
        keyLabel.setStyle("-fx-font-weight: bold;");

        TextField valueField = new TextField(config.isEncrypted() ? "" : config.getConfigValue());
        valueField.setPromptText(config.isEncrypted() ? "Enter new value to change" : "Configuration value");

        TextField categoryField = new TextField(config.getCategory());
        categoryField.setPromptText("Category");

        TextArea descriptionField = new TextArea(config.getDescription());
        descriptionField.setPromptText("Description");
        descriptionField.setPrefRowCount(2);

        CheckBox encryptedCheckbox = new CheckBox("Encrypt value");
        encryptedCheckbox.setSelected(config.isEncrypted());

        content.getChildren().addAll(
                new Label("Key:"), keyLabel,
                new Label("Value:"), valueField,
                new Label("Category:"), categoryField,
                new Label("Description:"), descriptionField,
                encryptedCheckbox
        );

        if (config.isEncrypted()) {
            content.getChildren().add(2, new Label("(Leave empty to keep current value)"));
        }

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String newValue = valueField.getText().trim();
                if (config.isEncrypted() && newValue.isEmpty()) {
                    // Keep current value if encrypted and empty
                    newValue = config.getConfigValue();
                }

                config.setConfigValue(newValue);
                config.setCategory(categoryField.getText().trim());
                config.setDescription(descriptionField.getText().trim());
                config.setEncrypted(encryptedCheckbox.isSelected());

                updateConfig(config);
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void handleDeleteConfig(SystemConfigDTO config) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Configuration");
        confirm.setHeaderText("Are you sure you want to delete this configuration?");
        confirm.setContentText("Key: " + config.getConfigKey() + "\nThis action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteConfig(config.getId());
        }
    }

    private void createConfig(SystemConfigDTO config) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.createSystemConfig(config);
                Platform.runLater(() -> {
                    loadConfigurations();
                    showInfo("Success", "Configuration created successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to create configuration: " + e.getMessage()));
            }
        });
    }

    private void updateConfig(SystemConfigDTO config) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateSystemConfig(config.getId(), config);
                Platform.runLater(() -> {
                    loadConfigurations();
                    showInfo("Success", "Configuration updated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to update configuration: " + e.getMessage()));
            }
        });
    }

    private void deleteConfig(Long configId) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.deleteSystemConfig(configId);
                Platform.runLater(() -> {
                    loadConfigurations();
                    showInfo("Success", "Configuration deleted successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to delete configuration: " + e.getMessage()));
            }
        });
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
