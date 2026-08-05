package ru.ivan;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MyParse {
    String doc = null;
    public String parcefile(String path) {
        String doc = parseFile(path);
        return doc;
    }

    public String parseFile(String path) {
        StringBuilder builder = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            lines.forEach(line -> builder.append(line + "\n"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.toString();
    }
}