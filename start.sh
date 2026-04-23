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

if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

START_DATA_SERVER="${DATA_SERVER_START:-${START_DATA_SERVER:-false}}"
START_COORDINATOR="${COORDINATOR_START:-${START_COORDINATOR:-false}}"
START_WORKERS="${WORKER_START:-${START_WORKERS:-false}}"
NUM_WORKERS="${WORKER_COUNT:-${NUM_WORKERS:-1}}"
WORKER_CAPACITY="${WORKER_CAPACITY:-1}"
SEEDS_COUNT="${COORDINATOR_SEEDS_COUNT:-${SEEDS_COUNT:-}}"
CLEAN_BUILD="${BUILD_CLEAN:-${CLEAN_BUILD:-false}}"

usage() {
    echo "Usage: $0 [--all] [--data-server] [--coordinator] [--workers [N]] [--capacity C] [--seeds-count N] [--clean]"
    echo "  Uses .env file at: $ENV_FILE (if present)"
    echo "  Preferred env vars: DATA_SERVER_*, COORDINATOR_*, WORKER_*, BUILD_*"
    echo "  --all             Start all services (default if no flags given)"
    echo "  --data-server     Start only the data-server"
    echo "  --coordinator     Start only the coordinator"
    echo "  --workers [N]     Start N workers (default: 1)"
    echo "  --capacity C      Set capacity per worker (default: 1)"
    echo "  --seeds-count N   Limit coordinator to read only N seeds from the file"
    echo "  --clean           Clean all Maven modules"
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

if [[ -n "$SEEDS_COUNT" && ! "$SEEDS_COUNT" =~ ^[0-9]+$ ]]; then
    echo "Error: SEEDS_COUNT must be numeric when provided (current: $SEEDS_COUNT)"
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

echo "Building all modules..."
if $CLEAN_BUILD; then
    mvn -f "$ROOT/pom.xml" clean -q
fi
mvn -f "$ROOT/pom.xml" install -DskipTests -q
echo "Preparing runtime dependencies..."
mvn -f "$ROOT/pom.xml" dependency:copy-dependencies -DincludeScope=runtime -q

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
        java -cp "$(build_runtime_classpath worker)" com.example.worker.Main --worker-id "worker-$i" --capacity "$WORKER_CAPACITY" &
        PIDS+=($!)
    done
fi

if [[ ${#PIDS[@]} -eq 0 ]]; then
    echo "No services selected."
    usage
fi

wait "${PIDS[@]}"
cleanup
