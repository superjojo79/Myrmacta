---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
phase1: 2026-09-07
phase2: 2026-09-07
todo_ref: TODO.md §57 · FILE-ATTENTE.md
---

# Requirements & Problem Understanding — `revue-adversariale-securite`

## Phase 2 close (2026-09-07)

Stakeholder : **`OK tous`** — Q1–Q12 = option **A** (recos). Le code PR #113
est **acté** (pas d’adaptation). Phases 3–9 = revalidation du design/code
existant, pas une réécriture.

## Problem Statement
**What problem are we solving?**

Une revue adversariale du dépôt (`main` @ `8080abf`) après clôture des
points 40–46 a trouvé **2 High et 13 Medium** encore exploitables. Le
sandboxing Thymeleaf / pin IP webhook / GRANT PG / secrets fail-fast tiennent ;
de nouvelles surfaces (BPMN, complete tâche, MCP/connecteurs, HMAC résiduel)
ne sont pas au même niveau.

- **Affectés :** `WORKFLOW_ADMIN` / `TENANT_ADMIN` (RCE BPMN) ; managers EDIT
  (hijack de tâche) ; humains Keycloak mal-rôlés `API_SERVICE` ; ops S3 ;
  partenaires webhook ; agents MCP.
- **Situation actuelle :** findings consignés TODO.md §57 (section Security) ;
  aucun correctif sur `main`.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

Implémenter **H1, H2, M1–M13** (Q1=A) selon les décisions Q2–Q12 = A.

### Non-goals

- L1 split d'autorités M2M / scopes JWT (D6 assumé).
- L2 fail-fast actuator hors loopback (casserait scrape documenté).
- L3 CSP/HSTS globaux (seulement preview HTML = M13).
- L4 AV, L5 rate-limit distribué, L6 dryRun `define_dossier_type`, L7 garde
  domaine deploy (REST/MCP déjà gated).
- Changer la décision 20 (ACL de type) ou le modèle instance = tenant.
- Nouvelle UI admin (sauf régression SPA existante).

## User Stories & Use Cases

- En tant qu'**ops SaaS**, un `WORKFLOW_ADMIN` ne peut plus obtenir une RCE via
  un BPMN `scriptTask` / `flowable:expression`.
- En tant qu'**assignee**, seul moi (ou un admin) complète ma userTask.
- En tant qu'**humain OIDC**, un rôle realm `API_SERVICE` ne m'élève pas.
- En tant qu'**intégrateur HMAC**, je signe `timestamp.idempotencyKey.body`.
- En tant qu'**admin connecteur**, une clé hors schéma est rejetée ; `mapping`
  reste un champ déclaré.

**Cas limites :**

- Tâche **sans** assignee : EDIT du type suffit (D-H2).
- BPMN existants du dépôt : uniquement `delegateExpression` catalogue → restent valides.
- `mapping` absent du schéma aujourd'hui → **ajouté** (sinon D-M5 casserait les instances).
- Preview live `htmlContent` : toujours TA-only ; M13 s'applique aussi au HTML stocké.

## Success Criteria

- [x] H1 : BPMN `scriptTask` / `flowable:class` / `flowable:expression` → 400 au
      parse/deploy ; `delegateExpression="${connectorServiceTask}"` OK. (Q2=A)
- [x] H2 : complete non-assignee non-admin → 403 ; assignee → 204. (Q3=A)
- [x] M1 : OIDC n'émet pas `ROLE_API_SERVICE`. (Q4=A)
- [x] M2 : `app.storage.s3.access-key-id` bind `APP_STORAGE_S3_ACCESS_KEY`.
- [x] M3 : `HttpRestConnector` pose `Host` d'origine et refuse un `Host` appelant.
- [x] M4 : URL `http://100.64.0.1/` et `http://0.0.0.1/` rejetées.
- [x] M5 : config `{ "backupToken": "x" }` → validation error ; `mapping` accepté. (Q7=A)
- [x] M6 : auth échouée n'incrémente pas le bucket partenaire. (Q10=A)
- [x] M7 : HMAC sans idempotency dans la signature → 401 ; même corps shared-secret
      rejoué dans la fenêtre → 401. (Q5=A, Q6=A)
- [x] M8 : `/runtime` d'une tâche d'autrui → 403. (même règle que Q3)
- [x] M9 : callback mapping `tenantId` → 400 ; `ConnectorServiceTask` ignore la var process.
- [x] M10 : `to=a@x.com,b@y.com` → 400.
- [x] M11 : réponse HTTP / retrieve au-delà du cap → erreur ; STORE oversized encodé → 400 avant OOM.
- [x] M12 : `DOSSIER_MANAGER` sans type VIEW lié au template → 403. (Q8=A)
- [x] M13 : template `th:utext` → 400 à l'activation ; preview HTML porte CSP. (Q11=A)
- [x] `./mvnw verify` 1705/0 ; Vitest 289 ; Playwright 65 (session 2026-09-07, code inchangé après Q1–Q12).

## Constraints & Assumptions

- Hexagone : validation BPMN dans `BpmnParserPort` (infra adapter), pas de Flowable dans `domain`.
- HMAC M7 = **rupture de contrat** (Q5=A, comme F6, TODO §54) — GUIDE / changelog.
- A-SPA (Q9=A) : zéro fichier frontend visé.
- A-EXPRESSION-EDITOR (Q9=A) : le champ `flowable:expression` du panel bpmn-js reste visible ;
  le backend refuse à la sauvegarde/deploy (400).

## Questions & Open Items

**Aucune ouverte.** Stakeholder 2026-09-07 : `OK tous` → Q1–Q12 = **A**.

| ID | Décision actée |
|---|---|
| Q1 | Lot unique H1/H2 + M1–M13 |
| Q2 | Allowlist BPMN parse-time (D-H1) |
| Q3 | Complete : assignee ou TA/WA ; unassigned → EDIT (D-H2) ; M8 idem |
| Q4 | OIDC sans `ROLE_API_SERVICE` ; MCP inchangé (D-M1) |
| Q5 | HMAC `timestamp.idempotencyKey.body` (rupture, comme F6) |
| Q6 | Shared-secret : cache replay corps 5 min |
| Q7 | Rejet clés hors schéma + `mapping` déclaré (D-M5) |
| Q8 | Generate : DM si type VIEW liste la clé ; garde **contrôleur** |
| Q9 | Pas de chantier FE (backend refuse `flowable:expression`) |
| Q10 | Auth webhook puis rate-limit (D-M6) |
| Q11 | Interdire `th:utext`/`[(${…})]` + CSP preview (D-M13) |
| Q12 | L1–L7 hors cycle |

### Alternatives de lot (Q1)

| Approche | Verdict |
|---|---|
| A — un lot H+M | **Acté** |
| B — High only | Écarté (M1 court-circuite H2) |
| C — HMAC plus tard | Écarté (M7 chaîne replay) |
