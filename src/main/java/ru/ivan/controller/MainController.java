package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private TextField pathToFile;
    @FXML
    private TextField pathToSave;
    @FXML
    private Button buttonSaveOK;
    @FXML
    private TextArea textSaveAreAll;

    /**
     * Обработчик кнопки OK.
     */
    @FXML
    protected void handleOk() {
        String inputPathStr = pathToFile.getText();
        String outputPathStr = pathToSave.getText();

        // Простая валидация
        if (inputPathStr == null || inputPathStr.isBlank()) {
            appendLog("Ошибка: не указан путь к исходному файлу.");
            return;
        }
        if (outputPathStr == null || outputPathStr.isBlank()) {
            appendLog("Ошибка: не указан путь для сохранения.");
            return;
        }

        Path inputPath = Paths.get(inputPathStr.trim());
        Path outputPath = Paths.get(outputPathStr.trim());

        // Проверка существования входного файла
        if (!Files.exists(inputPath)) {
            appendLog("Ошибка: файл по пути '" + inputPath + "' не найден.");
            log.warn("Input file not found: {}", inputPath);
            return;
        }
        if (!Files.isReadable(inputPath)) {
            appendLog("Ошибка: нет прав на чтение файла '" + inputPath + "'.");
            log.warn("No read permission for file: {}", inputPath);
            return;
        }

        // Запускаем тяжёлую операцию в отдельном потоке, чтобы не вешать UI
        new Thread(() -> {
            try {
                appendLog("Начинаю обработку: " + inputPath);
                log.info("Starting processing for input: {}, output: {}", inputPath, outputPath);

                // ---------------------------------------------------------
                // ЗДЕСЬ ТВОЯ РЕАЛЬНАЯ ЛОГИКА:
                // Например, парсинг JSONL, конвертация в CSV, сериализация RAL и т.п.
                // Пример: JsonlToCsvConverter.convert(inputPath, outputPath, textSaveAreAll);
                // ---------------------------------------------------------

                // Имитация долгой работы (удали это в реальном коде)
//                Thread.sleep(1500);

                Platform.runLater(() -> {
                    appendLog("Обработка завершена. Результат сохранён в: " + outputPath);
                    log.info("Processing completed. Output saved to: {}", outputPath);
                });

            } catch (Exception e) {
                String errorMsg = "Произошла ошибка при обработке: " + e.getMessage();
                log.error(errorMsg, e);
                Platform.runLater(() -> appendLog(errorMsg));
            }
        }).start();
    }

    /**
     * Безопасное добавление текста в TextArea из любого потока.
     */
    private void appendLog(String message) {
        // Если вызов уже из JavaFX Application Thread — можно писать напрямую.
        // Но для универсальности (особенно при многопоточном логировании) лучше всегда через Platform.runLater.
        Platform.runLater(() -> {
            if (textSaveAreAll != null) {
                textSaveAreAll.appendText(message + "\n");
                // Автопрокрутка вниз
                textSaveAreAll.selectPositionCaret(textSaveAreAll.getLength());
            }
        });
    }
    public static void main(String[] args) {
        launch(args);
    }
}
