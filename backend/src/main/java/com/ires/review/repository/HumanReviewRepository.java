package com.ires.review.repository;

import com.ires.review.entity.HumanReview;
import com.ires.review.entity.ReviewArtifactType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HumanReviewRepository extends JpaRepository<HumanReview, UUID> {

    List<HumanReview> findByArtifactTypeAndArtifactIdOrderByReviewedAtDesc(
            ReviewArtifactType artifactType,
            UUID artifactId
    );
}