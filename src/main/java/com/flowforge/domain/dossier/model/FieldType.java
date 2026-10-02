package com.flowforge.domain.dossier.model;

public enum FieldType {
    STRING,
    NUMBER,
    DATE,
    BOOLEAN,
    SELECT,
    FILE,
    /** TODO §76 (D1) — montant décimal exact et devise ISO 4217 : {@code {"amount":"1234.50","currency":"EUR"}}. */
    MONEY,
    /** TODO §76 (D1) — liste bornée de lignes typées (un seul niveau, sous-champs plats et non sensibles). */
    REPEATABLE_GROUP,
    /** TODO §76 (D1) — référence à une entité d'un système externe : {@code {"system":"d365fo","id":"V-0042"}}. */
    EXTERNAL_REF
}
