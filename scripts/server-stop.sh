#!/bin/bash
# Tắt tunnel và Caddy trước (ngắt truy cập từ Internet), rồi tắt các dịch vụ nền.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$PROJECT_ROOT"
for name in tunnel caddy; do
  pidfile=".local/pids/$name.pid"
  if [ -f "$pidfile" ]; then
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null && ps -p "$pid" -o command= | grep -Eq 'cloudflared tunnel --config deploy/cloudflared.yml|caddy run --config deploy/Caddyfile'; then
      kill -TERM "$pid"
    fi
    rm -f "$pidfile"
  fi
done
bash scripts/stop.sh
