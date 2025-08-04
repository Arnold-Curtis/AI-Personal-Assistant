CREATE TABLE session_memories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    chat_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    last_activity TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_context_sent_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_session_memories_user_id ON session_memories(user_id);
CREATE INDEX idx_session_memories_session_id ON session_memories(session_id);
CREATE INDEX idx_session_memories_active ON session_memories(is_active);
CREATE INDEX idx_session_memories_last_activity ON session_memories(last_activity);
CREATE UNIQUE INDEX idx_session_memories_user_session_active ON session_memories(user_id, session_id, is_active) WHERE is_active = true;
