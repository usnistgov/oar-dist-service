#!/usr/bin/env bash
#
# Runs HTTP benchmarks against the locally running OAR distribution service.
# Requires: wrk (brew install wrk)
#
# Usage:
#   1. Setup data:     bash scripts/benchmark/setup-bench-data.sh
#   2. Start service:  (see instructions below)
#   3. Run benchmark:  bash scripts/benchmark/run-benchmark.sh
#
set -euo pipefail

BASE_URL="${1:-http://localhost:8083/od}"
DURATION="${2:-10s}"

# Check that wrk is available
if ! command -v wrk &>/dev/null; then
    echo "ERROR: wrk not found. Install with: brew install wrk"
    exit 1
fi

# Check the service is up
if ! curl -sf "$BASE_URL/ds/" >/dev/null 2>&1; then
    echo "ERROR: Service not responding at $BASE_URL/ds/"
    echo ""
    echo "Start the service first:"
    echo "  1. Copy the bench profile:"
    echo "     cp scripts/benchmark/application-bench.yml src/main/resources/application-bench.yml"
    echo "  2. Run:"
    echo "     mvn spring-boot:run -Dspring-boot.run.profiles=bench"
    exit 1
fi

echo "============================================================="
echo "  OAR Distribution Service - HTTP Benchmark"
echo "  Target: $BASE_URL"
echo "  Duration per test: $DURATION"
echo "============================================================="
echo ""

# ---- Test 1: Version endpoint (baseline, no I/O) ----
echo "--- Test 1: Version endpoint (baseline, no file I/O) ---"
wrk -t2 -c10 -d"$DURATION" "$BASE_URL/ds/"
echo ""

# ---- Test 2: Single file, single connection (latency) ----
echo "--- Test 2: Single 1MB file, 1 connection (latency baseline) ---"
wrk -t1 -c1 -d"$DURATION" "$BASE_URL/ds/mds-bench/data/file-1m-1.dat"
echo ""

# ---- Test 3: Single file, concurrent connections ----
echo "--- Test 3: Single 1MB file, 16 concurrent connections ---"
wrk -t4 -c16 -d"$DURATION" "$BASE_URL/ds/mds-bench/data/file-1m-1.dat"
echo ""

# ---- Test 4: Multiple different files, concurrent connections ----
# wrk lua script for rotating through different files
LUA_SCRIPT=$(mktemp /tmp/wrk-bench-XXXXX.lua)
cat > "$LUA_SCRIPT" <<'LUAEOF'
-- Rotate through 20 different 1MB files
counter = 0
function request()
    counter = counter + 1
    local id = (counter % 20) + 1
    local path = "/od/ds/mds-bench/data/file-1m-" .. id .. ".dat"
    return wrk.format("GET", path)
end
LUAEOF

echo "--- Test 4: 20 different 1MB files, 16 concurrent connections ---"
wrk -t4 -c16 -d"$DURATION" -s "$LUA_SCRIPT" "$BASE_URL"
echo ""

# ---- Test 5: Different files, high concurrency ----
echo "--- Test 5: 20 different 1MB files, 64 concurrent connections ---"
wrk -t8 -c64 -d"$DURATION" -s "$LUA_SCRIPT" "$BASE_URL"
echo ""

# ---- Test 6: Large files, concurrent ----
LUA_LARGE=$(mktemp /tmp/wrk-bench-large-XXXXX.lua)
cat > "$LUA_LARGE" <<'LUAEOF'
-- Rotate through 5 different 10MB files
counter = 0
function request()
    counter = counter + 1
    local id = (counter % 5) + 1
    local path = "/od/ds/mds-bench/data/file-10m-" .. id .. ".dat"
    return wrk.format("GET", path)
end
LUAEOF

echo "--- Test 6: 5 different 10MB files, 16 concurrent connections ---"
wrk -t4 -c16 -d"$DURATION" -s "$LUA_LARGE" "$BASE_URL"
echo ""

# ---- Test 7: Stress test ----
echo "--- Test 7: 20 different 1MB files, 128 concurrent connections ---"
wrk -t8 -c128 -d"$DURATION" -s "$LUA_SCRIPT" "$BASE_URL"
echo ""

# Cleanup
rm -f "$LUA_SCRIPT" "$LUA_LARGE"

echo "============================================================="
echo "  Benchmark complete."
echo ""
echo "  Key metrics to compare before/after:"
echo "    - Requests/sec (higher is better)"
echo "    - Latency avg and p99 (lower is better)"
echo "    - Transfer/sec (higher is better)"
echo "============================================================="
