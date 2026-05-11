# 贡献指南

感谢您对 AgentOS 项目的关注和支持！我们非常欢迎社区的贡献，无论是报告 Bug、提出新功能建议，还是直接提交代码修改。

为了保证项目的质量和开发效率，请在贡献前仔细阅读以下指南。

## 提交 Issue

在提交 Issue 之前，请：
1. 搜索现有的 Issue，确认您的问题或建议是否已经被提出。
2. 尽量提供详尽的信息。如果是 Bug 报告，请包含复现步骤、预期行为、实际行为以及环境信息。

## 分支管理

我们采用标准的 Git Flow 工作流：
- `main`：稳定主分支，包含随时可发布的生产级代码。
- `develop`：开发主分支，所有的新功能开发都在此分支上集成。
- `feature/*`：功能分支，从 `develop` 分支切出，完成后合并回 `develop`。
- `bugfix/*`：Bug 修复分支。

## 提交 Pull Request

1. **Fork 本仓库** 并将您的更改推送到您自己的 Fork 仓库中。
2. **创建分支**：从 `develop` 分支创建一个新的描述性分支。
3. **保持代码风格统一**：
   - Java 代码请遵循 Spring Boot 标准规范。
   - 前端代码请遵循 ESLint 及 Prettier 规则。
4. **编写测试**：如果添加了新功能，请务必包含相应的单元测试。
5. **提交信息规范**：请使用语义化的 Git Commit 信息（如 `feat:`, `fix:`, `docs:`, `refactor:` 等）。
6. **创建 PR**：确保您的 PR 指向 `develop` 分支。

## 架构原则

提交核心代码前，请务必理解本项目的设计理念：

### 1. Agent Harness 循环
- Agent 采用迭代式 LLM → 工具执行 → LLM 循环，最大迭代次数为 10
- 每次迭代调用 LLM，解析 tool calls，执行工具，然后再次调用 LLM
- 最终响应为纯文本（无 tool calls 时结束循环）

### 2. 持久化
- 所有业务数据存储在 PostgreSQL 16 中
- 数据库 schema 由 Flyway 管理（`src/main/resources/db/migration/`）
- JPA Entities 不要直接暴露给 Controller，通过 DTO/Record 隔离
- 上传文件存储在磁盘 `uploads/` 目录，数据库只存元数据

### 3. 动态 Schema 发现
- `DatabaseSchemaProvider` 在启动时自动查询 `information_schema`
- Schema 信息注入到 LLM system prompt，确保模型始终知道表结构
- 新增表无需修改代码，重启后自动发现

### 4. 文件上传
- 支持 CSV（OpenCSV）、XLSX（Apache POI）、PDF（Apache Tika）
- 文件通过 `query_uploaded_data` 工具查询，而非写入临时表
- 预览前 5 行数据注入 LLM context，减少 token 消耗

### 5. 安全性
- 所有工具调用经过拦截器链：Validation → Logging → HumanApproval
- `database_query` 仅允许 SELECT 语句，防止 SQL 注入
- 破坏性工具（file_export, bash_execution）需要人工审批
- 审批记录持久化到 `tool_approvals` 表，可审计

### 6. 异步与非阻塞
- 使用 Java 21 虚拟线程处理 I/O 密集型任务
- SSE 流式响应通过 Reactor Flux 实现
- 工具并发执行由 `ConcurrencyPartitioningEngine` 管理

### 7. 前端状态管理
- SSE stream 通过 `ReadableStream` 在浏览器端解析
- 会话状态由 React hooks 管理（`useAgentConversation`）
- 文件上传状态独立管理（`useFileUpload`）

期待您的参与，让我们一起构建下一代本地 AI Agent 平台！
