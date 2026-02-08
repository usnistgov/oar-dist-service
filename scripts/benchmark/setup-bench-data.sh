#!/usr/bin/env bash
#
# Sets up local benchmark data: fake cache volumes with files of various sizes.
# This bypasses the bag extraction path entirely and tests the cached file serving path.
#
# Usage: bash scripts/benchmark/setup-bench-data.sh
#
set -euo pipefail

BENCH_DIR="${1:-/tmp/oar-bench}"
echo "Setting up benchmark data in: $BENCH_DIR"

rm -rf "$BENCH_DIR"
mkdir -p "$BENCH_DIR/lts"        # long-term storage (empty, we test cache hits)
mkdir -p "$BENCH_DIR/cache-vol"  # filesystem cache volume
mkdir -p "$BENCH_DIR/admindir"   # cache manager admin dir

# Generate files of various sizes in the cache volume
# These simulate cached dataset files that are served directly
echo "Generating test files..."

for i in $(seq 1 20); do
    dir="$BENCH_DIR/cache-vol/mds-bench/data"
    mkdir -p "$dir"
    # 1MB files
    dd if=/dev/urandom of="$dir/file-1m-${i}.dat" bs=1024 count=1024 2>/dev/null
done

for i in $(seq 1 5); do
    dir="$BENCH_DIR/cache-vol/mds-bench/data"
    # 10MB files
    dd if=/dev/urandom of="$dir/file-10m-${i}.dat" bs=1024 count=10240 2>/dev/null
done

for i in $(seq 1 2); do
    dir="$BENCH_DIR/cache-vol/mds-bench/data"
    # 50MB files
    dd if=/dev/urandom of="$dir/file-50m-${i}.dat" bs=1024 count=51200 2>/dev/null
done

echo ""
echo "Files created:"
ls -lh "$BENCH_DIR/cache-vol/mds-bench/data/" | tail -n +2
echo ""
total=$(du -sh "$BENCH_DIR/cache-vol" | cut -f1)
echo "Total cache volume size: $total"
echo ""
echo "Benchmark data ready at: $BENCH_DIR"
echo ""
echo "To run the service against this data, use the bench profile:"
echo "  mvn spring-boot:run -Dspring-boot.run.profiles=bench"
