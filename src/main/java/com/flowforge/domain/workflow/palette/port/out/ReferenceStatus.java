package com.flowforge.domain.workflow.palette.port.out;

/**
 * TODO §93 (D-DRAFT-REF-STATUS) — état d'une ressource référencée par une brique : utilisable ({@code ACTIVE}), présente
 * mais pas encore utilisable ({@code INACTIVE} : modèle en brouillon, type de dossier inactif, webhook ou connecteur
 * désactivé) ou absente ({@code MISSING} : inconnue, supprimée, retirée ou de mauvais rôle).
 */
public enum ReferenceStatus {
    ACTIVE,
    INACTIVE,
    MISSING
}
