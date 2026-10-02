---
phase: requirements
title: Requirements — D6 (socle MCP low-code : brouillon, publication, provenance, A3-M2)
status: reviewed
created: 2026-10-01
feature: d6-socle-mcp-low-code
lot: D6
todo_ref: TODO.md §82 · analyses 37 et 46
---

# Requirements — D6

## Problème
Un agent branché sur le MCP déploie **directement** sur l'instance (`deploy_workflow`, `deploy_decision_table`, `redeploy_decision_table`) : aucune relecture humaine avant qu'un processus ou une table de décision ne change le comportement en production. Les brouillons des éditeurs (`bpmn_draft`, `dmn_draft`) existent mais le MCP ne les utilise pas ; rien ne dit **qui** (humain, agent, assistant) a écrit une version ; et tout JWT M2M reçoit le rôle administrateur `API_SERVICE` sans allowlist obligatoire (risque A3-M2).

## Objectifs
- G1. Les écritures de configuration MCP sur les **processus BPMN et tables DMN** produisent un **brouillon**, pas un déploiement (D-MCP-LC-MIGRATION = A).
- G2. Un **écran « Brouillons à publier »** liste les brouillons (type, clé, provenance, auteur, date, écart avec la version publiée) ; un humain avec le rôle qui publie déjà peut publier ou écarter ; **un seul** cas d'usage de publication sert l'écran et l'outil MCP.
- G3. **Provenance** `UI` / `MCP` / `ASSISTANT` sur les brouillons et journal des publications (qui a rédigé, qui a publié, quand, quelle version, mode).
- G4. **Publication directe** par MCP seulement si l'instance le permet explicitement **et** n'est pas en production (D-MCP-LC-PUBLISH = C, défaut = brouillon).
- G5. **Acteur paramétrable** : un outil s'exécute avec le JWT M2M (canal actuel, inchangé) ou avec l'`AccessContext` d'un utilisateur de session (assistant LC3, in-process).
- G6. **Modes** *conception* / *opération* déclarés pour chaque outil MCP (consommés par LC3), avec un test qui interdit un outil non classé.
- G7. **A3-M2** : hors environnement local, `trusted-client-ids` est obligatoire dès que la chaîne M2M est active (D-MCP-LC-SCOPES = B) ; échec au démarrage sinon.

## Hors périmètre
Brouillons pour types de dossier, schémas de questions, instances de connecteur et templates (écrits directement, avec `dryRun`) — D-SOCLE-DRAFT-SCOPE ; l'assistant LC3 lui-même ; la dérivation de rôles depuis les scopes JWT (B suffit).

## Critères d'acceptation
- Par défaut, `deploy_workflow` valide (parseur, allowlist, références) puis **écrit un brouillon** (provenance `MCP`, configuration de workflow proposée jointe) et répond « en attente de publication humaine » ; aucun déploiement, aucune écriture de `WorkflowTypeConfig`. Idem `deploy_decision_table` ; `redeploy_decision_table` charge la version demandée dans un brouillon DMN.
- Un brouillon d'un autre auteur ou d'une autre provenance n'est **jamais écrasé en silence** par un outil MCP (refus explicite, `replaceExistingDraft=true` pour forcer).
- Publier depuis l'écran déploie exactement le brouillon relu (garde d'horodatage : un brouillon modifié entre-temps est refusé en 409), applique la configuration proposée, journalise la publication, supprime le brouillon.
- `app.mcp.publication.mode=direct` est refusé au démarrage si `app.environment=production` ; sans effet de bord en `draft`.
- M2M actif + `app.environment` ≠ `local` + `trusted-client-ids` vide ⇒ l'application ne démarre pas, avec un message qui dit quoi renseigner.
- Brouillons `MCP`/`ASSISTANT` non touchés depuis `app.drafts.retention-days` (défaut 30) purgés par un job ; les brouillons `UI` jamais.
- Tout outil MCP enregistré a un mode (test).
