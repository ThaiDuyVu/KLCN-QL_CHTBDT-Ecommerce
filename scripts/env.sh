#!/bin/bash
if [ -n "${ZSH_VERSION:-}" ]; then
  eval 'ENV_SCRIPT_PATH=${(%):-%x}'
else
  ENV_SCRIPT_PATH="${BASH_SOURCE[0]}"
fi
PROJECT_ROOT="$(cd "$(dirname "$ENV_SCRIPT_PATH")/.." && pwd)"
export JAVA_HOME="$PROJECT_ROOT/.local/tools/jdk-21.0.12.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PROJECT_ROOT/.local/tools/node-v22.23.3-darwin-arm64/bin:$PROJECT_ROOT/.local/tools/uv-aarch64-apple-darwin:$PROJECT_ROOT/chatbot-service/.venv/bin:$PATH"
export FLUTTER_ROOT="$PROJECT_ROOT/.local/tools/flutter"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PROJECT_ROOT/.local/gradle"
export PATH="$FLUTTER_ROOT/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
