#!/usr/bin/env bash
# Rebuilds backend + frontend and starts the stack.
# Usage: ./run.sh        (build + start, follow logs)
#        ./run.sh down   (stop)
set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "down" ]; then
  docker compose down
  exit 0
fi

docker compose up --build -d

echo
echo "Frontend : http://localhost:3000"
echo "Backend  : http://localhost:8080/api/status"
echo
docker compose logs -f
