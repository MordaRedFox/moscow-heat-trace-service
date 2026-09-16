#!/usr/bin/env bash
# Выполняется один раз после создания контейнера:
#  - чинит права на ~/.m2 (named volume монтируется от root)
#  - добавляет workspace в git safe.directory
#  - ставит Maven через SDKMAN (если еще не стоит)
#  - прогревает локальный кэш Maven-зависимостей

set +u

echo "[post-create] fixing ~/.m2 ownership"
sudo mkdir -p /home/vscode/.m2
sudo chown -R vscode:vscode /home/vscode/.m2

echo "[post-create] git safe.directory"
git config --global --add safe.directory /workspaces/moscow-heat-trace-service 2>/dev/null || true

echo "[post-create] init sdkman"
export SDKMAN_DIR=/usr/local/sdkman

source "${SDKMAN_DIR}/bin/sdkman-init.sh"

if ! command -v mvn >/dev/null 2>&1; then
  echo "[post-create] installing maven 3.9.9"
  sdk install maven 3.9.9 </dev/null || true
fi

echo "[post-create] maven version:"
mvn -version || true

if [ -f pom.xml ]; then
  echo "[post-create] warming up maven cache"
  mvn -B -q dependency:go-offline -DskipTests || true
else
  echo "[post-create] pom.xml not found, skipping cache warmup"
fi
