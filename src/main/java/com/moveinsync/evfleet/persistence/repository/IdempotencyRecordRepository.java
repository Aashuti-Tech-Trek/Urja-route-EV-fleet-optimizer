package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndTenantId(String idempotencyKey, String tenantId);
}
