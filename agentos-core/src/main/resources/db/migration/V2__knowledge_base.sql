CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id              UUID PRIMARY KEY,
    document_name   TEXT NOT NULL,
    chunk_index     INTEGER NOT NULL DEFAULT 0,
    chunk_text      TEXT NOT NULL,
    embedding       double precision[] NOT NULL,
    token_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_document
    ON knowledge_chunks(document_name);
