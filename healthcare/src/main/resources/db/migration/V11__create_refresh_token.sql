CREATE TABLE IF NOT EXISTS REFRESH_TOKEN (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_sw VARCHAR(1) NOT NULL DEFAULT 'N',
    revoked_sw VARCHAR(1) NOT NULL DEFAULT 'N',
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_family ON REFRESH_TOKEN (family_id);
