package com.heronix.admin.controller;

import com.heronix.admin.model.AuditLogDTO;
import com.heronix.admin.model.PagedResponse;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for audit logs view.
 */
@Slf4j
public class AuditLogsController implements Initializable {

    @FXML private ComboBox<String> actionFilter;
    @FXML private ComboBox<String> entityTypeFilter;
    @FXML private TextField usernameFilter;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;

    @FXML private TableView<AuditLogDTO> auditTable;
    @FXML private TableColumn<AuditLogDTO, LocalDateTime> timestampColumn;
    @FXML private TableColumn<AuditLogDTO, String> actionColumn;
    @FXML private TableColumn<AuditLogDTO, String> entityTypeColumn;
    @FXML private TableColumn<AuditLogDTO, Long> entityIdColumn;
    @FXML private TableColumn<AuditLogDTO, String> usernameColumn;
    @FXML private TableColumn<AuditLogDTO, String> ipAddressColumn;
    @FXML private TableColumn<AuditLogDTO, String> detailsColumn;

    @FXML private Label logCountLabel;
    @FXML private ComboBox<Integer> pageSizeCombo;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label pageLabel;

    private final ApiClient apiClient = ApiClient.getInstance();
    private final ObservableList<AuditLogDTO> logsList = FXCollections.observableArrayList();

    private int currentPage = 0;
    private int totalPages = 1;
    private int pageSize = 50;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupTable();
        setupPagination();
        loadAuditLogs();
    }

    private void setupFilters() {
        // Action filter
        actionFilter.setItems(FXCollections.observableArrayList(
                "All Actions", "LOGIN", "LOGOUT", "CREATE", "UPDATE", "DELETE",
                "LOCK", "UNLOCK", "PASSWORD_RESET", "PERMISSION_CHANGE"
        ));
        actionFilter.getSelectionModel().selectFirst();

        // Entity type filter
        entityTypeFilter.setItems(FXCollections.observableArrayList(
                "All Types", "USER", "ROLE", "SESSION", "MESSAGE", "CONVERSATION",
                "GROUP", "CONFIG", "SECURITY_POLICY", "NETWORK_CONFIG"
        ));
        entityTypeFilter.getSelectionModel().selectFirst();

        // Default date range: last 7 days
        toDatePicker.setValue(LocalDate.now());
        fromDatePicker.setValue(LocalDate.now().minusDays(7));
    }

    private void setupTable() {
        // Configure columns
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime timestamp, boolean empty) {
                super.updateItem(timestamp, empty);
                if (empty || timestamp == null) {
                    setText(null);
                } else {
                    setText(timestamp.format(TIMESTAMP_FORMATTER));
                }
            }
        });

        actionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
        actionColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String action, boolean empty) {
                super.updateItem(action, empty);
                if (empty || action == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(action);
                    label.getStyleClass().addAll("action-badge", "action-" + action.toLowerCase());
                    setGraphic(label);
                    setText(null);
                }
            }
        });

        entityTypeColumn.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        entityIdColumn.setCellValueFactory(new PropertyValueFactory<>("entityId"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        ipAddressColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        detailsColumn.setCellValueFactory(new PropertyValueFactory<>("details"));

        // Details column with tooltip for long text
        detailsColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String details, boolean empty) {
                super.updateItem(details, empty);
                if (empty || details == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String displayText = details.length() > 50
                            ? details.substring(0, 47) + "..."
                            : details;
                    setText(displayText);
                    if (details.length() > 50) {
                        setTooltip(new Tooltip(details));
                    }
                }
            }
        });

        // Row double-click to show details
        auditTable.setRowFactory(tv -> {
            TableRow<AuditLogDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showLogDetails(row.getItem());
                }
            });
            return row;
        });

        auditTable.setItems(logsList);
    }

    private void setupPagination() {
        pageSizeCombo.setItems(FXCollections.observableArrayList(25, 50, 100, 200));
        pageSizeCombo.setValue(50);
        pageSizeCombo.setOnAction(e -> {
            pageSize = pageSizeCombo.getValue();
            currentPage = 0;
            loadAuditLogs();
        });
    }

    private void loadAuditLogs() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAuditLogs(currentPage, pageSize);
            } catch (Exception e) {
                log.error("Failed to load audit logs", e);
                return null;
            }
        }).thenAccept(response -> {
            Platform.runLater(() -> {
                if (response != null) {
                    updateTable(response);
                } else {
                    showError("Failed to load audit logs");
                }
            });
        });
    }

    private void updateTable(PagedResponse<AuditLogDTO> response) {
        logsList.clear();
        if (response.getContent() != null) {
            logsList.addAll(response.getContent());
        }

        totalPages = response.getTotalPages();
        currentPage = response.getPage();

        logCountLabel.setText(response.getTotalElements() + " entries");
        pageLabel.setText("Page " + (currentPage + 1) + " of " + Math.max(1, totalPages));

        prevPageBtn.setDisable(response.isFirst());
        nextPageBtn.setDisable(response.isLast());
    }

    @FXML
    private void handleApplyFilters() {
        currentPage = 0;
        // In a full implementation, we would pass filter parameters to the API
        // For now, just reload
        loadAuditLogs();
    }

    @FXML
    private void handleClearFilters() {
        actionFilter.getSelectionModel().selectFirst();
        entityTypeFilter.getSelectionModel().selectFirst();
        usernameFilter.clear();
        toDatePicker.setValue(LocalDate.now());
        fromDatePicker.setValue(LocalDate.now().minusDays(7));
        currentPage = 0;
        loadAuditLogs();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            currentPage--;
            loadAuditLogs();
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            loadAuditLogs();
        }
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Audit Logs");
        fileChooser.setInitialFileName("audit_logs_" + LocalDate.now() + ".csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showSaveDialog(auditTable.getScene().getWindow());
        if (file != null) {
            exportToCsv(file);
        }
    }

    private void exportToCsv(File file) {
        CompletableFuture.runAsync(() -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Header
                writer.println("Timestamp,Action,Entity Type,Entity ID,Username,IP Address,Details");

                // Data
                for (AuditLogDTO log : logsList) {
                    writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                            log.getTimestamp() != null ? log.getTimestamp().format(TIMESTAMP_FORMATTER) : "",
                            log.getAction() != null ? log.getAction() : "",
                            log.getEntityType() != null ? log.getEntityType() : "",
                            log.getEntityId() != null ? log.getEntityId() : "",
                            log.getUsername() != null ? log.getUsername() : "",
                            log.getIpAddress() != null ? log.getIpAddress() : "",
                            log.getDetails() != null ? log.getDetails().replace("\"", "\"\"") : ""
                    ));
                }

                Platform.runLater(() -> showInfo("Export Complete",
                        "Audit logs exported successfully to:\n" + file.getAbsolutePath()));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Export failed: " + e.getMessage()));
            }
        });
    }

    private void showLogDetails(AuditLogDTO log) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Audit Log Details");
        alert.setHeaderText("Log Entry #" + log.getId());

        StringBuilder content = new StringBuilder();
        content.append("Timestamp: ").append(log.getTimestamp() != null
                ? log.getTimestamp().format(TIMESTAMP_FORMATTER) : "N/A").append("\n\n");
        content.append("Action: ").append(log.getAction()).append("\n");
        content.append("Entity Type: ").append(log.getEntityType()).append("\n");
        content.append("Entity ID: ").append(log.getEntityId()).append("\n\n");
        content.append("User: ").append(log.getUsername()).append("\n");
        content.append("IP Address: ").append(log.getIpAddress()).append("\n");
        content.append("User Agent: ").append(log.getUserAgent() != null ? log.getUserAgent() : "N/A").append("\n\n");
        content.append("Details:\n").append(log.getDetails() != null ? log.getDetails() : "N/A").append("\n\n");

        if (log.getOldValue() != null && !log.getOldValue().isEmpty()) {
            content.append("Old Value:\n").append(log.getOldValue()).append("\n\n");
        }
        if (log.getNewValue() != null && !log.getNewValue().isEmpty()) {
            content.append("New Value:\n").append(log.getNewValue());
        }

        TextArea textArea = new TextArea(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
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
