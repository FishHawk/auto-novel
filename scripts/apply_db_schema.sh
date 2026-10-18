#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"

exec docker compose \
  --file "$project_root/docker-compose.yml" \
  exec -T postgresql \
  psql \
  --username=auto-novel \
  --dbname=auto-novel \
  --set=ON_ERROR_STOP=1 \
  <"$project_root/sql/init.sql"
