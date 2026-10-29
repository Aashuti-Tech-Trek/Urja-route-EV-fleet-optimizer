#!/usr/bin/env bash
# UrjaRoute Comprehensive End-to-End Smoke Test Script (Bash/cURL)
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
AUTH_HEADER="X-API-KEY: key-operator-smoke_tenant"
JSON_HEADER="Content-Type: application/json"

echo "Running UrjaRoute E2E Smoke Test against $BASE_URL..."

# 1. Health Probe
echo "1. Checking application health..."
HEALTH=$(curl -s -f "$BASE_URL/actuator/health")
echo "$HEALTH" | grep -q '"status":"UP"' || { echo "Health check failed!"; exit 1; }
echo "   PASS: Actuator health is UP"

# 2. Unauthorized Access Check
echo "2. Checking unauthorized access rejection on protected endpoint..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/plans/versions" || true)
if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
    echo "   PASS: Protected endpoint correctly rejected unauthorized request (HTTP $HTTP_CODE)"
else
    echo "Security defect: Unauthenticated request returned HTTP $HTTP_CODE"; exit 1
fi

echo "All critical baseline endpoint checks verified."
