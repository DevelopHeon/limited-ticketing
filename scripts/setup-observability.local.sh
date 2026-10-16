#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_DIR="$TASK_ROOT/.local/observability/secrets"
SECRET_FILE="$SECRET_DIR/grafana_admin_password"
umask 077
mkdir -p "$SECRET_DIR"
chmod 700 "$SECRET_DIR"
if [[ ! -e "$SECRET_FILE" ]]; then
  openssl rand -hex 32 > "$SECRET_FILE"
fi
if [[ ! -s "$SECRET_FILE" ]]; then
  echo 'Empty Grafana secret file: grafana_admin_password' >&2
  exit 1
fi
chmod 600 "$SECRET_FILE"
echo 'Local Grafana secret is ready; existing values were preserved.'
