//package com.rightpath.dto;
//
//
//import lombok.*;
//
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//@Builder
//public class InterviewResponse {
//
//    private String status;        // QUESTION | COMPLETED
//    private String question;      // next question text
//    private String answerMode;    // VOICE | TEXT
//    private Integer timeLimit;    // seconds
//
//    // ---------- Factory methods ----------
//
//    public static InterviewResponse question(
//            String question,
//            String answerMode,
//            int timeLimit) {
//
//        return InterviewResponse.builder()
//                .status("QUESTION")
//                .question(question)
//                .answerMode(answerMode)
//                .timeLimit(timeLimit)
//                .build();
//    }
//
//    public static InterviewResponse completed() {
//        return InterviewResponse.builder()
//                .status("COMPLETED")
//                .build();
//    }
//
//	public CharSequence toJson() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//}
