package ru.ivan.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Launcher extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Путь к FXML относительно classpath (обычно лежит в src/main/resources или resources)
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ReaderFiles.fxml"));

        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("ReaderFiles");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
