package com.flowforge.domain.connector.port.out;

import java.util.UUID;

/**
 * Issue #205 (TODO §101, D-STORAGE-REF-DOSSIER) — registre des objets écrits par le connecteur {@code document-storage},
 * avec le dossier pour lequel ils l'ont été ({@code null} : appel direct, sans dossier). Depuis un process, le connecteur
 * ne lit, ne teste ni n'efface que les objets enregistrés ici pour le dossier de l'exécution.
 */
public interface ConnectorStoredObjectRepository {

    void register(String tenantId, String storageRef, UUID dossierId);

    /** Vrai si {@code storageRef} a été écrit par le connecteur pour ce dossier précis ({@code dossierId} non nul). */
    boolean isStoredFor(String tenantId, String storageRef, UUID dossierId);

    /** Retire l'entrée après effacement de l'objet ; sans effet si elle n'existe pas. */
    void forget(String tenantId, String storageRef);
}
