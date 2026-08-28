CREATE UNIQUE INDEX uq_template_active
ON document_template (
  tenant_id,
  template_key,
  COALESCE(variant_key, ''),
  COALESCE(locale, '')
)
WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_template_version
ON document_template (
  tenant_id,
  template_key,
  COALESCE(variant_key, ''),
  COALESCE(locale, ''),
  version
);
