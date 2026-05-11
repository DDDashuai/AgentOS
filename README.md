# AgentOS

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)
![React](https://img.shields.io/badge/React-19-blue.svg)
![Vite](https://img.shields.io/badge/Vite-8-646CFF.svg)
![MLX](https://img.shields.io/badge/MLX-local-orange.svg)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)

**AgentOS** is a full-stack AI Agent platform powered by local LLM inference via Apple MLX. It features a Spring Boot backend with LangChain4j for LLM integration, tool calling, and human-in-the-loop (HITL) approval, with a React dashboard for chat interaction and data visualization.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   React Dashboard                    │
│           (SSE streaming, ECharts charts)            │
└────────────────────────┬────────────────────────────┘
                         │ POST /api/chat (SSE)
                         │ POST /api/chat/approve
                         ▼
┌──────────────────────────────────────────────────────┐
│              Spring Boot Backend (port 9090)          │
│                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │ChatController│→ │AgentHarness  │→ │PromptOrch.  │  │
│  │(SSE stream)  │  │(agent loop)  │  │(prompt       │  │
│  └─────────────┘  │              │  │ building)    │  │
│                   │  ┌──────────┐│  └──────┬──────┘  │
│                   │  │Concurr.  ││         │         │
│                   │  │Partition ││         │         │
│                   │  │Engine    ││         │         │
│                   │  └────┬─────┘│         │         │
│                   └───────┼──────┘         │         │
│                           ▼                ▼         │
│  ┌──────────────────────────────────────────────┐    │
│  │           Security Interceptor Chain          │    │
│  │  Validation → Logging → HumanApproval (HITL) │    │
│  └──────────────────────────────────────────────┘    │
│                           │                          │
│            ┌──────────────┼──────────────┐           │
│            ▼              ▼              ▼           │
│     database_query  data_visualiz.  file_export     │
│            │              │              │           │
│            ▼              │              ▼           │
│        SQLite DB    ECharts Config     CSV File      │
└──────────────────────────────────────────────────────┘
                         │ POST /v1/chat
                         ▼
┌──────────────────────────────────────────────────────┐
│            MLX Local LLM Server (port 8080)           │
│          Qwen2.5-1.5B-Instruct (Apple Silicon)        │
└──────────────────────────────────────────────────────┘
```

## Features

- **Local LLM Inference** — Runs Qwen2.5-1.5B-Instruct on Apple Silicon via MLX, no cloud API dependency
- **Tool Calling** — Structured tool definitions with JSON Schema parameters (LangChain4j)
- **Agent Loop** — Iterative LLM → tool execution → LLM cycle with safety limits
- **SSE Streaming** — Real-time streaming of thinking events, tool execution results, and final responses
- **Human-in-the-Loop** — Destructive tools (file export, bash execution) require manual approval
- **Data Visualization** — LLM generates ECharts configurations dynamically from database queries
- **SQLite Integration** — Built-in sales data database for demo queries and charting
- **Virtual Threads** — Java 21 virtual threads for concurrent tool execution
- **Prompt Caching** — Static system prompt separation maximizes MLX prefix cache hit rates

## Project Structure

```
agentos-core/              # Spring Boot backend (Java 21)
├── src/main/java/com/agentos/core/
│   ├── config/            # LangChain4j, Async configuration
│   ├── controller/        # ChatController (SSE streaming)
│   ├── engine/            # ConcurrencyPartitioningEngine
│   ├── harness/           # AgentHarness (agent loop)
│   ├── llm/               # PromptOrchestrator
│   ├── security/          # HumanApprovalInterceptor, ValidationInterceptor
│   ├── session/           # ApprovalService (HITL state)
│   ├── state/             # AgentState, ChatMessage, AgentEvent
│   ├── tool/              # ToolExecutionRequest/Result, ToolDefinition
│   └── tools/impl/        # database_query, data_visualization, etc.
├── src/main/resources/
│   ├── application.yml    # Server config, MLX endpoint, model settings
│   └── agentos_data.db    # SQLite demo database (sales data)
└── pom.xml

agentos-dashboard/         # React frontend (React 19 + Vite 8 + Tailwind)
├── src/
│   ├── App.tsx            # Main layout with chat + visualization panels
│   ├── components/
│   │   ├── ChatPanel.tsx          # Chat message display
│   │   ├── DataVizPanel.tsx       # ECharts chart renderer
│   │   └── PipelineMonitor.tsx    # Agent pipeline status
│   └── hooks/
│       └── useAgentConversation.ts  # SSE streaming hook
├── vite.config.ts         # Vite config with /api proxy to backend
├── tailwind.config.js
└── package.json

agentos-mlx-server/        # MLX local LLM server
└── start_server.sh        # MLX LM server startup script
```

## Quick Start

### Prerequisites

- Java 21+ (JDK 21 recommended)
- Node.js 20+
- Python 3.10+ with MLX installed
- Apple Silicon Mac (M1/M2/M3/M4)

### 1. Start the MLX LLM Server

```bash
cd agentos-mlx-server

# Create virtual environment (first time)
python3 -m venv venv
source venv/bin/activate
pip install mlx-lm

# Start the server (downloads model on first run, ~3GB)
chmod +x start_server.sh
./start_server.sh
# Server runs on http://127.0.0.1:8080
```

If you're in a restricted network (e.g., China), set the HuggingFace mirror:
```bash
export HF_ENDPOINT=https://hf-mirror.com
./start_server.sh
```

### 2. Start the Backend

```bash
cd agentos-core
mvn clean compile
mvn spring-boot:run
# Backend runs on http://127.0.0.1:9090
```

### 3. Start the Frontend

```bash
cd agentos-dashboard
npm install
npm run dev
# Dashboard at http://127.0.0.1:5173
```

### 4. Test the Pipeline

```bash
curl -s --max-time 120 -X POST http://127.0.0.1:9090/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Query all sales data and create a bar chart"}'
```

## API Reference

### `POST /api/chat` — Agent Chat (SSE Stream)

Request:
```json
{"message": "Query all sales data and create a bar chart"}
```

Response (Server-Sent Events stream):
```
data: {"type":"thinking","thought":"Sending to LLM..."}
data: {"type":"tool_execution","results":[{"toolName":"database_query","success":true,...}]}
data: {"type":"thinking","thought":"Sending to LLM..."}
data: {"type":"tool_execution","results":[{"toolName":"data_visualization","success":true,...}]}
data: {"type":"final","text":"Here is the chart..."}
data: [DONE]
```

### `POST /api/chat/approve` — Approve Destructive Tool

Request:
```json
{"sessionId":"...","toolName":"file_export","approved":true}
```

Response:
```json
{"status":"approved"}
```

## Event Types

| Event | Description |
|-------|-------------|
| `thinking` | LLM is processing (status update) |
| `tool_execution` | Tool was executed with results |
| `hitl_required` | Destructive tool needs human approval |
| `final` | Final response from the agent |
| `[DONE]` | Stream complete |

## Tool System

| Tool | Description | Destructive |
|------|-------------|-------------|
| `database_query` | Execute SQL SELECT queries on SQLite | No |
| `data_visualization` | Generate ECharts chart config from data | No |
| `local_search` | Search local files and directories | No |
| `file_export` | Export data to CSV files | Yes |
| `bash_execution` | Execute arbitrary bash commands | Yes |

Destructive tools require human approval via the HITL flow before execution.

## Configuration

### Backend (`application.yml`)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: http://127.0.0.1:8080/v1   # MLX server endpoint
      model-name: Qwen/Qwen2.5-1.5B-Instruct # or larger models
      temperature: 0.7
      timeout: 300s
```

To use a different model (e.g., Qwen2.5-7B-Instruct), change the `model-name` and restart.

## License

MIT
