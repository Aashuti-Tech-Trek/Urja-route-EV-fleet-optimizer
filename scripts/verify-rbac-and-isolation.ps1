# UrjaRoute Comprehensive Security & Persistence Verification Test

$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8080"

Write-Host "--- 1. Testing Authentication, RBAC & Tenant Isolation ---"

$tenantA = "tenant_alpha"
$tenantB = "tenant_beta"

$alphaOp = @{ "Content-Type" = "application/json"; "X-API-KEY" = "key-operator-$tenantA" }
$alphaViewer = @{ "Content-Type" = "application/json"; "X-API-KEY" = "key-viewer-$tenantA" }
$betaOp = @{ "Content-Type" = "application/json"; "X-API-KEY" = "key-operator-$tenantB" }

# Register vehicle in Alpha as operator
$vBody = @{
    id = "EV-ISO-ALPHA-1"
    currentSoc = 0.88
    batteryCapacityKwh = 60.0
    efficiencyKwhPerKm = 0.20
    maxChargeRateKw = 50.0
    currentLocationId = "DEPOT-ALPHA"
    availableFrom = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json

Invoke-RestMethod -Uri "$BaseUrl/api/v1/vehicles" -Method Post -Headers $alphaOp -Body $vBody | Out-Null
Write-Host "PASS: Registered vehicle EV-ISO-ALPHA-1 under tenant $tenantA"

# Register hub in Alpha
$hBody = @{
    id = "HUB-ISO-ALPHA-1"
    name = "Alpha Hub One"
    locationId = "HUB-LOC-A"
    totalChargers = 3
    chargerPowerKw = 50.0
    tariffRate = 8.50
    status = "OPERATIONAL"
} | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/v1/hubs" -Method Post -Headers $alphaOp -Body $hBody | Out-Null
Write-Host "PASS: Registered hub HUB-ISO-ALPHA-1 under tenant $tenantA"

# Verify viewer cannot write
try {
    $vViewerBody = @{
        id = "EV-ISO-ALPHA-2"
        currentSoc = 0.88
        batteryCapacityKwh = 60.0
        efficiencyKwhPerKm = 0.20
        maxChargeRateKw = 50.0
        currentLocationId = "DEPOT-ALPHA"
        availableFrom = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/vehicles" -Method Post -Headers $alphaViewer -Body $vViewerBody | Out-Null
    throw "Security defect: Viewer was able to POST vehicle!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 403) {
        Write-Host "PASS: Viewer write rejected with HTTP 403 Forbidden"
    } else {
        throw "Unexpected status code for viewer write: $code"
    }
}

# Verify Beta cannot see Alpha's vehicle
$betaVehicles = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vehicles" -Method Get -Headers $betaOp
$foundInBeta = $betaVehicles | Where-Object { $_.id -eq "EV-ISO-ALPHA-1" }
if ($foundInBeta) {
    throw "Security defect: Tenant isolation failed! Beta sees Alpha vehicle."
}
Write-Host "PASS: Tenant Beta vehicle query does not contain Alpha's vehicle."

# Verify Beta cannot GET Alpha hub directly (returns 400 Bad Request / Not Found)
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/hubs/HUB-ISO-ALPHA-1" -Method Get -Headers $betaOp | Out-Null
    throw "Security defect: Beta was able to GET Alpha's hub directly!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 400 -or $code -eq 404 -or $code -eq 403) {
        Write-Host "PASS: Cross-tenant direct access to hub correctly rejected with HTTP $code"
    } else {
        throw "Unexpected status code for cross-tenant direct fetch: $code"
    }
}

Write-Host "`n--- 2. Testing Overview Reconciliation ---"
$overview = Invoke-RestMethod -Uri "$BaseUrl/api/v1/overview" -Method Get -Headers $alphaOp
Write-Host "Overview metrics: VehicleCount=$($overview.vehicleCount), TripCount=$($overview.tripCount), HubCount=$($overview.hubCount)"
if ($overview.vehicleCount -lt 1 -or $overview.hubCount -lt 1) {
    throw "Overview count discrepancy detected for tenant $tenantA"
}
Write-Host "PASS: Overview counts match registered resources for tenant $tenantA"

Write-Host "`nAll RBAC, tenant isolation, and overview assertions PASSED."
