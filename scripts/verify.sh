#!/usr/bin/env bash
set -euo pipefail

printf '\n=== Java runtime ===\n'
java -version
printf '\n=== Maven wrapper ===\n'
./mvnw -version
printf '\n=== Full clean verification ===\n'
./mvnw -B clean verify
printf '\n=== Docker Compose configuration ===\n'
docker compose config >/dev/null
printf '\n=== Docker image build ===\n'
docker compose build
printf '\nAll verification steps passed.\n'
