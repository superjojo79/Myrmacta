ALTER TABLE document_template
ADD active_template_key varchar(128)
GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN template_key ELSE NULL END);
ALTER TABLE document_template
ADD active_variant_key varchar(128)
GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN COALESCE(variant_key, '') ELSE NULL END);
ALTER TABLE document_template
ADD active_locale varchar(32)
GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN COALESCE(locale, '') ELSE NULL END);
CREATE UNIQUE INDEX uq_template_active
ON document_template (tenant_id, active_template_key, active_variant_key, active_locale);
ALTER TABLE document_template
ADD ver_variant_key varchar(128)
GENERATED ALWAYS AS (COALESCE(variant_key, ''));
ALTER TABLE document_template
ADD ver_locale varchar(32)
GENERATED ALWAYS AS (COALESCE(locale, ''));
CREATE UNIQUE INDEX uq_template_version
ON document_template (tenant_id, template_key, ver_variant_key, ver_locale, version);
