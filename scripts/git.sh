#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
docker run --rm -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0=/repo -v "$PROJECT_ROOT:/repo" -w /repo alpine/git "$@"
