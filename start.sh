#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

ENV_FILE="${ENV_FILE:-$ROOT/.env}"

START_DATA_SERVER=false
START_COORDINATOR=false
START_WORKERS=false
NUM_WORKERS=1
WORKER_CAPACITY=1
SEEDS_COUNT=""
CLEAN_BUILD=false
SKIP_BUILD="${BUILD_SKIP:-false}"

if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

NUM_WORKERS="${WORKER_COUNT:-1}"
WORKER_COORDINATOR_HOST="${WORKER_COORDINATOR_HOST:-localhost}"
WORKER_COORDINATOR_PORT="${WORKER_COORDINATOR_PORT:-7070}"
WORKER_DATA_SERVER_HOST="${WORKER_DATA_SERVER_HOST:-localhost}"
WORKER_DATA_SERVER_PORT="${WORKER_DATA_SERVER_PORT:-9090}"
WORKER_CAPACITY="${WORKER_CAPACITY:-1}"
SEEDS_COUNT="${COORDINATOR_SEEDS_COUNT:-}"
CLEAN_BUILD="${BUILD_CLEAN:-false}"

usage() {
    echo "Usage: $0 [--all] [--data-server] [--coordinator] [--workers [N]] [--capacity C] [--seeds-count N] [--clean] [--no-build]"
    echo "  Uses .env file at: $ENV_FILE (if present)"
    echo "  Env vars: WORKER_COUNT, WORKER_COORDINATOR_HOST, WORKER_COORDINATOR_PORT, WORKER_DATA_SERVER_HOST, WORKER_DATA_SERVER_PORT, WORKER_CAPACITY, COORDINATOR_SEEDS_COUNT, BUILD_CLEAN, BUILD_SKIP"
    echo "  Worker env vars: WORKER_COORDINATOR_HOST, WORKER_COORDINATOR_PORT, WORKER_DATA_SERVER_HOST, WORKER_DATA_SERVER_PORT, WORKER_CAPACITY"
    echo "  --all             Start all services (default if no flags given)"
    echo "  --data-server     Start only the data-server"
    echo "  --coordinator     Start only the coordinator"
    echo "  --workers [N]     Start N workers (default: 1)"
    echo "  --capacity C      Set capacity per worker (default: 1)"
    echo "  --seeds-count N   Limit coordinator to read only N seeds from the file"
    echo "  --clean           Clean all Maven modules"
    echo "  --no-build        Skip Maven build steps (use existing compiled artifacts)"
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
            --clean)
                CLEAN_BUILD=true
                shift
                ;;
            --no-build)
                SKIP_BUILD=true
                shift
                ;;
            *)
                echo "Unknown flag: $1"
                usage
                ;;
        esac
    done
fi

if ! [[ "$NUM_WORKERS" =~ ^[0-9]+$ ]]; then
    echo "Error: NUM_WORKERS must be numeric (current: $NUM_WORKERS)"
    exit 1
fi

if ! [[ "$WORKER_CAPACITY" =~ ^[0-9]+$ ]]; then
    echo "Error: WORKER_CAPACITY must be numeric (current: $WORKER_CAPACITY)"
    exit 1
fi

if ! [[ "$WORKER_COORDINATOR_PORT" =~ ^[0-9]+$ ]]; then
    echo "Error: WORKER_COORDINATOR_PORT must be numeric (current: $WORKER_COORDINATOR_PORT)"
    exit 1
fi

if ! [[ "$WORKER_DATA_SERVER_PORT" =~ ^[0-9]+$ ]]; then
    echo "Error: WORKER_DATA_SERVER_PORT must be numeric (current: $WORKER_DATA_SERVER_PORT)"
    exit 1
fi

if [[ -n "$SEEDS_COUNT" && ! "$SEEDS_COUNT" =~ ^[0-9]+$ ]]; then
    echo "Error: SEEDS_COUNT must be numeric when provided (current: $SEEDS_COUNT)"
    exit 1
fi

if $SKIP_BUILD && $CLEAN_BUILD && ($START_DATA_SERVER || $START_COORDINATOR || $START_WORKERS); then
    echo "Error: --clean and --no-build cannot be used together when starting services"
    exit 1
fi

if $CLEAN_BUILD && ! $START_DATA_SERVER && ! $START_COORDINATOR && ! $START_WORKERS; then
    echo "Cleaning all modules..."
    mvn -f "$ROOT/pom.xml" clean -q
    echo "Clean complete."
    exit 0
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

build_runtime_classpath() {
    local module="$1"
    local classpath="$ROOT/$module/target/classes:$ROOT/common/target/classes"
    local dependency_dir="$ROOT/$module/target/dependency"

    if [[ -d "$dependency_dir" ]]; then
        classpath="$classpath:$dependency_dir/*"
    fi

    printf '%s' "$classpath"
}

if $SKIP_BUILD; then
    echo "Skipping build steps (--no-build)."
else
    echo "Building all modules..."
    if $CLEAN_BUILD; then
        mvn -f "$ROOT/pom.xml" clean -q
    fi
    mvn -f "$ROOT/pom.xml" install -DskipTests -q
    echo "Preparing runtime dependencies..."
    mvn -f "$ROOT/pom.xml" dependency:copy-dependencies -DincludeScope=runtime -q
fi

if $START_DATA_SERVER; then
    echo "Starting data-server..."
    java -cp "$(build_runtime_classpath data-server)" com.example.dataserver.Main --server 9090 &
    PIDS+=($!)
    sleep 1
fi

if $START_COORDINATOR; then
    echo "Starting coordinator..."
    COORDINATOR_ARGS=(--coordinator)
    if [[ -n "$SEEDS_COUNT" ]]; then
        COORDINATOR_ARGS+=(--seeds-count "$SEEDS_COUNT")
    fi
    java -cp "$(build_runtime_classpath coordinator)" com.example.coordinator.Main "${COORDINATOR_ARGS[@]}" &
    PIDS+=($!)
    sleep 1
fi

if $START_WORKERS; then
    echo "Starting $NUM_WORKERS worker(s)..."
    for i in $(seq 1 "$NUM_WORKERS"); do
        WORKER_ARGS=(
            --coordinator-host "$WORKER_COORDINATOR_HOST"
            --coordinator-port "$WORKER_COORDINATOR_PORT"
            --data-server-host "$WORKER_DATA_SERVER_HOST"
            --data-server-port "$WORKER_DATA_SERVER_PORT"
            --capacity "$WORKER_CAPACITY"
            --worker-id "worker-$i"
        )

        java -cp "$(build_runtime_classpath worker)" com.example.worker.Main "${WORKER_ARGS[@]}" &
        PIDS+=($!)
    done
fi

if [[ ${#PIDS[@]} -eq 0 ]]; then
    echo "No services selected."
    usage
fi

wait "${PIDS[@]}"
cleanup
