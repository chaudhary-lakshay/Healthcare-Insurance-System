CREATE TABLE IF NOT EXISTS NOTICE (
    notice_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    notice_type VARCHAR(100) NOT NULL,
    subject VARCHAR(255),
    body VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    sent_at DATETIME
);
