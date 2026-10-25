package be.myrmacta.domain.connector.model;

public enum ConfigFieldType {
    STRING,
    PASSWORD,
    URL,
    INTEGER,
    BOOLEAN,
    SELECT,
    /** Multi-line secret (e.g. a PEM private key) — {@link #PASSWORD} renders a single-line input. */
    SECRET_TEXTAREA
}
