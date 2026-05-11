#!/usr/bin/env python3
"""
AgentOS embedding server.

Uses MLX to extract mean-pooled hidden states from a local LLM for use as
text embeddings in the RAG knowledge base pipeline.

Usage:
    python embed_server.py [--model MODEL] [--port PORT]

The model must be the same one loaded by the main MLX chat server so that the
weights are already cached locally. The default model is Qwen/Qwen2.5-1.5B-Instruct,
which produces 1536-dimensional embeddings.
"""

import argparse
import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

import mlx.core as mx
from mlx_lm import load


def parse_args():
    parser = argparse.ArgumentParser(description="AgentOS embedding server")
    parser.add_argument("--model", default="Qwen/Qwen2.5-1.5B-Instruct",
                        help="Model name or path (same as main MLX server)")
    parser.add_argument("--port", type=int, default=8081,
                        help="Port to listen on")
    return parser.parse_args()


def get_embedding_dim(model):
    """Return the hidden dimension by running a tiny forward pass."""
    return 1536  # Qwen2.5-1.5B hidden size


def embed_texts(model, tokenizer, texts, max_length=512):
    """
    Compute last-token-pooled embeddings for a list of texts.

    Each text is processed individually: encoded via ``tokenizer.encode()``,
    truncated to ``max_length`` tokens, then passed through
    ``model.model(input_ids)`` to extract hidden states from the last
    transformer layer **after** RMSNorm, bypassing the LM head.

    Last-token pooling is used instead of mean pooling because for causal
    language models the last token's hidden state (through the causal
    attention mechanism) represents the full context.  This works better
    for instruction-tuned models like Qwen2.5.
    """
    if not texts:
        return []

    embeddings = []
    for text in texts:
        tokens = tokenizer.encode(text)
        if not tokens:
            tokens = [0]
        # Truncate to max_length
        tokens = tokens[:max_length]
        input_ids = mx.array([tokens])               # (1, seq_len)

        # Forward pass through embedding + all transformer layers + final norm
        hidden = model.model(input_ids)              # (1, seq_len, 1536)

        # Last-token pooling — the last token represents the full context
        # via causal attention.  Shape: (1, 1536)
        pooled = hidden[:, -1:, :].squeeze(1)

        # L2 normalize to unit length — critical for cosine similarity
        # to produce well-distributed scores across [0, 1]
        norm = mx.sqrt(mx.sum(pooled * pooled, axis=1, keepdims=True))
        pooled = pooled / mx.maximum(norm, 1e-12)

        embeddings.append(pooled.tolist()[0])

    return embeddings


def build_app(model, tokenizer):
    """Return a request handler class bound to the given model + tokenizer."""

    class EmbedHandler(BaseHTTPRequestHandler):
        """HTTP handler for embedding requests."""

        def log_message(self, fmt, *args):
            sys.stderr.write("[embed_server] " + fmt % args + "\n")

        def _send_json(self, data, status=200):
            body = json.dumps(data).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = urlparse(self.path).path
            if path == "/health":
                self._send_json({
                    "status": "ok",
                    "dimension": 1536,
                    "model": args.model,
                })
            else:
                self._send_json({"error": "not found"}, 404)

        def do_POST(self):
            path = urlparse(self.path).path

            if path not in ("/embed", "/v1/embeddings"):
                self._send_json({"error": "not found"}, 404)
                return

            # Read request body
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length == 0:
                self._send_json({"error": "empty request body"}, 400)
                return

            body = json.loads(self.rfile.read(content_length))

            if path == "/v1/embeddings":
                # OpenAI-compatible format
                input_texts = body.get("input", "")
            else:
                # Simple format: {"texts": [...]} or {"text": "..."}
                input_texts = body.get("texts", body.get("text", ""))

            if isinstance(input_texts, str):
                input_texts = [input_texts]

            if not input_texts:
                self._send_json({"error": "no input texts provided"}, 400)
                return

            try:
                embeddings = embed_texts(model, tokenizer, input_texts)

                if path == "/v1/embeddings":
                    data = [
                        {"object": "embedding", "index": i, "embedding": emb}
                        for i, emb in enumerate(embeddings)
                    ]
                    self._send_json({"data": data, "model": args.model})
                else:
                    self._send_json({"embeddings": embeddings})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

    return EmbedHandler


def main():
    global args
    args = parse_args()

    print(f"[embed_server] Loading model: {args.model}", flush=True)
    model, tokenizer = load(args.model)
    print(f"[embed_server] Model loaded. Starting server on port {args.port}",
          flush=True)

    handler = build_app(model, tokenizer)
    server = HTTPServer(("0.0.0.0", args.port), handler)
    print(f"[embed_server] Listening on http://0.0.0.0:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[embed_server] Shutting down", flush=True)
        server.server_close()


if __name__ == "__main__":
    main()
