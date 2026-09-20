#!/usr/bin/env bash
# Выполняется при каждом старте контейнера:
#  - гарантирует, что Maven выбран как default в SDKMAN
#  - выставляет права на /var/run/docker.sock (пересоздаётся при restart)

set +u

export SDKMAN_DIR=/usr/local/sdkman

source "${SDKMAN_DIR}/bin/sdkman-init.sh"

sdk default maven 3.9.9 >/dev/null 2>&1 || true

echo "[post-start] mvn: $(command -v mvn || echo 'not found')"

echo "[post-start] fixing docker.sock permissions"
if [ -S /var/run/docker.sock ]; then
  sudo chmod 666 /var/run/docker.sock
fi
