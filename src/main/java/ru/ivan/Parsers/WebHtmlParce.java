package ru.ivan.Parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class WebHtmlParce {

    public Document parceWebHtml(String path) {
        Document doc = null;
        try {
            doc = Jsoup.connect(path).get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert doc != null;
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
