#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if command -v docker-compose >/dev/null 2>&1; then
  docker-compose up --build -d
elif docker compose version >/dev/null 2>&1; then
  docker compose up --build -d
else
  echo "Docker Compose is required (docker compose or docker-compose)." >&2
  exit 1
fi
