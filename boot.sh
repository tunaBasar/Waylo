#!/bin/bash
# Waylo Boot Script - starts services in dependency order
# Usage: ./boot.sh [start|stop|status]

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Service groups in startup order
PHASE_0=("config-service" "discovery-service")
PHASE_1=("api-gateway")
PHASE_2=("flight-service" "flight-seat-inventory-service" "hotel-service" "car-rental-service" "package-booking-service" "pricing-service" "payment-service" "crm-service" "checkin-service" "notification-service")
PHASE_3=("package-booking-saga-orchestrator")
PHASE_4=("traffic-simulator")

# Port mapping
declare -A PORTS
PORTS[config-service]=8888
PORTS[discovery-service]=8761
PORTS[api-gateway]=8080
PORTS[flight-service]=8081
PORTS[flight-seat-inventory-service]=8082
PORTS[hotel-service]=8083
PORTS[car-rental-service]=8084
PORTS[package-booking-service]=8085
PORTS[pricing-service]=8086
PORTS[payment-service]=8087
PORTS[crm-service]=8088
PORTS[checkin-service]=8089
PORTS[notification-service]=8090
PORTS[package-booking-saga-orchestrator]=8091
PORTS[traffic-simulator]=8095

wait_for_health() {
    local service=$1
    local port=${PORTS[$service]}
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -sf "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓${NC} $service is healthy (port $port)"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    echo -e "  ${RED}✗${NC} $service failed to start (port $port)"
    return 1
}

start_service() {
    local service=$1
    local port=${PORTS[$service]}
    local jar=$(ls "$PROJECT_DIR/$service/build/libs/"*.jar 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
        echo -e "  ${RED}✗${NC} $service: no jar found. Run './gradlew bootJar' first."
        return 1
    fi
    echo -e "  ${BLUE}→${NC} Starting $service on port $port..."
    java -jar "$jar" > "$LOG_DIR/$service.log" 2>&1 &
    echo $! > "$LOG_DIR/$service.pid"
}

start_phase() {
    local phase_name=$1
    shift
    local services=("$@")

    echo -e "\n${YELLOW}═══ $phase_name ═══${NC}"

    for service in "${services[@]}"; do
        start_service "$service"
    done

    # Wait for config-service first, then discovery-service
    if [ "$phase_name" = "Phase 0: Infrastructure" ]; then
        wait_for_health "config-service"
        wait_for_health "discovery-service"
    else
        for service in "${services[@]}"; do
            wait_for_health "$service"
        done
    fi
}

stop_all() {
    echo -e "\n${YELLOW}Stopping all services...${NC}"
    for pidfile in "$LOG_DIR"/*.pid; do
        if [ -f "$pidfile" ]; then
            local pid=$(cat "$pidfile")
            local service=$(basename "$pidfile" .pid)
            if kill -0 "$pid" 2>/dev/null; then
                kill "$pid" 2>/dev/null || true
                echo -e "  ${GREEN}✓${NC} Stopped $service (PID $pid)"
            fi
            rm -f "$pidfile"
        fi
    done
    echo -e "${GREEN}All services stopped.${NC}"
}

status_all() {
    echo -e "\n${YELLOW}Service Status:${NC}"
    for service in "${PHASE_0[@]}" "${PHASE_1[@]}" "${PHASE_2[@]}" "${PHASE_3[@]}" "${PHASE_4[@]}"; do
        local port=${PORTS[$service]}
        if curl -sf "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓${NC} $service (port $port) - UP"
        else
            echo -e "  ${RED}✗${NC} $service (port $port) - DOWN"
        fi
    done
}

case "${1:-start}" in
    start)
        echo -e "${GREEN}╔══════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║     Waylo Microservices Startup      ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════╝${NC}"

        start_phase "Phase 0: Infrastructure" "${PHASE_0[@]}"
        start_phase "Phase 1: API Gateway" "${PHASE_1[@]}"
        start_phase "Phase 2: Domain Services" "${PHASE_2[@]}"
        start_phase "Phase 3: Saga Orchestrator" "${PHASE_3[@]}"
        start_phase "Phase 4: Traffic Simulator" "${PHASE_4[@]}"

        echo -e "\n${GREEN}All services started successfully!${NC}"
        ;;
    stop)
        stop_all
        ;;
    status)
        status_all
        ;;
    *)
        echo "Usage: $0 {start|stop|status}"
        exit 1
        ;;
esac
