# File d’attente produit — FlowForge

> Ordre recommandé des **prochaines features** après les lots lifecycle déjà
> livrés. À relire **avant** de choisir la feature suivante (même critères
> qu’aux analyses 2026-08-17 / 2026-08-18).
>
> - Points ouverts à trancher : [TODO.md](TODO.md)
> - Inventaire follow-ups / lots : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
> - Séquencement « avant prod » : [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9
>
> **Dernière mise à jour :** 2026-08-19 (après `edition-champs-dossier`, PR #53).

## Comment s’en servir

1. Ne pas relancer une feature marquée **Livré** / **En revue**.
2. Prendre le premier item **Prochain** (ou l’alternative si le prochain est
   bloqué par un point TODO « à décider »).
3. Ne **pas** trancher silencieusement un point ouvert de `TODO.md`.
4. Après clôture d’une feature : déplacer la ligne vers *Récemment livré*,
   recalculer *Prochain*, dater ce fichier.

## Critères de sélection (stables)

Même grille que pour `preview-documents` puis `edition-champs-dossier` :

| Priorité du critère | Règle |
|---|---|
| 1 | Lot **nommé** déjà spécifié (analyse / DEFERRED « lot suivant ») |
| 2 | **Pas** de décision produit encore ouverte (`TODO.md` « À décider ») |
| 3 | Parcours **métier utilisateur** (API + UI) plutôt qu’un contrat transverse |
| 4 | REVUE §9 « avant prod » (rétention §21, purge §22, sensible §23) — seulement si les arbitrages sont tranchés ou acceptés en phase requirements |

## Récemment livré (ne pas replanifier)

| Feature | Réf. | Notes |
|---|---|---|
| `preview-documents` | PR #52, lot D-PREVIEW | Aperçu HTML/PDF admin templates |
| `edition-champs-dossier` | PR #53 | UI `PATCH /api/dossiers/{id}/fields` sur le détail (D-DIFF, D-DEFAULTS) |

Lots lifecycle antérieurs (`workflow-engine` … `frontend`, pagination, relations, PATCH fields API, éléments) : voir [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2.

## File d’attente

| Priorité | Feature / lot kebab-case | Pourquoi ce n’est pas (encore) le suivant immédiat — ou pourquoi ça l’est |
|---|---|---|
| **1 — prochain** | `retention-dossier-type` (TODO §21) puis `purge-stockage` (§22) | Séquencement « avant prod » de la revue. **Arbitrages produit encore ouverts** : durée par tenant/type, action à expiration, lien `CLOTURE`/`ARCHIVE` ; pour §22 : transaction vs job vs suppression logique, purge conjointe PostgreSQL / object store (port S3/FS, **pas MinIO en dur** — DEFERRED §0). Démarrer = **phase requirements** pour trancher, pas implémenter en silence. |
| **2** | `openapi-pagination-admin` (reste TODO §24) | Contrat transverse : OpenAPI formel, pagination des **autres** ressources, surface admin exhaustive. Pas un parcours métier. `ProblemDetail` et pagination dossiers déjà livrés. |
| **3** | `observabilite` (TODO §29) | Docs `docs/ai/monitoring/**` encore `deferred` / squelettes. MDC `tenantId`/`dossierId`/`processInstanceId`, Micrometer (âge outbox, jobs Flowable, PDF, webhooks DLQ, SSE). |
| **Hors V1** | Rapports / SLA ; connecteur `http-rest` **SERVICE** ; antivirus pièces jointes | Explicitement reportés (maquette UI / TODO §30 / connecteurs V1 = TRIGGER). |

### Alternative si on refuse d’ouvrir §21 maintenant

Le n°1 reste le prochain **produit**, mais il est **bloqué** tant que les
questions §21/§22 ne sont pas tranchées (conventions : ne pas décider un
point ouvert en silence).

Dans ce cas le premier lot **implémentable sans arbitrage** est le **n°2**
(`openapi-pagination-admin`).

## Hors file produit (ne pas traiter comme « prochaine feature »)

- Provisioning tenant (schéma PG, instance Boot, bloc proxy, `app.tenant.id`)
- Smoke Keycloak / PostgreSQL réels
- Testcontainers / charge Flowable (reste TODO §28)
- Follow-ups ops (ex. DEV-PURGE : rétention outbox / TTL idempotency non schedulées)
- Chore `com.monapp` résiduel dans `analysis doc/`

## Décisions §21 / §22 encore ouvertes (rappel)

À clôturer **avant** ou **pendant** la phase requirements de
`retention-dossier-type` :

**§21 — Rétention par `DossierType`**

- Durée configurable par tenant et par type ?
- Action à expiration (archivage puis purge) ?
- Articulation avec les statuts `CLOTURE` / `ARCHIVE` déjà existants ?

**§22 — Purge PostgreSQL / object store**

- Transaction applicative, job de réconciliation, ou suppression logique + balayage ?
- Port stockage abstrait S3 + filesystem (DEFERRED §0) — jamais MinIO couplé en dur.
- Suppression de tenant = décommission instance (décision n° 13) + `DROP SCHEMA` + purge objets.

## Historique court des choix « prochaine feature »

| Date | Choix | Motif |
|---|---|---|
| 2026-08-17 | `preview-documents` | Dernier lot nommé D-PREVIEW ; pas de point TODO bloquant |
| 2026-08-18 | `edition-champs-dossier` | Plus de lot nommé ; trou API-sans-UI ; §21 toujours « à décider » |
| 2026-08-19 | file ci-dessus | `edition-champs-dossier` en revue (PR #53) ; prochain = §21 si on tranche, sinon §24 |
