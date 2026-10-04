#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
(cd "$PROJECT_ROOT/backend"; sh mvnw -B -DskipTests package)
(cd "$PROJECT_ROOT/Fontend"; npm run build)
