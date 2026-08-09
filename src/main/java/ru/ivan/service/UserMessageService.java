package ru.ivan.service;

import ru.ivan.Parsers.MyParse;
import ru.ivan.utils.MySearchFiles;

import java.util.List;

public class UserMessageService  {
    public String getResultString(String path) throws Exception {
        MySearchFiles mySearchFiles = new MySearchFiles();
        List<String> list = mySearchFiles.processPath(path);
//        System.out.println(list.size());
//        list.forEach(System.out::println);
        MyParse myParse = new MyParse();

        StringBuilder sb = new StringBuilder();
        if (list.size() > 0) {
            for (String f : list) {
                String fileParse = myParse.parseFile(f);
                System.out.println(f);
                sb.append("\n\n*******************\nфайл:\t").append(f).append("\n*******************\n").append(fileParse);
            }
            return sb.toString();
        } else {
            String fileParse = myParse.parseFile(path);
            sb.append("\n\n*******************\nфайл:\t").append(path).append("\n*******************\n").append(fileParse);
            return sb.toString();
        }
    }
}
