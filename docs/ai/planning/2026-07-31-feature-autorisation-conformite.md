---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-03
updated: 2026-08-03
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine** : `TenantAdminGuard` + `FieldDefinition.sensitive` +
  branchement use cases admin
- [ ] **M2 — Security HTTP** : `@EnableMethodSecurity` + `@PreAuthorize` +
  suppression guard infra + 403 cohérent
- [ ] **M3 — API / persistence sensitive** : round-trip JSON + IT
- [ ] **M4 — Preuves & docs** : couverture testing, TODO §19/§23,
  DEFERRED note stockage, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [ ] **T1. `FieldDefinition.sensitive`**
  - **Outcome :** 8e composant `boolean sensitive` ; défaut `false` ;
    tous les call sites / tests compilent.
  - **Depends on :** —
  - **Validation :** compile + unit FieldDefinition / validator ignore flag.
  - **Tests liés :** Unit — FieldDefinition.sensitive ; validator.

- [ ] **T2. `TenantAdminGuard` domaine + tests**
  - **Outcome :** classe pure `domain.dossier` ;
    `requireTenantAdmin(AccessContext.User)` → `AccessDeniedException`.
  - **Depends on :** —
  - **Validation :** tests unitaires verts.
  - **Tests liés :** Unit — TenantAdminGuard.

- [ ] **T3. Brancher guard sur use cases admin**
  - **Outcome :** Create/Update type, Grant/Revoke utilisent le guard domaine ;
    suppression des `requireTenantAdmin` privés dupliqués ; wiring Spring
    (`DossierConfig` bean).
  - **Depends on :** T2
  - **Validation :** unit use cases non-admin → deny.
  - **Tests liés :** Unit — use cases admin.

### M2 — Security HTTP

- [ ] **T4. `@EnableMethodSecurity`**
  - **Outcome :** activé sur la config Security existante (BFF).
  - **Depends on :** —
  - **Validation :** contexte boot IT.
  - **Tests liés :** IT method security (smoke).

- [ ] **T5. Annoter controllers + retirer guard infra**
  - **Outcome :** matrice design appliquée (`dossier-types` mutateurs + ACL
    → `TENANT_ADMIN` ; workflow type-config mutateurs →
    `TENANT_ADMIN|WORKFLOW_ADMIN`) ; suppression
    `infrastructure.web.dossier.TenantAdminGuard` et injections.
  - **Depends on :** T4
  - **Validation :** MockMvc 403 / 2xx.
  - **Tests liés :** IT — Method security MockMvc.

- [ ] **T6. Bridging 403 method security**
  - **Outcome :** refus `@PreAuthorize` → HTTP 403 `ProblemDetail` (pas 500) ;
    alignement advice si nécessaire.
  - **Depends on :** T5
  - **Validation :** MockMvc assert status + body type.
  - **Tests liés :** IT — Method security MockMvc.

### M3 — sensitive bout-en-bout

- [ ] **T7. API / JSON `sensitive`**
  - **Outcome :** create/update/get type transportent le flag ; omission →
    false ; pas de redaction sur GET dossier.
  - **Depends on :** T1
  - **Validation :** IT API + persistence JSON.
  - **Tests liés :** IT — sensitive bout-en-bout ; Persistence JSON.

### M4 — Preuves & docs

- [ ] **T8. Couverture testing + régression**
  - **Outcome :** cases testing doc cochables ; `./mvnw test` vert ;
    Jacoco ≥ 80 % ; ArchUnit OK.
  - **Depends on :** T3, T5, T6, T7
  - **Validation :** surefire + jacoco.
  - **Tests liés :** tous.

- [ ] **T9. Docs suivi produit**
  - **Outcome :** TODO §19 clos ; §23 partiel (flag oui, masquage non) ;
    note anti-MinIO / port S3-FS dans DEFERRED ou design ; doc
    `implementation` amorcée en phase 5.
  - **Depends on :** T8 (pour preuves) — peut démarrer en parallèle doc
  - **Validation :** relecture ; lint feature.
  - **Tests liés :** —

## Dependencies
**What needs to happen in what order?**

```text
T1 ──┬── T7 ──┐
     │        ├── T8 ── T9
T2 ── T3 ─────┤
T4 ── T5 ── T6 ┘
```

- Features amont : `authentification`, `dossiers`, `workflow-engine` (Complete).
- Pas de dépendance code vers connecteurs / MinIO.

## Timeline & Estimates
**When will things be done?**

Effort relatif (pas calendaire) : M1 petit ; M2 moyen (matrice + 403) ; M3
petit ; M4 doc + preuves. Risque principal = bridging exception method
security.

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| `@PreAuthorize` → 500 au lieu de 403 | T6 explicite + test MockMvc body |
| Oubli d’un endpoint admin | Matrice design + grep `TenantAdminGuard` / hasRole |
| Cassure constructeurs `FieldDefinition` | T1 compile-first + IT JSON legacy |
| Sur-annotation des GET dossiers | Matrice : pas de rôle unique ; policy |

## Resources Needed
**What do we need to succeed?**

- Worktree `.worktrees/feature-autorisation-conformite`
- Branche `cursor/autorisation-conformite-f54c`
- `./mvnw test`
- Traçage `task` CLI : **indisponible** (`unknown command 'task'`)

## Coverage map — testing → tasks

| Scénario testing | Tasks |
|---|---|
| Unit TenantAdminGuard | T2 |
| Unit FieldDefinition.sensitive / validator | T1 |
| Unit use cases admin | T3 |
| Unit policy régression | T3 / T8 |
| IT method security 403/2xx / 401 | T4, T5, T6, T8 |
| IT sensitive round-trip + clair dossier | T7, T8 |
| IT persistence JSON | T7 |
| Régression mvn test | T8 |
