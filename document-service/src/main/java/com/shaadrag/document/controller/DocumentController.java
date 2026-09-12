package com.shaadrag.document.controller;

import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.shaadrag.document.dto.response.*;

import com.shaadrag.document.model.Document;
import com.shaadrag.document.service.DocumentService;
import com.shaadrag.document.service.S3Service;

import lombok.RequiredArgsConstructor;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final S3Service s3Service;


    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<List<DocumentResponse>> uploadDocuments(
            @RequestPart("files") MultipartFile[] files,
            Authentication authentication
    ) {

        return ResponseEntity
                .status(201)
                .body(
                        documentService.uploadDocuments(
                                files,
                                authentication
                        )
                );
    }


    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<InputStreamResource> viewDocument(
            @PathVariable String documentId,
            Authentication authentication
    ) {

        Document document =
                documentService.getDocumentForViewing(
                        documentId,
                        authentication
                );

        ResponseInputStream<GetObjectResponse> s3Object =
                s3Service.getFile(
                        document.getRemoteName()
                );

        GetObjectResponse s3Response =
                s3Object.response();

        MediaType mediaType;

        try {

            mediaType =
                    MediaType.parseMediaType(
                            document.getMimeType()
                    );

        } catch (Exception exception) {

            mediaType =
                    MediaType.APPLICATION_OCTET_STREAM;
        }

        InputStreamResource resource =
                new InputStreamResource(s3Object);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(
                        s3Response.contentLength()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition
                                .inline()
                                .filename(
                                        document.getOriginalName()
                                )
                                .build()
                                .toString()
                )
                .body(resource);
    }


    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String documentId,
            Authentication authentication
    ) {

        documentService.deleteDocument(
                documentId,
                authentication
        );

        return ResponseEntity
                .noContent()
                .build();
    }


    @DeleteMapping
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<DeleteAllResponse> deleteAllDocuments(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                documentService.deleteAllDocuments(
                        authentication
                )
        );
    }
}