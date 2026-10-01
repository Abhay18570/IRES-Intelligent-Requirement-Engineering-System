package com.ires.requirement.criteria.entity;

import com.ires.requirement.entity.Requirement;
import com.ires.story.entity.UserStory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acceptance_criteria")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcceptanceCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_story_id")
    private UserStory userStory;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 10000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "criteria_type", nullable = false, length = 20)
    private CriteriaType criteriaType = CriteriaType.FUNCTIONAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CriteriaStatus status = CriteriaStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AcceptanceCriteria(Requirement requirement, UserStory userStory, String title, String description,
                              CriteriaType criteriaType, CriteriaStatus status) {
        this.requirement = requirement;
        this.userStory = userStory;
        this.title = title;
        this.description = description;
        this.criteriaType = criteriaType;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
