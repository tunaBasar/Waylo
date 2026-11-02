-- Bus companies become a partner type.
ALTER TABLE organizations DROP CONSTRAINT chk_business_type;
ALTER TABLE organizations
    ADD CONSTRAINT chk_business_type CHECK (business_type IN ('HOTEL', 'CAR_RENTAL', 'BUS'));
