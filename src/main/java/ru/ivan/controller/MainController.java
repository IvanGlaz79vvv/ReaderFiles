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

    @FXML
    private TextField pathToFile;
    @FXML
    private TextArea textSaveAreaAll;
    @FXML
    private TextArea textAreaLogs;
    @FXML
    private Label labelFontSize;
    @FXML
    private Button buttonRead;
    @FXML
    private Button buttonClear;
    @FXML
    private Button buttonChancel;
    @FXML
    private Button increaseFontSizeBtn;
    @FXML
    private Button decreaseFontSizeBtn;

    // Для отмены задачи чтения/парсинга
    private Future<?> currentTask = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private double fontSize = 18.0;

    public void initialize() {
        updateFontSize();
        log("Готов к работе");

        /**При нажатии Enter в поле пути — запускаем чтение*/
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
            List<String> listOfPaths = new MySearchFiles().processPath(pathStr);

            if (listOfPaths.isEmpty()) {
                Platform.runLater(() -> logError("Не найдено файлов для обработки"));
                return;
            }

            int[] totalFiles = { listOfPaths.size() };
            int[] successCount = { 0 };
            int[] errorCount = { 0 };
            long[] totalLines = { 0 }; // суммарное число строк

            if (totalFiles[0] > 1) {
                String msg = String.format("\n[INFO] Путь ведёт на директорию. Найдено файлов: %d\n", totalFiles[0]);
                Platform.runLater(() -> textSaveAreaAll.appendText(msg));
            }

            int[] lastContentStartPos = { -1 };

            for (String filePath : listOfPaths) {
                if (currentTask.isCancelled()) {
                    LOG.info("Обработка отменена пользователем");
                    String logMsg = String.format("Обработка прервана: всего=%d, успех=%d, ошибки=%d, всего строк=%d",
                            totalFiles[0], successCount[0], errorCount[0], totalLines[0]);
                    Platform.runLater(() -> log(logMsg));
                    return;
                }

                Path path = Path.of(filePath);
                String fileName = path.getFileName().toString();

                try {
                    // Читаем как строку
                    String content = Files.readString(path, StandardCharsets.UTF_8);

                    // Считаем строки: split("\n") + если есть контент и не заканчивается на \n — всё равно ок
                    long lineCount = 0;
                    if (!content.isEmpty()) {
                        lineCount = content.lines().count(); // надёжный способ
                    }

                    // --- делаем финальную копию для лямбды ---
                    final long finalLineCount = lineCount;
                    final String finalFileName = fileName;
                    // -----------------------------------------------------

                    Platform.runLater(() -> {
                        String header = "\n**********************\nфайл: " + fileName + "\n**********************\n";
                        textSaveAreaAll.appendText(header);

                        int contentStartPos = textSaveAreaAll.getLength();
                        textSaveAreaAll.appendText(content);

                        // Сообщение об успехе с количеством строк — в textSaveAreaAll
                        textSaveAreaAll.appendText("\n[\"" + fileName + "\" прочитан успешно. Строк: " + finalLineCount + "]\n");

                        lastContentStartPos[0] = contentStartPos;
                        textSaveAreaAll.positionCaret(contentStartPos);
                    });

                    successCount[0]++;
                    totalLines[0] += lineCount;// тут можно использовать обычный lineCount (вне лямбды)

                    // Лог в textAreaLogs
                    String successLog = "\"" + fileName + "\" прочитан успешно. Строк: " + finalLineCount;
                    Platform.runLater(() -> log(successLog));

                    // SLF4J (app.log / консоль)
                    LOG.info("Обработан файл: {}, строк: {}", path, lineCount);

                    // Терминал (System.out)
                    System.out.println("[LOG] \"" + fileName + "\" прочитан успешно. Строк: " + lineCount);

                } catch (Exception e) {
                    errorCount[0]++;

                    String userMessage;
                    String originalMsg = e.getMessage();

                    if (originalMsg != null && originalMsg.contains("Input length")) {
                        userMessage = "Ошибка чтения файла: возможно, файл повреждён, имеет неверную кодировку или пуст.";
                    } else {
                        userMessage = "Ошибка чтения: " + (originalMsg != null ? originalMsg : "неизвестная ошибка");
                    }

                    final String finalFileName = fileName;
                    final String finalUserMessage = userMessage;

                    // ОШИБКА — в textSaveAreaAll в требуемом формате
                    Platform.runLater(() -> {
                        String header = "\n**********************\nфайл: " + finalFileName + "\n**********************\n";
                        textSaveAreaAll.appendText(header);

                        textSaveAreaAll.appendText("[ОШИБКА]: Файл: " + finalFileName + "\n");
                        textSaveAreaAll.appendText(userMessage + "\n");
                    });

                    //??????????????????????????????????????????????????????????????????????????????????????
                    // Лог ошибки в textAreaLogs (без количества строк, т.к. не смогли прочитать)???????????
                    String errorLog = "[ОШИБКА]: Файл: " + finalFileName + " — " + finalUserMessage;
                    Platform.runLater(() -> log(errorLog));

                    // SLF4J
                    LOG.error("Ошибка чтения файла: {}. {}", path, finalUserMessage, e);

                    // Терминал
                    System.out.println("[ОШИБКА]: Файл: " + finalFileName + " — " + finalUserMessage);
                }
            }

            // Итоговая статистика
            String logMsg = String.format("Обработка завершена: всего=%d, успех=%d, ошибки=%d, всего строк=%d",
                    totalFiles[0], successCount[0], errorCount[0], totalLines[0]);

            Platform.runLater(() -> log(logMsg));
            LOG.info(logMsg);
            System.out.println("[SUMMARY] " + logMsg);

            if (lastContentStartPos[0] != -1) {
                Platform.runLater(() -> {
                    textSaveAreaAll.positionCaret(lastContentStartPos[0]);
                });
            }
        });
    }



    @FXML
    private void onButtonCleardClick() {
        Platform.runLater(() -> {
            textSaveAreaAll.clear();
            textAreaLogs.clear();
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
        // Оставляем твой старый вариант для быстрой ошибки, но в onButtonReadClick
        // теперь используем прямой appendText для точного формата ошибки.
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
