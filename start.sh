#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
    echo ""
    echo "Shutting down..."
    kill "$DATA_SERVER_PID" "$COORDINATOR_PID" 2>/dev/null
    wait "$DATA_SERVER_PID" "$COORDINATOR_PID" 2>/dev/null
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

echo "Starting worker..."
mvn -f "$ROOT/worker/pom.xml" exec:java

cleanup
