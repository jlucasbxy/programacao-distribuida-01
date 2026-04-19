#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

START_DATA_SERVER=false
START_COORDINATOR=false
START_WORKERS=false
NUM_WORKERS=1
WORKER_CAPACITY=1
SEEDS_COUNT=""

usage() {
    echo "Usage: $0 [--all] [--data-server] [--coordinator] [--workers [N]] [--capacity C] [--seeds-count N]"
    echo "  --all             Start all services (default if no flags given)"
    echo "  --data-server     Start only the data-server"
    echo "  --coordinator     Start only the coordinator"
    echo "  --workers [N]     Start N workers (default: 1)"
    echo "  --capacity C      Set capacity per worker (default: 1)"
    echo "  --seeds-count N   Limit coordinator to read only N seeds from the file"
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
            --capacity)
                shift
                if [[ $# -gt 0 && "$1" =~ ^[0-9]+$ ]]; then
                    WORKER_CAPACITY="$1"
                    shift
                else
                    echo "Error: --capacity requires a numeric argument"
                    usage
                fi
                ;;
            --seeds-count)
                shift
                if [[ $# -gt 0 && "$1" =~ ^[0-9]+$ ]]; then
                    SEEDS_COUNT="$1"
                    shift
                else
                    echo "Error: --seeds-count requires a numeric argument"
                    usage
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
    echo "Starting data-server..."
    mvn -f "$ROOT/data-server/pom.xml" exec:java \
        -Dexec.args="--server 9090 $INTERNET_MOCK" &
    PIDS+=($!)
    sleep 1
fi

if $START_COORDINATOR; then
    echo "Starting coordinator..."
    COORDINATOR_ARGS="--coordinator --seeds-file $ROOT/coordinator/src/main/resources/seeds.txt"
    if [[ -n "$SEEDS_COUNT" ]]; then
        COORDINATOR_ARGS="$COORDINATOR_ARGS --seeds-count $SEEDS_COUNT"
    fi
    mvn -f "$ROOT/coordinator/pom.xml" exec:java \
        -Dexec.args="$COORDINATOR_ARGS" &
    PIDS+=($!)
    sleep 1
fi

if $START_WORKERS; then
    echo "Starting $NUM_WORKERS worker(s)..."
    for i in $(seq 1 "$NUM_WORKERS"); do
        mvn -f "$ROOT/worker/pom.xml" exec:java -Dexec.args="--worker-id worker-$i --capacity $WORKER_CAPACITY" &
        PIDS+=($!)
    done
fi

if [[ ${#PIDS[@]} -eq 0 ]]; then
    echo "No services selected."
    usage
fi

wait "${PIDS[@]}"
cleanup
