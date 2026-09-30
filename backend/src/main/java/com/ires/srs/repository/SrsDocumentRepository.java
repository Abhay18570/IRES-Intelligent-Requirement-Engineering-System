package com.ires.srs.repository;

import com.ires.srs.entity.SrsDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SrsDocumentRepository extends JpaRepository<SrsDocument, UUID> {
}