package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.ChargerHubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChargerHubRepository extends JpaRepository<ChargerHubEntity, String> {
    List<ChargerHubEntity> findByTenantId(String tenantId);
}
