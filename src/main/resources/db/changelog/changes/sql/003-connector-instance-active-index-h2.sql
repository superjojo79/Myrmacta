ALTER TABLE connector_instance
ADD active_name varchar(255)
GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END);
CREATE UNIQUE INDEX uq_connector_instance_tenant_name_active
ON connector_instance (tenant_id, active_name);
