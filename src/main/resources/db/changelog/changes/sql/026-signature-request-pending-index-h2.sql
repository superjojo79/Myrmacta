ALTER TABLE signature_request
ADD pending_key varchar(200)
GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN process_instance_id || '|' || activity_id ELSE NULL END);
CREATE UNIQUE INDEX ux_signature_request_pending
ON signature_request (pending_key);
