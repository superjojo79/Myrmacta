UPDATE purge_outbox SET updated_at = created_at WHERE updated_at IS NULL;
