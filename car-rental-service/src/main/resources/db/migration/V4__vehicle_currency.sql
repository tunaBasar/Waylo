ALTER TABLE vehicles ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'TRY';
ALTER TABLE vehicles ADD CONSTRAINT vehicles_currency_chk CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE rentals ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'TRY';
ALTER TABLE rentals ADD CONSTRAINT rentals_currency_chk CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE vehicles ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE rentals ALTER COLUMN currency DROP DEFAULT;
