package com.rightpath.service;

public interface SpeechToTextService {

	String transcribeAudio(byte[] audioBytes, String filename);

}
