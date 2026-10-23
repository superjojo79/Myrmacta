# §37 — `findClosedForRetention` sans `tenantId` explicite — rupture du pattern « ceinture et bretelles »

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §37 · [registre](README.md)

**Constat (revue PR #55, `retention-dossier-type`) :** `SpringDataDossierRepository` scope déjà
certaines requêtes par isolation de schéma **et** par `tenantId` explicite en paramètre (ex.
`findByTenantIdAndReference(tenantId, reference)`) — double barrière conforme à la décision n° 1.
La nouvelle requête `findClosedForRetention(status, pageable)`, ajoutée pour le job de rétention,
ne prend **pas** de `tenantId` et s'appuie uniquement sur l'isolation par schéma
(`search_path`, décision n° 13/14). Non exploitable aujourd'hui — une instance Spring Boot = un
tenant, donc aucune fuite inter-tenant possible en pratique — mais l'incohérence de pattern dans le
même fichier est un piège si ce postulat d'architecture évolue un jour (ex. pooling de connexions
multi-tenant).

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :** `findClosedForRetention` aligné
sur le pattern existant — `tenantId` explicite ajouté au port (`DossierRepository`), à l'adaptateur
JPA (`JpaDossierRepositoryAdapter`) et à la requête (`SpringDataDossierRepository`, `and
d.tenantId = :tenantId`) ; `EvaluateDossierRetentionUseCaseImpl` propage le `tenantId` déjà en
paramètre de `evaluate(...)`. Test de non-régression (seul appelant existant) + nouveau test
d'isolation multi-tenant dans `DossierPersistenceTest`.

**Impacte :** `SpringDataDossierRepository.java`, `JpaDossierRepositoryAdapter.java`,
[02-multi-tenant.md](../specs/02-multi-tenant.md).
