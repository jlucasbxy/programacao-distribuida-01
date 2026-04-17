#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
NUM_WORKERS="${1:-1}"

WORKER_PIDS=()

cleanup() {
    echo ""
    echo "Shutting down..."
    kill "$DATA_SERVER_PID" "$COORDINATOR_PID" "${WORKER_PIDS[@]}" 2>/dev/null
    wait "$DATA_SERVER_PID" "$COORDINATOR_PID" "${WORKER_PIDS[@]}" 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "Building all modules..."
mvn -f "$ROOT/pom.xml" install -DskipTests -q

echo "Starting data-server..."
mvn -f "$ROOT/data-server/pom.xml" exec:java -Dexec.args="--server" &
DATA_SERVER_PID=$!

sleep 1

echo "Starting coordinator..."
mvn -f "$ROOT/coordinator/pom.xml" exec:java -Dexec.args="--coordinator" &
COORDINATOR_PID=$!

sleep 1

echo "Starting $NUM_WORKERS worker(s)..."
for i in $(seq 1 "$NUM_WORKERS"); do
    mvn -f "$ROOT/worker/pom.xml" exec:java -Dexec.args="--worker-id worker-$i" &
    WORKER_PIDS+=($!)
done

wait "${WORKER_PIDS[@]}"
cleanup
