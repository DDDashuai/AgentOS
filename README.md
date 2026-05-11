# AgentOS

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)
![React](https://img.shields.io/badge/React-19-blue.svg)
![Vite](https://img.shields.io/badge/Vite-8-646CFF.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)
![MLX](https://img.shields.io/badge/MLX-local-orange.svg)

**AgentOS** is a full-stack AI Agent platform powered by local LLM inference via Apple MLX. It features a Spring Boot backend with LangChain4j for LLM integration, tool calling, dynamic schema discovery, file upload (CSV/XLSX/PDF) analysis, PostgreSQL persistence, and human-in-the-loop (HITL) approval, with a React dashboard for chat interaction and data visualization.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   React Dashboard                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Chat     │ │ DataViz  │ │ Pipeline │ │ FileUpload │  │
│  │ Panel    │ │ ECharts  │ │ Monitor  │ │ Panel      │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
└──────────────────────┬───────────────────────────────────┘
                       │ SSE Streaming / REST
┌──────────────────────▼───────────────────────────────────┐
│              Spring Boot Backend (port 9090)              │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  ChatController (SSE)                               │  │
│  │    ↓ AgentHarness (agent loop with MAX_ITERATIONS)  │  │
│  │    ↓ PromptOrchestrator (dynamic system prompt)     │  │
│  │    ↓ ConcurrencyPartitioningEngine (virtual threads)│  │
│  │    ↓ Security: Validation → HumanApproval (HITL)    │  │
│  └─────────────────────────────────────────────────────┘  │
│                           │                                │
│   ┌───────────────────────┼───────────────────────────┐    │
│   ▼                       ▼                           ▼    │
│ database_query    data_visualization          file_export  │
│ query_uploaded    local_search                bash_exec    │
│ _data                                                  │   │
│   │                       │                           │   │
│   ▼                       ▼                           ▼   │
│ PostgreSQL 16      ECharts Config               CSV File   │
│ + pgvector(未来)                                              │
└──────────────────────────────────────────────────────────┘
                         │ POST /v1/chat
                         ▼
┌──────────────────────────────────────────────────────────┐
│            MLX Local LLM Server (port 8080)               │
│          Qwen2.5-1.5B-Instruct (Apple Silicon)            │
│            + Embedding endpoint (for RAG)                 │
└──────────────────────────────────────────────────────────┘
```

## Features

### Core Platform
- **Local LLM Inference** — Runs Qwen2.5-1.5B-Instruct on Apple Silicon via MLX, no cloud API dependency
- **Dynamic Schema Discovery** — Automatically detects database tables and columns at startup via `information_schema`
- **Tool Calling** — 6 built-in tools with JSON Schema parameter definitions (LangChain4j)
- **Agent Loop** — Iterative LLM → tool execution → LLM cycle with `MAX_ITERATIONS=10` safety limit
- **SSE Streaming** — Real-time streaming of thinking events, tool execution results, final responses
- **Human-in-the-Loop** — Destructive tools (file_export, bash_execution) require manual approval
- **Data Visualization** — LLM generates ECharts configurations dynamically from query results
- **Java 21 Virtual Threads** — Non-blocking concurrent tool execution

### Persistence
- **PostgreSQL 16** — Production-grade relational database replacing SQLite
- **Flyway Migrations** — Version-controlled schema management (`V1__init.sql`)
- **JPA Entities** — `chat_sessions`, `chat_messages`, `uploaded_files`, `tool_approvals`
- **Disk Storage** — Uploaded files stored on disk (`uploads/{sessionId}/`), metadata in DB
- **Session Persistence** — Chat messages survive restarts, loaded from DB on session resume

### File Upload & Analysis
- **CSV** — OpenCSV parsing with header detection and type inference
- **XLSX** — Apache POI sheet extraction (first sheet, up to 5000 rows)
- **PDF** — Apache Tika text extraction with tabular data detection
- **Preview** — First 5 rows returned to LLM for context
- **`query_uploaded_data` Tool** — LLM can query uploaded file data on demand

### Security
- **Validation Interceptor** — Input validation and SQL injection prevention (only SELECT queries allowed)
- **Human Approval Interceptor** — Destructive tools blocked until user approves via REST endpoint
- **Audit Trail** — All tool approvals persisted in `tool_approvals` table

## Project Structure

```
agentos-core/              # Spring Boot backend (Java 21)
├── src/main/java/com/agentos/core/
│   ├── config/            # LangChain4j, Async configuration
│   ├── controller/        # ChatController, FileUploadController
│   ├── engine/            # ConcurrencyPartitioningEngine
│   ├── entity/            # JPA entities (ChatSession, ChatMessage, etc.)
│   ├── file/              # FileParserService, FileStorageService, UploadedFile
│   ├── harness/           # AgentHarness (agent loop)
│   ├── llm/               # PromptOrchestrator (dynamic system prompt)
│   ├── repository/        # JPA repositories
│   ├── security/          # ValidationInterceptor, HumanApprovalInterceptor
│   ├── session/           # ApprovalService (HITL)
│   ├── state/             # AgentState, ChatMessage, AgentEvent
│   ├── tool/              # ToolExecutionRequest/Result, ToolDefinition
│   └── tools/impl/        # database_query, data_visualization, query_uploaded_data, etc.
├── src/main/resources/
│   ├── application.yml    # Server, DB, MLX, upload config
│   └── db/migration/
│       └── V1__init.sql   # Flyway schema migration
└── pom.xml

agentos-dashboard/         # React frontend (React 19 + Vite 8 + Tailwind)
├── src/
│   ├── App.tsx            # Main layout with sidebar + main area
│   ├── components/
│   │   ├── ChatPanel.tsx          # Chat message display
│   │   ├── DataVizPanel.tsx       # ECharts chart renderer
│   │   ├── FileUploadPanel.tsx    # Drag-and-drop file upload
│   │   └── PipelineMonitor.tsx    # Agent pipeline status
│   └── hooks/
│       ├── useAgentConversation.ts  # SSE streaming + HITL
│       └── useFileUpload.ts         # File upload state
├── vite.config.ts         # Vite config with /api proxy
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
- PostgreSQL 16+

### 0. Setup PostgreSQL

```bash
# Install PostgreSQL 16 via Homebrew
brew install postgresql@16
brew services start postgresql@16

# Create database and user
psql postgres -c "CREATE USER agentos WITH PASSWORD 'agentos123';"
psql postgres -c "CREATE DATABASE agentos OWNER agentos;"
psql -d agentos -c "GRANT CREATE ON SCHEMA public TO agentos;"
```

### 1. Start the MLX LLM Server

```bash
cd agentos-mlx-server

# Create virtual environment (first time)
python3 -m venv venv
source venv/bin/activate
pip install mlx-lm

# Start the server (~1.5B model, ~3GB download)
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

> Flyway will automatically create the required tables on first startup.

### 3. Start the Frontend

```bash
cd agentos-dashboard
npm install
npm run dev
# Dashboard at http://127.0.0.1:5173
```

### 4. Test the Pipeline

```bash
# Basic chat query
curl -s --max-time 120 -X POST http://127.0.0.1:9090/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Query all tables in the database and describe the schema"}'
```

```bash
# File upload
curl -s -X POST http://127.0.0.1:9090/api/upload \
  -F "file=@/path/to/data.csv" \
  -F "sessionId=test-session"
```

## API Reference

### `POST /api/chat` — Agent Chat (SSE Stream)

Request:
```json
{"message": "Query sales data and create a bar chart"}
```

Response (Server-Sent Events stream):
```
data: {"type":"thinking","thought":"Sending to LLM..."}
data: {"type":"tool_execution","results":[{"toolName":"database_query","success":true,...}]}
data: {"type":"hitl_required","toolName":"file_export","message":"..."}
data: {"type":"final","response":"Here is the result...","sessionId":"..."}
data: {"type":"[DONE]"}
```

### `POST /api/chat/approve` — Approve Destructive Tool

Request:
```json
{"sessionId":"...","toolName":"file_export"}
```

### `POST /api/upload` — Upload File (Multipart)

Request (multipart/form-data):
| Field | Type | Required |
|-------|------|----------|
| `file` | File | Yes (csv, xlsx, pdf, max 10MB) |
| `sessionId` | String | No (auto-generated if omitted) |

### `GET /api/upload/{sessionId}` — List Uploaded Files

Returns metadata for all files uploaded in the session.

## Event Types

| Event | Description |
|-------|-------------|
| `thinking` | LLM is processing (status update with thought text) |
| `tool_execution` | Tool was executed, results included |
| `hitl_required` | Destructive tool blocked, needs user approval |
| `final` | Final response from the agent with sessionId |
| `[DONE]` | Stream complete |

## Tool System

| Tool | Description | Destructive | Data Source |
|------|-------------|-------------|-------------|
| `database_query` | Execute SQL SELECT queries | No | PostgreSQL |
| `data_visualization` | Generate ECharts chart config | No | Query results |
| `query_uploaded_data` | Query uploaded file data | No | In-memory cache → Disk |
| `local_search` | Search local files | No | Filesystem |
| `file_export` | Export data to CSV | Yes | Runtime |
| `bash_execution` | Execute bash commands | Yes | Shell |

Destructive tools require human approval via the HITL flow before execution.

## Configuration

### Backend (`application.yml`)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: http://127.0.0.1:8080/v1   # MLX server endpoint
      model-name: Qwen/Qwen2.5-1.5B-Instruct
      temperature: 0.7

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentos
    username: agentos
    password: agentos123

agentos:
  upload:
    max-file-size: 10485760  # 10MB
    allowed-types: csv,xlsx,pdf
    max-preview-rows: 5
    max-rows-per-file: 5000
```

To use a different model (e.g., Qwen2.5-7B-Instruct), change `model-name` in `application.yml` and the corresponding model name in `start_server.sh`.

## Roadmap

- [x] **Phase 0**: Core agent loop, tool system, SSE streaming, ECharts visualization
- [x] **Phase 1**: PostgreSQL persistence, Flyway migrations, file disk storage
- [ ] **Phase 2**: RAG Knowledge Base (pgvector embeddings, semantic search)
- [ ] **Phase 3**: Rich data visualization (multi-series charts, dashboards)
- [ ] **Phase 4**: Data table/spreadsheet (AG Grid, inline editing, pagination)
- [ ] **Phase 5**: User system (JWT auth, Spring Security, data isolation)

## License

MIT
