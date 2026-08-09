package ru.ivan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class MySearchFiles {
    private static final Logger LOG = LoggerFactory.getLogger(MySearchFiles.class);

    public List<String> processPath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            LOG.warn("Передан пустой или null путь");
            return Collections.emptyList();
        }

        Path path;
        try {
            path = Paths.get(pathStr).toAbsolutePath();
        } catch (InvalidPathException e) {
            LOG.warn("Некорректный путь: {}", pathStr, e);
            return Collections.emptyList();
        }

        if (!Files.exists(path)) {
            LOG.warn("Путь не существует: {}", path);
            return Collections.emptyList();
        }

        boolean isSymlink = Files.isSymbolicLink(path);
        boolean isDir = Files.isDirectory(path);          // БЕЗ FOLLOW_LINKS
        boolean isFile = Files.isRegularFile(path);      // БЕЗ FOLLOW_LINKS

        if (isDir) {
            LOG.info("Путь ведёт на директорию: {}, запускаем рекурсию", path);
            List<String> result = new ArrayList<>();
            Set<String> visitedDirs = new HashSet<>();
            recursiveListFiles(path, visitedDirs, result);
            return result;
        } else if (isFile) {
            LOG.info("Путь ведёт на файл: {}, возвращаем один файл", path);
            return Collections.singletonList(path.toAbsolutePath().toString());
        } else {
            if (isSymlink) {
                LOG.warn("Указан симлинк на неизвестный объект: {}", path);
            } else {
                LOG.warn("Путь указывает на объект, который не является ни файлом, ни директорией: {}", path);
            }
            return Collections.emptyList();
        }
    }

    private void recursiveListFiles(Path dir, Set<String> visitedDirs, List<String> result) {
        String absPath = dir.toAbsolutePath().toString();
        if (!visitedDirs.add(absPath)) {
            return; // защита от циклов
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                // БЕЗ FOLLOW_LINKS: симлинки на папки игнорируются
                if (Files.isDirectory(entry)) {
                    recursiveListFiles(entry, visitedDirs, result);
                } else if (Files.isRegularFile(entry)) {
                    result.add(entry.toAbsolutePath().toString());
                }
            }
        } catch (IOException e) {
            LOG.error("Не удалось прочитать директорию: {}", dir, e);
        }
    }
}
