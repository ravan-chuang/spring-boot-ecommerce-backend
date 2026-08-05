ALTER TABLE idempotency_records
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD COLUMN response_status INTEGER NOT NULL DEFAULT 200,
    ADD COLUMN expires_at TIMESTAMP(6);

DELETE FROM idempotency_records
WHERE idempotency_key IS NULL
   OR BTRIM(idempotency_key) = ''
   OR request_path IS NULL
   OR payment_id IS NULL
   OR NOT EXISTS (
       SELECT 1
       FROM payments
       WHERE payments.id = idempotency_records.payment_id
   );

UPDATE idempotency_records
SET idempotency_key = BTRIM(idempotency_key),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP);

UPDATE idempotency_records AS record
SET request_fingerprint = CASE payment.method
        WHEN 'CREDIT_CARD' THEN 'b41381f93987bd40ee50d3325112ba45be62e4cd0999e1bf0c866881f4e2c0a4'
        WHEN 'LINE_PAY' THEN 'f1c2d1c9590efedb31bdb7c66bc2011bfbf904838580d9e3852fc6f33f8065ff'
        WHEN 'ATM' THEN '72eeaa36e534dd4568c710f0fb8e5c96592c52f78d358441c89de91c338b8d26'
        ELSE '24e50253a04c4ea9dc2e2ab4c58f9c6cd3629e3bf10a71a72ef22d6f9413d85b'
    END,
    expires_at = record.created_at + INTERVAL '24 hours'
FROM payments AS payment
WHERE payment.id = record.payment_id;

DELETE FROM idempotency_records AS duplicate
USING idempotency_records AS keeper
WHERE duplicate.id > keeper.id
  AND duplicate.idempotency_key = keeper.idempotency_key
  AND duplicate.request_path = keeper.request_path;

DROP INDEX IF EXISTS idx_idempotency_records_key_path;

ALTER TABLE idempotency_records
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN request_path SET NOT NULL,
    ALTER COLUMN request_fingerprint SET NOT NULL,
    ALTER COLUMN payment_id SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL,
    ADD CONSTRAINT uk_idempotency_records_key_path
        UNIQUE (idempotency_key, request_path),
    ADD CONSTRAINT fk_idempotency_records_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id);
