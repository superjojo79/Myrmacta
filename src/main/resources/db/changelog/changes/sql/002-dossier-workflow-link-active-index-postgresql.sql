CREATE UNIQUE INDEX uq_dossier_workflow_link_active
ON dossier_workflow_link (dossier_id)
WHERE ended_at IS NULL;
