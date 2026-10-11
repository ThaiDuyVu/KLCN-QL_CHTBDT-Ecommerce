#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$PROJECT_ROOT"
mkdir -p .local/logs .local/pids
docker compose -p klcn-ecommerce up -d --wait
start_service() {
  local name="$1" directory="$2"; shift 2
  local pidfile="$PROJECT_ROOT/.local/pids/$name.pid"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "$name đang chạy"; return
  fi
  python3 - "$directory" "$pidfile" "$PROJECT_ROOT/.local/logs/$name.log" "$@" <<'PYTHON'
import subprocess, sys
from pathlib import Path
with open(sys.argv[3], "a") as log:
    process = subprocess.Popen(sys.argv[4:], cwd=sys.argv[1], stdin=subprocess.DEVNULL,
                               stdout=log, stderr=log, start_new_session=True)
Path(sys.argv[2]).write_text(str(process.pid))
PYTHON
}
if [ -x "$PROJECT_ROOT/.local/tools/ollama/ollama" ]; then
  export OLLAMA_HOST="127.0.0.1:11434"
  export OLLAMA_MODELS="$PROJECT_ROOT/.local/ollama-models"
  start_service ollama "$PROJECT_ROOT" "$PROJECT_ROOT/.local/tools/ollama/ollama" serve
fi
start_service backend "$PROJECT_ROOT/backend" java -jar "$PROJECT_ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar" --spring.profiles.active=dev --server.address=127.0.0.1
start_service frontend "$PROJECT_ROOT/Fontend" "$PROJECT_ROOT/.local/tools/node-v22.23.3-darwin-arm64/bin/node" node_modules/vite/bin/vite.js --host 127.0.0.1
start_service chatbot "$PROJECT_ROOT/chatbot-service" "$PROJECT_ROOT/chatbot-service/.venv/bin/python" -m uvicorn app.main:app --host 127.0.0.1 --port 8090 --reload --reload-dir app
echo 'Frontend: http://localhost:5173 | Backend: http://localhost:8080 | Chatbot: http://localhost:8090/docs'
for endpoint in http://localhost:8080/api/auth/csrf http://localhost:5173 http://localhost:8090/api/v1/health; do
  ready=false
  for attempt in {1..60}; do
    if curl -fsS "$endpoint" -o /dev/null 2>/dev/null; then ready=true; break; fi
    sleep 1
  done
  if [ "$ready" != true ]; then echo "Chưa sẵn sàng: $endpoint. Kiểm tra .local/logs/"; exit 1; fi
done
echo 'Các dịch vụ đã sẵn sàng.'
