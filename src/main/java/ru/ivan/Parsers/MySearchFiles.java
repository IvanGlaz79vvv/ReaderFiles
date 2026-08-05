package ru.ivan;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MySearchFiles {
    List<String> result = new ArrayList<>();

    /**
     * Ищет все файлы в директории рекурсивно.
     * Возвращает список абсолютных путей.
     */
    public List<String> searchMyFiles(String path) {
        File file = new File(path);
        if (file.exists() && file.isDirectory()) {
//            List<String> result = new ArrayList<>();
            Set<String> visitedDirs = new HashSet<>();
            recursiveListFiles(file, visitedDirs, result);
            return result;
        }
        return List.of();
    }

    private void recursiveListFiles(File dir, Set<String> visitedDirs, List<String> result) {
        String absPath = dir.getAbsolutePath();

        // Защита от циклов (symlink)
        if (!visitedDirs.add(absPath)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            // Можно логировать через SLF4J вместо System.out
            System.out.println("Не удалось прочитать директорию: " + absPath);
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                recursiveListFiles(file, visitedDirs, result);
            } else {
                result.add(file.getAbsolutePath()); // полный путь
            }
        }
    }
}
