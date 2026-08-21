---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: reviewed
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
phase1: 2026-08-21
phase3: 2026-08-21
phase4: 2026-08-21
phase5: 2026-08-21
requirements_ref: docs/ai/requirements/2026-08-21-feature-durcissement-donnees-sensibles.md
design_ref: docs/ai/design/2026-08-21-feature-durcissement-donnees-sensibles.md
testing_ref: docs/ai/testing/2026-08-21-feature-durcissement-donnees-sensibles.md
---

# Project Planning & Task Breakdown — `durcissement-donnees-sensibles`

> Phase 4 **reviewed**. Design figé (D-AV, D-CSV-SCOPE/D-CSV-ROLE/D-CSV-UI,
> D-SERVICE-HTTP, D-SECRETS-AUDIT). Backend uniquement — aucun changement
> `frontend/` (D-CSV-UI).

## Milestones

- [x] **M1** — Upload durci (sniffer + port scan pluggable)
- [x] **M2** — Export CSV masqué (port + use case + contrôleur)
- [x] **M3** — Skeleton `ServiceConnector` `http-rest` non enregistré
- [x] **M4** — Audit + garde-fou anti-fuite de secrets
- [x] **M5** — Qualité (ArchUnit, OpenAPI, `./mvnw verify`, mise à jour TODO/FILE-ATTENTE)

Les 4 milestones M1–M4 sont indépendants entre eux (aucune dépendance
croisée) — ordre d'exécution libre, M5 les valide tous en fin de lot.

## Task Breakdown

TDD : test rouge → code → vert. Validation : commandes en fin de document.

### M1 — Upload durci

#### T1.1 `AttachmentContentSniffer` (domaine pur)
- **Outcome :** classe statique `com.flowforge.domain.dossier.AttachmentContentSniffer`
  avec `matchesDeclaredType(byte[], String)` — signatures PDF/PNG/JPEG/ZIP
  (OOXML)/heuristique texte, décrites en design (D-AV).
- **Deps :** aucune.
- **Tests :** scénarios `AttachmentContentSniffer` du testing doc (PDF/PNG/
  JPEG/OOXML valides, PNG renommé `.pdf`, binaire déclaré texte, texte
  légitime, contenu vide, type non reconnu).
- **Status :** done

#### T1.2 `AttachmentScanPort` + `NoOpAttachmentScanAdapter`
- **Outcome :** `AttachmentScanResult` (record) + interface
  `domain.dossier.port.out.AttachmentScanPort` ; adaptateur
  `infrastructure` retournant toujours propre, javadoc documentant le
  contrat pour un futur moteur AV.
- **Deps :** aucune.
- **Tests :** scénario `NoOpAttachmentScanAdapter` du testing doc.
- **Status :** done

#### T1.3 Intégration `DossierElementUploadController`
- **Outcome :** lecture unique des bytes, ordre : check MIME déclaré
  (existant) → `AttachmentContentSniffer` → `AttachmentScanPort` → store ;
  nouvelle dépendance constructeur `AttachmentScanPort`.
- **Deps :** T1.1, T1.2.
- **Tests :** scénarios `DossierElementUploadController` du testing doc
  (rejet magic-bytes, rejet scan infecté, upload valide inchangé, lecture
  unique du fichier).
- **Status :** done

### M2 — Export CSV masqué

#### T2.1 `DossierExportPort` + adaptateur JPA streamé
- **Outcome :** interface `domain.dossier.port.out.DossierExportPort`
  (`Stream<Dossier> streamForExport(tenantId, Filter)`) ; adaptateur
  `infrastructure.persistence.dossier` via Hibernate `getResultStream()`,
  fermé par le use case (`try-with-resources`).
- **Deps :** aucune (réutilise `DossierSearchPort.Filter` existant).
- **Tests :** scénario "flux fermé même en cas d'exception" du testing
  doc ; test d'intégration JPA de base (stream non vide, colonnes
  attendues).
- **Status :** done

#### T2.2 `ExportDossiersUseCase` (+ Impl)
- **Outcome :** port.in `domain.dossier.port.in.ExportDossiersUseCase` +
  impl mirroring `SearchDossiersUseCaseImpl` (ACL via
  `DossierTypeAclRepository`, `dossierTypeCode` obligatoire,
  `AccessDeniedException` si pas de VIEW), applique
  `DossierFieldRedactor.redact(...)` par dossier avant de le streamer.
- **Deps :** T2.1.
- **Tests :** scénarios `ExportDossiersUseCase` du testing doc (type
  manquant/inconnu, accès refusé/autorisé, admin non restreint, filtres
  status/reference, masquage sensible, type sans dossiers, champ dynamique
  supprimé).
- **Status :** done

#### T2.3 `DossierExportController` + `CsvWriter`
- **Outcome :** contrôleur dédié `GET /api/dossiers/export.csv`
  (`dossierTypeCode` obligatoire, `status`/`reference` optionnels),
  écriture streamée (`HttpServletResponse` + `PrintWriter`), utilitaire
  `CsvWriter` (échappement RFC4180 minimal, pas de dépendance externe),
  en-têtes + `Content-Disposition`.
- **Deps :** T2.2.
- **Tests :** scénarios `DossierExportController`/`CsvWriter` du testing
  doc (en-têtes colonnes, échappement, content-type/disposition, cellule
  vide sur masquage) + test d'intégration MockMvc bout en bout (admin vs
  viewer avec/sans droit).
- **Status :** done

#### T2.4 OpenAPI
- **Outcome :** `GET /api/dossiers/export.csv` documenté (springdoc,
  `produces = "text/csv"`), golden `openapi/openapi.yaml` régénéré.
- **Deps :** T2.3.
- **Tests :** scénario OpenAPI golden du testing doc.
- **Status :** done

### M3 — Skeleton `ServiceConnector` `http-rest`

#### T3.1 `HttpRestServiceConnector`
- **Outcome :** classe `infrastructure.connector.builtin.HttpRestServiceConnector`
  implémentant **seulement** `ServiceConnector`, **pas** `@Component`,
  **pas** `ConnectorDefinition` — appelle `OutboundUrlGuard.assertSafe(...)`
  avant tout appel HTTP (client injecté, ex. `java.net.http.HttpClient`) ;
  ne recopie jamais `instance.config()` tel quel dans le `Map` de résultat.
- **Deps :** aucune (réutilise `OutboundUrlGuard` existant tel quel).
- **Tests :** scénarios `HttpRestServiceConnector` du testing doc (guard
  bloque avant appel, guard passe → appel tenté, pas de secret recopié
  dans le résultat, absence d'implémentation de `ConnectorDefinition`).
- **Status :** done

### M4 — Audit + garde-fou anti-fuite de secrets

#### T4.1 Vérification ciblée des surfaces identifiées
- **Outcome :** relecture de `ConnectorApiDtos`, `NotificationApiDtos`,
  `ConnectorExecuteController`/`M2mConnectorExecuteController`, et grep
  ciblé des messages d'exception connecteur/notification — confirmer
  qu'aucune régression n'a été introduite depuis la Phase 3 (déjà validées
  saines à ce stade).
- **Deps :** aucune.
- **Tests :** aucun test dédié (vérification manuelle documentée dans
  `implementation.md`).
- **Status :** done

#### T4.2 Test garde-fou anti-régression
- **Outcome :** test unitaire ciblé sérialisant `ConnectorResponse` et
  `NotificationWebhookResponse` avec un secret de test connu en entrée,
  assertant l'absence de cette valeur en clair dans le JSON produit.
- **Deps :** T4.1.
- **Tests :** scénarios "Garde-fou anti-fuite de secrets" du testing doc.
- **Status :** done

### M5 — Qualité

#### T5.1 ArchUnit
- **Outcome :** vérifier que les règles existantes (`..domain..` sans
  Spring/JPA) couvrent bien `AttachmentScanPort`, `DossierExportPort`,
  `AttachmentContentSniffer` sans modification nécessaire ; ajuster si un
  écart apparaît.
- **Deps :** M1, M2.
- **Tests :** `ArchitectureRulesTest` (existant).
- **Status :** done

#### T5.2 Verify + mise à jour docs produit
- **Outcome :** `./mvnw verify -Dmaven.compiler.release=21` vert ;
  `TODO.md` §23/§30 mis à jour (clos ou reste réduit) ; `FILE-ATTENTE.md`
  et `DEFERRED-FEATURES.md` mis à jour après implémentation.
- **Deps :** M1–M4, T5.1.
- **Tests :** evidence Phase 8 collée dans le testing doc.
- **Status :** done

## Mapping tests ↔ tâches

| Scénario testing | Tâches |
|---|---|
| `AttachmentContentSniffer` (tous cas) | T1.1 |
| `NoOpAttachmentScanAdapter` | T1.2 |
| Upload durci (rejet/accept) | T1.3 |
| `DossierExportPort` streamé + fermeture | T2.1 |
| `ExportDossiersUseCase` (ACL, filtres, masquage) | T2.2 |
| `DossierExportController` / `CsvWriter` | T2.3 |
| MockMvc export bout en bout | T2.2, T2.3 |
| OpenAPI golden export | T2.4 |
| `HttpRestServiceConnector` (guard, non-régression) | T3.1 |
| Garde-fou secrets (Connector/Webhook DTO) | T4.2 |
| ArchUnit | T5.1 |

Tous les scénarios testing ont une tâche.

## Dependencies (ordre)

Aucune dépendance croisée entre M1, M2, M3, M4 — peuvent être menés en
parallèle ou dans n'importe quel ordre. À l'intérieur de chaque milestone :
T1.1 → T1.2 → T1.3 ; T2.1 → T2.2 → T2.3 → T2.4 ; T3.1 (seul) ; T4.1 → T4.2.
M5 (T5.1, T5.2) ferme le lot après M1–M4.

## Timeline & Estimates

Pas d'estimation calendaire. Lot unique de développement, toutes les
tâches M1–M5.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| `getResultStream()` Hibernate mal fermé (fuite de connexion) | `try-with-resources` explicite + test dédié (T2.1) |
| Sniffer trop strict (faux positifs sur fichiers légitimes) | Tests avec fichiers réels représentatifs de chaque type autorisé (T1.1) |
| `HttpRestServiceConnector` accidentellement exposé plus tard | Test de non-régression vérifiant l'absence de `ConnectorDefinition` (T3.1) |
| Regex de détection de clé secrète (`isSecretKey`) incomplète pour de nouveaux DTO futurs | Garde-fou T4.2 documente la liste exacte des DTO couverts — toute extension future doit y ajouter un cas |

## Resources Needed

Design reviewed, testing doc scénarisé, ports/adapters existants
(`OutboundUrlGuard`, `DossierFieldRedactor`, `DossierTypeAclRepository`),
`DossierElementUploadController` comme point d'intégration M1.

## Progress (Phase 5)

Toutes les tâches T1.1–T5.2 **done** (2026-08-21), TDD strict (rouge → vert
sur chaque nouvelle classe de production).

- Backend : `./mvnw verify -Dmaven.compiler.release=21` → **BUILD SUCCESS**,
  Tests run **960**, Failures **0**, Jacoco check met.
- Frontend : `npm run test` → 47 files / **136** tests ; `npm run test:e2e`
  (`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`, cache Playwright
  local à une version antérieure — cf. note environnement) → **39** passed.
  Aucun changement `frontend/` dans cette feature (D-CSV-UI) : régression
  complète du projet demandée explicitement, pas un critère de cette feature.
- `TODO.md` §23 (reste réduit) et §30 (clos) mis à jour ; `FILE-ATTENTE.md`
  daté avec l'évidence Phase 5.
- Prochaine étape : `dev-planning` Phase 6 / `dev-implementation` Phase 7
  (Check Implementation) puis `dev-testing` Phase 8 et `dev-review` Phase 9.
