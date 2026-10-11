#!/bin/bash
if [ -n "${ZSH_VERSION:-}" ]; then
  eval 'ENV_SCRIPT_PATH=${(%):-%x}'
else
  ENV_SCRIPT_PATH="${BASH_SOURCE[0]}"
fi
PROJECT_ROOT="$(cd "$(dirname "$ENV_SCRIPT_PATH")/.." && pwd)"

# 1. Cấu hình Java 21 của Windows
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.101-hotspot"

# 2. Cấu hình các công cụ hệ thống của Windows (NodeJS, Python, v.v.)
export PATH="$JAVA_HOME/bin:$PROJECT_ROOT/chatbot-service/.venv/Scripts:/c/Program Files/nodejs:/c/Program Files/Git/cmd:$PATH"

# 3. Cấu hình Android SDK và Flutter trên Windows
export FLUTTER_ROOT="/e/ltdd/flutter"
export ANDROID_HOME="/c/Android/SDK"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# 4. Đưa các công cụ Android vào biến PATH để Git Bash nhận diện lệnh adb, flutter
export PATH="$FLUTTER_ROOT/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export POSTGRES_HOST="ep-odd-poetry-b36qdrad-pooler.c-4.ap-southeast-1.aws.neon.tech"
export POSTGRES_PORT="5432"
export POSTGRES_DB="neondb"
export POSTGRES_USER="neondb_owner"
export POSTGRES_PASSWORD="npg_iwzn48cpLkAG"

# Cấu hình chuỗi JDBC có kèm tham số SSL require để Spring Boot kết nối thành công vào Neon
export SPRING_DATASOURCE_URL="jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB?sslmode=require"
# 6. Cấu hình chuỗi bảo mật JWT siêu bảo mật (bắt buộc dài trên 32 ký tự)
export JWT_SECRET="day_la_chuoi_bi_mat_sieu_bao_mat_cua_du_an_123456789"
export ANDROID_HOME="C:\Users\Admin\AppData\Local\Android\Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
