package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.PlanVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, String> {
    List<PlanVersionEntity> findByTenantIdOrderByVersionNumberDesc(String tenantId);

    @Query("SELECT MAX(pv.versionNumber) FROM PlanVersionEntity pv WHERE pv.tenantId = :tenantId")
    Long findMaxVersionByTenantId(@Param("tenantId") String tenantId);

    Optional<PlanVersionEntity> findByTenantIdAndStatus(String tenantId, String status);

    Optional<PlanVersionEntity> findByTenantIdAndVersionNumber(String tenantId, long versionNumber);
}
