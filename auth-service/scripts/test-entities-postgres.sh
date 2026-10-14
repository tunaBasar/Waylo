#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

# Dedicated disposable DB only. Never starts the application, Kafka, or SMTP.
# All credentials below are for this local temporary database, not production.
container=$(docker run --rm -d \
    --label waylo.purpose=auth-entity-tests \
    -p 127.0.0.1::5432 \
    -e POSTGRES_DB=waylo_auth_test \
    -e POSTGRES_USER=auth_test \
    -e POSTGRES_PASSWORD=auth_test \
    postgres:16-alpine)
trap 'docker stop "$container" >/dev/null 2>&1 || true' EXIT

ready=false
for attempt in $(seq 1 30); do
    if docker exec "$container" pg_isready -U auth_test -d waylo_auth_test >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    docker logs "$container"
    exit 1
fi
port=$(docker inspect --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' "$container")
export AUTH_TEST_DB_URL="jdbc:postgresql://127.0.0.1:${port}/waylo_auth_test"
export AUTH_TEST_DB_USERNAME=auth_test
export AUTH_TEST_DB_PASSWORD=auth_test
./gradlew :auth-service:test :auth-service:integrationTest :auth-service:bootJar \
    --rerun-tasks --no-daemon --console=plain
