#!/usr/bin/env bash
set -euo pipefail

# Activate the Python virtual environment (adjust path as needed)
VENV_PATH="${VENV_PATH:-./venv}"

if [ -d "$VENV_PATH" ]; then
    source "$VENV_PATH/bin/activate"
fi

# Start the MLX LM server with OpenAI-compatible API
# Model can be overridden via the MODEL environment variable
MODEL="${MODEL:-Qwen/Qwen2.5-1.5B-Instruct}"
PORT="${PORT:-8080}"

python -m mlx_lm server \
    --model "$MODEL" \
    --port "$PORT"
