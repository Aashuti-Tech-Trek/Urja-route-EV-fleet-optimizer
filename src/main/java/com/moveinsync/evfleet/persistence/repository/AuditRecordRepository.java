package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.AuditRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, String> {
    List<AuditRecordEntity> findByTenantIdOrderByTimestampDesc(String tenantId);
}
