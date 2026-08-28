CREATE TABLE dossier_reference_seq (
    tenant_id varchar(64) NOT NULL,
    dossier_type_code varchar(64) NOT NULL,
    "year" integer NOT NULL,
    last_value bigint NOT NULL
);
ALTER TABLE dossier_reference_seq
    ADD CONSTRAINT pk_dossier_reference_seq
    PRIMARY KEY (tenant_id, dossier_type_code, "year");
