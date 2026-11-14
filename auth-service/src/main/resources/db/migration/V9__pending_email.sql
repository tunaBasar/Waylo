-- Email change: the new address waits here until the link sent to it is confirmed
-- (the confirmation token reuses users.verification_token / verification_token_expiry).
ALTER TABLE users ADD COLUMN pending_email VARCHAR(255);
