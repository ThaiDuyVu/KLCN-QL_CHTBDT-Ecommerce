#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$PROJECT_ROOT"
for name in backend frontend chatbot; do
  pidfile=".local/pids/$name.pid"
  if [ -f "$pidfile" ]; then
    pid="$(cat "$pidfile")"
    process_cwd="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' || true)"
    if kill -0 "$pid" 2>/dev/null && [[ "$process_cwd" == "$PROJECT_ROOT/"* ]] && ps -p "$pid" -o command= | grep -Eq 'backend-0.0.1-SNAPSHOT.jar|uvicorn app.main|node_modules/vite/bin/vite.js'; then
      kill -TERM "$pid"
    fi
  fi
done
if command -v adb >/dev/null 2>&1; then
  while read -r device state; do
    [ "$state" = device ] || continue
    case "$device" in
      emulator-*)
        avd_name="$(adb -s "$device" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
        if [ "$avd_name" = ecommerce_android ]; then
          adb -s "$device" shell am force-stop com.example.ecommerce_app
          adb -s "$device" emu kill
        fi
        ;;
    esac
  done < <(adb devices | tail -n +2)
fi
if [ -d "$GRADLE_USER_HOME/daemon" ]; then
  (cd flutter/android; bash gradlew --stop)
fi
docker compose -p klcn-ecommerce stop
