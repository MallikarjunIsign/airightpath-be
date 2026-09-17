package com.rightpath.service.impl;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.exceptions.StorageException;
import com.rightpath.service.StorageService;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
public class S3StorageService implements StorageService {

    private final String bucketName;
    private final Region region;
    private final S3Client s3Client;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner presigner;

    public S3StorageService(
            @Value("${aws.s3.bucket-name}") String bucketName,
            @Value("${aws.s3.region}") String regionName,
            @Value("${aws.s3.access-key}") String accessKey,
            @Value("${aws.s3.secret-key}") String secretKey) {
        this.bucketName = bucketName;
        this.region = Region.of(regionName);
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        this.s3Client = S3Client.builder()
                .region(this.region)
                .credentialsProvider(credentials)
                .build();
        this.presigner = software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                .region(this.region)
                .credentialsProvider(credentials)
                .build();
    }

    @Override
    public String presignedUrl(String storedReference, java.time.Duration ttl) {
        String key = keyOf(storedReference);
        // The response headers are overridden on the request rather than read
        // from the object. Recordings already in the bucket were stored before
        // the content type above was set, so they are still octet-stream at
        // rest — signing them with the right type and an inline disposition
        // makes those play too, with nothing re-uploaded.
        var getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .responseContentType(contentTypeOf(null, key))
                .responseContentDisposition("inline")
                .build();
        var presignRequest = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public String presignedDownloadUrl(String storedReference, String downloadName,
            java.time.Duration ttl) {
        String key = keyOf(storedReference);
        String safeName = (downloadName == null || downloadName.isBlank())
                ? key.substring(key.lastIndexOf('/') + 1)
                // Quoted and stripped of quotes/newlines: the value goes into a
                // response header, and a filename carrying either would let a
                // caller shape headers the server sends.
                : downloadName.replaceAll("[\"\r\n]", "");

        var getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .responseContentType(contentTypeOf(null, key))
                .responseContentDisposition("attachment; filename=\"" + safeName + "\"")
                .build();
        var presignRequest = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * The content type to store or serve an object as.
     *
     * <p>Prefers what the uploader declared; falls back to the file extension,
     * which is all that is known when signing a link to something stored
     * earlier. Only the types this application actually stores are listed —
     * anything else stays a generic binary, which downloads, and that is the
     * right outcome for a file nothing here can play.</p>
     */
    private String contentTypeOf(String declared, String key) {
        if (declared != null && !declared.isBlank() && !"application/octet-stream".equals(declared)) {
            return declared;
        }
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".ogg")) return "video/ogg";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    /**
     * The object key inside a reference this service produced.
     *
     * <p>Uploads return {@code s3://bucket/key}; a bare key is accepted too, so
     * a reference stored before that format settled still resolves.</p>
     */
    private String keyOf(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            throw new StorageException("No stored reference to sign", null);
        }
        String reference = storedReference.trim();
        if (!reference.startsWith("s3://")) {
            return reference;
        }
        String withoutScheme = reference.substring("s3://".length());
        int slash = withoutScheme.indexOf('/');
        if (slash < 0 || slash == withoutScheme.length() - 1) {
            throw new StorageException("Stored reference names no object: " + storedReference, null);
        }
        return withoutScheme.substring(slash + 1);
    }

    @Override
    public String uploadFile(String prefix, String fileName, MultipartFile file) {
        validateInputs(prefix, fileName, file);
        try {
            String key = prefix + "/" + fileName;
            // Stored with its type. Without this S3 serves every object as
            // application/octet-stream, and a browser given one downloads it
            // rather than playing it — which is what interview recordings did.
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentTypeOf(file.getContentType(), key))
                    .build();
            s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return String.format("s3://%s/%s", bucketName, key);
        } catch (IOException | S3Exception e) {
            throw new StorageException("Failed to upload file to S3 [bucket=" + bucketName + ", key=" + prefix + "/" + fileName + "]", e);
        }
    }

    @Override
    public byte[] downloadFile(String prefix, String fileName) {
        validateInputs(prefix, fileName);
        try {
            String key = prefix + "/" + fileName;
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            return s3Client.getObject(getReq, software.amazon.awssdk.core.sync.ResponseTransformer.toBytes()).asByteArray();
        }  catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() != null 
                    ? e.awsErrorDetails().errorCode() 
                    : "UNKNOWN";

                log.error("S3 ERROR: code={}, message={}", errorCode, e.getMessage(), e);

                if ("NoSuchKey".equals(errorCode)) {
                    throw new StorageException("File not found in S3 [bucket=" + bucketName + ", key=" + prefix + "/" + fileName + "]", e);
                }

                throw new StorageException("Failed to download file from S3 [bucket=" + bucketName + ", key=" + prefix + "/" + fileName + "]", e);
            }
    }

    @Override
    public String downloadFileAsText(String prefix, String fileName) {
        return new String(downloadFile(prefix, fileName), StandardCharsets.UTF_8);
    }

    @Override
    public boolean fileExists(String prefix, String fileName) {
        validateInputs(prefix, fileName);
        try {
            String key = prefix + "/" + fileName;
            HeadObjectRequest headReq = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headReq);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.awsErrorDetails() != null && "Not Found".equalsIgnoreCase(e.awsErrorDetails().errorMessage())) {
                return false;
            }
            throw new StorageException("Failed to check file existence in S3 [bucket=" + bucketName + ", key=" + prefix + "/" + fileName + "]", e);
        }
    }

    private void validateInputs(String containerName, String fileName) {
        if (containerName == null || containerName.isBlank()) {
            throw new IllegalArgumentException("Container name must not be empty");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be empty");
        }
    }

    private void validateInputs(String containerName, String fileName, MultipartFile file) {
        validateInputs(containerName, fileName);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }
    }
    
    @Override
    public void uploadStringContent(String prefix, String fileName, String content) {
        validateInputs(prefix, fileName);
        try {
            String key = prefix + "/" + fileName;
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.putObject(putReq, RequestBody.fromString(content));
        } catch (S3Exception e) {
            throw new StorageException("Failed to upload string content to S3 [bucket=" + bucketName + ", key=" + prefix + "/" + fileName + "]", e);
        }
    }
}
