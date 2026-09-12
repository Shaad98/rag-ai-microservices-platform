package com.shaadrag.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaadrag.document.model.Document;

import java.util.*;

public interface DocumentRepository extends JpaRepository<Document,String>{
    long countByUserId(String userId);

    Optional<Document> findByDocumentIdAndUserId(
            String documentId,
            String userId
    );

    List<Document> findAllByUserId(String userId);
}
