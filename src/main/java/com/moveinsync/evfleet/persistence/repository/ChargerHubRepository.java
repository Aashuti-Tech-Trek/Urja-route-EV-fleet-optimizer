package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargerHubRepository extends JpaRepository<ChargerHubEntity, String> {
    List<ChargerHubEntity> findByTenantId(String tenantId);
    Page<ChargerHubEntity> findByTenantId(String tenantId, Pageable pageable);
    Optional<ChargerHubEntity> findByIdAndTenantId(String id, String tenantId);
    boolean existsByIdAndTenantId(String id, String tenantId);
    void deleteByIdAndTenantId(String id, String tenantId);
}
