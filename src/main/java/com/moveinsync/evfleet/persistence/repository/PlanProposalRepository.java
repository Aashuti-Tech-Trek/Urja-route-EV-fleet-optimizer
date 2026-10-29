package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.PlanProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanProposalRepository extends JpaRepository<PlanProposalEntity, String> {
    List<PlanProposalEntity> findByTenantId(String tenantId);
}
