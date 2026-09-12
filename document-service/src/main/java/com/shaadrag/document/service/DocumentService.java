package com.shaadrag.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.shaadrag.document.dto.response.*;
import com.shaadrag.document.exception.DocumentLimitExceededException;
import com.shaadrag.document.exception.DocumentNotFoundException;
import com.shaadrag.document.exception.InvalidDocumentException;
import com.shaadrag.document.model.Document;
import com.shaadrag.document.model.DocumentStatus;
import com.shaadrag.document.model.DocumentType;
import com.shaadrag.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;

    @Value("${app.document.application-url}")
    private String applicationUrl;


    // =========================================================
    // UPLOAD DOCUMENTS
    // =========================================================

    @Transactional
    public List<DocumentResponse> uploadDocuments(
            MultipartFile[] files,
            Authentication authentication
    ) {

        String userId = authentication.getName();

        String role = getRole(authentication);

        int maximumDocuments = getMaximumDocuments(role);

        validateFiles(files);

        long existingDocuments =
                documentRepository.countByUserId(userId);

        long totalDocuments =
                existingDocuments + files.length;

        if (totalDocuments > maximumDocuments) {

            throw new DocumentLimitExceededException(
                    "Document limit exceeded. " +
                    role +
                    " users can have maximum " +
                    maximumDocuments +
                    " documents."
            );
        }

        // Validate all files before uploading any file
        List<DocumentType> documentTypes = new ArrayList<>();

        for (MultipartFile file : files) {

            documentTypes.add(
                    getDocumentType(
                            file.getOriginalFilename()
                    )
            );
        }

        List<DocumentResponse> responses = new ArrayList<>();

        List<String> uploadedRemoteNames = new ArrayList<>();

        try {

            for (int i = 0; i < files.length; i++) {

                MultipartFile file = files[i];

                DocumentType documentType =
                        documentTypes.get(i);

                String originalName =
                        file.getOriginalFilename();

                String remoteName =
                        UUID.randomUUID()
                                + getExtension(originalName);

                // Upload file to S3
                String documentHostedUrl =
                        s3Service.uploadFile(
                                file,
                                remoteName
                        );

                uploadedRemoteNames.add(remoteName);

                Document document = new Document();

                document.setUserId(userId);
                document.setOriginalName(originalName);
                document.setRemoteName(remoteName);

                // Internal S3 URL
                document.setDocumentHostedUrl(
                        documentHostedUrl
                );

                document.setDocumentType(documentType);

                document.setDocumentStatus(
                        DocumentStatus.UPLOADED
                );

                document.setMimeType(
                        file.getContentType()
                );

                document.setFileSize(
                        file.getSize()
                );

                document.setErrorMessage(null);

                /*
                 * Save first so Hibernate generates
                 * documentId.
                 */
                document =
                        documentRepository.save(document);

                /*
                 * User-facing URL.
                 */
                document.setApplicationUrl(
                        applicationUrl
                                + "/"
                                + document.getDocumentId()
                );

                document =
                        documentRepository.save(document);


                /*
                 * IMPORTANT:
                 * Do not expose documentHostedUrl.
                 */
                responses.add(
                        new DocumentResponse(
                                document.getDocumentId(),
                                document.getOriginalName(),
                                document.getApplicationUrl(),
                                document.getDocumentType(),
                                document.getDocumentStatus(),
                                document.getFileSize()
                        )
                );
            }

            return responses;

        } catch (Exception exception) {

            // Delete already uploaded S3 objects
            for (String remoteName : uploadedRemoteNames) {

                try {

                    s3Service.deleteFile(remoteName);

                } catch (Exception ignored) {
                    // Add logging later
                }
            }

            throw new RuntimeException(
                    "Failed to upload documents",
                    exception
            );
        }
    }


    // =========================================================
    // DELETE ONE DOCUMENT
    // =========================================================

    @Transactional
    public void deleteDocument(
            String documentId,
            Authentication authentication
    ) {

        String userId = authentication.getName();

        Document document =
                documentRepository
                        .findByDocumentIdAndUserId(
                                documentId,
                                userId
                        )
                        .orElseThrow(
                                () -> new DocumentNotFoundException(
                                        "Document not found"
                                )
                        );

        s3Service.deleteFile(
                document.getRemoteName()
        );

        documentRepository.delete(document);
    }


    // =========================================================
    // DELETE ALL DOCUMENTS
    // =========================================================

    @Transactional
    public DeleteAllResponse deleteAllDocuments(
            Authentication authentication
    ) {

        String userId = authentication.getName();

        List<Document> documents =
                documentRepository.findAllByUserId(userId);

        for (Document document : documents) {

            s3Service.deleteFile(
                    document.getRemoteName()
            );
        }

        documentRepository.deleteAll(documents);

        return new DeleteAllResponse(
                documents.size()
        );
    }


    // =========================================================
    // GET DOCUMENT FOR VIEWING
    // =========================================================

    public Document getDocumentForViewing(
            String documentId,
            Authentication authentication
    ) {

        String userId = authentication.getName();

        return documentRepository
                .findByDocumentIdAndUserId(
                        documentId,
                        userId
                )
                .orElseThrow(
                        () -> new DocumentNotFoundException(
                                "Document not found"
                        )
                );
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateFiles(
            MultipartFile[] files
    ) {

        if (files == null || files.length == 0) {

            throw new InvalidDocumentException(
                    "At least one file is required"
            );
        }

        for (MultipartFile file : files) {

            if (file == null || file.isEmpty()) {

                throw new InvalidDocumentException(
                        "File cannot be empty"
                );
            }

            if (file.getSize() > MAX_FILE_SIZE) {

                throw new InvalidDocumentException(
                        "Each file must be less than or equal to 5 MB"
                );
            }

            String originalName =
                    file.getOriginalFilename();

            if (originalName == null ||
                    originalName.isBlank()) {

                throw new InvalidDocumentException(
                        "Invalid file name"
                );
            }
        }
    }


    private DocumentType getDocumentType(
            String fileName
    ) {

        String extension =
                getExtension(fileName)
                        .toLowerCase();

        return switch (extension) {

            case ".pdf" -> DocumentType.PDF;

            case ".docx" -> DocumentType.DOCX;

            case ".txt" -> DocumentType.TXT;

            default ->
                    throw new InvalidDocumentException(
                            "Only PDF, DOCX and TXT files are supported"
                    );
        };
    }


    private String getExtension(
            String fileName
    ) {

        int index = fileName.lastIndexOf('.');

        if (index == -1) {

            throw new InvalidDocumentException(
                    "File extension is required"
            );
        }

        return fileName.substring(index);
    }


    // =========================================================
    // ROLE
    // =========================================================

    private String getRole(
            Authentication authentication
    ) {

        return authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority ->
                        authority.startsWith("ROLE_")
                                ? authority.substring(5)
                                : authority
                )
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "User role not found"
                        )
                );
    }


    private int getMaximumDocuments(
            String role
    ) {

        return switch (role) {

            case "MEMBER" -> 3;

            case "ADMIN" -> 5;

            default ->
                    throw new IllegalStateException(
                            "Unsupported role: " + role
                    );
        };
    }
}