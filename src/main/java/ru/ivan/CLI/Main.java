package ru.ivan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String path = "C:\\Projects\\FX\\TestFXv1\\src\\main";
//        String path = "C:\\Projects\\RAL\\ral_v1\\src\\main\\java\\ru\\ivan";
//        String path = "C:\\Projects\\FX\\TestFXv1\\src";
//        String path = "C:\\Projects\\RZHD\\RZHD_FX_My_3v\\src\\main";
//        String txtName = "RZHD_FX_My_3v.txt";
        MySearchFiles mySearchFiles = new MySearchFiles();
        List<String> list = mySearchFiles.searchMyFiles(path);

        MyParse myParse = new MyParse();

        StringBuilder sb = new StringBuilder();
        for (String f : list) {
            String fileParse = myParse.parseFile(f);
            sb.append("\n\n\n").append(fileParse);
        }
        Path outputFile = Paths.get("TestFXv1");
        Files.writeString(outputFile.toAbsolutePath(), sb.toString());
    }
}
