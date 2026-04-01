package com.rightpath.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

	String uploadFile(String containerName, String fileName, MultipartFile file);

	byte[] downloadFile(String containerName, String fileName);

	String downloadFileAsText(String containerName, String fileName);

	boolean fileExists(String containerName, String fileName);

	 
}
