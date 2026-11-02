-- Add partner-type columns to organizations.
ALTER TABLE organizations
    ADD COLUMN business_type VARCHAR(20) NOT NULL DEFAULT 'HOTEL'
        CONSTRAINT chk_business_type CHECK (business_type IN ('HOTEL', 'CAR_RENTAL')),
    ADD COLUMN legal_type    VARCHAR(20) NOT NULL DEFAULT 'COMPANY'
        CONSTRAINT chk_legal_type CHECK (legal_type IN ('COMPANY', 'INDIVIDUAL'));
