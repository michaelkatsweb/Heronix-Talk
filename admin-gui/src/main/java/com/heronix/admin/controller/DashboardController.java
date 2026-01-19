package com.heronix.admin.controller;

import com.heronix.admin.AdminApplication;
import com.heronix.admin.model.DashboardDTO;
import com.heronix.admin.model.SystemHealthDTO;
import com.heronix.admin.model.UserDTO;
import com.heronix.admin.service.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller for the main dashboard view.
 */
@Slf4j
public class DashboardController implements Initializable {

    // Navigation buttons
    @FXML private Button navDashboard;
    @FXML private Button navUsers;
    @FXML private Button navRoles;
    @FXML private Button navSecurity;
    @FXML private Button navNetwork;
    @FXML private Button navSystem;
    @FXML private Button navAuditLogs;

    // Header elements
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label currentUserLabel;
    @FXML private Label currentRoleLabel;

    // Content containers
    @FXML private StackPane contentPane;
    @FXML private ScrollPane dashboardContent;
    @FXML private VBox loadingOverlay;

    // Dashboard stats
    @FXML private Label totalUsersLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label onlineUsersLabel;
    @FXML private Label activeSessionsLabel;
    @FXML private Label todayMessagesLabel;
    @FXML private Label totalMessagesLabel;
    @FXML private Label totalConversationsLabel;
    @FXML private Label totalGroupsLabel;

    // System health
    @FXML private Label systemStatusLabel;
    @FXML private Label cpuUsageLabel;
    @FXML private ProgressBar cpuProgressBar;
    @FXML private Label memoryUsageLabel;
    @FXML private ProgressBar memoryProgressBar;
    @FXML private Label diskUsageLabel;
    @FXML private ProgressBar diskProgressBar;
    @FXML private Label uptimeLabel;
    @FXML private Label dbStatusLabel;
    @FXML private Label connectionsLabel;

    private final ApiClient apiClient = ApiClient.getInstance();
    private ScheduledExecutorService scheduler;
    private Button activeNavButton;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        activeNavButton = navDashboard;

        // Set user info
        UserDTO currentUser = apiClient.getCurrentUser();
        if (currentUser != null) {
            currentUserLabel.setText(currentUser.getDisplayName() != null
                    ? currentUser.getDisplayName()
                    : currentUser.getUsername());
            if (currentUser.getRoles() != null && !currentUser.getRoles().isEmpty()) {
                currentRoleLabel.setText(String.join(", ", currentUser.getRoles()));
            }
        }

        // Load initial dashboard data
        loadDashboardData();

        // Schedule periodic refresh every 30 seconds
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (dashboardContent.isVisible()) {
                Platform.runLater(this::loadDashboardData);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void loadDashboardData() {
        showLoading(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                return apiClient.getDashboard();
            } catch (Exception e) {
                log.error("Failed to load dashboard data", e);
                return null;
            }
        }).thenAccept(dashboard -> {
            Platform.runLater(() -> {
                showLoading(false);
                if (dashboard != null) {
                    updateDashboard(dashboard);
                    lastUpdatedLabel.setText("Last updated: " + LocalDateTime.now().format(TIME_FORMATTER));
                }
            });
        });
    }

    private void updateDashboard(DashboardDTO dashboard) {
        // Update stats
        totalUsersLabel.setText(String.valueOf(dashboard.getTotalUsers()));
        activeUsersLabel.setText(dashboard.getActiveUsers() + " active");
        onlineUsersLabel.setText(String.valueOf(dashboard.getOnlineUsers()));
        activeSessionsLabel.setText(dashboard.getActiveSessions() + " sessions");
        todayMessagesLabel.setText(formatNumber(dashboard.getTodayMessages()));
        totalMessagesLabel.setText(formatNumber(dashboard.getTotalMessages()) + " total");
        totalConversationsLabel.setText(formatNumber(dashboard.getTotalConversations()));
        totalGroupsLabel.setText(dashboard.getTotalGroups() + " groups");

        // Update system health
        SystemHealthDTO health = dashboard.getSystemHealth();
        if (health != null) {
            updateSystemHealth(health);
        }
    }

    private void updateSystemHealth(SystemHealthDTO health) {
        // Status
        String status = health.getStatus() != null ? health.getStatus().toUpperCase() : "UNKNOWN";
        systemStatusLabel.setText(status);
        systemStatusLabel.getStyleClass().removeAll("status-healthy", "status-warning", "status-error");
        switch (status) {
            case "HEALTHY":
            case "UP":
                systemStatusLabel.getStyleClass().add("status-healthy");
                break;
            case "WARNING":
                systemStatusLabel.getStyleClass().add("status-warning");
                break;
            default:
                systemStatusLabel.getStyleClass().add("status-error");
        }

        // CPU
        double cpuUsage = health.getCpuUsage();
        cpuUsageLabel.setText(String.format("%.1f%%", cpuUsage));
        cpuProgressBar.setProgress(cpuUsage / 100.0);
        updateProgressBarStyle(cpuProgressBar, cpuUsage / 100.0);

        // Memory
        long memoryUsedMB = health.getMemoryUsed() / (1024 * 1024);
        long memoryMaxMB = health.getMemoryMax() / (1024 * 1024);
        memoryUsageLabel.setText(String.format("%d / %d MB", memoryUsedMB, memoryMaxMB));
        double memoryRatio = memoryMaxMB > 0 ? (double) memoryUsedMB / memoryMaxMB : 0;
        memoryProgressBar.setProgress(memoryRatio);
        updateProgressBarStyle(memoryProgressBar, memoryRatio);

        // Disk
        long diskUsedGB = health.getDiskUsed() / (1024 * 1024 * 1024);
        long diskTotalGB = health.getDiskTotal() / (1024 * 1024 * 1024);
        diskUsageLabel.setText(String.format("%d / %d GB", diskUsedGB, diskTotalGB));
        double diskRatio = diskTotalGB > 0 ? (double) diskUsedGB / diskTotalGB : 0;
        diskProgressBar.setProgress(diskRatio);
        updateProgressBarStyle(diskProgressBar, diskRatio);

        // Footer info
        uptimeLabel.setText("Uptime: " + formatUptime(health.getUptime()));
        dbStatusLabel.setText("Database: " + (health.getDatabaseStatus() != null ? health.getDatabaseStatus() : "Unknown"));
        connectionsLabel.setText("Connections: " + health.getActiveConnections());
    }

    private void updateProgressBarStyle(ProgressBar bar, double value) {
        bar.getStyleClass().removeAll("progress-ok", "progress-warning", "progress-danger");
        if (value > 0.9) {
            bar.getStyleClass().add("progress-danger");
        } else if (value > 0.7) {
            bar.getStyleClass().add("progress-warning");
        } else {
            bar.getStyleClass().add("progress-ok");
        }
    }

    private String formatUptime(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) return String.format("%.1fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fK", number / 1_000.0);
        return String.valueOf(number);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
    }

    private void setActiveNavButton(Button button) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
        }
        activeNavButton = button;
        button.getStyleClass().add("nav-button-active");
    }

    private void loadView(String fxmlPath, String title, String subtitle) {
        showLoading(true);
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);

        CompletableFuture.supplyAsync(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                return loader.load();
            } catch (IOException e) {
                log.error("Failed to load view: {}", fxmlPath, e);
                return null;
            }
        }).thenAccept(view -> {
            Platform.runLater(() -> {
                showLoading(false);
                if (view != null) {
                    contentPane.getChildren().clear();
                    contentPane.getChildren().addAll((Parent) view, loadingOverlay);
                }
            });
        });
    }

    // Navigation handlers

    @FXML
    private void showDashboard() {
        setActiveNavButton(navDashboard);
        pageTitle.setText("Dashboard");
        pageSubtitle.setText("System overview and statistics");
        contentPane.getChildren().clear();
        contentPane.getChildren().addAll(dashboardContent, loadingOverlay);
        loadDashboardData();
    }

    @FXML
    private void showUsers() {
        setActiveNavButton(navUsers);
        loadView("/fxml/UserManagement.fxml", "User Management", "Manage users and their accounts");
    }

    @FXML
    private void showRoles() {
        setActiveNavButton(navRoles);
        loadView("/fxml/RoleManagement.fxml", "Roles & Permissions", "Manage roles and permissions");
    }

    @FXML
    private void showSecurityPolicy() {
        setActiveNavButton(navSecurity);
        loadView("/fxml/SecurityPolicy.fxml", "Security Policy", "Configure security settings");
    }

    @FXML
    private void showNetworkConfig() {
        setActiveNavButton(navNetwork);
        loadView("/fxml/NetworkConfig.fxml", "Network Settings", "Configure network and connectivity");
    }

    @FXML
    private void showSystemConfig() {
        setActiveNavButton(navSystem);
        loadView("/fxml/SystemConfig.fxml", "System Configuration", "Manage system settings");
    }

    @FXML
    private void showAuditLogs() {
        setActiveNavButton(navAuditLogs);
        loadView("/fxml/AuditLogs.fxml", "Audit Logs", "View system activity logs");
    }

    @FXML
    private void handleRefresh() {
        if (dashboardContent.isVisible()) {
            loadDashboardData();
        }
        // Trigger refresh on current view if it supports it
    }

    @FXML
    private void handleLogout() {
        // Stop scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        // Logout from API
        try {
            apiClient.logout();
        } catch (Exception e) {
            log.error("Logout failed", e);
        }

        // Return to login screen
        try {
            AdminApplication.showLogin();
        } catch (IOException e) {
            log.error("Failed to show login", e);
        }
    }

    // Quick actions

    @FXML
    private void quickAddUser() {
        showUsers();
        // TODO: Open add user dialog
    }

    @FXML
    private void quickCreateRole() {
        showRoles();
        // TODO: Open create role dialog
    }

    @FXML
    private void quickBackup() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Backup");
        alert.setHeaderText("Backup Feature");
        alert.setContentText("Database backup feature will be available in a future version.");
        alert.showAndWait();
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
