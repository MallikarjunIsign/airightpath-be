package com.rightpath.service.impl;

import java.io.IOException;
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

@Service
public class S3StorageService implements StorageService {

    private final String bucketName;
    private final Region region;
    private final S3Client s3Client;

    public S3StorageService(
            @Value("${aws.s3.bucket-name}") String bucketName,
            @Value("${aws.s3.region}") String regionName,
            @Value("${aws.s3.access-key}") String accessKey,
            @Value("${aws.s3.secret-key}") String secretKey) {
        this.bucketName = bucketName;
        this.region = Region.of(regionName);
        this.s3Client = S3Client.builder()
                .region(this.region)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Override
    public String uploadFile(String prefix, String fileName, MultipartFile file) {
        validateInputs(prefix, fileName, file);
        try {
            String key = prefix + "/" + fileName;
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
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
        } catch (S3Exception e) {
            if (e instanceof NoSuchKeyException) {
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
}
