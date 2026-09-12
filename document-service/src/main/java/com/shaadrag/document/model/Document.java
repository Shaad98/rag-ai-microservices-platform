package com.shaadrag.document.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor 

@Entity 
@Table(name = "documents")
public class Document {

    @Id 
    @GeneratedValue 
    @UuidGenerator 
    private String documentId;

    private String userId;

    private String originalName;

    private String remoteName;

    private String documentHostedUrl;

    private String applicationUrl;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    private DocumentStatus documentStatus;

    private String mimeType;

    private Long fileSize;

    private String errorMessage;


    private LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        uploadedAt = LocalDateTime.now();

    }

}
