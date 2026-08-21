# File d’attente produit — FlowForge

> Ordre recommandé des **prochaines features** après les lots lifecycle déjà
> livrés. À relire **avant** de choisir la feature suivante (même critères
> qu’aux analyses 2026-08-17 / 2026-08-18).
>
> - Points ouverts à trancher : [TODO.md](TODO.md)
> - Inventaire follow-ups / lots : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
> - Séquencement « avant prod » : [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9
>
> **Dernière mise à jour :** 2026-08-21 (`contrat-api-client` Phase 1+2
> reviewed — requirements figées, 4 décisions produit actées).

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
| `observabilite` | PR #59, TODO §29 | ECS + MDC ; Micrometer port management ; snapshot + SPA admin |
| `durcissement-donnees-sensibles` | PR #62, TODO §23 (reste réduit)/§30 (clos) | Magic-bytes + `AttachmentScanPort` no-op sur upload ; export CSV masqué (`GET /api/dossiers/export.csv`) ; skeleton `HttpRestServiceConnector` non enregistré ; garde-fous anti-fuite secrets ; injection de formule CSV trouvée et corrigée en revue |

Lots lifecycle antérieurs (`workflow-engine` … `frontend`, pagination, relations, PATCH fields API, éléments) : voir [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2.

## File d’attente

Plus de lot produit V1 nommé après `observabilite` : les points TODO hygiène
restants (§23 reste, §24 reste, §25, §26 reste, §28, §30 reste, §35, §37)
ont été regroupés en 5 features candidates (regroupement validé 2026-08-20).
§31 (« découpage en lots ») est clos ci-dessous plutôt que transformé en
feature — le process `dev-lifecycle` + ce fichier + `DEFERRED-FEATURES.md`
le remplit déjà en pratique à chaque lot livré. `durcissement-donnees-sensibles`
(priorité 1) livrée le 2026-08-21 (PR #62) — file renumérotée.

| Priorité | Feature / lot kebab-case | TODO couverts | Contenu | Pourquoi cet ordre |
|---|---|---|---|---|
| 1 | `contrat-api-client` | §24 (reste) | Codegen client OpenAPI (typescript-axios ou équivalent) · pagination des listes imbriquées (relations, tasks, `connector-definitions`…) · spec formelle SSE / multipart attachment / webhook ingress (actuellement `@Hidden`) | Aucune décision ouverte ; clôture définitivement le point 24 |
| 2 | `fiabilisation-tests-plateforme` | §28, §26 (reste), §37 | Testcontainers (étanchéité `search_path`) · test de charge Flowable (dimensionnement verrous) · tests du relais outbox (rejeu, idempotence, crash entre publication et marquage) · migration Liquibase multi-schéma avec échec partiel · seuils de couverture · smoke PostgreSQL réel multi-pods (référence séquentielle) · `findClosedForRetention` aligné sur le pattern `tenantId` explicite | Seuils de couverture à trancher (mineur) — sinon prêt à démarrer |
| 3 | `deploiement-bpmn` | §35 | Trancher (a) acter l’état actuel + corriger doc, (b) intégrer Flowable Modeler, ou (c) écran d’upload BPMN minimal FlowForge — puis livrer l’option retenue | **Bloqué** : décision produit à trancher avant tout dev (critère 2) |
| 4 | `modele-physique-consolide` | §25 | ERD/DDL consolidé, répartition schéma tenant / `platform`, description `TenantRegistry` + politique de rafraîchissement du cache | Doc pure (aucune décision ouverte) mais sans parcours métier utilisateur direct (critère 3) — reste en dernier |

**Hors V1 (reporté, pas dans cette file) :** Rapports / SLA (maquette UI de
référence uniquement) ; connecteur `http-rest` en rôle **SERVICE** lui-même
(V1 = TRIGGER uniquement — le hardening SSRF est pré-positionné depuis
`durcissement-donnees-sensibles`, pas le connecteur).

## Hors file produit (ne pas traiter comme « prochaine feature »)

- Provisioning tenant (schéma PG, instance Boot, bloc proxy, `app.tenant.id`)
- Smoke Keycloak réel (environnement manuel — distinct du smoke PostgreSQL/Testcontainers
  automatisé désormais dans `fiabilisation-tests-plateforme`, §26/§28)
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
| 2026-08-20 | `observabilite` Phase 2 reviewed | Q1=B ; Q2=A ; Q3=B ; Q4=A ; Q5=A ; Q6=A |
| 2026-08-20 | `observabilite` Phase 3 reviewed | DD-JSON=ecs ; DD-PROM-NAMES ; DD-FLOWABLE-API ; DD-THRESHOLDS |
| 2026-08-20 | `observabilite` Phase 5 done | M1–M5 ; verify 906 + Vitest 135 + Playwright 39 |
| 2026-08-20 | `observabilite` Phase 9 READY | PR #59 ; file V1 sans lot nommé suivant |
| 2026-08-20 | §36 `canEditDossier` clos | PR #60 ; frontend aligné sur `DossierAccessPolicy` via `canEdit` |
| 2026-08-20 | Regroupement TODO restants en 5 features | `durcissement-donnees-sensibles`, `contrat-api-client`, `fiabilisation-tests-plateforme`, `deploiement-bpmn`, `modele-physique-consolide` ; §31 clos (process déjà en pratique) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 1+2 reviewed | D-AV=B (magic-bytes + port no-op) ; D-CSV-SCOPE=A (export construit) ; D-CSV-ROLE=lecture liste ; D-SERVICE-HTTP=skeleton non enregistré ; D-SECRETS-AUDIT=exhaustif |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 3 reviewed | AttachmentContentSniffer+AttachmentScanPort ; DossierExportPort streamé + ExportDossiersUseCase ; HttpRestServiceConnector non-`ConnectorDefinition` (double garde registry) ; garde-fou secrets = test unitaire ciblé |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 4 reviewed | 5 milestones (M1 upload, M2 export CSV, M3 skeleton connecteur, M4 audit secrets, M5 qualité) ; M1–M4 sans dépendance croisée ; D-CSV-UI=pas d'écran SPA V1 (feature backend seul) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 5 done | M1–M5 en TDD ; `./mvnw verify` 960 tests (0 échec) + Jacoco OK ; Vitest 136 + Playwright 39 (feature backend-only, régression complète) ; TODO §23 reste réduit, §30 clos |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 7 done | Revue fichier par fichier ; injection de formule CSV trouvée sur `CsvWriter` (labels/champs dynamiques exportés non protégés) et corrigée en TDD ; `./mvnw verify` 964 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 READY | Revue finale : traçage appelants, cohérence modules similaires, pas de duplication, pas de migration à risque ; aucun blocage ; PR #62 (commit `284345c`) prête pour revue humaine |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 8 rejouée | Relecture Jacoco classe par classe (pas juste le total bundle) ; 2 lacunes réelles comblées (`HttpRestServiceConnector` chemin d'erreur, `AttachmentContentSniffer` texte vide) ; `./mvnw verify` 969 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 re-vérifiée | Utilisateur : « la phase 9 a-t-elle été correctement exécutée ? » → non totalement ; balayage Jacoco corrigé (motif CSS `pc bpc` raté au 1er passage) sur les 15 fichiers modifiés, 4 lacunes réelles trouvées et comblées (`DossierExportController` closedAt, `AttachmentContentSniffer` null, `parseStatus` blanc, `CsvWriter` \r isolé) ; `./mvnw verify` 973 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` livrée | PR #62 mergée sur `main` (commit `a4443e5`) ; TODO §23 reste réduit, §30 clos ; prochain = `contrat-api-client` §24 (reste) |
| 2026-08-21 | `contrat-api-client` Phase 1+2 reviewed | D-CODEGEN=exclu (contradiction FILE-ATTENTE/TODO résolue en faveur de TODO) ; D-RELATIONS=pas de pagination (décision existante respectée) ; D-PAGINATION-SCOPE=6 listes (elements, workflow-tasks/mine, connector-definitions, dossiers/{id}/workflows, workflow-links history+tasks) ; D-HIDDEN-SPEC=documentation complète SSE/multipart/webhook |
