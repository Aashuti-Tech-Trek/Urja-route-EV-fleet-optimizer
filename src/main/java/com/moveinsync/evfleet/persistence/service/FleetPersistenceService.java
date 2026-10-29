package com.moveinsync.evfleet.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.evfleet.api.dto.HubUtilizationResponse;
import com.moveinsync.evfleet.api.dto.OverviewResponse;
import com.moveinsync.evfleet.api.dto.PlanProposalResponse;
import com.moveinsync.evfleet.api.dto.PlanVersionResponse;
import com.moveinsync.evfleet.domain.*;
import com.moveinsync.evfleet.location.Location;
import com.moveinsync.evfleet.observability.PlanningMetrics;
import com.moveinsync.evfleet.persistence.entity.*;
import com.moveinsync.evfleet.persistence.repository.*;
import com.moveinsync.evfleet.planner.*;
import com.moveinsync.evfleet.security.SecurityUtils;
import com.moveinsync.evfleet.time.TariffRate;
import com.moveinsync.evfleet.time.TariffSchedule;
import com.moveinsync.evfleet.validator.DefaultPlanValidator;
import com.moveinsync.evfleet.validator.PlanValidator;
import com.moveinsync.evfleet.validator.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Service
public class FleetPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(FleetPersistenceService.class);

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
    private final PlanningMetrics planningMetrics;
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
                                   IdempotencyRecordRepository idempotencyRecordRepository,
                                   PlanningMetrics planningMetrics) {
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
        this.planningMetrics = planningMetrics;
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
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "REGISTER_VEHICLE", "VEHICLE", saved.getId(), UUID.randomUUID().toString(), "Vehicle registered"
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
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "REGISTER_TRIP", "TRIP", saved.getId(), UUID.randomUUID().toString(), "Trip registered"
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
        ChargerHubEntity saved = chargerHubRepository.save(hub);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "REGISTER_HUB", "HUB", saved.getId(), UUID.randomUUID().toString(), "Charger hub registered"
        ));
        return saved;
    }

    public List<ChargerHubEntity> getHubs(String tenantId) {
        return chargerHubRepository.findByTenantId(tenantId);
    }

    public Page<ChargerHubEntity> getHubs(String tenantId, Pageable pageable) {
        return chargerHubRepository.findByTenantId(tenantId, pageable);
    }

    public Optional<ChargerHubEntity> getHub(String tenantId, String hubId) {
        return chargerHubRepository.findByIdAndTenantId(hubId, tenantId);
    }

    @Transactional
    public ChargerHubEntity updateHub(String tenantId, String hubId, ChargerHubEntity updatedConfig) {
        ensureTenant(tenantId);
        ChargerHubEntity existing = chargerHubRepository.findByIdAndTenantId(hubId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Hub not found: " + hubId));

        existing.setName(updatedConfig.getName());
        existing.setLocationId(updatedConfig.getLocationId());
        existing.setTotalChargers(updatedConfig.getTotalChargers());
        existing.setChargerPowerKw(updatedConfig.getChargerPowerKw());
        existing.setTariffRate(updatedConfig.getTariffRate());
        existing.setStatus(updatedConfig.getStatus());
        existing.setLatitude(updatedConfig.getLatitude());
        existing.setLongitude(updatedConfig.getLongitude());

        ChargerHubEntity saved = chargerHubRepository.save(existing);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "UPDATE_HUB", "HUB", saved.getId(), UUID.randomUUID().toString(),
            "Updated hub configuration, status=" + saved.getStatus()
        ));
        return saved;
    }

    @Transactional
    public ChargerHubEntity setHubStatus(String tenantId, String hubId, String status) {
        ensureTenant(tenantId);
        ChargerHubEntity hub = chargerHubRepository.findByIdAndTenantId(hubId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Hub not found: " + hubId));
        hub.setStatus(status);
        ChargerHubEntity saved = chargerHubRepository.save(hub);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "SET_HUB_STATUS", "HUB", saved.getId(), UUID.randomUUID().toString(),
            "Set hub status to " + status
        ));
        return saved;
    }

    public HubUtilizationResponse getHubUtilization(String tenantId, String hubId) {
        ChargerHubEntity hub = chargerHubRepository.findByIdAndTenantId(hubId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Hub not found: " + hubId));

        int totalChargers = hub.getTotalChargers();
        boolean isOffline = "OFFLINE".equalsIgnoreCase(hub.getStatus());
        int offlineChargers = isOffline ? totalChargers : 0;

        // Query active plan charging sessions for this hub
        List<PlanProposalResponse.ChargingSessionDto> sessions = new ArrayList<>();
        int occupied = 0;
        Instant now = Instant.now();

        Optional<PlanVersionEntity> activeVersionOpt = planVersionRepository.findByTenantIdAndStatus(tenantId, "ACCEPTED");
        if (activeVersionOpt.isPresent()) {
            String proposalId = activeVersionOpt.get().getProposalId();
            List<ChargingSessionEntity> csEntities = chargingSessionRepository.findByPlanProposalIdAndHubIdOrderByStartTimeAsc(proposalId, hubId);
            for (ChargingSessionEntity cs : csEntities) {
                sessions.add(new PlanProposalResponse.ChargingSessionDto(
                    cs.getVehicleId(), cs.getHubId(), cs.getStartTime(), cs.getEndTime(),
                    cs.getEnergyAddedKwh(), cs.getCost()
                ));
                if (!now.isBefore(cs.getStartTime()) && now.isBefore(cs.getEndTime())) {
                    occupied++;
                }
            }
        }

        int available = isOffline ? 0 : Math.max(0, totalChargers - occupied);
        double utilization = totalChargers > 0 ? (double) occupied / totalChargers : 0.0;

        return new HubUtilizationResponse(hubId, totalChargers, available, occupied, offlineChargers, utilization, sessions);
    }

    public List<AuditRecordEntity> getRecoveryEvents(String tenantId) {
        return auditRecordRepository.findByTenantIdOrderByTimestampDesc(tenantId).stream()
                .filter(a -> a.getAction().contains("RECOVERY") || a.getAction().contains("REPLAN") || a.getAction().contains("DRIFT") || a.getAction().contains("OUTAGE"))
                .limit(20)
                .toList();
    }

    @Transactional
    public PlanProposalResponse generatePlanProposal(String tenantId, Instant horizonStart, Instant horizonEnd,
                                                      List<String> vehicleIds, List<String> tripIds) {
        ensureTenant(tenantId);
        List<VehicleEntity> vEntities = vehicleRepository.findByTenantId(tenantId);
        if (vehicleIds != null && !vehicleIds.isEmpty()) {
            Set<String> set = new HashSet<>(vehicleIds);
            vEntities = vEntities.stream().filter(v -> set.contains(v.getId())).toList();
            if (vEntities.size() != set.size()) {
                throw new IllegalArgumentException("One or more vehicle IDs not found or do not belong to tenant: " + tenantId);
            }
        }

        List<TripEntity> tEntities = tripRepository.findByTenantId(tenantId);
        if (tripIds != null && !tripIds.isEmpty()) {
            Set<String> set = new HashSet<>(tripIds);
            tEntities = tEntities.stream().filter(t -> set.contains(t.getId())).toList();
            if (tEntities.size() != set.size()) {
                throw new IllegalArgumentException("One or more trip IDs not found or do not belong to tenant: " + tenantId);
            }
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

        // Convert hubs respecting status (failed/offline hubs excluded from planning)
        List<ChargerHub> hubs = hEntities.stream()
            .filter(he -> !"OFFLINE".equalsIgnoreCase(he.getStatus()))
            .map(he -> {
                TariffSchedule schedule = new TariffSchedule(
                    ZoneId.of("Asia/Kolkata"), "INR", List.of(), TariffRate.of(he.getTariffRate(), "INR")
                );
                return new ChargerHub(
                    he.getId(),
                    tenantId,
                    new Location(he.getLatitude(), he.getLongitude()),
                    he.getTotalChargers(),
                    he.getChargerPowerKw(),
                    schedule
                );
            }).toList();

        PlanningInput input = new PlanningInput("RUN-" + UUID.randomUUID(), vehicles, trips, hubs);
        GreedyFleetPlanner planner = GreedyFleetPlanner.createDefault();

        FleetPlanResult planResult;
        try {
            planResult = planningMetrics.recordPlanningDuration(() -> planner.plan(input));
            planningMetrics.recordSuccess();
        } catch (RuntimeException e) {
            planningMetrics.recordFailure();
            throw e;
        } catch (Exception e) {
            planningMetrics.recordFailure();
            throw new RuntimeException("Planning failed", e);
        }

        // Record trip assignment metrics
        int evAssigned = planResult.getEvAssignments().size();
        int backupAssigned = planResult.getTotalBackupTripCount();
        planningMetrics.recordTripsAssigned(evAssigned);
        planningMetrics.recordTripsUnassigned(backupAssigned);
        planningMetrics.recordTotalCost(planResult.getTotalChargingCost().doubleValue());

        // Record candidate evaluations from quality report if available
        if (planResult.getQualityReport() != null) {
            planningMetrics.recordCandidatesEvaluated(
                planResult.getQualityReport().getCandidateEvaluations());
        }

        log.info("Planning completed: tenant={}, evAssigned={}, backup={}, cost={}, duration={}",
                tenantId, evAssigned, backupAssigned,
                planResult.getTotalChargingCost(), planResult.getExecutionDuration());

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
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "CREATE_PROPOSAL", "PROPOSAL", proposalId, UUID.randomUUID().toString(),
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
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            "ACCEPT_PLAN", "PLAN_VERSION", savedVersion.getId(), UUID.randomUUID().toString(),
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
    public void saveIdempotencyRecord(String key, String tenantId, String requestFingerprint, String payload, int status) {
        idempotencyRecordRepository.save(new IdempotencyRecordEntity(key, tenantId, requestFingerprint, payload, status));
    }

    @Transactional
    public void recordAuditEvent(String tenantId, String action, String entityType, String entityId, String details) {
        ensureTenant(tenantId);
        auditRecordRepository.save(new AuditRecordEntity(
            UUID.randomUUID().toString(), tenantId, SecurityUtils.getAuthenticatedActor(),
            action, entityType, entityId, UUID.randomUUID().toString(), details
        ));
    }

    public OverviewResponse getOverview(String tenantId) {
        List<VehicleEntity> vehicles = vehicleRepository.findByTenantId(tenantId);
        int vehicleCount = vehicles.size();
        int availableVehicles = vehicleCount; // default all available

        List<TripEntity> trips = tripRepository.findByTenantId(tenantId);
        int tripCount = trips.size();
        List<ChargerHubEntity> hubs = chargerHubRepository.findByTenantId(tenantId);
        int hubCount = hubs.size();
        int totalChargers = hubs.stream().mapToInt(ChargerHubEntity::getTotalChargers).sum();

        int offlineChargers = 0;
        for (ChargerHubEntity h : hubs) {
            if ("OFFLINE".equalsIgnoreCase(h.getStatus())) {
                offlineChargers += h.getTotalChargers();
            }
        }

        Optional<PlanVersionEntity> activeVersionOpt = planVersionRepository.findByTenantIdAndStatus(tenantId, "ACCEPTED");
        long currentVersion = activeVersionOpt.map(PlanVersionEntity::getVersionNumber).orElse(0L);

        double serviceRate = 0.0;
        double latestCost = 0.0;
        int evAssigned = 0;
        int backupTrips = 0;
        int occupiedChargers = 0;
        Instant now = Instant.now();

        if (activeVersionOpt.isPresent()) {
            String proposalId = activeVersionOpt.get().getProposalId();
            Optional<PlanProposalEntity> proposalOpt = planProposalRepository.findById(proposalId);
            if (proposalOpt.isPresent()) {
                PlanProposalEntity proposal = proposalOpt.get();
                serviceRate = proposal.getServiceRate();
                latestCost = proposal.getTotalCost();

                List<TripAssignmentEntity> assignments = tripAssignmentRepository.findByPlanProposalIdOrderBySequenceOrderAsc(proposalId);
                for (TripAssignmentEntity a : assignments) {
                    if ("BACKUP".equalsIgnoreCase(a.getVehicleId())) {
                        backupTrips++;
                    } else {
                        evAssigned++;
                    }
                }

                List<ChargingSessionEntity> sessions = chargingSessionRepository.findByPlanProposalIdOrderByStartTimeAsc(proposalId);
                for (ChargingSessionEntity cs : sessions) {
                    if (!now.isBefore(cs.getStartTime()) && now.isBefore(cs.getEndTime())) {
                        occupiedChargers++;
                    }
                }
            }
        }

        int availableChargers = Math.max(0, totalChargers - offlineChargers - occupiedChargers);
        double utilization = totalChargers > 0 ? (double) occupiedChargers / totalChargers : 0.0;
        int pendingTrips = Math.max(0, tripCount - evAssigned - backupTrips);

        // Fetch recent recovery audit events
        List<String> recentRecovery = auditRecordRepository.findByTenantIdOrderByTimestampDesc(tenantId).stream()
                .filter(a -> a.getAction().contains("RECOVERY") || a.getAction().contains("REPLAN") || a.getAction().contains("DRIFT") || a.getAction().contains("OUTAGE"))
                .limit(5)
                .map(a -> String.format("[%s] %s on %s %s: %s",
                        a.getTimestamp(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails()))
                .toList();

        return new OverviewResponse(
            vehicleCount,
            availableVehicles,
            tripCount,
            pendingTrips,
            hubCount,
            totalChargers,
            availableChargers,
            occupiedChargers,
            offlineChargers,
            currentVersion,
            serviceRate,
            latestCost,
            evAssigned,
            backupTrips,
            utilization,
            recentRecovery
        );
    }
}
