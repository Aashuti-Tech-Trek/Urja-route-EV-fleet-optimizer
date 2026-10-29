package com.moveinsync.evfleet.persistence.repository;

import com.moveinsync.evfleet.persistence.entity.TripAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripAssignmentRepository extends JpaRepository<TripAssignmentEntity, String> {
    List<TripAssignmentEntity> findByPlanProposalIdOrderBySequenceOrderAsc(String planProposalId);
}
