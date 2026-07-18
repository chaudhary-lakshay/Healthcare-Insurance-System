-- ShedLock's single lock row per scheduled task. JDBC-only (JdbcTemplateLockProvider),
-- not a JPA entity, so ddl-auto: validate never looks at it.
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
