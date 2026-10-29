package com.moveinsync.evfleet.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.PlanProposalResponse;
import com.moveinsync.evfleet.api.dto.PlanVersionResponse;
import com.moveinsync.evfleet.domain.*;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.persistence.entity.*;
import com.moveinsync.evfleet.persistence.repository.*;
import com.moveinsync.evfleet.planner.*;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import com.moveinsync.evfleet.validator.PlanValidator;
import com.moveinsync.evfleet.validator.ValidationReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Service
public class FleetPersistenceService {

    private final TenantRepository tenantRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargerHubRepository chargerHubRepository;
    private final TripRepository tripRepository;
    private final PlanProposalRepository planProposalRepository;
    private final PlanVersionRepository planVersionRepository;
    private final TripAssignmentRepository tripAssignmentRepository;
    private final ChargingSessionRepository chargingSessionRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public FleetPersistenceService(TenantRepository tenantRepository,
                                   VehicleRepository vehicleRepository,
                                   ChargerHubRepository chargerHubRepository,
                                   TripRepository tripRepository,
                                   PlanProposalRepository planProposalRepository,
                                   PlanVersionRepository planVersionRepository,
                                   TripAssignmentRepository tripAssignmentRepository,
                                   ChargingSessionRepository chargingSessionRepository,
                                   AuditRecordRepository auditRecordRepository,
                                   IdempotencyRecordRepository idempotencyRecordRepository) {
        this.tenantRepository = tenantRepository;
        this.vehicleRepository = vehicleRepository;
        this.chargerHubRepository = chargerHubRepository;
        this.tripRepository = tripRepository;
        this.planProposalRepository = planProposalRepository;
        this.planVersionRepository = planVersionRepository;
        this.tripAssignmentRepository = tripAssignmentRepository;
        this.chargingSessionRepository = chargingSessionRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    private void ensureTenant(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            tenantRepository.save(new TenantEntity(tenantId, "Tenant " + tenantId));
        }
    }

    @Transactional
    public VehicleEntity registerVehicle(String tenantId, VehicleEntity vehicle) {
        ensureTenant(tenantId);
        vehicle.setTenantId(tenantId);
        VehicleEntity saved = vehicleRepository.save(vehicle);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, "REGISTER_VEHICLE", "VEHICLE", saved.getId(), "Vehicle registered"
        ));
        return saved;
    }

    public List<VehicleEntity> getVehicles(String tenantId) {
        return vehicleRepository.findByTenantId(tenantId);
    }

    @Transactional
    public TripEntity registerTrip(String tenantId, TripEntity trip) {
        ensureTenant(tenantId);
        trip.setTenantId(tenantId);
        TripEntity saved = tripRepository.save(trip);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, "REGISTER_TRIP", "TRIP", saved.getId(), "Trip registered"
        ));
        return saved;
    }

    public List<TripEntity> getTrips(String tenantId) {
        return tripRepository.findByTenantId(tenantId);
    }

    @Transactional
    public ChargerHubEntity registerHub(String tenantId, ChargerHubEntity hub) {
        ensureTenant(tenantId);
        hub.setTenantId(tenantId);
        return chargerHubRepository.save(hub);
    }

    public List<ChargerHubEntity> getHubs(String tenantId) {
        return chargerHubRepository.findByTenantId(tenantId);
    }

    @Transactional
    public PlanProposalResponse generatePlanProposal(String tenantId, Instant horizonStart, Instant horizonEnd,
                                                      List<String> vehicleIds, List<String> tripIds) {
        ensureTenant(tenantId);
        List<VehicleEntity> vEntities = vehicleRepository.findByTenantId(tenantId);
        if (vehicleIds != null && !vehicleIds.isEmpty()) {
            Set<String> set = new HashSet<>(vehicleIds);
            vEntities = vEntities.stream().filter(v -> set.contains(v.getId())).toList();
        }

        List<TripEntity> tEntities = tripRepository.findByTenantId(tenantId);
        if (tripIds != null && !tripIds.isEmpty()) {
            Set<String> set = new HashSet<>(tripIds);
            tEntities = tEntities.stream().filter(t -> set.contains(t.getId())).toList();
        }

        List<ChargerHubEntity> hEntities = chargerHubRepository.findByTenantId(tenantId);

        // Convert to Domain models
        List<Vehicle> vehicles = vEntities.stream().map(ve ->
            Vehicle.create(
                ve.getId(),
                tenantId,
                ve.getBatteryCapacityKwh(),
                ve.getEfficiencyKwhPerKm(),
                ve.getCurrentSoc(),
                new Location(12.9716, 77.5946),
                ve.getAvailableFrom()
            )
        ).toList();

        List<Trip> trips = tEntities.stream().map(te ->
            new Trip(
                te.getId(),
                tenantId,
                new Location(12.9716, 77.5946),
                new Location(12.9352, 77.6245),
                te.getDistanceKm(),
                te.getPickupWindowStart(),
                te.getDeadline()
            )
        ).toList();

        TariffSchedule schedule = new TariffSchedule(
            ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(8.50, "INR")
        );

        List<ChargerHub> hubs = hEntities.stream().map(he ->
            new ChargerHub(
                he.getId(),
                tenantId,
                new Location(12.9915, 77.5583),
                he.getTotalChargers(),
                50.0,
                schedule
            )
        ).toList();

        PlanningInput input = new PlanningInput("RUN-" + UUID.randomUUID(), vehicles, trips, hubs);
        GreedyFleetPlanner planner = GreedyFleetPlanner.createDefault();
        FleetPlanResult planResult = planner.plan(input);

        PlanValidator validator = new DefaultPlanValidator();
        ValidationReport report = validator.validate(planResult, input);

        String proposalId = UUID.randomUUID().toString();
        String reportJson = "";
        try {
            reportJson = objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            reportJson = report.toString();
        }

        double totalCost = planResult.getTotalChargingCost().doubleValue();
        double serviceRate = trips.isEmpty() ? 1.0 :
            (double) planResult.getEvAssignments().size() / trips.size();

        PlanProposalEntity proposalEntity = new PlanProposalEntity(
            proposalId, tenantId, horizonStart, horizonEnd,
            totalCost, serviceRate, report.isValid(), reportJson
        );
        planProposalRepository.save(proposalEntity);

        // Save Trip Assignments
        List<PlanProposalResponse.TripAssignmentDto> assignmentDtos = new ArrayList<>();
        int seq = 0;
        for (TripAssignment ta : planResult.getTripAssignments()) {
            String vId = ta.isEvAssignment() ? ta.getAssignedVehicle().getVehicleId() : "BACKUP";
            Trip t = ta.getTrip();
            TripAssignmentEntity entity = new TripAssignmentEntity(
                UUID.randomUUID().toString(),
                proposalId,
                vId,
                t.getTripId(),
                t.getScheduledStartTime(),
                t.getLatestAllowedFinishTime(),
                0.5,
                seq++
            );
            tripAssignmentRepository.save(entity);
            assignmentDtos.add(new PlanProposalResponse.TripAssignmentDto(
                vId, t.getTripId(), t.getScheduledStartTime(), t.getLatestAllowedFinishTime(), 0.5, seq
            ));
        }

        // Save Charging Sessions
        List<PlanProposalResponse.ChargingSessionDto> chargingDtos = new ArrayList<>();
        for (ChargingSession cs : planResult.getChargingSessions()) {
            ChargingSessionEntity cse = new ChargingSessionEntity(
                UUID.randomUUID().toString(),
                proposalId,
                cs.getVehicleId(),
                cs.getHubId(),
                cs.getStartTime(),
                cs.getEndTime(),
                cs.getEnergyDeliveredKwh(),
                cs.getEstimatedCost().doubleValue()
            );
            chargingSessionRepository.save(cse);
            chargingDtos.add(new PlanProposalResponse.ChargingSessionDto(
                cs.getVehicleId(), cs.getHubId(), cs.getStartTime(), cs.getEndTime(),
                cs.getEnergyDeliveredKwh(), cs.getEstimatedCost().doubleValue()
            ));
        }

        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, "CREATE_PROPOSAL", "PROPOSAL", proposalId,
            "Created plan proposal isValid=" + report.isValid()
        ));

        return new PlanProposalResponse(
            proposalId, tenantId, horizonStart, horizonEnd, totalCost,
            serviceRate, report.isValid(), reportJson, assignmentDtos, chargingDtos
        );
    }

    @Transactional
    public PlanVersionResponse acceptPlan(String tenantId, String proposalId, long expectedCurrentVersion) {
        ensureTenant(tenantId);
        PlanProposalEntity proposal = planProposalRepository.findById(proposalId)
            .orElseThrow(() -> new IllegalArgumentException("Plan proposal not found: " + proposalId));

        if (!proposal.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Plan proposal does not belong to tenant: " + tenantId);
        }

        if (!proposal.isValid()) {
            throw new IllegalStateException("Cannot accept invalid plan proposal: " + proposalId);
        }

        Long currentMax = planVersionRepository.findMaxVersionByTenantId(tenantId);
        long currentVersion = currentMax != null ? currentMax : 0L;

        if (currentVersion != expectedCurrentVersion) {
            throw new IllegalStateException("Version conflict: expected " + expectedCurrentVersion + " but current active version is " + currentVersion);
        }

        // Supersede previous active version if any
        Optional<PlanVersionEntity> activeOpt = planVersionRepository.findByTenantIdAndStatus(tenantId, "ACCEPTED");
        activeOpt.ifPresent(prev -> {
            prev.setStatus("SUPERSEDED");
            planVersionRepository.save(prev);
        });

        long newVersionNumber = currentVersion + 1;
        PlanVersionEntity newVersion = new PlanVersionEntity(
            UUID.randomUUID().toString(),
            tenantId,
            newVersionNumber,
            proposalId,
            "ACCEPTED",
            Instant.now()
        );
        PlanVersionEntity savedVersion = planVersionRepository.save(newVersion);

        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, "ACCEPT_PLAN", "PLAN_VERSION", savedVersion.getId(),
            "Accepted proposal " + proposalId + " as version " + newVersionNumber
        ));

        return new PlanVersionResponse(
            savedVersion.getId(), tenantId, savedVersion.getVersionNumber(),
            savedVersion.getProposalId(), savedVersion.getStatus(), savedVersion.getAcceptedAt()
        );
    }

    public List<PlanVersionResponse> getPlanVersions(String tenantId) {
        return planVersionRepository.findByTenantIdOrderByVersionNumberDesc(tenantId).stream()
            .map(pv -> new PlanVersionResponse(
                pv.getId(), pv.getTenantId(), pv.getVersionNumber(), pv.getProposalId(), pv.getStatus(), pv.getAcceptedAt()
            )).toList();
    }

    public Optional<IdempotencyRecordEntity> getIdempotencyRecord(String key, String tenantId) {
        return idempotencyRecordRepository.findByIdempotencyKeyAndTenantId(key, tenantId);
    }

    @Transactional
    public void saveIdempotencyRecord(String key, String tenantId, String payload, int status) {
        idempotencyRecordRepository.save(new IdempotencyRecordEntity(key, tenantId, payload, status));
    }
}
