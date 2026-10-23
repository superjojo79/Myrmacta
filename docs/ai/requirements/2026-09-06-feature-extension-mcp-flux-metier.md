---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `extension-mcp-flux-metier`

> Origine : [docs/specs/24-extension-mcp-flux-metier.md](../../specs/24-extension-mcp-flux-metier.md)
> (analyse initiale, demande utilisateur 2026-09-06). Cadrage initial du serveur MCP :
> [docs/specs/15-serveur-mcp.md](../../specs/15-serveur-mcp.md) (PR #82, 9 tools livrés
> le 2026-08-27). Décisions ci-dessous actées via `AskUserQuestion`, une à la fois.

## Problem Statement

Le serveur MCP interne (`POST /api/m2m/mcp`, PR #82) expose aujourd'hui 9 tools couvrant *type de
dossier* + *BPMN/`WorkflowTypeConfig`* + *lecture* (dossiers, templates, SLA). Depuis sa livraison
(2026-08-27), cinq features ont ajouté des mécanismes de **configuration** qu'un flux métier complet
requiert désormais, sans qu'aucun tool MCP ne les couvre :

1. **Connecteurs** (`ConnectorInstance`) — PR #95/#99/#102 : un `serviceTask` BPMN référence un
   connecteur par UUID d'instance, provisionné aujourd'hui uniquement par l'écran admin
   (`ConnectorAdminController`, `TENANT_ADMIN`-only).
2. **Association templates ↔ type de dossier** (`DossierType.documentTemplateKeys`) — PR #107 :
   nécessaire dès qu'un `userTask` porte `formKey = document-generation`. Vérifié dans le code :
   `DefineDossierTypeTool.defineCreate` force ce champ à `null`, `defineUpdate` le préserve tel
   quel (ligne 183) — c'est le seul champ de `DossierType` inaccessible en écriture via MCP.
3. **Questions de tâche** (`TaskQuestionSchema`, TODO §55) — postérieur au cadrage MCP, aucun tool
   ne couvre `/api/task-question-schemas`.

Sans ces trois capacités, un agent MCP peut déployer un BPMN qui référence un connecteur, un
template ou un schéma de question, mais ne peut pas provisionner ce à quoi le process s'accroche —
obligeant un aller-retour manuel sur l'UI admin. Le flux métier ne peut donc pas être défini de
bout en bout par le canal MCP seul.

**Trouvaille supplémentaire, faite en explorant le code pendant cette phase (pas anticipée par
l'analyse initiale) :** `ManageConnectorInstanceUseCaseImpl` et `TaskQuestionSchemaUseCaseImpl`
n'ont **aucune garde de rôle au niveau domaine** — le RBAC (`TENANT_ADMIN` pour les connecteurs,
`TENANT_ADMIN`/`WORKFLOW_ADMIN` pour les questions) n'est vérifié qu'au contrôleur REST
(`@PreAuthorize`), jamais dans le use case. `define_dossier_type` évite ce problème car
`CreateDossierTypeUseCaseImpl`/`UpdateDossierTypeUseCaseImpl` embarquent déjà `TenantAdminGuard`
(domaine, accepte `TENANT_ADMIN` ou `API_SERVICE`). Mais **`WorkflowTypeConfigUseCaseImpl`, déjà
utilisé par `deploy_workflow` (livré en PR #82), n'a pas non plus ce garde-fou** — écart
pré-existant sur du code déjà en production, pas seulement un risque introduit par cette feature.

## Goals & Objectives

**Objectifs principaux**

- Ajouter 7 nouveaux tools MCP pour couvrir les 3 lacunes de configuration ci-dessus :
  `list_connector_definitions`, `list_connector_instances`, `get_connector_instance`,
  `define_connector_instance`, `list_task_question_schemas`, `get_task_question_schema`,
  `define_task_question_schema`.
- Étendre `define_dossier_type` pour accepter `documentTemplateKeys` en entrée (paramètre optionnel,
  même principe que `fieldDefinitions` — le schéma complet à chaque appel).
- Ajouter un garde-fou de rôle au niveau domaine (accepte le(s) rôle(s) admin REST habituel(s) **ou**
  `API_SERVICE`) sur `ManageConnectorInstanceUseCaseImpl`, `TaskQuestionSchemaUseCaseImpl`, **et**
  corriger le même écart pré-existant sur `WorkflowTypeConfigUseCaseImpl` — un seul point
  d'application partagé entre REST et MCP, cohérent avec le pattern `TenantAdminGuard` déjà en place
  pour `DossierType`.

**Objectifs secondaires**

- `dryRun=true` sur les 2 nouveaux tools d'écriture (`define_connector_instance`,
  `define_task_question_schema`), par cohérence de contrat avec `define_dossier_type`/
  `deploy_workflow`.
- Masquage des secrets connecteurs (`PASSWORD`/`*secret`/`*password`) en lecture MCP, même
  politique que l'admin REST (`ConnectorResponseSecretMaskingTest`) — jamais renvoyés en clair par
  `list_connector_instances`/`get_connector_instance`.

**Non-goals**

- `create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document` : hors périmètre,
  déjà en backlog explicite de [15-serveur-mcp.md](../../specs/15-serveur-mcp.md) pour
  une raison différente (écriture sur des données métier, pas de la configuration).
- Gestion des ACL (`grant/revoke_dossier_type_access`) : exclusion déjà actée
  ([15-serveur-mcp.md](../../specs/15-serveur-mcp.md) « Exclusions explicites »),
  inchangée.
- Aucun nouveau mécanisme de secret hors-bande (vault externe, référence indirecte) — le secret
  connecteur transite en clair sur l'appel MCP, comme sur l'écran admin (voir décisions).
- Pas de tool de création/édition de `DocumentTemplate` lui-même (contenu HTML) — seule
  l'association `documentTemplateKeys` (référence par clé) est dans le périmètre.

## Décisions actées (via `AskUserQuestion`)

| # | Décision | Choix retenu |
|---|---|---|
| D-MCP-EXT-SCOPE | Un seul cycle `dev-lifecycle` couvrant les 3 lacunes, ou 3 cycles séparés ? | **Un seul cycle** — 7 tools + extension `define_dossier_type` + correctif RBAC domaine, une seule PR |
| D-MCP-EXT-SECRET | Comment un secret connecteur (`PASSWORD`) transite par `define_connector_instance` ? | **Même flux que l'admin REST** — fourni en clair dans l'appel MCP (canal M2M déjà authentifié `API_SERVICE`), chiffré AES-GCM à l'écriture, jamais renvoyé en clair en lecture |
| D-MCP-EXT-DOCTPL | `documentTemplateKeys` : étendre `define_dossier_type` ou nouveau tool dédié ? | **Étendre `define_dossier_type`** — paramètre optionnel, même principe que `fieldDefinitions` |
| D-MCP-EXT-DRYRUN | `dryRun` sur les 2 nouveaux tools d'écriture ? | **Oui, partout** — cohérence de contrat avec `define_dossier_type`/`deploy_workflow` |
| D-MCP-EXT-NAMING | Noms définitifs des 7 nouveaux tools ? | Confirmés : `list_connector_definitions`, `list_connector_instances`, `get_connector_instance`, `define_connector_instance`, `list_task_question_schemas`, `get_task_question_schema`, `define_task_question_schema` |
| D-MCP-EXT-RBAC-GUARD | `ManageConnectorInstanceUseCaseImpl`/`TaskQuestionSchemaUseCaseImpl` n'ont aucune garde domaine ; `WorkflowTypeConfigUseCaseImpl` (déjà en PR #82) non plus. Comment traiter ? | **Ajouter le garde-fou domaine partout**, y compris correctif de `WorkflowTypeConfigUseCaseImpl` (écart pré-existant, à documenter comme correctif sur du code déjà livré, pas juste comme nouvelle feature) |
| D-MCP-EXT-RBAC-SCOPE | Phase 2 : le RBAC REST exact diffère par contrôleur — `ConnectorAdminController` gate **toutes** ses routes (`@PreAuthorize` classe) ; `TaskQuestionSchemaController` gate explicitement **list et get**, pas seulement les écritures ; `WorkflowTypeConfigController` ne gate **que** create/update, ses `GET` sont ouverts à tout utilisateur authentifié (et `list_workflow_types`, déjà livré en PR #82, s'appuie sur cette lecture ouverte). Le garde-fou domaine doit-il reproduire cette asymétrie ou l'uniformiser ? | **Reproduire l'asymétrie telle quelle** — `ManageConnectorInstanceUseCaseImpl` : garde sur toutes les opérations (create/update/get/list/softDelete/restore) ; `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl` : garde sur toutes les opérations ; `WorkflowTypeConfigUseCaseImpl` : garde **uniquement** sur `create`/`update` — `list`/`getByKey` restent ouverts, zéro régression sur `list_workflow_types` déjà en production |

## User Stories & Use Cases

- En tant qu'agent MCP (Claude Code interne), je veux lister les types de connecteurs disponibles
  (`list_connector_definitions`) puis créer une instance (`define_connector_instance`) afin de
  provisionner ce qu'un `serviceTask` du BPMN que je viens de déployer référence par
  `connectorInstanceId`.
- En tant qu'agent MCP, je veux associer des templates de documents à un type de dossier
  (`define_dossier_type` étendu) afin qu'un `userTask` `document-generation` du process propose un
  choix non vide.
- En tant qu'agent MCP, je veux créer un schéma de questions de tâche
  (`define_task_question_schema`) afin qu'un `userTask` `user-question:<schemaKey>` du process ait
  un formulaire à afficher.
- En tant qu'agent MCP, je veux prévisualiser (`dryRun=true`) la création d'un connecteur ou d'un
  schéma de question avant de l'appliquer, comme je peux déjà le faire pour un type de dossier ou
  un déploiement BPMN.
- En tant qu'opérateur qui audite les appels MCP, je veux que la trace d'audit (`createdBy`/
  `updatedBy`) et le contrôle de rôle des nouveaux tools se comportent comme ceux déjà en place
  pour `define_dossier_type`/`deploy_workflow` — pas un mécanisme divergent.

**Cas limites à couvrir**

- Secret fourni en `dryRun=true` : ne doit être ni chiffré ni persisté (même contrat que
  `define_dossier_type` : « no save, no author stamp »).
- Mise à jour d'un connecteur existant sans repasser le secret (valeur vide/masque) : le secret
  actuel doit être préservé, pas écrasé par une valeur vide — comportement déjà présent dans
  `ManageConnectorInstanceUseCaseImpl.mergeConfig` (`isBlankOrMask`), à conserver tel quel côté MCP.
- Appel `define_connector_instance`/`define_task_question_schema` par un JWT M2M dont le rôle
  ne serait pas `API_SERVICE` (ne devrait jamais arriver via `/api/m2m/mcp`, mais le garde-fou
  domaine doit rejeter explicitement plutôt que de faire confiance au transport seul).
- `documentTemplateKeys` référençant une clé de template inexistante ou inactive — comportement à
  aligner sur celui déjà défini pour l'écran admin (PR #107 : liste vide = aucun template
  disponible, pas d'erreur bloquante a priori) ; à confirmer en design.

## Success Criteria

- 7 nouveaux tools exposés sur `/api/m2m/mcp`, découvrables (schema JSON généré) et testés par IT
  HTTP M2M réelles, même famille que `McpToolCallIntegrationTest` (M7 de `serveur-mcp`).
- `define_dossier_type` accepte `documentTemplateKeys` en entrée, round-trip vérifié.
- `ManageConnectorInstanceUseCaseImpl` (toutes opérations) et `TaskQuestionSchemaUseCaseImpl`/
  `GetTaskQuestionSchemaUseCaseImpl` (toutes opérations) rejettent un acteur sans rôle admin/
  `API_SERVICE` ; `WorkflowTypeConfigUseCaseImpl` le fait uniquement sur `create`/`update` (`list`/
  `getByKey` restent ouverts, cf. D-MCP-EXT-RBAC-SCOPE) — test de régression prouvant que le rejet
  a lieu dans le use case lui-même, pas seulement au contrôleur REST.
- Secrets connecteurs jamais renvoyés en clair par `list_connector_instances`/
  `get_connector_instance` (test miroir de `ConnectorResponseSecretMaskingTest`).
- `./mvnw verify` vert (Jacoco LINE/BRANCH aux seuils actuels), zéro régression sur les 9 tools
  existants et sur les endpoints REST déjà couverts (`ConnectorAdminController`,
  `TaskQuestionSchemaController`, `WorkflowBpmnDeploymentController`).
- Feature backend-only (comme `serveur-mcp`) — zéro fichier `frontend/**` attendu, à confirmer en
  Phase 2/design.

## Constraints & Assumptions

- Architecture hexagonale : les nouveaux tools sont des adaptateurs fins dans
  `infrastructure.mcp.tool`, appellent des use cases domaine existants
  (`ManageConnectorInstanceUseCase`, `TaskQuestionSchemaUseCase`), aucune logique métier propre au
  tool — même pattern que les 9 tools existants.
- Un serveur MCP par instance tenant (décision n° 13, inchangée) — aucune résolution dynamique de
  tenant à introduire.
- Le correctif RBAC sur `WorkflowTypeConfigUseCaseImpl` touche du code déjà en production
  (PR #82) : à traiter comme un correctif de sécurité documenté explicitement (pas glissé
  silencieusement dans le diff de la feature), cohérent avec la façon dont les corrections
  post-merge de `connecteur-callback` (TODO §53/§54) ont été tracées.
- Le placement exact du garde-fou domaine (réutilisation de `domain.dossier.TenantAdminGuard`
  tel quel, nouvelle classe par bounded context, ou déplacement dans un package partagé) n'est
  **pas** tranché ici — décision de conception, renvoyée à la Phase 3 (design) pour respecter les
  frontières de contexte (`ConnectorInstance`/`TaskQuestionSchema` ne sont pas dans
  `domain.dossier`).

## Questions & Open Items

Aucune question matérielle restant ouverte à l'issue de cette phase — les 7 décisions ci-dessus
(6 de Phase 1 + D-MCP-EXT-RBAC-SCOPE de Phase 2) couvrent le périmètre, le flux du secret, le
nommage et le garde-fou RBAC (y compris son asymétrie exacte par use case). Deux points que le
brouillon design (Phase 1) avait laissés incertains ont été vérifiés contre le code réel en Phase 2
et ne sont **plus** des trous :

- **`list_task_question_schemas`** : `GetTaskQuestionSchemaUseCase.list(PageQuery)` existe déjà
  (`domain/workflow/port/in/GetTaskQuestionSchemaUseCase.java`) — pas de nouveau port à créer.
- **`list_connector_definitions`** : `ConnectorDefinitionController` (REST) n'appelle aucun use
  case dédié, il lit directement `ConnectorCatalog.listAll()` (port `out`, catalogue statique en
  mémoire, aucune donnée tenant, aucun secret) — le tool MCP fera de même, cohérent avec le
  pattern déjà utilisé par certains tools de lecture existants (ex. `ListWorkflowTypesTool` appelle
  directement `GetWorkflowTypeConfigUseCase`, pas de couche supplémentaire).

Reste à trancher en Phase 3 (design), de nature technique et non produit :

- Emplacement/forme exacte du garde-fou domaine partagé (voir « Constraints ») — 3 options déjà
  esquissées dans le brouillon design (D2), aucune ne change le périmètre fonctionnel.
- Comportement précis sur une clé de template inconnue dans `documentTemplateKeys` (validation à
  l'écriture vs. tolérance jusqu'à l'usage runtime).
