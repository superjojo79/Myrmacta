CREATE UNIQUE INDEX uq_notification_template_active
ON notification_template (
  tenant_id,
  template_key,
  COALESCE(variant_key, ''),
  COALESCE(locale, '')
)
WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_notification_template_version
ON notification_template (
  tenant_id,
  template_key,
  COALESCE(variant_key, ''),
  COALESCE(locale, ''),
  version
);
