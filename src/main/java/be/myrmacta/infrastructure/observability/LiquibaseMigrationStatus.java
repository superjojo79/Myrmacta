package be.myrmacta.infrastructure.observability;

import java.time.Instant;

/**
 * État courant de la migration Liquibase pour l'instance locale — §119.
 *
 * <p>Chaque instance Spring Boot ne connaît que son propre schéma. Ce record expose le nombre
 * de changesets appliqués (niveau de schéma), l'identifiant et l'horodatage du dernier changeset.
 *
 * <p>Si {@code DATABASECHANGELOG} n'existe pas encore (instance qui n'a pas encore migré),
 * {@code appliedChangesetsCount} vaut 0 et les deux autres champs sont {@code null}.
 */
public record LiquibaseMigrationStatus(
        long appliedChangesetsCount,
        String lastAppliedChangesetId,
        Instant lastAppliedAt) {}
