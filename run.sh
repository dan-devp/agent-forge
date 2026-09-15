#!/usr/bin/env bash
# Baut Backend + Frontend neu und startet den Stack.
# Nutzung: ./run.sh        (bauen + starten, Logs folgen)
#          ./run.sh down   (stoppen)
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
