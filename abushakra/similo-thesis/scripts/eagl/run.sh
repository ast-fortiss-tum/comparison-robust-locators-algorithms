#!/bin/bash
# Generate EAGL locators inside an isolated container (the stability models are pickles: loading one runs code).
# Usage: scripts/eagl/run.sh <EAGL checkout> <models dir> [eagl_generate.py options] site [site ...]
#   The checkout is mounted at /eagl and the models dir at /models (read-only), e.g.
#   scripts/eagl/run.sh ../EAGL ../EAGL/database --model /models/cross_training.joblib --cache out/eagl_cache.json amazon
#   scripts/eagl/run.sh ../EAGL ../rq4 --scaled --model-map /work/scripts/eagl/rq4_models.json --cache out/eagl_scaled_cache.json amazon
# Output goes to out/eagl (mounted at /work/out); copy the cache to cache/ to use it.
set -euo pipefail
EAGL=$(cd "$1" && pwd); MODELS=$(cd "$2" && pwd); shift 2
HERE=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$HERE/out/eagl"
docker build -q -t eagl-bridge "$HERE/scripts/eagl" >/dev/null
docker run --rm \
  --network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  --user "$(id -u):$(id -g)" \
  -v "$EAGL":/eagl:ro \
  -v "$MODELS":/models:ro \
  -v "$HERE/scripts":/work/scripts:ro \
  -v "$HERE/sites":/work/sites:ro \
  -v "$HERE/out/eagl":/work/out \
  eagl-bridge --eagl /eagl --report-dir out "$@"
