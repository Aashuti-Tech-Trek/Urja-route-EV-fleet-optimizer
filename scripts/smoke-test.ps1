# UrjaRoute Comprehensive End-to-End Smoke Test Script
# Tests health, authentication, vehicles, trips, hubs, plan proposal, validation,
# plan version acceptance, stale version conflict, battery drift & charger failure recovery.

$ErrorActionPreference = "Stop"
$BaseUrl = $args[0]
if (-not $BaseUrl) { $BaseUrl = "http://localhost:8080" }

$TenantId = "smoke_tenant"
$ApiKey = "key-operator-$TenantId"

$Headers = @{
    "Content-Type" = "application/json"
    "X-API-KEY" = $ApiKey
}

Write-Host "Running UrjaRoute E2E Smoke Test against $BaseUrl..."

# 1. Health Probe
Write-Host "1. Checking application health..."
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
if ($health.status -ne "UP") { throw "Application health check failed: status is $($health.status)" }
Write-Host "   PASS: Actuator health is UP"

# 2. Unauthorized Access Check
Write-Host "2. Checking unauthorized access rejection on protected endpoint..."
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/plans/versions" -Method Get
    throw "Security defect: Unauthenticated request was NOT rejected!"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401 -or $_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "   PASS: Protected endpoint correctly rejected unauthorized request (HTTP $($_.Exception.Response.StatusCode.value__))"
    } else {
        throw "Unexpected status code for unauthorized request: $($_.Exception.Response.StatusCode.value__)"
    }
}

# 3. Register Vehicle
Write-Host "3. Registering electric vehicle..."
$vBody = @{
    id = "EV-SMOKE-1"
    currentSoc = 0.90
    batteryCapacityKwh = 60.0
    efficiencyKwhPerKm = 0.20
    maxChargeRateKw = 50.0
    currentLocationId = "DEPOT-A"
    availableFrom = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json
$vehicle = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vehicles" -Method Post -Headers $Headers -Body $vBody
Write-Host "   PASS: Registered vehicle $($vehicle.id)"

# 4. Register Trip
Write-Host "4. Ingesting trip demand..."
$now = (Get-Date).ToUniversalTime()
$tBody = @{
    id = "TRIP-SMOKE-1"
    pickupLocationId = "DEPOT-A"
    dropoffLocationId = "OFFICE-B"
    pickupWindowStart = $now.AddMinutes(15).ToString("yyyy-MM-ddTHH:mm:ssZ")
    pickupWindowEnd = $now.AddMinutes(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
    deadline = $now.AddMinutes(90).ToString("yyyy-MM-ddTHH:mm:ssZ")
    distanceKm = 12.0
    passengerCount = 2
} | ConvertTo-Json
$trip = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trips" -Method Post -Headers $Headers -Body $tBody
Write-Host "   PASS: Ingested trip $($trip.id)"

# 5. Register Hub
Write-Host "5. Registering charger hub..."
$hBody = @{
    id = "HUB-SMOKE-1"
    name = "Smoke Central Charging Hub"
    locationId = "HUB-LOC-1"
    totalChargers = 4
    chargerPowerKw = 60.0
    tariffRate = 9.0
    status = "OPERATIONAL"
} | ConvertTo-Json
$hub = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hubs" -Method Post -Headers $Headers -Body $hBody
Write-Host "   PASS: Registered hub $($hub.id)"

# 6. Verify Overview Reconciliation
Write-Host "6. Verifying overview summary reconciliation..."
$overview = Invoke-RestMethod -Uri "$BaseUrl/api/v1/overview" -Method Get -Headers $Headers
if ($overview.vehicleCount -lt 1 -or $overview.tripCount -lt 1 -or $overview.hubCount -lt 1) {
    throw "Overview reconciliation mismatch! Vehicles=$($overview.vehicleCount), Trips=$($overview.tripCount), Hubs=$($overview.hubCount)"
}
Write-Host "   PASS: Overview reconciled (Vehicles: $($overview.vehicleCount), Trips: $($overview.tripCount), Hubs: $($overview.hubCount))"

# 7. Generate Plan Proposal & Validate
Write-Host "7. Generating candidate fleet plan proposal..."
$pBody = @{
    planningHorizonStart = $now.ToString("yyyy-MM-ddTHH:mm:ssZ")
    planningHorizonEnd = $now.AddHours(8).ToString("yyyy-MM-ddTHH:mm:ssZ")
    vehicleIds = @()
    tripIds = @()
} | ConvertTo-Json
$proposal = Invoke-RestMethod -Uri "$BaseUrl/api/v1/plans/propose" -Method Post -Headers $Headers -Body $pBody
if (-not $proposal.valid) { throw "Plan proposal validation failed!" }
Write-Host "   PASS: Plan proposal $($proposal.proposalId) generated and validated PASS (Service rate: $($proposal.serviceRate * 100)%)"

# 8. Accept Plan Proposal
Write-Host "8. Accepting validated plan proposal (currentVersion=0)..."
$accBody = @{ expectedCurrentVersion = 0 } | ConvertTo-Json
$accHeaders = $Headers.Clone()
$accHeaders["Idempotency-Key"] = [guid]::NewGuid().ToString()
$version = Invoke-RestMethod -Uri "$BaseUrl/api/v1/plans/$($proposal.proposalId)/accept" -Method Post -Headers $accHeaders -Body $accBody
Write-Host "   PASS: Accepted plan as version $($version.versionNumber)"

# 9. Verify Stale Version Rejection (409 Conflict)
Write-Host "9. Verifying optimistic locking conflict on stale version..."
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/plans/$($proposal.proposalId)/accept" -Method Post -Headers $Headers -Body $accBody
    throw "Security defect: Stale version acceptance did NOT conflict!"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 409) {
        Write-Host "   PASS: Optimistic locking correctly rejected stale version with 409 Conflict"
    } else {
        throw "Unexpected status code for stale version: $($_.Exception.Response.StatusCode.value__)"
    }
}

# 10. Live Battery Drift Recovery
Write-Host "10. Triggering live battery drift recovery event..."
$driftBody = @{
    vehicleId = "EV-SMOKE-1"
    observedSoc = 0.70
    eventTime = $now.AddMinutes(5).ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json
$driftRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/replanning/battery-drift" -Method Post -Headers $Headers -Body $driftBody
if ($driftRes.replanDurationMs -gt 2000) { throw "Replanning exceeded 2.0s SLA: $($driftRes.replanDurationMs) ms" }
Write-Host "   PASS: Drift recovery outcome: $($driftRes.outcome) in $($driftRes.replanDurationMs) ms (<2.0s SLA)"

# 11. Live Charger Outage Recovery & Restoration
Write-Host "11. Triggering live charger hub outage and subsequent restoration..."
$outageBody = @{
    hubId = "HUB-SMOKE-1"
    outageStart = $now.ToString("yyyy-MM-ddTHH:mm:ssZ")
    estimatedRestoration = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json
$outageRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/replanning/charger-failure" -Method Post -Headers $Headers -Body $outageBody
Write-Host "   PASS: Outage recovery outcome: $($outageRes.outcome) in $($outageRes.replanDurationMs) ms"

$restoreRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/replanning/charger-restore/HUB-SMOKE-1" -Method Post -Headers $Headers
Write-Host "   PASS: Hub restoration outcome: $($restoreRes.outcome)"

Write-Host "`nAll 11 Smoke Test assertions PASSED successfully."
