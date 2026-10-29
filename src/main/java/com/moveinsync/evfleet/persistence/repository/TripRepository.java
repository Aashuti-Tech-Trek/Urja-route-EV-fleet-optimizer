package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<TripEntity, String> {
    List<TripEntity> findByTenantId(String tenantId);
}
