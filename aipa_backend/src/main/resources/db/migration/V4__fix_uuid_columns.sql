-- Drop existing tables to recreate them with proper UUID handling
DROP TABLE IF EXISTS calendar_events;
DROP TABLE IF EXISTS memories;
DROP TABLE IF EXISTS users;

-- Recreate users table with proper UUID handling
CREATE TABLE users (
    id BLOB PRIMARY KEY,  -- Store UUID as BLOB
    email VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(120) NOT NULL,
    full_name VARCHAR(100),
    profile_image VARCHAR(255),
    account_created TIMESTAMP NOT NULL,
    last_login TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    email_verified BOOLEAN DEFAULT false,
    verification_token VARCHAR(255),
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    provider VARCHAR(20) DEFAULT 'LOCAL'
);

-- Recreate calendar_events table with proper UUID handling
CREATE TABLE calendar_events (
    id BLOB PRIMARY KEY,  -- Store UUID as BLOB
    title VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    description VARCHAR(1000),
    is_all_day BOOLEAN DEFAULT true,
    event_color VARCHAR(50),
    plan_title VARCHAR(255),
    user_id BLOB NOT NULL,  -- Store UUID as BLOB
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Recreate memories table with proper UUID handling
CREATE TABLE memories (
    id BLOB PRIMARY KEY,  -- Store UUID as BLOB
    user_id BLOB NOT NULL,  -- Store UUID as BLOB
    category VARCHAR(100) NOT NULL,
    encrypted_content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT true,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Recreate indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_verification_token ON users(verification_token);
CREATE INDEX idx_users_reset_token ON users(reset_token);
CREATE INDEX idx_calendar_events_user_id ON calendar_events(user_id);
CREATE INDEX idx_memories_user_id ON memories(user_id);
CREATE INDEX idx_memories_category ON memories(category); 