ALTER TABLE dossier_relation
  ADD CONSTRAINT chk_dossier_relation_not_self
  CHECK (source_dossier_id <> target_dossier_id);
CREATE INDEX IF NOT EXISTS idx_dossier_relation_source
  ON dossier_relation (tenant_id, source_dossier_id);
CREATE INDEX IF NOT EXISTS idx_dossier_relation_target
  ON dossier_relation (tenant_id, target_dossier_id);
