//package com.rightpath.entity;
//
//import lombok.Data;
//
//@Data
//public class ParsedAiResponse {
//
//    private String result;
//    private String nextQuestion;
//    private boolean completed;
//
//    public static ParsedAiResponse parse(String aiText) {
//        ParsedAiResponse res = new ParsedAiResponse();
//
//        if (aiText.toLowerCase().contains("interview completed")) {
//            res.completed = true;
//            return res;
//        }
//
//        for (String line : aiText.split("\n")) {
//            if (line.startsWith("[RESULT]")) {
//                res.result = line.replace("[RESULT]", "").trim();
//            }
//            if (line.startsWith("[NEXT]")) {
//                res.nextQuestion = line.replace("[NEXT]", "").trim();
//            }
//        }
//        return res;
//    }
//}
