ALTER TABLE dossier_type_acl
    ADD CONSTRAINT chk_dossier_type_acl_permission
    CHECK (permission IN ('VIEW', 'EDIT'));
