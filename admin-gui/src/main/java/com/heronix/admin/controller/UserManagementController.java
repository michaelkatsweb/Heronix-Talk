package com.heronix.admin.controller;

import com.heronix.admin.model.RoleDTO;
import com.heronix.admin.model.UserDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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

/**
 * Controller for user management view.
 */
@Slf4j
public class UserManagementController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> roleFilter;
    @FXML private TableView<UserDTO> usersTable;
    @FXML private TableColumn<UserDTO, Long> idColumn;
    @FXML private TableColumn<UserDTO, String> usernameColumn;
    @FXML private TableColumn<UserDTO, String> displayNameColumn;
    @FXML private TableColumn<UserDTO, String> emailColumn;
    @FXML private TableColumn<UserDTO, String> departmentColumn;
    @FXML private TableColumn<UserDTO, String> statusColumn;
    @FXML private TableColumn<UserDTO, String> rolesColumn;
    @FXML private TableColumn<UserDTO, Void> actionsColumn;
    @FXML private Label pageInfoLabel;
    @FXML private Pagination pagination;

    private final ApiClient apiClient = ApiClient.getInstance();
    private final ObservableList<UserDTO> usersList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFilters();
        setupTable();
        loadUsers();
        loadRolesForFilter();
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "All", "Online", "Away", "Busy", "Offline"
        ));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> filterUsers());

        roleFilter.setOnAction(e -> filterUsers());
    }

    private void setupTable() {
        // Configure columns
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        displayNameColumn.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        departmentColumn.setCellValueFactory(new PropertyValueFactory<>("department"));

        // Status column with custom styling
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label label = new Label(status);
                    label.getStyleClass().addAll("status-badge", "status-" + status.toLowerCase());
                    setGraphic(label);
                    setText(null);
                }
            }
        });

        // Roles column
        rolesColumn.setCellValueFactory(cellData -> {
            Set<String> roles = cellData.getValue().getRoles();
            String rolesStr = roles != null ? String.join(", ", roles) : "";
            return new SimpleStringProperty(rolesStr);
        });

        // Actions column
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button lockBtn = new Button();
            private final Button resetBtn = new Button("Reset");
            private final Button editBtn = new Button("Edit");
            private final HBox buttons = new HBox(5, lockBtn, resetBtn, editBtn);

            {
                buttons.setAlignment(Pos.CENTER);
                lockBtn.getStyleClass().add("btn-small");
                resetBtn.getStyleClass().add("btn-small");
                editBtn.getStyleClass().add("btn-small");

                lockBtn.setOnAction(e -> {
                    UserDTO user = getTableView().getItems().get(getIndex());
                    toggleUserLock(user);
                });

                resetBtn.setOnAction(e -> {
                    UserDTO user = getTableView().getItems().get(getIndex());
                    showResetPasswordDialog(user);
                });

                editBtn.setOnAction(e -> {
                    UserDTO user = getTableView().getItems().get(getIndex());
                    showEditUserDialog(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    UserDTO user = getTableView().getItems().get(getIndex());
                    lockBtn.setText(user.isLocked() ? "Unlock" : "Lock");
                    lockBtn.getStyleClass().removeAll("btn-danger", "btn-success");
                    lockBtn.getStyleClass().add(user.isLocked() ? "btn-success" : "btn-danger");
                    setGraphic(buttons);
                }
            }
        });

        usersTable.setItems(usersList);
    }

    private void loadUsers() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllUsers();
            } catch (Exception e) {
                log.error("Failed to load users", e);
                return Collections.<UserDTO>emptyList();
            }
        }).thenAccept(users -> {
            Platform.runLater(() -> {
                usersList.clear();
                usersList.addAll(users);
                pageInfoLabel.setText("Showing " + users.size() + " users");
            });
        });
    }

    private void loadRolesForFilter() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllRoles();
            } catch (Exception e) {
                log.error("Failed to load roles", e);
                return Collections.<RoleDTO>emptyList();
            }
        }).thenAccept(roles -> {
            Platform.runLater(() -> {
                List<String> roleNames = new ArrayList<>();
                roleNames.add("All");
                roles.forEach(r -> roleNames.add(r.getName()));
                roleFilter.setItems(FXCollections.observableArrayList(roleNames));
                roleFilter.getSelectionModel().selectFirst();
            });
        });
    }

    @FXML
    private void handleSearch() {
        filterUsers();
    }

    private void filterUsers() {
        String search = searchField.getText().toLowerCase().trim();
        String status = statusFilter.getValue();
        String role = roleFilter.getValue();

        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllUsers();
            } catch (Exception e) {
                log.error("Failed to load users", e);
                return Collections.<UserDTO>emptyList();
            }
        }).thenAccept(users -> {
            Platform.runLater(() -> {
                List<UserDTO> filtered = users.stream()
                        .filter(u -> search.isEmpty() ||
                                (u.getUsername() != null && u.getUsername().toLowerCase().contains(search)) ||
                                (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(search)) ||
                                (u.getEmail() != null && u.getEmail().toLowerCase().contains(search)))
                        .filter(u -> status == null || "All".equals(status) ||
                                (u.getStatus() != null && u.getStatus().equalsIgnoreCase(status)))
                        .filter(u -> role == null || "All".equals(role) ||
                                (u.getRoles() != null && u.getRoles().contains(role)))
                        .toList();

                usersList.clear();
                usersList.addAll(filtered);
                pageInfoLabel.setText("Showing " + filtered.size() + " users");
            });
        });
    }

    @FXML
    private void handleAddUser() {
        showAddUserDialog();
    }

    private void showAddUserDialog() {
        Dialog<UserDTO> dialog = new Dialog<>();
        dialog.setTitle("Add New User");
        dialog.setHeaderText("Create a new user account");

        ButtonType saveButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.getStyleClass().add("dialog-content");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        TextField displayNameField = new TextField();
        displayNameField.setPromptText("Display Name");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        TextField employeeIdField = new TextField();
        employeeIdField.setPromptText("Employee ID");
        TextField departmentField = new TextField();
        departmentField.setPromptText("Department");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        content.getChildren().addAll(
                new Label("Username:"), usernameField,
                new Label("Display Name:"), displayNameField,
                new Label("Email:"), emailField,
                new Label("Employee ID:"), employeeIdField,
                new Label("Department:"), departmentField,
                new Label("Password:"), passwordField
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                // Create user via API
                // This would need an API endpoint to create users
                showInfo("User Creation", "User creation would be implemented with proper API endpoint.");
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showEditUserDialog(UserDTO user) {
        Dialog<UserDTO> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Edit user: " + user.getUsername());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.getStyleClass().add("dialog-content");

        TextField displayNameField = new TextField(user.getDisplayName());
        displayNameField.setPromptText("Display Name");
        TextField emailField = new TextField(user.getEmail());
        emailField.setPromptText("Email");
        TextField departmentField = new TextField(user.getDepartment());
        departmentField.setPromptText("Department");
        TextField positionField = new TextField(user.getPosition());
        positionField.setPromptText("Position");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.setPromptText("Select Role");

        // Load roles
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllRoles();
            } catch (Exception e) {
                return Collections.<RoleDTO>emptyList();
            }
        }).thenAccept(roles -> {
            Platform.runLater(() -> {
                roles.forEach(r -> roleCombo.getItems().add(r.getName()));
                if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                    roleCombo.setValue(user.getRoles().iterator().next());
                }
            });
        });

        content.getChildren().addAll(
                new Label("Display Name:"), displayNameField,
                new Label("Email:"), emailField,
                new Label("Department:"), departmentField,
                new Label("Position:"), positionField,
                new Label("Role:"), roleCombo
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                // Update role if changed
                String selectedRole = roleCombo.getValue();
                if (selectedRole != null && !selectedRole.isEmpty()) {
                    updateUserRole(user.getId(), selectedRole);
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showResetPasswordDialog(UserDTO user) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password for: " + user.getUsername());
        dialog.setContentText("New Password:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> {
            if (password.length() < 6) {
                showError("Password must be at least 6 characters");
                return;
            }
            resetPassword(user.getId(), password);
        });
    }

    private void toggleUserLock(UserDTO user) {
        String action = user.isLocked() ? "unlock" : "lock";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm " + action);
        confirm.setHeaderText("Are you sure you want to " + action + " this user?");
        confirm.setContentText("User: " + user.getUsername());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            CompletableFuture.runAsync(() -> {
                try {
                    if (user.isLocked()) {
                        apiClient.unlockUser(user.getId());
                    } else {
                        apiClient.lockUser(user.getId());
                    }
                    Platform.runLater(() -> {
                        loadUsers();
                        showInfo("Success", "User " + action + "ed successfully");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Failed to " + action + " user: " + e.getMessage()));
                }
            });
        }
    }

    private void resetPassword(Long userId, String newPassword) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.resetUserPassword(userId, newPassword);
                Platform.runLater(() -> showInfo("Success", "Password reset successfully"));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to reset password: " + e.getMessage()));
            }
        });
    }

    private void updateUserRole(Long userId, String roleName) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateUserRole(userId, roleName);
                Platform.runLater(() -> {
                    loadUsers();
                    showInfo("Success", "User role updated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to update role: " + e.getMessage()));
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
