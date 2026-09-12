package com.shaadrag.document.service;

import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public interface S3Service {

    String uploadFile(
            MultipartFile file,
            String remoteName
    );

    ResponseInputStream<GetObjectResponse> getFile(
            String remoteName
    );

    void deleteFile(
            String remoteName
    );
}