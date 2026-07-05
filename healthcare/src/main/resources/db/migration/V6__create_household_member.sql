CREATE TABLE IF NOT EXISTS HOUSEHOLD_MEMBER (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    full_name VARCHAR(255),
    relationship VARCHAR(50) NOT NULL,
    dob DATE,
    member_income DOUBLE,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_household_member_case ON HOUSEHOLD_MEMBER (case_no);
