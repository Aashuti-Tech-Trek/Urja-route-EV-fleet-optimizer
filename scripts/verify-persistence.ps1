# UrjaRoute Container Health & PostgreSQL Persistence Verification

$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8080"

$retry = 0
$healthy = $false
while ($retry -lt 20) {
    Start-Sleep -Seconds 2
    try {
        $res = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
        if ($res.status -eq "UP") {
            Write-Host "Container is UP and healthy!"
            $healthy = $true
            break
        }
    } catch {
        Write-Host "Waiting for container health endpoint..."
    }
    $retry++
}

if (-not $healthy) {
    throw "Application did not become healthy within 40 seconds after restart."
}

# Verify Alpha's vehicle and hub persisted across restart
$alphaOp = @{ "Content-Type" = "application/json"; "X-API-KEY" = "key-operator-tenant_alpha" }
$vList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vehicles" -Method Get -Headers $alphaOp
Write-Host "Vehicles after restart: $($vList.Count)"
$foundV = $vList | Where-Object { $_.id -eq "EV-ISO-ALPHA-1" }
if (-not $foundV) {
    throw "Persistence failed: Vehicle EV-ISO-ALPHA-1 not found after restart!"
}

$h = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hubs/HUB-ISO-ALPHA-1" -Method Get -Headers $alphaOp
Write-Host "Hub retrieved after restart: $($h.name) (Status: $($h.status))"
if ($h.id -ne "HUB-ISO-ALPHA-1") {
    throw "Persistence failed: Hub HUB-ISO-ALPHA-1 not found after restart!"
}

Write-Host "`nPASS: PostgreSQL data persistence across application container restart VERIFIED!"
