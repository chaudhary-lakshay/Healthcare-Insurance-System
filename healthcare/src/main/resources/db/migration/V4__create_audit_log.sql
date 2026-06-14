CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME NOT NULL,
    actor VARCHAR(255) NOT NULL,
    actor_role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    detail VARCHAR(1000)
);
