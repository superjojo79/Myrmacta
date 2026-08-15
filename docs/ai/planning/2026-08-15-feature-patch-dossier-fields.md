---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-15
updated: 2026-08-15
feature: patch-dossier-fields
design_aligned: 2026-08-15
phase4: 2026-08-15
phase6: 2026-08-15
sequencing: layered
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : `DossierFieldMerger` (merge + diff sensitive), extension `Dossier.withFields`, port + use case
- [x] **M2 — API REST** : DTO, `DossierController`, extension `SecurityAuditLog`, wiring `DossierConfig`
- [x] **M3 — Preuves & docs** : suite de tests verte, `DEFERRED-FEATURES.md` §1.4 / `TODO.md` point 24 mis à jour

Pas de milestone persistence (aucune nouvelle table) ni frontend (hors
scope V1, voir design) — feature volontairement plus petite que
`relations-dossiers`.

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. `DossierFieldMerger`**
  - **Outcome :** `domain/dossier/DossierFieldMerger.java` — classe
    statique, même patron que `DossierFieldRedactor`. `merge(Map existing, Map patch)`
    (valeur `null` dans `patch` → clé retirée du résultat ; clé présente
    → écrasée ; clé absente de `patch` → conservée). `changedSensitiveFieldNames(List<FieldDefinition> definitions, Map before, Map after)`
    (noms des champs `isSensitive()` dont la valeur avant/après diffère).
  - **Depends on :** —
  - **Validation :** unitaires verts ; ArchUnit `domain_has_no_framework_dependency`.
  - **Tests liés :** `DossierFieldMerger.merge` + `.changedSensitiveFieldNames` — tous scénarios testing §.

- [x] **T2. `Dossier.withFields(Map, String, Instant)` — nouvelle surcharge**
  - **Outcome :** pose `fields`, `updatedBy`, `updatedAt` ; laisse
    `status`/`version`/`closedBy`/`closedAt` inchangés. Surcharge
    existante `withFields(Map)` conservée telle quelle (utilisée par
    `DossierFieldRedactor`).
  - **Depends on :** —
  - **Validation :** unitaires verts sur les deux surcharges.
  - **Tests liés :** `Dossier.withFields(Map, String, Instant)` — testing §.

- [x] **T3. Port `UpdateDossierFieldsUseCase`**
  - **Outcome :** `domain/dossier/port/in/UpdateDossierFieldsUseCase.java`
    — `record UpdateDossierFieldsCommand(UUID dossierId, Map<String,Object> fieldsPatch, String updatedBy, Long expectedVersion)`,
    `record UpdateDossierFieldsResult(Dossier dossier, List<String> sensitiveFieldsChanged)`,
    méthode `update(UpdateDossierFieldsCommand, AccessContext)`.
  - **Depends on :** —
  - **Validation :** compile.
  - **Tests liés :** —

- [x] **T4. `UpdateDossierFieldsUseCaseImpl`**
  - **Outcome :** rejette body vide (400, D-EMPTY-BODY) ; charge le
    dossier (404) ; `assertCanEdit` (403) ; gate `CLOTURE`/`ARCHIVE`
    (409, D-STATUS-GATE) ; verrou `expectedVersion` (409) ; fusionne via
    `DossierFieldMerger.merge` ; charge le type ; valide le jeu fusionné
    via `DossierFieldValidator.validate` existant (400,
    `DossierFieldValidationException`) ; calcule
    `changedSensitiveFieldNames` ; sauvegarde via
    `withFields(merged, updatedBy, now)` ; renvoie
    `UpdateDossierFieldsResult`.
  - **Depends on :** T1, T2, T3
  - **Validation :** unitaires verts (mocks repository/policy/type repo).
  - **Tests liés :** `UpdateDossierFieldsUseCaseImpl` — tous scénarios testing §, y compris la vérification ArchUnit (aucune dépendance `infrastructure.*`).

### M2 — API REST

- [x] **T5. `SecurityAuditLog.sensitiveFieldChanged`**
  - **Outcome :** nouvelle méthode sur `infrastructure/security/SecurityAuditLog.java`,
    niveau **INFO** (distinct des refus existants, niveau WARN) ; log
    `dossierId` + nom du champ + auteur — jamais la valeur.
  - **Depends on :** —
  - **Validation :** unitaires verts (capture logger, comme les tests
    existants d'`accessDenied`/`tenantMismatch`).
  - **Tests liés :** `SecurityAuditLog.sensitiveFieldChanged` — testing §.

- [x] **T6. DTO `UpdateDossierFieldsRequest`**
  - **Outcome :** `record UpdateDossierFieldsRequest(Map<String, Object> fields, Long expectedVersion)`
    dans `infrastructure/web/dossier/DossierApiDtos.java`, même fichier
    que `UpdateDossierStatusRequest`.
  - **Depends on :** T3
  - **Validation :** compile.
  - **Tests liés :** —

- [x] **T7. `DossierController` — `PATCH /{id}/fields`**
  - **Outcome :** `@PatchMapping("/{id}/fields")`, même patron que
    `updateStatus` ; construit `UpdateDossierFieldsCommand` depuis le
    body + `actor.userId()` ; après l'appel au use case, boucle sur
    `result.sensitiveFieldsChanged()` et appelle
    `SecurityAuditLog.sensitiveFieldChanged(request, authentication, id, field)`
    pour chacun ; renvoie `DossierResponse.from(result.dossier())`.
  - **Depends on :** T4, T5, T6
  - **Validation :** MockMvc.
  - **Tests liés :** MockMvc — 200/400/403/404/409, désérialisation
    `null` JSON → clé présente à `null` dans la Map, événement
    `SECURITY_AUDIT` émis/absent selon le contenu du patch, régression
    `POST`/`PATCH .../status`/`GET .../elements`/`GET .../relations`.

- [x] **T8. Wiring `DossierConfig`**
  - **Outcome :** bean `UpdateDossierFieldsUseCase`.
  - **Depends on :** T4
  - **Validation :** contexte Spring monte (IT boot existante).
  - **Tests liés :** IT boot.

### M3 — Preuves & docs

- [x] **T9. Suite de tests complète + couverture**
  - **Outcome :** `./mvnw test` vert (module `dossier`) ; Jacoco ≥ 80 %
    zones touchées ; checklist testing doc cochée ou justifiée ;
    régression pagination (`GET /api/dossiers?sort=updatedAt,desc` fait
    remonter le dossier édité).
  - **Depends on :** T1–T8
  - **Validation :** commandes CI locales.
  - **Tests liés :** tous scénarios testing restants.

- [x] **T10. Docs implementation + `DEFERRED-FEATURES.md`/`TODO.md`**
  - **Outcome :** notes `docs/ai/implementation/2026-08-15-feature-patch-dossier-fields.md` ;
    `DEFERRED-FEATURES.md` §1.4 (« PATCH fields dossier » → Livré) ;
    `TODO.md` point 24 (contrat d'API) noté comme partiellement comblé.
  - **Depends on :** T9
  - **Validation :** `lint --feature patch-dossier-fields`.
  - **Tests liés :** —

## Traceability — testing → tasks

| Scénario testing | Tasks |
|---|---|
| `DossierFieldMerger.merge` — null efface/écrase/conserve | T1 |
| `DossierFieldMerger.changedSensitiveFieldNames` — ajout/effacement/changement/no-op/non-sensitive | T1 |
| `Dossier.withFields(Map, String, Instant)` — nouvelle surcharge + non-régression surcharge existante | T2 |
| `UpdateDossierFieldsUseCaseImpl` — ACL/404/409 statut/409 version/400 validation/A1/A2 | T4, T9 |
| ArchUnit — use case sans dépendance infrastructure | T4, T9 |
| `SecurityAuditLog.sensitiveFieldChanged` — métadonnées seulement, niveau INFO | T5 |
| MockMvc `PATCH .../fields` + désérialisation `null` + régression | T7, T9 |
| Audit `SECURITY_AUDIT` bout en bout (émis/absent) | T7, T9 |
| Régression tri `updatedAt,desc` (pagination-dossiers) | T9 |
| Docs `DEFERRED-FEATURES`/`TODO` | T10 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T4 ┐
T2 → T4 ┤
T3 → T4 ┴→ T7 → T9 → T10
T3 → T6 ↗
T4 → T8 ↗
T5 → T7 ↗
```

- T1, T2, T3, T5 sont indépendants entre eux — peuvent être menés en
  parallèle dès le début.
- T6 ne dépend que de T3 (le contrat du port) — peut démarrer avant T4.
- Aucune dépendance externe (pas de nouvelle table, pas de process BPMN,
  pas de composant FE) — la feature la plus courte du backlog jusqu'ici.

## Timeline & Estimates
**When will things be done?**

| Milestone | Effort indicatif |
|---|---|
| M1 — Domaine | 0.5 j |
| M2 — API REST | 0.5 j |
| M3 — Preuves & docs | 0.5 j |
| **Total** | **~1.5 j** |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Jackson supprime silencieusement les clés à valeur `null` avant que le use case les voie (dépendrait de la config `ObjectMapper`) | Test d'intégration dédié (T7/T9) qui vérifie explicitement qu'un body `{"champ": null}` produit bien une entrée `null` dans la `Map<String,Object>` reçue côté serveur, pas une clé absente |
| Comparaison de valeurs dans `changedSensitiveFieldNames` faussement positive si un même nombre arrive sous deux types différents (ex. `Integer` JSON vs `Long`/`Double` déjà stocké) | Accepté en V1 — impact limité à un événement d'audit superflu (pas une fuite, pas un refus incorrect), pas une comparaison de sécurité ; à revisiter si le bruit devient gênant en usage réel |
| Régression sur `DossierFieldRedactor` si la nouvelle surcharge `withFields` est confondue avec l'existante | T2 inclut explicitement un test de non-régression sur la surcharge `withFields(Map)` seule |
| Oubli de mettre à jour `DEFERRED-FEATURES.md`/`TODO.md` (déjà arrivé pour d'autres features, corrigé en cours de route dans les audits précédents) | T10 dédié, checké par `lint --feature` en fin de plan |

## Resources Needed
**What do we need to succeed?**

- Worktree `feature-patch-dossier-fields` (déjà en place) + JDK 25
- Skills : `tdd`, `verify`
- Docs requirements/design `reviewed` (Phases 1–3 closes) ; testing doc
  comme référence de scénarios

## Sequencing notes

- Ordre **layered** mais très parallélisable : domaine (T1–T4) puis API
  (T5–T8) puis preuves (T9–T10). Contrairement à `relations-dossiers`,
  quasiment toutes les tâches domaine peuvent démarrer en parallèle
  (aucune dépendance transactionnelle multi-écriture, aucun ServiceTask).
- Après chaque tâche implémentée → Phase 6 (`dev-planning`, maj planning)
  puis reprise Phase 5 (`dev-implementation`).
- Phase 7 (Check Implementation) quand T1–T10 `[x]`.

## Phase 6 — Update Planning (clôture)

**T1–T10 tous `[x]`.** Aucun blocage, aucune tâche abandonnée, aucun écart
de scope par rapport au plan initial — l'ordre d'exécution réel a suivi
T1→T2→T3→T5 (indépendants) puis T4 (dépend de T1/T2/T3) puis T6→T7→T8
puis T9→T10, conforme au graphe de dépendances ci-dessus. Preuves fraîches
à chaque étape : `DossierFieldMerger` 11/11, `DossierTest` 3/3 +
non-régression `DossierFieldRedactorTest` 5/5, `SecurityAuditLogTest`
5/5, `UpdateDossierFieldsUseCaseImplTest` 15/15, `ArchitectureRulesTest`
3/3 (vérifié après T4 spécifiquement, pas seulement en fin de plan),
`DossierApiTest` 53/53 (18 nouveaux scénarios PATCH fields + 35
scénarios existants inchangés), suite complète `./mvnw test` 691/691,
Jacoco 92–100 % sur les six classes touchées (`DossierFieldMerger` 100 %,
`Dossier` 99.2 %, `SecurityAuditLog` 100 %, `UpdateDossierFieldsUseCaseImpl`
95.1 %, `DossierController` 92.2 %). Aucune tâche nouvelle découverte en
cours de route. `DEFERRED-FEATURES.md` §1.4 et `TODO.md` point 24 mis à
jour (T10). Prochaine étape : Phase 7 (Check Implementation), puis
`dev-testing`/`dev-review`.
