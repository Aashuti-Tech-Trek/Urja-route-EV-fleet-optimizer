package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.ChargingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChargingSessionRepository extends JpaRepository<ChargingSessionEntity, String> {
    List<ChargingSessionEntity> findByPlanProposalIdOrderByStartTimeAsc(String planProposalId);
    List<ChargingSessionEntity> findByPlanProposalIdAndHubIdOrderByStartTimeAsc(String planProposalId, String hubId);
    List<ChargingSessionEntity> findByHubId(String hubId);
}
