package com.rightpath.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

	String uploadFile(String containerName, String fileName, MultipartFile file);

	byte[] downloadFile(String containerName, String fileName);

	String downloadFileAsText(String containerName, String fileName);

	boolean fileExists(String containerName, String fileName);

	void uploadStringContent(String prefix, String fileName, String content);

	/**
	 * A time-limited, directly-fetchable URL for something already stored.
	 *
	 * <p>Takes the reference {@link #uploadFile} returned, so callers never have
	 * to know how a stored location is spelled. That spelling is why this exists:
	 * uploads return an {@code s3://} URI, a browser cannot open that scheme, and
	 * a reviewer clicking an interview recording got a blank tab.</p>
	 *
	 * <p>A signed URL rather than bytes through the application: a screen
	 * recording of a full interview runs to hundreds of megabytes, and a video
	 * player needs range requests to seek at all.</p>
	 *
	 * @param storedReference the value returned by {@code uploadFile}
	 * @param ttl             how long the URL stays valid
	 */
	String presignedUrl(String storedReference, java.time.Duration ttl);
	 
}
