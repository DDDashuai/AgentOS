#!/usr/bin/env bash
set -euo pipefail

# Activate the Python virtual environment (adjust path as needed)
VENV_PATH="${VENV_PATH:-./venv}"

if [ -d "$VENV_PATH" ]; then
    source "$VENV_PATH/bin/activate"
fi

MODEL="${MODEL:-Qwen/Qwen2.5-1.5B-Instruct}"
EMBED_PORT="${EMBED_PORT:-8081}"

python embed_server.py \
    --model "$MODEL" \
    --port "$EMBED_PORT"
