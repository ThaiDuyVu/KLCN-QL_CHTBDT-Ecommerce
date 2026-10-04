#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/env.sh"
export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
cd "$PROJECT_ROOT/backend"
sh mvnw -B -Dapi.version=1.44 test "$@"
