#!/usr/bin/env bash
# Start only the services a task needs, to keep CPU and RAM low on a laptop.
# Usage:
#   scripts/dev-up.sh car        # car rental work (default)
#   scripts/dev-up.sh hotel      # hotel + orders + payments
#   scripts/dev-up.sh all        # everything (heavy)
#   scripts/dev-up.sh down       # stop all containers (data is kept)
# Extra args after the set name are passed to `docker compose up -d`, e.g. --build.
set -euo pipefail
cd "$(dirname "$0")/.."

CORE="postgres kafka redis config-service discovery-service api-gateway auth-service frontend"

case "${1:-car}" in
  car)   SET="$CORE car-rental-service package-booking-service payment-service" ;;
  hotel) SET="$CORE hotel-service package-booking-service payment-service" ;;
  flight) SET="$CORE flight-service flight-seat-inventory-service package-booking-service payment-service" ;;
  mail)  SET="$CORE notification-service" ;;
  all)   SET="" ;;
  down)  docker compose stop; exit 0 ;;
  *) echo "unknown set: $1 (car|hotel|flight|mail|all|down)"; exit 1 ;;
esac
shift || true

# Stop running services that are not in the set, so switching sets frees memory.
if [ -n "$SET" ]; then
  for running in $(docker compose ps --services --status running); do
    case " $SET " in *" $running "*) ;; *) docker compose stop "$running" ;; esac
  done
fi

# shellcheck disable=SC2086
docker compose up -d --no-deps "$@" $SET
