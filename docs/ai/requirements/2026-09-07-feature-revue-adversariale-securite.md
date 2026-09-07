---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
phase1: 2026-09-07
todo_ref: TODO.md §57 · FILE-ATTENTE.md
---

# Requirements & Problem Understanding — `revue-adversariale-securite`

## Reprise Phase 1–2 (2026-09-07, soir)

Les Phases 1–9 avaient été closes sans questions stakeholder. **Rouvert** : le
code de la PR #113 est **conservé** en proposition, pas acté. Les D-* / A-*
ci-dessous sont des **recommandations**, pas des décisions. Les critères de
succès redeviennent des cases ouvertes jusqu’à confirmation.

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

Implémenter **H1, H2, M1–M13** selon les réponses Q1–Q12 (reco actuelle = tableau TODO §57).

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

> Cases rouvertes. Le code PR #113 implémente déjà la colonne « reco » ;
> à recocher seulement après confirmation stakeholder.

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
- [ ] `./mvnw verify` / Vitest / Playwright verts (preuve locale déjà produite, à
      revalider si le code change après les réponses).

## Constraints & Assumptions

> Propositions, pas actées (Phase 2 ouverte).

- Hexagone : validation BPMN dans `BpmnParserPort` (infra adapter), pas de Flowable dans `domain`.
- HMAC M7 = **rupture de contrat** si Q5=A (comme F6, TODO §54) — GUIDE / changelog.
- A-SPA (Q9=A) : zéro fichier frontend visé.
- A-EXPRESSION-EDITOR : le champ `flowable:expression` du panel bpmn-js reste visible
  si Q9=A.

## Questions & Open Items

**Toutes ouvertes.** Reco = ce que le code PR #113 fait aujourd’hui. Répondre
par ID (`Q1 = A`, ou `OK tous`).

| ID | Question | Options | Reco | Pourquoi |
|---|---|---|---|---|
| **Q1** | Périmètre de ce cycle ? | **A** High+Medium H1/H2/M1–M13 en un lot. **B** High seulement (H1/H2), Medium plus tard. **C** High + Medium non-rupture d’abord, HMAC (M7) ensuite. | **A** | La revue est un seul requirement ; B/C laissent H2/M1/M7 exploitables. |
| **Q2** | Comment bloquer la RCE BPMN (H1) ? | **A** Allowlist parse-time (`scriptTask`/`class`/`expression` refusés ; `delegateExpression` catalogue). **B** Désactiver seulement le script engine Flowable (rate les `expression` serviceTask). **C** WA ne peut plus déployer ; seul TA + revue manuelle. | **A** | B ne coupe pas `flowable:expression`. C casse le métier WA. L’éditeur peut encore *afficher* expression : le backend refuse (Q9). |
| **Q3** | Qui peut `complete` une tâche assignée (H2) ? | **A** Assignee **ou** TA/WA ; sans assignee, EDIT du type suffit. **B** Assignee uniquement (admin doit d’abord réassigner). **C** Tout EDIT du type (statu quo, on ne fixe pas H2). | **A** | B alourdit le support (admin ne peut plus débloquer). C est le finding. Unassigned+EDIT = V1 pragmatique (pas de candidate groups). |
| **Q4** | Humain Keycloak avec rôle realm `API_SERVICE` (M1) ? | **A** OIDC n’émet plus `ROLE_API_SERVICE` ; MCP continue d’injecter le rôle. **B** Retirer `API_SERVICE` de `DossierAccessPolicy` (humain *et* MCP). **C** Documenter seulement (accepter l’élévation). | **A** | B casse `get_dossier` MCP (D6). C laisse H2/ACL contournables. |
| **Q5** | HMAC : inclure `Idempotency-Key` (M7) ? | **A** Rupture in-place `timestamp.idempotencyKey.body` (comme F6). **B** Dual-accept ancien+nouveau pendant une fenêtre. **C** Nouveau header de version, ancien contrat conservé. | **A** | Replay 5 min avec clés neuves est réel. B double la surface. C = dette. **Ops :** prévenir les partenaires avant merge. |
| **Q6** | Shared-secret sans timestamp (M7) ? | **A** Cache anti-rejeu `(instanceId, sha256(body))` 5 min. **B** Exiger un timestamp comme HMAC. **C** Ne rien changer (token volé = hors scope). | **A** | B = 2ᵉ rupture pour les partenaires token. C laisse le replay illimité sur le même corps. Token + *nouveau* corps = vol de credential, hors scope. |
| **Q7** | Clés connecteur hors schéma (M5) ? | **A** Rejeter + déclarer `mapping` au schéma. **B** Strip silencieux des inconnues. **C** Heuristique `*token`/`*secret` seulement. | **A** | B masque les typos. C rate `backupToken` mal nommé. `mapping` doit être déclaré sinon A casse les instances existantes. |
| **Q8** | `POST /api/documents/generate` pour un DM (M12) ? | **A** TA/WA libres ; DM seulement si un type VIEW liste la clé. **B** Retirer DM de `@PreAuthorize` (TA/WA seuls). **C** Statu quo (rôle DM suffit). | **A** | B retire le generate métier. C est le finding. Garde dans le **contrôleur** (pas le use case) pour ne pas casser `documentGenerationTask` BPMN. |
| **Q9** | Champ `flowable:expression` dans bpmn-js (H1 UX) ? | **A** Backend refuse au save/deploy ; pas de chantier FE. **B** Masquer le champ dans le panel. **C** A + B. | **A** | A-SPA : zéro fichier frontend. B/C = autre feature. 400 à la sauvegarde suffit. |
| **Q10** | Ordre rate-limit webhook (M6) ? | **A** Auth puis `tryConsume` (échec auth ne débite pas le partenaire). **B** Bucket anonyme séparé + bucket partenaire. **C** Statu quo (rate-limit avant auth). | **A** | C est le finding (DoS du partenaire). B plus juste mais plus de code V1. |
| **Q11** | `th:utext` / `[(${…})]` (M13) ? | **A** Interdire au checker + CSP `default-src 'none'; sandbox` sur preview. **B** CSP seulement, garder `utext`. **C** Interdire `utext`, pas de header CSP. | **A** | `utext` = HTML non échappé (SSTI/HTML inject). CSP iframe SPA `sandbox=""` est insuffisant seul. |
| **Q12** | Low L1–L7 dans ce cycle ? | **A** Hors correctif (L1 accepté D6 ; L2–L5 reliquats S-6…S-9 ; L6/L7 déjà gated ailleurs). **B** Traiter L1 (scopes M2M). **C** Fail-fast actuator hors loopback (L2). | **A** | L1/L2 sont des choix d’archi déjà documentés ; les rouvrir mélange ce durcissement et un chantier IdP/k8s. |

### Alternatives de lot (Q1, brainstorm)

| Approche | Trade-off | Reco |
|---|---|---|
| A — un lot H+M | Un merge, rupture HMAC dans le même train | **Retenu en reco** |
| B — High only | Plus sûr à relire, Medium restent ouverts | Non : M1 court-circuite H2 |
| C — HMAC plus tard | Moins de rupture maintenant | Non : M7 est la chaîne starts/dossiers en boucle |

Code **inchangé** tant que les réponses ne demandent pas d’adapter.
