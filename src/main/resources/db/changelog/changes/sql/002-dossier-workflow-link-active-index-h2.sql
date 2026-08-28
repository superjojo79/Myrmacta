ALTER TABLE dossier_workflow_link
ADD active_dossier_id uuid
GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN dossier_id ELSE NULL END);
CREATE UNIQUE INDEX uq_dossier_workflow_link_active
ON dossier_workflow_link (active_dossier_id);
