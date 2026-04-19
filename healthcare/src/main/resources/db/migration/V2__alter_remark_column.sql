-- V2: Widens columns that were narrow in the original microservice schemas.
-- This migration is for upgrading from the old 12-service schema.
-- On a FRESH install (V1 creates the schema correctly), this migration is harmless
-- because the ALTER statements change columns that already have the target widths.
-- Each statement uses MODIFY COLUMN which is idempotent if the column already exists.

ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN remark VARCHAR(1000);
ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN state_name VARCHAR(100);
ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN full_name VARCHAR(100);
ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN email VARCHAR(100);
ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN created_by VARCHAR(100);
ALTER TABLE CITIZEN_APPLICATION MODIFY COLUMN updated_by VARCHAR(100);
