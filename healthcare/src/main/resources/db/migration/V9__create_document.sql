CREATE TABLE IF NOT EXISTS DOCUMENT (
    doc_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    content LONGBLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_document_case ON DOCUMENT (case_no);
