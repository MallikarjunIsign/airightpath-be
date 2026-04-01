package com.rightpath.service;

import java.util.List;

import com.rightpath.dto.CodingQuestion;
import com.rightpath.dto.Question;

public interface OpenAiService {

	List<Question> generateQuestions(String jobPrefix);

	List<CodingQuestion> generateCodingQuestions(String jobPrefix);

	String ask(String conversationHistory);

}
