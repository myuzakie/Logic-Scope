#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "Checking LogicScope backend..."
curl --fail --silent http://localhost:4377/api/health
echo

echo "Checking PostgreSQL..."
if command -v docker-compose >/dev/null 2>&1; then
  docker-compose exec -T postgres pg_isready -U "${POSTGRES_USER:-logicscope}" -d "${POSTGRES_DB:-logicscope}"
else
  docker compose exec -T postgres pg_isready -U "${POSTGRES_USER:-logicscope}" -d "${POSTGRES_DB:-logicscope}"
fi

echo "Checking OpenTelemetry Collector health endpoint..."
curl --fail --silent http://localhost:13133/ >/dev/null
echo "All local services are healthy."
