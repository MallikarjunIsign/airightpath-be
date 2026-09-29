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

	/**
	 * The same link, but asking the browser to save the file rather than show it.
	 *
	 * <p>Separate from {@link #presignedUrl} because the disposition is signed
	 * into the URL — one link cannot both play inline and download, so the caller
	 * has to say which the person asked for.</p>
	 *
	 * @param downloadName the filename to save as
	 */
	String presignedDownloadUrl(String storedReference, String downloadName, java.time.Duration ttl);

	/**
	 * The size in bytes of a stored object, or empty if it is not there.
	 *
	 * <p>Signing a URL does not touch the object — a presigned link to a key
	 * that was never written looks exactly like one to a real recording, and the
	 * reviewer finds out only when the player refuses it with "format not
	 * supported". Checking first turns that into a straight answer about whether
	 * the recording was saved.</p>
	 *
	 * <p>A present-but-empty object is its own failure and the caller needs to
	 * tell it apart from a missing one, which is why this returns the size
	 * rather than a boolean.</p>
	 */
	java.util.Optional<Long> objectSize(String storedReference);
	 
}
