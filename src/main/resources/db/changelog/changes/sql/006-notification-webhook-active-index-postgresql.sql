CREATE UNIQUE INDEX uq_notification_webhook_tenant_name_active
ON notification_webhook (tenant_id, name)
WHERE deleted_at IS NULL;
