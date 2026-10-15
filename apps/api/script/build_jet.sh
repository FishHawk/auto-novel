#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Use an isolated database and a dynamically assigned local port.
CONTAINER_ID=$(docker run --rm -d \
  -e POSTGRES_USER=user \
  -e POSTGRES_PASSWORD=pass \
  -e POSTGRES_DB=main \
  -p 127.0.0.1::5432 \
  postgres:17-alpine)
trap 'docker stop "$CONTAINER_ID" >/dev/null' EXIT

for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$CONTAINER_ID" pg_isready -U user -d main >/dev/null 2>&1; then
    break
  fi
  if ((attempt == 59)); then
    docker logs "$CONTAINER_ID"
    echo 'PostgreSQL did not become ready' >&2
    exit 1
  fi
  sleep 1
done

# Apply the schema explicitly after startup to avoid init-script readiness races.
docker exec -i "$CONTAINER_ID" psql -U user -d main -v ON_ERROR_STOP=1 < ../../sql/init.sql
PORT=$(docker port "$CONTAINER_ID" 5432/tcp)
PORT=${PORT##*:}
"$(go env GOPATH)/bin/jet" \
  -dsn="postgresql://user:pass@127.0.0.1:${PORT}/main?sslmode=disable" \
  -schema=public -path=./.gen
