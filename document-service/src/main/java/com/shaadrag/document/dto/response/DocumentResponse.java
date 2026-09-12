package com.shaadrag.document.dto.response;

import com.shaadrag.document.model.DocumentStatus;
import com.shaadrag.document.model.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentResponse {

    private String documentId;

    private String originalName;

    private String applicationUrl;

    private DocumentType documentType;

    private DocumentStatus documentStatus;

    private Long fileSize;
}