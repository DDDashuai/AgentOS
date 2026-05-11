CREATE TABLE IF NOT EXISTS chat_sessions (
    id          UUID PRIMARY KEY,
    title       TEXT,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id           UUID PRIMARY KEY,
    session_id   UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role         TEXT NOT NULL,
    content      TEXT NOT NULL,
    tool_call_id TEXT,
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session
    ON chat_messages(session_id, created_at);

CREATE TABLE IF NOT EXISTS uploaded_files (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    original_name TEXT NOT NULL,
    file_type     TEXT NOT NULL,
    file_path     TEXT,
    headers       JSONB,
    row_count     INTEGER DEFAULT 0,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_uploaded_files_session
    ON uploaded_files(session_id);

CREATE TABLE IF NOT EXISTS tool_approvals (
    id          UUID PRIMARY KEY,
    session_id  UUID NOT NULL,
    tool_name   TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(session_id, tool_name)
);

CREATE INDEX IF NOT EXISTS idx_tool_approvals_session
    ON tool_approvals(session_id);
