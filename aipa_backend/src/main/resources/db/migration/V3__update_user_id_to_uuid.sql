-- Create a temporary table with UUID
CREATE TABLE users_new (
    id UUID PRIMARY KEY,
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

-- Copy data from old table to new table
INSERT INTO users_new (
    id,
    email,
    password,
    full_name,
    profile_image,
    account_created,
    last_login,
    is_active,
    email_verified,
    verification_token,
    reset_token,
    reset_token_expiry,
    provider
)
SELECT 
    gen_random_uuid(),
    email,
    password,
    full_name,
    profile_image,
    account_created,
    last_login,
    is_active,
    email_verified,
    verification_token,
    reset_token,
    reset_token_expiry,
    provider
FROM users;

-- Drop the old table
DROP TABLE users;

-- Rename the new table to the original name
ALTER TABLE users_new RENAME TO users;

-- Recreate indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_verification_token ON users(verification_token);
CREATE INDEX idx_users_reset_token ON users(reset_token); 