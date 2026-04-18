#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

START_DATA_SERVER=false
START_COORDINATOR=false
START_WORKERS=false
NUM_WORKERS=1

usage() {
    echo "Usage: $0 [--all] [--data-server] [--coordinator] [--workers [N]]"
    echo "  --all           Start all services (default if no flags given)"
    echo "  --data-server   Start only the data-server"
    echo "  --coordinator   Start only the coordinator"
    echo "  --workers [N]   Start N workers (default: 1)"
    exit 1
}

if [[ $# -eq 0 ]]; then
    START_DATA_SERVER=true
    START_COORDINATOR=true
    START_WORKERS=true
else
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --all)
                START_DATA_SERVER=true
                START_COORDINATOR=true
                START_WORKERS=true
                shift
                ;;
            --data-server)
                START_DATA_SERVER=true
                shift
                ;;
            --coordinator)
                START_COORDINATOR=true
                shift
                ;;
            --workers)
                START_WORKERS=true
                shift
                if [[ $# -gt 0 && "$1" =~ ^[0-9]+$ ]]; then
                    NUM_WORKERS="$1"
                    shift
                fi
                ;;
            *)
                echo "Unknown flag: $1"
                usage
                ;;
        esac
    done
fi

PIDS=()

cleanup() {
    echo ""
    echo "Shutting down..."
    kill "${PIDS[@]}" 2>/dev/null
    wait "${PIDS[@]}" 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "Building all modules..."
mvn -f "$ROOT/pom.xml" install -DskipTests -q

if $START_DATA_SERVER; then
    INTERNET_MOCK="$ROOT/data-server/src/main/resources/internet-mock.json"
    INTERNET_MOCK_FULL="$ROOT/data-server/src/main/resources/internet-mock-full.json"
    if [[ -f "$INTERNET_MOCK_FULL" ]]; then
        INTERNET_MOCK="$INTERNET_MOCK_FULL"
    fi
    echo "Starting data-server..."
    mvn -f "$ROOT/data-server/pom.xml" exec:java \
        -Dexec.args="--server 9090 $INTERNET_MOCK" &
    PIDS+=($!)
    sleep 1
fi

if $START_COORDINATOR; then
    echo "Starting coordinator..."
    mvn -f "$ROOT/coordinator/pom.xml" exec:java \
        -Dexec.args="--coordinator --seeds-file $ROOT/coordinator/src/main/resources/seeds.txt" &
    PIDS+=($!)
    sleep 1
fi

if $START_WORKERS; then
    echo "Starting $NUM_WORKERS worker(s)..."
    for i in $(seq 1 "$NUM_WORKERS"); do
        mvn -f "$ROOT/worker/pom.xml" exec:java -Dexec.args="--worker-id worker-$i" &
        PIDS+=($!)
    done
fi

if [[ ${#PIDS[@]} -eq 0 ]]; then
    echo "No services selected."
    usage
fi

wait "${PIDS[@]}"
cleanup
