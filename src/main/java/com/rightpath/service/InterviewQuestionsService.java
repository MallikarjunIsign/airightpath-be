package com.rightpath.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


public interface InterviewQuestionsService {

	void uploadInterviewQuestions(String jobPrefix, MultipartFile file);

	String fetchInterviewQuestions(String jobPrefix);

}
