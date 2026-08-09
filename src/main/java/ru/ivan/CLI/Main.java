package ru.ivan.CLI;

import ru.ivan.service.UserMessageService;


public class Main {
    public static void main(String[] args) throws Exception {
//        String path = "C:\\Projects\\FX\\ReaderFiles\\src\\main\\java\\ru\\ivan";
//        String path = "C:\\Projects\\RAL\\ral_v1\\src\\main\\java\\ru\\ivan";
//        String path = "C:\\Projects\\FX\\TestFXv1\\src";
//        String path = "C:\\Projects\\RZHD\\RZHD_FX_My_3v\\src\\main";
//        String txtName = "RZHD_FX_My_3v.txt";

//        String path = "C:\\Projects\\FX\\ReaderFiles\\src\\main\\java\\ru\\ivan";
        String path = "C:\\Projects\\FX\\TestFXv1\\data";
//        String path = "C:\\Projects\\FX\\TestFXv1\\data\\cardsOfColors.csv";
//        String path = "C:\\Projects\\FX\\TestFXv1\\data\\cardsOfColors.xlsx";
        UserMessageService userMessageService = new UserMessageService();
        String str =  userMessageService.getResultString(path);
        System.out.println(str);



//        String doc = new LocalHtmlParce().parseFile("C:\\Projects\\FX\\TestFXv1\\data\\cardsOfColors.csv");
//        String doc = new LocalHtmlParce().parseFile("C:\\Projects\\FX\\TestFXv1\\data");
//        System.out.println(doc);


//        Path outputFile = Paths.get("TestFXv1");
//        Files.writeString(outputFile.toAbsolutePath(), sb.toString());
    }
}
