ALTER TABLE notification_webhook
ADD active_name varchar(255)
GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END);
CREATE UNIQUE INDEX uq_notification_webhook_tenant_name_active
ON notification_webhook (tenant_id, active_name);
