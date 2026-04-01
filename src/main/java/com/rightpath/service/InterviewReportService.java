package com.rightpath.service;

public interface InterviewReportService {
	String generateAndUpload(String sessionId, String transcript, String summary);
}
