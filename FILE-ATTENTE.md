# File d’attente produit — FlowForge

> Ordre recommandé des **prochaines features** après les lots lifecycle déjà
> livrés. À relire **avant** de choisir la feature suivante (même critères
> qu’aux analyses 2026-08-17 / 2026-08-18).
>
> - Points ouverts à trancher : [TODO.md](TODO.md)
> - Inventaire follow-ups / lots : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
> - Séquencement « avant prod » : [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9
>
> **Dernière mise à jour :** 2026-08-20 (`observabilite` Phase 2
> **reviewed** — Q1–Q6 ; PR #59).

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
| `retention-dossier-type` | PR #55, TODO §21 | Défaut tenant + override type ; job ARCHIVE + `purge_outbox` ; UI admin |
| `purge-stockage` | PR #56, TODO §22 | Job `purge_outbox` store+PG ; `DocumentStoragePort.delete` ; file admin lecture + enqueue |
| `openapi-pagination-admin` | PR #58, TODO §24 | Envelope 6 GET admin ; OpenAPI golden + flag off ; pagers SPA ; écran webhooks |

Lots lifecycle antérieurs (`workflow-engine` … `frontend`, pagination, relations, PATCH fields API, éléments) : voir [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2.

## File d’attente

| Priorité | Feature / lot kebab-case | Pourquoi ce n’est pas (encore) le suivant immédiat — ou pourquoi ça l’est |
|---|---|---|
| **1 — en cours** | `observabilite` (TODO §29) | MDC + Micrometer (âge outbox déclenchement, jobs Flowable, PDF, webhooks DLQ, SSE) **et** visualisation admin lecture (`/admin/observabilite`, **D-FE** acté 2026-08-20). Rapports/SLA restent hors V1. |
| **Hors V1** | Rapports / SLA ; connecteur `http-rest` **SERVICE** ; antivirus pièces jointes ; codegen OpenAPI | Explicitement reportés (maquette UI / TODO §30 / connecteurs V1 = TRIGGER / §24 reste codegen). |

## Hors file produit (ne pas traiter comme « prochaine feature »)

- Provisioning tenant (schéma PG, instance Boot, bloc proxy, `app.tenant.id`)
- Smoke Keycloak / PostgreSQL réels
- Testcontainers / charge Flowable (reste TODO §28)
- Follow-ups ops (ex. DEV-PURGE : rétention outbox / TTL idempotency non schedulées ; recovery `PROCESSING` coincé)
- Chore `com.monapp` résiduel dans `analysis doc/`

## Décisions §21 / §22 (rappel)

**§21 — Rétention par `DossierType` — acté 2026-08-19** (voir TODO §21 et
`docs/ai/requirements/2026-08-19-feature-retention-dossier-type.md`).

**§22 — Purge PostgreSQL / object store — livré 2026-08-19** (feature
`purge-stockage`, PR #56) :

- Job `ProcessPurgeOutboxUseCase` consomme `purge_outbox` (Q1=B).
- Port `DocumentStoragePort.delete` S3 + filesystem — jamais MinIO (Q2, DEFERRED §0).
- Ordre store puis PG ; graphe complet ; Flowable best-effort (Q3–Q5).
- UI lecture `/admin/purge-outbox` ; enqueue `POST` `CLOTURE`/`ARCHIVE` (Q6, Q7, Q11).
- Suppression de tenant = runbook ops (Q8, décision n° 13) : pas d’API destroy.

## Historique court des choix « prochaine feature »

| Date | Choix | Motif |
|---|---|---|
| 2026-08-17 | `preview-documents` | Dernier lot nommé D-PREVIEW ; pas de point TODO bloquant |
| 2026-08-18 | `edition-champs-dossier` | Plus de lot nommé ; trou API-sans-UI ; §21 toujours « à décider » |
| 2026-08-19 | file ci-dessus | `edition-champs-dossier` en revue (PR #53) ; prochain = §21 si on tranche, sinon §24 |
| 2026-08-19 | `retention-dossier-type` Phase 9 READY | PR #55 ; prochain = `purge-stockage` §22 |
| 2026-08-19 | `purge-stockage` Phase 9 READY | PR #56 ; prochain = `openapi-pagination-admin` §24 |
| 2026-08-19 | `openapi-pagination-admin` Phase 5 | PR #58 ; §24 partiel clos ; prochain = `observabilite` §29 |
| 2026-08-20 | `observabilite` Phase 1 | Stakeholder : UI admin (D-FE) ; Rapports/SLA hors V1 |
| 2026-08-20 | `observabilite` Phase 2 reviewed | Q1=B (audit lectures logs) ; Q2=A (port management) ; Q3=A ; Q4=A (KPI) ; Q5=A (`TENANT_ADMIN`) ; Q6=A (refresh manuel) |
