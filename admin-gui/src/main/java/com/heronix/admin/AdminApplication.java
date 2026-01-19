package com.heronix.admin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Objects;

/**
 * Main JavaFX Application for Heronix Talk Admin Dashboard.
 */
public class AdminApplication extends Application {

    private static Stage primaryStage;
    private static Scene mainScene;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        // Load login view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        mainScene = new Scene(root, 400, 500);
        mainScene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/dark-theme.css")).toExternalForm());

        stage.setTitle("Heronix Talk Admin");
        stage.setScene(mainScene);
        stage.setResizable(false);
        stage.centerOnScreen();

        // Try to load application icon
        try {
            stage.getIcons().add(new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/icon.png"))));
        } catch (Exception ignored) {
            // Icon not found, continue without it
        }

        stage.show();
    }

    /**
     * Switch to the main dashboard view after successful login.
     */
    public static void showDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(AdminApplication.class.getResource("/fxml/Dashboard.fxml"));
        Parent root = loader.load();

        mainScene = new Scene(root, 1400, 900);
        mainScene.getStylesheets().add(Objects.requireNonNull(
                AdminApplication.class.getResource("/css/dark-theme.css")).toExternalForm());

        primaryStage.setScene(mainScene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(800);
        primaryStage.centerOnScreen();
        primaryStage.setTitle("Heronix Talk Admin Dashboard");
    }

    /**
     * Switch back to login view.
     */
    public static void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(AdminApplication.class.getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        mainScene = new Scene(root, 400, 500);
        mainScene.getStylesheets().add(Objects.requireNonNull(
                AdminApplication.class.getResource("/css/dark-theme.css")).toExternalForm());

        primaryStage.setScene(mainScene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.setTitle("Heronix Talk Admin");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
