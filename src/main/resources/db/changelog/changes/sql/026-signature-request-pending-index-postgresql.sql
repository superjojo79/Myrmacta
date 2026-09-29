CREATE UNIQUE INDEX ux_signature_request_pending
ON signature_request (process_instance_id, activity_id)
WHERE status = 'PENDING';
