#!/bin/bash
# builds an Excalidraw version inside Docker (third-party install scripts stay in the container): build.sh <version>
V=$1; D=/Users/stocco/repos/tools/oracle-work/ex-v$V
docker run --rm -v $D:/src -w /src -e NODE_OPTIONS="--max-old-space-size=3072 --openssl-legacy-provider" -e CI=false node:18-bullseye bash -c "yarn install --frozen-lockfile --ignore-scripts --network-timeout 600000 && yarn build:app:docker" > $D/../build-$V.log 2>&1
echo "$V build rc=$? $(date +%H:%M)" >> $D/../build.status
