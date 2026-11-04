#!/bin/bash
# Build APK release cho buổi demo: app gọi server qua https://shop.devthaiduyvu.bid
# và gửi kèm Service Token của Cloudflare Access.
# Token đọc từ tệp ngoài repo (mặc định ~/.config/klcn/cf-access-token.env) gồm 2 dòng:
#   CF_ACCESS_CLIENT_ID=...
#   CF_ACCESS_CLIENT_SECRET=...
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$PROJECT_ROOT/flutter"

TOKEN_FILE="${CF_TOKEN_FILE:-$HOME/.config/klcn/cf-access-token.env}"
[ -f "$TOKEN_FILE" ] || { echo "Không thấy tệp token: $TOKEN_FILE"; exit 1; }
set -a; source "$TOKEN_FILE"; set +a
: "${CF_ACCESS_CLIENT_ID:?Thiếu CF_ACCESS_CLIENT_ID trong $TOKEN_FILE}"
: "${CF_ACCESS_CLIENT_SECRET:?Thiếu CF_ACCESS_CLIENT_SECRET trong $TOKEN_FILE}"

API_BASE_URL="${API_BASE_URL:-https://shop.devthaiduyvu.bid}"

flutter build apk --release \
  --dart-define=API_BASE_URL="$API_BASE_URL" \
  --dart-define=CF_ACCESS_CLIENT_ID="$CF_ACCESS_CLIENT_ID" \
  --dart-define=CF_ACCESS_CLIENT_SECRET="$CF_ACCESS_CLIENT_SECRET"

mkdir -p "$PROJECT_ROOT/.local/demo"
cp build/app/outputs/flutter-apk/app-release.apk "$PROJECT_ROOT/.local/demo/dien-viet-demo.apk"
echo "APK: $PROJECT_ROOT/.local/demo/dien-viet-demo.apk (chứa token, KHÔNG commit hay chia sẻ công khai)"
