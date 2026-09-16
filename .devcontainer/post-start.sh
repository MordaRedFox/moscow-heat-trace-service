#!/usr/bin/env bash
# Выполняется при каждом старте контейнера
# Гарантирует, что Maven выбран как default в SDKMAN

set +u

export SDKMAN_DIR=/usr/local/sdkman

source "${SDKMAN_DIR}/bin/sdkman-init.sh"

sdk default maven 3.9.9 >/dev/null 2>&1 || true

echo "[post-start] mvn: $(command -v mvn || echo 'not found')"
