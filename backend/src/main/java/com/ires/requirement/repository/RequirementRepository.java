package com.ires.requirement.repository;

import com.ires.requirement.entity.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;
import java.util.List;
import java.util.Collection;
import com.ires.requirement.entity.RequirementStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementRepository extends JpaRepository<Requirement, UUID>, JpaSpecificationExecutor<Requirement> {
        List<Requirement> findByProjectId(UUID projectId);

    long countByStatus(RequirementStatus status);

    long countByAssignedToIdAndStatus(UUID userId, RequirementStatus status);
    long countByAssignedToId(UUID userId);
    long countByAssignedToIdAndStatusNot(UUID userId, RequirementStatus status);
    long countByProjectId(UUID projectId);

    @Query("select count(distinct r) from Requirement r left join r.project.members m " +
            "where (r.project.client.id = :userId or m.user.id = :userId) and (:status is null or r.status = :status)")
    long countAccessibleToByStatus(@Param("userId") UUID userId, @Param("status") RequirementStatus status);

    @Query("select count(distinct r) from Requirement r left join r.project.members m " +
            "where (r.project.client.id = :userId or m.user.id = :userId) and r.status in :statuses")
    long countAccessibleToByStatusIn(@Param("userId") UUID userId,
                                     @Param("statuses") Collection<RequirementStatus> statuses);
}
