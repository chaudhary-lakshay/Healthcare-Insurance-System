CREATE TABLE IF NOT EXISTS PLAN_RULE (
    rule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(255) NOT NULL UNIQUE,
    income_limit DOUBLE,
    benefit_amt DOUBLE
);

INSERT INTO PLAN_RULE (plan_name, income_limit, benefit_amt) VALUES
    ('SNAP', 300, 200),
    ('CCAP', 300, 300),
    ('MEDCARE', NULL, 350),
    ('MEDAID', 300, 200),
    ('CAJW', NULL, 300),
    ('QHP', NULL, NULL);
