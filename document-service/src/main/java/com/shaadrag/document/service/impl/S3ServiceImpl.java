package com.shaadrag.document.service.impl;

import com.shaadrag.document.service.S3Service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;


    @Override
    public String uploadFile(
            MultipartFile file,
            String remoteName
    ) {

        try {

            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(remoteName)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            return s3Client.utilities()
                    .getUrl(builder ->
                            builder
                                    .bucket(bucketName)
                                    .key(remoteName)
                    )
                    .toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to upload file to S3",
                    e
            );
        }
    }


    @Override
    public ResponseInputStream<GetObjectResponse> getFile(
            String remoteName
    ) {

        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(remoteName)
                        .build();

        return s3Client.getObject(request);
    }


    @Override
    public void deleteFile(
            String remoteName
    ) {

        DeleteObjectRequest request =
                DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(remoteName)
                        .build();

        s3Client.deleteObject(request);
    }
}