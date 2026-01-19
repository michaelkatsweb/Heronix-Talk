package com.heronix.admin.controller;

import com.heronix.admin.model.RoleDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for role management view.
 */
@Slf4j
public class RoleManagementController implements Initializable {

    @FXML private ListView<RoleDTO> rolesListView;
    @FXML private Button editRoleBtn;
    @FXML private Button deleteRoleBtn;
    @FXML private Button savePermissionsBtn;

    @FXML private Label roleDetailsTitle;
    @FXML private Label systemRoleBadge;
    @FXML private Label roleNameLabel;
    @FXML private Label roleDisplayNameLabel;
    @FXML private Label roleDescriptionLabel;

    @FXML private VBox permissionsContainer;

    // Permission checkboxes
    @FXML private CheckBox permUserView;
    @FXML private CheckBox permUserCreate;
    @FXML private CheckBox permUserEdit;
    @FXML private CheckBox permUserDelete;
    @FXML private CheckBox permUserLock;
    @FXML private CheckBox permUserResetPassword;

    @FXML private CheckBox permRoleView;
    @FXML private CheckBox permRoleCreate;
    @FXML private CheckBox permRoleEdit;
    @FXML private CheckBox permRoleDelete;
    @FXML private CheckBox permRoleAssign;

    @FXML private CheckBox permMessageView;
    @FXML private CheckBox permMessageDelete;
    @FXML private CheckBox permMessageModerate;

    @FXML private CheckBox permAdminDashboard;
    @FXML private CheckBox permAdminConfig;
    @FXML private CheckBox permAdminSecurity;
    @FXML private CheckBox permAdminNetwork;
    @FXML private CheckBox permAdminAudit;
    @FXML private CheckBox permAdminBackup;

    private final ApiClient apiClient = ApiClient.getInstance();
    private final ObservableList<RoleDTO> rolesList = FXCollections.observableArrayList();
    private RoleDTO selectedRole;

    private final Map<String, CheckBox> permissionCheckboxes = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPermissionMapping();
        setupRolesList();
        loadRoles();
    }

    private void setupPermissionMapping() {
        permissionCheckboxes.put("user.view", permUserView);
        permissionCheckboxes.put("user.create", permUserCreate);
        permissionCheckboxes.put("user.edit", permUserEdit);
        permissionCheckboxes.put("user.delete", permUserDelete);
        permissionCheckboxes.put("user.lock", permUserLock);
        permissionCheckboxes.put("user.reset_password", permUserResetPassword);

        permissionCheckboxes.put("role.view", permRoleView);
        permissionCheckboxes.put("role.create", permRoleCreate);
        permissionCheckboxes.put("role.edit", permRoleEdit);
        permissionCheckboxes.put("role.delete", permRoleDelete);
        permissionCheckboxes.put("role.assign", permRoleAssign);

        permissionCheckboxes.put("message.view", permMessageView);
        permissionCheckboxes.put("message.delete", permMessageDelete);
        permissionCheckboxes.put("message.moderate", permMessageModerate);

        permissionCheckboxes.put("admin.dashboard", permAdminDashboard);
        permissionCheckboxes.put("admin.config", permAdminConfig);
        permissionCheckboxes.put("admin.security", permAdminSecurity);
        permissionCheckboxes.put("admin.network", permAdminNetwork);
        permissionCheckboxes.put("admin.audit", permAdminAudit);
        permissionCheckboxes.put("admin.backup", permAdminBackup);
    }

    private void setupRolesList() {
        rolesListView.setItems(rolesList);
        rolesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(RoleDTO role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(role.getDisplayName() != null ? role.getDisplayName() : role.getName());
                    if (role.isSystemRole()) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        rolesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedRole = newVal;
            updateRoleDetails(newVal);
            updateButtonStates();
        });
    }

    private void loadRoles() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getAllRoles();
            } catch (Exception e) {
                log.error("Failed to load roles", e);
                return Collections.<RoleDTO>emptyList();
            }
        }).thenAccept(roles -> {
            Platform.runLater(() -> {
                rolesList.clear();
                rolesList.addAll(roles);
                if (!roles.isEmpty()) {
                    rolesListView.getSelectionModel().selectFirst();
                }
            });
        });
    }

    private void updateRoleDetails(RoleDTO role) {
        if (role == null) {
            roleDetailsTitle.setText("Role Details");
            roleNameLabel.setText("--");
            roleDisplayNameLabel.setText("--");
            roleDescriptionLabel.setText("--");
            systemRoleBadge.setVisible(false);
            clearPermissionCheckboxes();
            return;
        }

        roleDetailsTitle.setText("Role: " + role.getName());
        roleNameLabel.setText(role.getName());
        roleDisplayNameLabel.setText(role.getDisplayName() != null ? role.getDisplayName() : "--");
        roleDescriptionLabel.setText(role.getDescription() != null ? role.getDescription() : "--");
        systemRoleBadge.setVisible(role.isSystemRole());

        updatePermissionCheckboxes(role);
    }

    private void updatePermissionCheckboxes(RoleDTO role) {
        Set<String> permissions = role.getPermissions() != null ? role.getPermissions() : Collections.emptySet();
        boolean hasAll = permissions.contains("*");
        boolean isSystemRole = role.isSystemRole();

        for (Map.Entry<String, CheckBox> entry : permissionCheckboxes.entrySet()) {
            CheckBox checkbox = entry.getValue();
            String permission = entry.getKey();

            boolean hasPermission = hasAll || permissions.contains(permission);
            checkbox.setSelected(hasPermission);
            checkbox.setDisable(isSystemRole); // System roles cannot be edited
        }

        savePermissionsBtn.setDisable(isSystemRole);
    }

    private void clearPermissionCheckboxes() {
        for (CheckBox checkbox : permissionCheckboxes.values()) {
            checkbox.setSelected(false);
            checkbox.setDisable(true);
        }
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedRole != null;
        boolean isSystemRole = hasSelection && selectedRole.isSystemRole();

        editRoleBtn.setDisable(!hasSelection || isSystemRole);
        deleteRoleBtn.setDisable(!hasSelection || isSystemRole);
        savePermissionsBtn.setDisable(!hasSelection || isSystemRole);
    }

    @FXML
    private void handleCreateRole() {
        Dialog<RoleDTO> dialog = new Dialog<>();
        dialog.setTitle("Create New Role");
        dialog.setHeaderText("Enter role details");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);

        TextField nameField = new TextField();
        nameField.setPromptText("Role name (e.g., MODERATOR)");
        TextField displayNameField = new TextField();
        displayNameField.setPromptText("Display name");
        TextArea descriptionField = new TextArea();
        descriptionField.setPromptText("Description");
        descriptionField.setPrefRowCount(3);

        content.getChildren().addAll(
                new Label("Name:"), nameField,
                new Label("Display Name:"), displayNameField,
                new Label("Description:"), descriptionField
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                String name = nameField.getText().trim().toUpperCase();
                if (name.isEmpty()) {
                    showError("Role name is required");
                    return null;
                }

                RoleDTO newRole = new RoleDTO();
                newRole.setName(name);
                newRole.setDisplayName(displayNameField.getText().trim());
                newRole.setDescription(descriptionField.getText().trim());
                newRole.setPermissions(new HashSet<>());

                createRole(newRole);
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void createRole(RoleDTO role) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.createRole(role);
                Platform.runLater(() -> {
                    loadRoles();
                    showInfo("Success", "Role created successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to create role: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void handleEditRole() {
        if (selectedRole == null || selectedRole.isSystemRole()) return;

        Dialog<RoleDTO> dialog = new Dialog<>();
        dialog.setTitle("Edit Role");
        dialog.setHeaderText("Edit role: " + selectedRole.getName());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);

        TextField displayNameField = new TextField(selectedRole.getDisplayName());
        displayNameField.setPromptText("Display name");
        TextArea descriptionField = new TextArea(selectedRole.getDescription());
        descriptionField.setPromptText("Description");
        descriptionField.setPrefRowCount(3);

        content.getChildren().addAll(
                new Label("Display Name:"), displayNameField,
                new Label("Description:"), descriptionField
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                selectedRole.setDisplayName(displayNameField.getText().trim());
                selectedRole.setDescription(descriptionField.getText().trim());
                updateRole(selectedRole);
            }
            return null;
        });

        dialog.showAndWait();
    }

    @FXML
    private void handleDeleteRole() {
        if (selectedRole == null || selectedRole.isSystemRole()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Role");
        confirm.setHeaderText("Are you sure you want to delete this role?");
        confirm.setContentText("Role: " + selectedRole.getName() + "\nThis action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteRole(selectedRole.getId());
        }
    }

    @FXML
    private void handleSavePermissions() {
        if (selectedRole == null || selectedRole.isSystemRole()) return;

        Set<String> newPermissions = new HashSet<>();
        for (Map.Entry<String, CheckBox> entry : permissionCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                newPermissions.add(entry.getKey());
            }
        }

        selectedRole.setPermissions(newPermissions);
        updateRole(selectedRole);
    }

    private void updateRole(RoleDTO role) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.updateRole(role.getId(), role);
                Platform.runLater(() -> {
                    loadRoles();
                    showInfo("Success", "Role updated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to update role: " + e.getMessage()));
            }
        });
    }

    private void deleteRole(Long roleId) {
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.deleteRole(roleId);
                Platform.runLater(() -> {
                    loadRoles();
                    showInfo("Success", "Role deleted successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to delete role: " + e.getMessage()));
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
