package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.VehicleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<VehicleEntity, String> {
    List<VehicleEntity> findByTenantId(String tenantId);
}
