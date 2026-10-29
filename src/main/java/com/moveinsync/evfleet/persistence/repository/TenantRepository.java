package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, String> {
}
