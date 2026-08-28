CREATE UNIQUE INDEX uq_connector_instance_tenant_name_active
ON connector_instance (tenant_id, name)
WHERE deleted_at IS NULL;
