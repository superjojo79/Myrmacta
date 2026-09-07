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

## Phase 2: Review requirements

Revue 2026-09-07. L'utilisateur a demandé de traiter la revue adversariale **comme
requirement** et de lancer le cycle SDD. Les D-* ci-dessous sont les fixes
recommandés par la revue, actés comme décisions de ce cycle (agent background :
pas d'`AskUserQuestion` ; hypothèses nommées A-* si un choix existait).
Aucune question ouverte.

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

Implémenter **H1, H2, M1–M13** selon le tableau TODO §57 (colonne « Fix retenu »).

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

- [ ] H1 : BPMN `scriptTask` / `flowable:class` / `flowable:expression` → 400 au
      parse/deploy ; `delegateExpression="${connectorServiceTask}"` OK.
- [ ] H2 : complete non-assignee non-admin → 403 ; assignee → 204.
- [ ] M1 : OIDC n'émet pas `ROLE_API_SERVICE`.
- [ ] M2 : `app.storage.s3.access-key-id` bind `APP_STORAGE_S3_ACCESS_KEY`.
- [ ] M3 : `HttpRestConnector` pose `Host` d'origine et refuse un `Host` appelant.
- [ ] M4 : URL `http://100.64.0.1/` et `http://0.0.0.1/` rejetées.
- [ ] M5 : config `{ "backupToken": "x" }` → validation error ; `mapping` accepté.
- [ ] M6 : auth échouée n'incrémente pas le bucket partenaire.
- [ ] M7 : HMAC sans idempotency dans la signature → 401 ; même corps shared-secret
      rejoué dans la fenêtre → 401.
- [ ] M8 : `/runtime` d'une tâche d'autrui → 403.
- [ ] M9 : callback mapping `tenantId` → 400 ; `ConnectorServiceTask` ignore la var process.
- [ ] M10 : `to=a@x.com,b@y.com` → 400.
- [ ] M11 : réponse HTTP / retrieve au-delà du cap → erreur ; STORE oversized encodé → 400 avant OOM.
- [ ] M12 : `DOSSIER_MANAGER` sans type VIEW lié au template → 403.
- [ ] M13 : template `th:utext` → 400 à l'activation ; preview HTML porte CSP.
- [ ] `./mvnw verify` vert ; Vitest vert ; Playwright régression si SPA non touchée
      (feature backend) **ou** e2e complete-task toujours vert.

## Constraints & Assumptions

- Hexagone : validation BPMN dans `BpmnParserPort` (infra adapter), pas de Flowable dans `domain`.
- HMAC M7 = **rupture de contrat** (comme F6, TODO §54) — documentée GUIDE / changelog.
- A-SPA : zéro fichier frontend visé (complete reste le même bouton « mes tâches »).
- A-EXPRESSION-EDITOR : le champ `flowable:expression` du panel bpmn-js reste visible ;
  le backend refuse à la sauvegarde/deploy (400). Pas de chantier FE dans ce cycle.

## Questions & Open Items

Aucune. Décisions D-H1…D-M13 = tableau TODO §57.
