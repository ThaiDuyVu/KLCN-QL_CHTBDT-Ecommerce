#!/bin/bash
# Chạy hệ thống như một server: dịch vụ nền (start.sh) + bản build web qua Caddy + Cloudflare Tunnel.
# Truy cập: https://shop.devthaiduyvu.bid (chỉ các email được Cloudflare Access cho phép).
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$PROJECT_ROOT"
export PROJECT_ROOT
mkdir -p .local/logs .local/pids

bash scripts/start.sh

# Build lại web khi mã nguồn mới hơn bản build hiện có.
if [ ! -f Fontend/dist/index.html ] || [ -n "$(find Fontend/src Fontend/index.html -newer Fontend/dist/index.html -print -quit)" ]; then
  echo 'Build web...'
  (cd Fontend && npm run -s build)
fi

running() { [ -f ".local/pids/$1.pid" ] && kill -0 "$(cat ".local/pids/$1.pid")" 2>/dev/null; }

if running caddy; then
  echo 'caddy đang chạy'
else
  nohup caddy run --config deploy/Caddyfile --adapter caddyfile >> .local/logs/caddy.log 2>&1 &
  echo $! > .local/pids/caddy.pid
fi

if running tunnel; then
  echo 'tunnel đang chạy'
else
  nohup cloudflared tunnel --config deploy/cloudflared.yml run klcn-shop >> .local/logs/tunnel.log 2>&1 &
  echo $! > .local/pids/tunnel.pid
fi

for attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:8088/api/auth/csrf -o /dev/null 2>/dev/null; then
    echo 'Server sẵn sàng: https://shop.devthaiduyvu.bid (cục bộ: http://127.0.0.1:8088)'
    exit 0
  fi
  sleep 1
done
echo 'Caddy chưa phản hồi. Kiểm tra .local/logs/caddy.log'; exit 1
