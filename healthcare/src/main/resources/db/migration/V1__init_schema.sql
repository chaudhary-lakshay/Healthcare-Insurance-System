CREATE TABLE IF NOT EXISTS USER_MASTER (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile_no BIGINT,
    ssn BIGINT,
    gender VARCHAR(10),
    dob DATE,
    active_sw VARCHAR(1) DEFAULT 'Y',
    role VARCHAR(50) DEFAULT 'USER',
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS WORKER_MASTER (
    worker_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile_no BIGINT,
    ssn BIGINT,
    gender VARCHAR(10),
    dob DATE,
    designation VARCHAR(100),
    help_center_name VARCHAR(255),
    help_center_location VARCHAR(255),
    active_sw VARCHAR(1) DEFAULT 'Y',
    role VARCHAR(50) DEFAULT 'WORKER',
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS ADMIN_MASTER (
    admin_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'ADMIN',
    active_sw VARCHAR(1) DEFAULT 'Y',
    created_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_on DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS CITIZEN_APPLICATION (
    app_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    gender VARCHAR(10),
    phone_no BIGINT,
    ssn BIGINT NOT NULL,
    state_name VARCHAR(100),
    dob DATE,
    remark VARCHAR(1000),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    creation_date DATE DEFAULT (CURRENT_DATE),
    updation_date DATE DEFAULT (CURRENT_DATE)
);

CREATE TABLE IF NOT EXISTS DC_CASES (
    case_no BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id BIGINT NOT NULL,
    plan_id BIGINT
);

CREATE TABLE IF NOT EXISTS DC_INCOME (
    income_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    emp_income DOUBLE,
    property_income DOUBLE
);

CREATE TABLE IF NOT EXISTS DC_EDUCATION (
    education_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    highest_qlfy VARCHAR(100),
    pass_out_year INT
);

CREATE TABLE IF NOT EXISTS DC_CHILDREN (
    child_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    child_dob DATE,
    child_ssn BIGINT
);

CREATE TABLE IF NOT EXISTS PLAN_MASTER (
    plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    description VARCHAR(255),
    category_id BIGINT,
    active_sw VARCHAR(1) DEFAULT 'Y',
    creation_date DATE DEFAULT (CURRENT_DATE),
    updation_date DATE DEFAULT (CURRENT_DATE),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS PLAN_CATEGORY (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(255) NOT NULL,
    active_sw VARCHAR(1) DEFAULT 'Y',
    created_date DATE DEFAULT (CURRENT_DATE),
    updated_date DATE DEFAULT (CURRENT_DATE),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS ELIGIBILITY_DETERMINATION (
    ed_trace_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    holder_name VARCHAR(255),
    holder_ssn BIGINT,
    plan_name VARCHAR(255),
    plan_status VARCHAR(50),
    plan_start_date DATE,
    plan_end_date DATE,
    benefit_amt DOUBLE,
    denial_reason VARCHAR(255),
    bank_name VARCHAR(255),
    account_number VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS CO_TRIGGERS (
    trigger_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_no BIGINT NOT NULL,
    co_notice_pdf LONGBLOB,
    trigger_status VARCHAR(50) DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS ISH_GOVERNMENT_REPORTS (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_name VARCHAR(255) NOT NULL,
    report_type VARCHAR(100),
    report_format VARCHAR(50),
    report_status VARCHAR(50),
    report_description VARCHAR(500),
    report_file_path VARCHAR(500),
    generated_for VARCHAR(255),
    department_name VARCHAR(255),
    period_covered VARCHAR(255),
    report_content LONGTEXT,
    created_date DATE DEFAULT (CURRENT_DATE),
    updated_date DATE DEFAULT (CURRENT_DATE),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
