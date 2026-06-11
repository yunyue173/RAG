#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-ubuntu}"
APP_DIR="${APP_DIR:-/opt/rag-doc-qa}"
SERVICE_NAME="${SERVICE_NAME:-rag-doc-qa}"
APP_PORT="${APP_PORT:-8080}"
ENV_DIR="/etc/${SERVICE_NAME}"
ENV_FILE="${ENV_DIR}/rag-doc-qa.env"

echo "Installing Java 21..."
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk

echo "Creating application directories..."
sudo mkdir -p "${APP_DIR}/current" "${APP_DIR}/data/rag-store" "${APP_DIR}/logs" "${ENV_DIR}"
sudo chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "Creating ${ENV_FILE} ..."
  sudo tee "${ENV_FILE}" >/dev/null <<ENV
APP_PORT=${APP_PORT}
RAG_STORAGE_DIR=${APP_DIR}/data/rag-store

# DeepSeek / OpenAI compatible LLM settings.
# Fill LLM_API_KEY on the server. Do not commit real API keys to GitHub.
LLM_API_KEY=
LLM_BASE_URL=https://api.deepseek.com
LLM_CHAT_MODEL=deepseek-chat
LLM_EMBEDDING_ENABLED=false
ENV
fi

echo "Creating systemd service..."
sudo tee "/etc/systemd/system/${SERVICE_NAME}.service" >/dev/null <<SERVICE
[Unit]
Description=RAG Doc QA Spring Boot Application
After=network.target

[Service]
User=${APP_USER}
WorkingDirectory=${APP_DIR}/current
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${APP_DIR}/current/rag-doc-qa.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
SERVICE

sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}"

echo
echo "Server bootstrap finished."
echo "Next steps:"
echo "1. Edit ${ENV_FILE} and fill LLM_API_KEY."
echo "2. Put GitHub Actions secrets in your GitHub repository."
echo "3. Push code to main/master to deploy automatically."
