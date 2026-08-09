package ru.ivan.Parsers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MyParse {
    // Пробрасываем исключение дальше, чтобы контроллер мог его красиво обработать
        public String parseFile(String path) throws Exception  {
        StringBuilder builder = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            lines.forEach(line -> builder.append(line).append("\n"));
        } catch (Exception e) {
// НЕ делаем printStackTrace! Пусть контроллер решит, что с этим делать.
            throw new RuntimeException("Невозможно прочитать файл: " + path, e);
        }
        return builder.toString();
    }
}