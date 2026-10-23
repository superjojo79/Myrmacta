---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `extension-mcp-flux-metier`

> Phase 4 close ; Phase 5 (implémentation) close, M1–M9 enchaînés en TDD — voir
> [docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](../implementation/2026-09-06-feature-extension-mcp-flux-metier.md).
> S'appuie sur
> [docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md](../requirements/2026-09-06-feature-extension-mcp-flux-metier.md),
> [docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](../design/2026-09-06-feature-extension-mcp-flux-metier.md)
> et [docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md](../testing/2026-09-06-feature-extension-mcp-flux-metier.md).
> 9 milestones, même granularité que `serveur-mcp` (PR #82, M1 gardes domaine d'abord). Chaque
> tâche en TDD (`tdd`) : test qui échoue → code → `./mvnw verify` avant de passer à la suivante.

## Milestones

- [x] M1 : `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService` (nouvelle méthode, D2)
- [x] M2 : Garde-fou domaine — connecteurs (`ManageConnectorInstanceUseCase`, toutes opérations)
- [x] M3 : Garde-fou domaine — questions de tâche (`TaskQuestionSchema*UseCase`, toutes opérations)
- [x] M4 : Correctif garde-fou domaine — `WorkflowTypeConfigUseCase` (`create`/`update` seulement, PR #82)
- [x] M5 : `define_dossier_type` étendu (`documentTemplateKeys`)
- [x] M6 : 5 nouveaux tools MCP de lecture
- [x] M7 : 2 nouveaux tools MCP d'écriture (`dryRun`, secrets)
- [x] M8 : IT HTTP M2M réelles (7 nouveaux tools + `define_dossier_type` étendu)
- [x] M9 : Vérification globale + documentation

## Task Breakdown

### M1 — Garde-fou domaine partagé (D2)

- [x] T1.1 : `TenantAdminGuardTest` — nouveau test unitaire couvrant
      `requireTenantOrWorkflowAdminOrApiService` : accepte `TENANT_ADMIN`, `WORKFLOW_ADMIN`,
      `API_SERVICE` ; rejette tout le reste (`AccessDeniedException`). TDD : le test échoue
      d'abord (méthode inexistante), puis implémentation dans `TenantAdminGuard.java`.
      *(Testing : « `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService`... test unitaire
      dédié ».)*

### M2 — Garde-fou domaine : connecteurs

- [x] T2.1 : `ManageConnectorInstanceUseCase` (`port.in`) — ajouter `AccessContext.User actor` en
      paramètre de `create`, `update`, `get`, `list`, `softDelete`, `restore` (D5 : toutes les
      opérations, miroir `@PreAuthorize` classe de `ConnectorAdminController`).
- [x] T2.2 : `ManageConnectorInstanceUseCaseImpl` — injecter `TenantAdminGuard`, appeler
      `requireTenantAdminOrApiService(actor)` (méthode **existante**, pas de nouvelle méthode ici)
      en tête de chaque méthode. Tests TDD par méthode et par rôle (`ManageConnectorInstanceUseCaseImplTest`) :
      rejet sans rôle, acceptation `TENANT_ADMIN` (régression), acceptation `API_SERVICE` (nouveau).
      *(Testing : bloc « `ManageConnectorInstanceUseCaseImpl` (garde-fou ajouté) ».)*
- [x] T2.3 : `ConnectorAdminController` — passer l'acteur (déjà disponible via `Authentication`,
      pattern `DossierController`) à chaque appel du use case. Test API existant
      (`ConnectorAdminControllerTest`) rejoué sans régression.
- [x] T2.4 : Non-régression — `update` avec secret vide/masque préserve l'existant
      (`mergeConfig`/`isBlankOrMask` inchangés, test déjà existant à revalider après le changement
      de signature).

### M3 — Garde-fou domaine : questions de tâche

- [x] T3.1 : `SaveTaskQuestionSchemaUseCase`, `DeleteTaskQuestionSchemaUseCase`,
      `GetTaskQuestionSchemaUseCase` (`port.in`) — ajouter `AccessContext.User actor` sur `save`,
      `delete`, `getByKey`, `list` (D5 : toutes les opérations, y compris les lectures, miroir
      exact de `TaskQuestionSchemaController`).
- [x] T3.2 : `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl` — injecter
      `TenantAdminGuard`, appeler `requireTenantOrWorkflowAdminOrApiService(actor)` (nouvelle
      méthode de M1) en tête de chaque méthode. Tests TDD par méthode et par rôle.
      *(Testing : bloc « `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl`
      (garde-fou ajouté sur TOUTES les opérations, D5) ».)*
- [x] T3.3 : `TaskQuestionSchemaController` — passer l'acteur à chaque appel (`create`, `update`,
      `delete`, `list`, `getByKey` — **pas** `getForTask`, hors périmètre, RBAC différent
      D-USERQ-RUNTIME-SCOPE). Test API existant (`TaskQuestionSchemaApiTest`) rejoué sans
      régression.

### M4 — Correctif garde-fou domaine : `WorkflowTypeConfigUseCase` (PR #82)

- [x] T4.1 : `SaveWorkflowTypeConfigUseCase` (`port.in`) — ajouter `AccessContext.User actor` sur
      `create`/`update` **uniquement**. `GetWorkflowTypeConfigUseCase` (`list`/`getByKey`) reste
      **inchangé** (D5 : pas de régression sur `list_workflow_types`).
- [x] T4.2 : `WorkflowTypeConfigUseCaseImpl` — injecter `TenantAdminGuard`, appeler
      `requireTenantOrWorkflowAdminOrApiService(actor)` dans `create`/`update` seulement. Tests
      TDD : rejet sans rôle sur `create`/`update` ; `list`/`getByKey` **toujours accessibles** sans
      rôle (test de non-régression explicite, pas juste absence de garde par omission).
      *(Testing : bloc « `WorkflowTypeConfigUseCaseImpl` (correctif pré-existant...) ».)*
- [x] T4.3 : `WorkflowTypeConfigController` — passer l'acteur sur `create`/`update` seulement (ses
      méthodes `GET` non touchées).
- [x] T4.4 : `DeployWorkflowTool` (MCP, tool `deploy_workflow` existant) — passer l'acteur déjà
      résolu (`accessContextFactory.currentActor()`, ligne 125) aux appels
      `saveWorkflowTypeConfigUseCase.create`/`update`. Test de régression explicite sur
      `deploy_workflow` (`McpToolCallIntegrationTest` existant, à rejouer).

### M5 — `define_dossier_type` étendu

- [x] T5.1 : `DefineDossierTypeTool` — ajouter le paramètre optionnel `documentTemplateKeys`
      (absent/`null` en update préserve l'existant, `[]` par défaut en création). Réutilise
      `CreateDossierTypeUseCase.CreateDossierTypeCommand`/`UpdateDossierTypeUseCase.UpdateDossierTypeCommand`
      qui portent déjà ce champ (aucun changement domaine nécessaire ici, contrairement à M2–M4).
      Tests TDD : absent/`null` préserve (régression), fourni en création est persisté, fourni en
      update remplace, `dryRun=true` ne persiste rien.
      *(Testing : bloc « `DefineDossierTypeTool` (étendu) ».)*

### M6 — Nouveaux tools MCP de lecture

- [x] T6.1 : `ListConnectorDefinitionsTool` → `ConnectorCatalog.listAll()` directement (pas de use
      case, catalogue statique — D4/design). Test : retourne le catalogue, jamais de secret.
- [x] T6.2 : `ListConnectorInstancesTool` → `ManageConnectorInstanceUseCase.list`. Test : masquage
      secret (miroir `ConnectorResponseSecretMaskingTest`).
- [x] T6.3 : `GetConnectorInstanceTool` → `ManageConnectorInstanceUseCase.get`. Test : masquage
      secret ; erreur explicite si id inconnu/tenant différent.
- [x] T6.4 : `ListTaskQuestionSchemasTool` → `GetTaskQuestionSchemaUseCase.list` (existe déjà,
      D4/Phase 2). Test : pagination correcte.
- [x] T6.5 : `GetTaskQuestionSchemaTool` → `GetTaskQuestionSchemaUseCase.getByKey`. Test : erreur
      explicite si `schemaKey` inconnu.

### M7 — Nouveaux tools MCP d'écriture

- [x] T7.1 : `DefineConnectorInstanceTool` → `ManageConnectorInstanceUseCase.create`/`update`,
      `dryRun` (pattern `DefineDossierTypeTool`). Tests : secret en clair persisté chiffré ; secret
      vide/masque préserve l'existant ; `dryRun=true` ne persiste rien ; type de connecteur inconnu
      → erreur explicite.
- [x] T7.2 : `DefineTaskQuestionSchemaTool` → `SaveTaskQuestionSchemaUseCase.save`, `dryRun`. Tests :
      création/MAJ avec `FieldDefinition` valides ; `dryRun=true` ne persiste rien ; collision avec
      les 4 variables plateforme réservées rejetée (réutilise
      `WorkflowVariables.isReservedOrKnownKey`, TODO §55).

### M8 — IT HTTP M2M réelles

- [x] T8.1 : Étendre `McpToolCallIntegrationTest` (ou dédier une nouvelle classe, à trancher en
      Phase 5 selon la taille) : appel HTTP réel `/api/m2m/mcp` pour chacun des 7 nouveaux tools +
      `define_dossier_type` étendu, à travers la vraie chaîne Spring Security (pas de mock JWT) —
      même exigence que M7 de `serveur-mcp`, qui avait révélé le risque d'un endpoint
      inatteignable (cf. le trou trouvé post-merge sur `connecteur-callback`, TODO §53/§54, à ne
      pas reproduire ici).
- [x] T8.2 : Round-trip complet (scénario métier, justifie le nom de la feature) :
      `define_connector_instance` → `define_dossier_type` (avec `documentTemplateKeys`) →
      `define_task_question_schema` → `deploy_workflow` référençant les trois — prouve qu'un flux
      métier complet est atteignable sans sortir du canal MCP.

### M9 — Vérification globale + documentation

- [x] T9.1 : `./mvnw verify -Dmaven.compiler.release=21` — Jacoco LINE ≥ 85 % / BRANCH ≥ 75 %,
      vérifié classe par classe sur les fichiers modifiés/ajoutés (convention TODO §28), 0 échec.
- [x] T9.2 : Régression explicite des 9 tools MCP existants + 3 contrôleurs REST touchés
      (`ConnectorAdminControllerTest`, `TaskQuestionSchemaApiTest`, `WorkflowTypeConfigController`
      tests) + `frontend` (`npm run test`) — feature backend-only pressentie, à confirmer par
      `git diff --stat` (zéro fichier `frontend/**`).
- [x] T9.3 : Mise à jour documentation : [15-serveur-mcp.md](../../specs/15-serveur-mcp.md)
      (catalogue des tools, 9 → 16), [06-connecteurs.md](../../specs/06-connecteurs.md)
      si pertinent, `README.md`/`GETTING-STARTED.md` si une variable d'environnement change (a
      priori non — aucune prévue). `CHANGELOG.md` : entrée `[Non publié]` à la PR (convention
      `flowforge-conventions`).
- [x] T9.4 : Clôture TODO §56 (statut `[x]`), resynchronisation `FILE-ATTENTE.md`.

## Dependencies

- **M1 avant M2/M3/M4** : ces trois milestones appellent la nouvelle méthode de `TenantAdminGuard`
  (M2 réutilise en fait une méthode déjà existante, mais reste après M1 dans l'ordre pour garder
  un unique point d'entrée « gardes domaine d'abord », cohérent avec `serveur-mcp`).
- **M2/M3/M4 indépendants entre eux** — 3 bounded contexts distincts (`connector`, `workflow` ×2),
  parallélisables si besoin (pattern déjà utilisé sur `edition-bpmn`, agents en worktrees isolés) ;
  pas nécessaire ici vu la taille, séquentiel par défaut.
- **M5 indépendant de M1–M4** — n'touche pas le garde-fou (`define_dossier_type` a déjà
  `TenantAdminGuard` depuis `serveur-mcp`), peut être fait en parallèle.
- **M6/M7 après M2/M3** — les nouveaux tools appellent les use cases dont la signature vient de
  changer (paramètre acteur).
- **M8 après M5/M6/M7** — les IT exercent les tools finis.
- **M9 après tout le reste**.

## Timeline & Estimates

Pas d'estimation en jours — convention du dépôt (cycles précédents mesurés en milestones/tâches,
pas en temps). 9 milestones / ~26 tâches, comparable à `serveur-mcp` (9 milestones / 32 tâches) et
`connecteurs-hors-v1` (3 connecteurs en un cycle).

## Risks & Mitigation

- **Risque de régression sur `deploy_workflow` (M4.4)** — tool déjà en production. Mitigation :
  test de régression explicite avant toute modification (TDD), rejeu complet de
  `McpToolCallIntegrationTest` existant après le changement.
- **Risque de régression sur `list_workflow_types` (M4)** — le garde-fou ne doit *pas* s'étendre à
  `list`/`getByKey`. Mitigation : test de non-régression explicite (T4.2) prouvant l'absence de
  garde sur ces deux méthodes, pas seulement l'absence d'un test qui l'exigerait.
- **Risque sur les secrets connecteurs (M7.1)** — un bug de masquage exposerait un secret en clair
  sur `list_connector_instances`/`get_connector_instance`. Mitigation : test miroir de
  `ConnectorResponseSecretMaskingTest` avant de considérer M6.2/M6.3/M7.1 terminés.
- **Risque d'incohérence de couverture par classe (comme trouvé en Phase 8 de plusieurs cycles
  précédents, ex. TODO §55)** — mitigation : balayage Jacoco classe par classe en M9.1, pas
  seulement à l'agrégat.

## Resources Needed

- Aucune nouvelle dépendance (pas de nouveau connecteur, pas de nouvelle lib) — réutilisation
  stricte de `spring-ai-mcp-annotations`, `ConnectorSecretCipher`, `TenantAdminGuard` étendu.
- Commandes : `./mvnw verify -Dmaven.compiler.release=21` (backend), `cd frontend && npm run test`
  (régression FE, feature backend-only pressentie).
