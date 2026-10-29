$ErrorActionPreference = "Stop"

Write-Host "`n=== Java runtime ==="
java -version
if ($LASTEXITCODE -ne 0) { throw "Java runtime check failed" }

Write-Host "`n=== Maven wrapper ==="
.\mvnw.cmd -version
if ($LASTEXITCODE -ne 0) { throw "Maven wrapper check failed" }

Write-Host "`n=== JavaScript syntax ==="
node --check src/main/resources/static/app.js
if ($LASTEXITCODE -ne 0) { throw "Dashboard JavaScript syntax check failed" }

Write-Host "`n=== Full clean verification ==="
.\mvnw.cmd -B clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven clean verify failed" }

Write-Host "`n=== Docker Compose configuration ==="
docker compose config | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Compose configuration invalid" }

Write-Host "`n=== Docker image build ==="
docker compose build
if ($LASTEXITCODE -ne 0) { throw "Docker image build failed" }

Write-Host "`nAll verification steps passed."
