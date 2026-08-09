package ru.ivan.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivan.utils.MySearchFiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    @FXML private TextField pathToFile;
    @FXML private TextArea textSaveAreaAll;
    @FXML private TextArea textAreaLogs;
    @FXML private Label labelFontSize;
    @FXML private Button buttonRead;
    @FXML private Button buttonClear;
    @FXML private Button buttonChancel;
    @FXML private Button increaseFontSizeBtn;
    @FXML private Button decreaseFontSizeBtn;

    // Для отмены задачи чтения/парсинга
    private Future<?> currentTask = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private double fontSize = 18.0;

    public void initialize() {
        updateFontSize();
        log("Готов к работе");

        // ГЛАВНОЕ ИЗМЕНЕНИЕ: при нажатии Enter в поле пути — запускаем чтение
        pathToFile.setOnAction(event -> onButtonReadClick());
    }

    @FXML
    private void onButtonReadClick() {
        String pathStr = pathToFile.getText();
        if (pathStr == null || pathStr.trim().isEmpty()) {
            logError("Не указан путь к файлу");
            return;
        }

        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }

        currentTask = executor.submit(() -> {
            try {
                Path path = Path.of(pathStr.trim());

                if (!Files.exists(path)) {
                    Platform.runLater(() -> logError("Файл не найден: " + path.toAbsolutePath()));
                    return;
                }
//                MySearchFiles mySearchFiles = new MySearchFiles();
                List<String> listOfFiles = new MySearchFiles().processPath(pathToFile.getText());
                String content = Files.readString(path, StandardCharsets.UTF_8);

                Platform.runLater(() -> {
                    // 1. Формируем заголовок блока
                    String header = "\n**********************\nфайл: " + pathStr + "\n**********************\n";

                    // 2. Запоминаем позицию, где начнётся заголовок (текущая длина)
                    int headerStartPos = textSaveAreaAll.getLength();

                    // 3. Вставляем заголовок
                    textSaveAreaAll.appendText(header);

                    // 4. Позиция, где начнётся сам контент файла (сразу после заголовка)
                    int contentStartPos = textSaveAreaAll.getLength();

                    // 5. Вставляем содержимое файла
                    textSaveAreaAll.appendText(content);

                    // 6. Ставим каретку в начало контента (после заголовка, но перед текстом файла)
                    textSaveAreaAll.positionCaret(contentStartPos);

                    // 7. Прокручиваем вниз, чтобы новый блок был виден
                    textSaveAreaAll.setScrollTop(Double.MAX_VALUE);

                    log("Файл прочитан: " + path.toAbsolutePath());
                });

            } catch (Exception e) {
                String userMessage;
                String originalMsg = e.getMessage();

                if (originalMsg != null && originalMsg.contains("Input length")) {
                    userMessage = "Ошибка чтения файла: возможно, файл повреждён, имеет неверную кодировку или пуст. Путь: " + pathStr;
                } else {
                    userMessage = "Ошибка чтения файла: " + originalMsg;
                }

                Platform.runLater(() -> logError(userMessage));
                LOG.error("Ошибка чтения", e);
            }
        });
    }

    @FXML
    private void onButtonCleardClick() {
        Platform.runLater(() -> {
            textSaveAreaAll.clear();
            textSaveAreaAll.positionCaret(0);
            textSaveAreaAll.setScrollTop(0);
            log("Содержимое очищено (Clear)");
        });
    }

    @FXML
    private void onButtonChanselClick() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
            log("Чтение/парсинг отменено (Chancel)");
        } else {
            log("Нет активной задачи для отмены");
        }
    }

    @FXML
    private void decreaseFontSize() {
        if (fontSize > 8) {
            fontSize -= 1;
            updateFontSize();
        }
    }

    @FXML
    private void increaseFontSize() {
        if (fontSize < 48) {
            fontSize += 1;
            updateFontSize();
        }
    }

    private void updateFontSize() {
        String style = "-fx-font-size: " + fontSize + "px;";
        textSaveAreaAll.setStyle(style);
        textAreaLogs.setStyle("-fx-font-size: 14px;");
        labelFontSize.setText("Шрифт: " + (int) fontSize);
    }

    private void log(String message) {
        Platform.runLater(() -> {
            textAreaLogs.appendText(message + "\n");
            textAreaLogs.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void logError(String message) {
        Platform.runLater(() -> {
            String errorText = "ОШИБКА: " + message + "\n";
            textAreaLogs.appendText(errorText);
            textAreaLogs.setScrollTop(Double.MAX_VALUE);
        });

        Platform.runLater(() -> {
            textSaveAreaAll.appendText("\n[ОШИБКА]: " + message + "\n");
        });
    }
}
