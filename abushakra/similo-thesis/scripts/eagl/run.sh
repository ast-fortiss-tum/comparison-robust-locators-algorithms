#!/bin/bash
# Generate EAGL locators inside an isolated container (the stability model is a pickle: loading it runs code).
# Usage: scripts/eagl/run.sh <path-to-EAGL-checkout> <model.joblib relative to the checkout> site [site ...]
# Output: out/eagl/eagl_cache.json and out/eagl/generation_<model>.csv (copy the cache to cache/ to use it).
set -euo pipefail
EAGL=$(cd "$1" && pwd); MODEL=$2; shift 2
HERE=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$HERE/out/eagl"
docker build -q -t eagl-bridge "$HERE/scripts/eagl" >/dev/null
docker run --rm \
  --network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  --user "$(id -u):$(id -g)" \
  -v "$EAGL":/eagl:ro \
  -v "$HERE/scripts":/work/scripts:ro \
  -v "$HERE/sites":/work/sites:ro \
  -v "$HERE/out/eagl":/work/out \
  eagl-bridge --eagl /eagl --model "/eagl/$MODEL" --cache out/eagl_cache.json --report-dir out "$@"
