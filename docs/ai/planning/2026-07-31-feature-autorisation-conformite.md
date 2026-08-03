---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: implemented
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
main_integrated: 2026-08-03 # PR #18
---

# Project Planning & Task Breakdown

Plan initial **phase 4** — dérivé des docs `requirements` / `design` /
`testing` en `reviewed` (ou `design_aligned`). Prêt pour phase 5.

## Phase 5 — synthèse

**9/9 tâches done (T1–T9).** `./mvnw verify` → 193 tests / 0 fail ; Jacoco
90,2 % ; ArchUnit OK. Écart low DEV-SENS-BOOL (`Boolean` coerce). Suite :
phase 7 Check Implementation.

## Phase 4 — synthèse

**9 tâches d’implémentation (T1–T9), 4 milestones.** Aucun bloqueur.
Traçage `task` CLI indisponible. Branche :
`cursor/autorisation-conformite-f54c`.

**Main intégré (PR #18) :** TX2 atomique, `WorkflowVariables` helper,
exceptions not-found typées, **`DossierAccessGuard`**. Contrainte phase 5 :
conserver `DossierAccessGuard` sur controllers workflow instance ; ne pas
réintroduire le couple find+assert.

**Prochaines actions phase 5 (ordre) :** T1 + T2 en parallèle → T3 ; T4 → T5
→ T6 ; T7 après T1 ; puis T8 → T9.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : `TenantAdminGuard` + `FieldDefinition.sensitive` +
  branchement use cases admin types/ACL
- [x] **M2 — Security HTTP** : `@EnableMethodSecurity` + `@PreAuthorize`
  (matrice) + suppression guards manuels HTTP + advice D-403
- [x] **M3 — sensitive bout-en-bout** : JSON / API / persistence ; pas de
  redaction lecture dossier
- [x] **M4 — Preuves & docs** : testing vert, Jacoco ≥ 80 %, TODO §19/§23,
  note S3/FS, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. `FieldDefinition.sensitive` (D-SENS-DEF)**
  - **Outcome :** 8e composant `boolean sensitive` ; constructeur confort
    déléguant avec `sensitive=false` ; tous les call sites / tests
    compilent ; validator ignore le flag.
  - **Depends on :** —
  - **Validation :** `./mvnw -Dtest=DossierFieldValidatorTest,FieldDefinition*`
    (ou compile + units ciblés) vert.
  - **Tests liés :** Unit — FieldDefinition.sensitive ; validator.
  - **Req :** success `sensitive` bout-en-bout (partie domaine).

- [x] **T2. `TenantAdminGuard` domaine + tests**
  - **Outcome :** `com.flowforge.domain.dossier.TenantAdminGuard` pur ;
    `requireTenantAdmin(AccessContext.User)` →
    `com.flowforge.domain…AccessDeniedException` si pas `TENANT_ADMIN`.
  - **Depends on :** —
  - **Validation :** unitaires TENANT_ADMIN OK ; WORKFLOW_ADMIN /
    MANAGER / sans rôle → deny.
  - **Tests liés :** Unit — TenantAdminGuard.
  - **Req :** guard domaine.

- [x] **T3. Brancher guard sur use cases admin**
  - **Outcome :** Create/Update `DossierType`, Grant/Revoke ACL appellent
    le guard ; suppression des `requireTenantAdmin` privés ; bean dans
    `DossierConfig` ; injection constructeur.
  - **Depends on :** T2
  - **Validation :** units use cases non-admin → deny ; admin happy path.
  - **Tests liés :** Unit — use cases admin ; policy régression (suite
    existante inchangée).
  - **Req :** double garde types/ACL.

### M2 — Security HTTP

- [x] **T4. `@EnableMethodSecurity`**
  - **Outcome :** annotation sur `SecurityConfig` (BFF) ; contexte boot OK.
  - **Depends on :** —
  - **Validation :** IT boot / smoke security existante verte.
  - **Tests liés :** IT method security (smoke via T5/T8).
  - **Req :** EnableMethodSecurity actif.

- [x] **T5. Annoter controllers + retirer guards HTTP manuels**
  - **Outcome :** matrice design `reviewed` :
    - `POST`/`PATCH` dossier-types + ACL → `@PreAuthorize("hasRole('TENANT_ADMIN')")`
    - mutateurs workflow-type-configs →
      `@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")`
    - **aucune** annotation rôle sur dossiers / start / history / complete
      (D-WF-INST) ; **conserver** `DossierAccessGuard` (PR #18)
    - suppression `infrastructure.web.dossier.TenantAdminGuard` + injections
    - suppression `requireWorkflowAdmin`
  - **Depends on :** T4
  - **Validation :** MockMvc 403/2xx ; `rg TenantAdminGuard infrastructure/web`
    vide (sauf absence) ; pas de `requireWorkflowAdmin` ;
    `DossierAccessGuard` toujours référencé par workflow controllers.
  - **Tests liés :** IT — Method security MockMvc (MANAGER, WORKFLOW_ADMIN
    sur types, TENANT_ADMIN OK, ACL, workflow-config).
  - **Req :** surface livrable ; Q7–Q9.

- [x] **T6. Bridging 403 method security (D-403)**
  - **Outcome :** advice (`MethodSecurityExceptionHandler` ou équivalent)
    mappe `AuthorizationDeniedException` /
    `org.springframework.security.access.AccessDeniedException` → 403
    `ProblemDetail` ; handlers domaine inchangés (FQCN distincts) ; 401
    anonyme préservé.
  - **Depends on :** T5
  - **Validation :** MockMvc assert status **403** + corps ProblemDetail
    (pas 500).
  - **Tests liés :** IT — Method security MockMvc (ProblemDetail).
  - **Req :** 403 cohérent method security.

### M3 — sensitive bout-en-bout

- [x] **T7. API / JSON / persistence `sensitive`**
  - **Outcome :** create/update/get type transportent le flag ; omission →
    `false` ; IT persistence JSON ; GET dossier avec champ sensible →
    **valeur en clair** (pas de redaction).
  - **Depends on :** T1
  - **Validation :** IT API + persistence.
  - **Tests liés :** IT — sensitive bout-en-bout ; Persistence JSON.
  - **Req :** `sensitive` API ; critère anti-masquage.

### M4 — Preuves & docs

- [x] **T8. Couverture testing + régression**
  - **Outcome :** cases du doc testing cochables (ou couverts par suites
    existantes documentées) ; `./mvnw test` vert ; Jacoco ≥ 80 % ;
    ArchUnit OK ; scénarios req 3/6 (MANAGER sans grant, VIEWER sans EDIT)
    vérifiés.
  - **Depends on :** T3, T5, T6, T7
  - **Validation :** surefire exit 0 + rapport Jacoco.
  - **Tests liés :** tous + E2E « régression mvn test ».
  - **Req :** critères tests / Jacoco / ArchUnit.

- [x] **T9. Docs suivi produit + implementation**
  - **Outcome :** TODO §19 **clos** ; §23 **partiel** (flag oui, masquage
    non) ; note anti-MinIO / port S3–FS dans `DEFERRED-FEATURES.md` (ou
    équivalent) ; `docs/ai/implementation/…autorisation-conformite.md`
    amorcé/à jour ; lint feature docs OK.
  - **Depends on :** T8 (preuves) — rédaction peut démarrer avant
  - **Validation :** relecture ; `npx ai-devkit@latest lint --feature
    autorisation-conformite` (hors MISS branche cloud).
  - **Tests liés :** —
  - **Req :** TODO + contrainte stockage documentée.

## Dependencies
**What needs to happen in what order?**

```text
T1 ──┬── T7 ────────┐
     │              ├── T8 ── T9
T2 ── T3 ───────────┤
T4 ── T5 ── T6 ─────┘
```

- Parallélisable : (T1 ∥ T2 ∥ T4) puis fan-in.
- Features amont Complete : `authentification`, `dossiers`, `workflow-engine`.
- Pas de dépendance code storage / connecteurs.

## Timeline & Estimates
**When will things be done?**

Effort relatif (pas calendaire) :

| Milestone | Effort | Notes |
|---|---|---|
| M1 | Petit | Record + guard + wiring |
| M2 | Moyen | Matrice + D-403 (risque principal) |
| M3 | Petit | JSON déjà document-based |
| M4 | Petit–moyen | Suite complète + docs produit |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| `@PreAuthorize` → 500 au lieu de 403 | T6 + assert ProblemDetail MockMvc |
| Oubli d’un endpoint admin | Matrice design + grep guards manuels |
| Cassure call sites `FieldDefinition` | T1 ctor confort + compile-first |
| Sur-annotation dossiers / workflow instance | D-WF-INST explicite dans T5 ; review diff |
| Confusion AccessDeniedException domaine vs Spring | FQCN dans handlers ; tests séparés |
| Jacoco sous le seuil après ajouts | T8 ; tests ciblés avant fin M4 |

## Resources Needed
**What do we need to succeed?**

- Workdir : `/workspace/.worktrees/feature-autorisation-conformite`
- Branche : `cursor/autorisation-conformite-f54c`
- `./mvnw test`
- Traçage `task` : **indisponible** (`npx ai-devkit@latest task` → unknown
  command)

## Coverage map — testing → tasks

| Scénario testing | Tasks |
|---|---|
| Unit TenantAdminGuard (4 cases) | T2 |
| Unit FieldDefinition.sensitive / validator | T1 |
| Unit use cases admin | T3 |
| Unit policy régression | T3 / T8 (suite existante) |
| IT `POST` types MANAGER → 403 ProblemDetail | T5, T6, T8 |
| IT `POST` types WORKFLOW_ADMIN → 403 | T5, T6, T8 |
| IT `POST` types TENANT_ADMIN → 2xx | T5, T8 |
| IT ACL sans TENANT_ADMIN → 403 | T5, T6, T8 |
| IT workflow-config sans rôle → 403 | T5, T6, T8 |
| IT workflow-config WORKFLOW_ADMIN OK | T5, T8 |
| IT VIEWER PATCH status sans EDIT → 403 policy | T8 |
| IT session absente → 401 | T8 (régression) |
| IT sensitive true/false / clair dossier | T7, T8 |
| IT persistence JSON sensitive | T7 |
| Régression `./mvnw test` | T8 |

## Coverage map — requirements success → tasks

| Critère success requirements | Tasks |
|---|---|
| Point 19 clos V1 | T9 |
| EnableMethodSecurity + surface livrable | T4, T5 |
| Guard domaine + suppression infra | T2, T3, T5 |
| Suppression `requireWorkflowAdmin` | T5 |
| `sensitive` bout-en-bout / écriture admin | T1, T7 |
| Pas de masquage lecture | T7, T8 |
| MockMvc 403 ProblemDetail + régressions | T5, T6, T8 |
| Contrainte S3/FS documentée | T9 |
| mvn test / ArchUnit / Jacoco | T8 |
| Docs + TODO §19/§23 | T9 |

**Vérification phase 4 :** chaque case `- [ ]` du doc testing a ≥ 1 tâche ;
chaque critère success requirements a ≥ 1 tâche. Aucun trou.

## Phase 4 — notes

| Point | Décision |
|---|---|
| Plan pré-existant (phase 1/3) | Revalidé et enrichi (maps req, ordres, risques) |
| Status | `ready` — exécutable en phase 5 |
| Bloqueurs | Aucun |
| Hors plan | §21–22, ObjectStoragePort, masquage, frontend |

**Prochaine phase :** `dev-implementation` phase 5 — démarrer **T1** et
**T2** (puis T4 en parallèle si souhaité).
