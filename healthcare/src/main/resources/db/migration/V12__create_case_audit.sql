-- Envers revision bookkeeping. Shapes must match what Hibernate 6.5 maps for
-- DefaultRevisionEntity + the @Audited entities, or ddl-auto: validate kills startup.
CREATE TABLE IF NOT EXISTS REVINFO (
    REV INT AUTO_INCREMENT PRIMARY KEY,
    REVTSTMP BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS DC_CASES_AUD (
    case_no BIGINT NOT NULL,
    REV INT NOT NULL,
    REVTYPE TINYINT,
    app_id BIGINT,
    plan_id BIGINT,
    case_status VARCHAR(40),
    PRIMARY KEY (case_no, REV)
);

-- ssn/bank columns are @NotAudited on purpose — no immutable PII copies
CREATE TABLE IF NOT EXISTS ELIGIBILITY_DETERMINATION_AUD (
    ed_trace_id BIGINT NOT NULL,
    REV INT NOT NULL,
    REVTYPE TINYINT,
    case_no BIGINT,
    holder_name VARCHAR(255),
    plan_name VARCHAR(255),
    plan_status VARCHAR(50),
    plan_start_date DATE,
    plan_end_date DATE,
    benefit_amt DOUBLE,
    denial_reason VARCHAR(255),
    PRIMARY KEY (ed_trace_id, REV)
);
