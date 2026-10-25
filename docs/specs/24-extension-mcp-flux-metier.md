# Extension du serveur MCP — couvrir les capacités livrées depuis `serveur-mcp`

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Questions ouvertes » en
> fin de document. Origine : demande utilisateur (2026-09-06) — inspecter les fonctionnalités
> ajoutées à Myrmacta depuis la création du serveur MCP ([15-serveur-mcp.md](15-serveur-mcp.md),
> PR #82, 2026-08-27) et déterminer ce qu'il manque pour qu'un agent puisse définir un **flux
> métier complet** (type de dossier + workflow + BPMN + tout ce qui l'entoure) sans sortir du canal
> MCP. Constat vérifié contre le code réel, pas seulement contre la documentation.

---

## 1. Rappel du périmètre livré (PR #82, 2026-08-27)

9 tools, inchangés depuis leur livraison (`src/main/java/be/myrmacta/infrastructure/mcp/tool/`) :

| Tool | Use case domaine | Portée |
|---|---|---|
| `define_dossier_type` | `CreateDossierTypeUseCase` / `UpdateDossierTypeUseCase` | écriture |
| `get_dossier_type` / `list_dossier_types` | `GetDossierTypeUseCase` / `DossierTypeRepository` | lecture |
| `deploy_workflow` | déploiement BPMN + upsert `WorkflowTypeConfig` | écriture |
| `list_workflow_types` | `WorkflowTypeConfigRepository` | lecture |
| `get_dossier` / `search_dossiers` | `GetDossierUseCase` / recherche paginée | lecture |
| `list_document_templates` | `ListDocumentTemplateUseCase` | lecture (métadonnées seules) |
| `query_sla_summary` | `GetSlaSummaryUseCase` | lecture |

Cadrage d'origine ([15-serveur-mcp.md](15-serveur-mcp.md)) : le périmètre V1 couvrait
volontairement *type de dossier* + *BPMN/`WorkflowTypeConfig`* + *lecture*. Les tools d'écriture
touchant des données métier (`create_dossier`, `add_dossier_element`, `start_workflow`,
`generate_document`) restent explicitement en backlog hors V1 — ce constat ne remet **pas** en
cause cette exclusion, il porte sur un angle différent : des mécanismes de **configuration**
apparus après le cadrage MCP, du même ordre que ceux déjà en périmètre (type de dossier, BPMN),
mais absents des 9 tools.

---

## 2. Constat — trois mécanismes de configuration ajoutés depuis, invisibles au MCP

### 2.1 Connecteurs (`ConnectorInstance`) — lacune la plus bloquante

Livré en trois vagues, toutes postérieures au 27/08 :

| PR | Date | Apport |
|---|---|---|
| [#95](https://github.com/superjojo79/FlowForge/pull/95) | 2026-08-31 | `http-rest` utilisable en rôle **SERVICE** (`serviceTask` BPMN) |
| [#99](https://github.com/superjojo79/FlowForge/pull/99) | 2026-09-01 | `scheduler` (TRIGGER, cron), `smtp` (SERVICE), `document-storage` (SERVICE) |
| [#102](https://github.com/superjojo79/FlowForge/pull/102) | 2026-09-02 | `flowable-callback` (capability CALLBACK, corrélation asynchrone) |

Un `serviceTask` référence désormais un connecteur configuré par **UUID d'instance**
(`docs/guides/GUIDE-CONCEPTION-BPMN.md` §4, extrait) :

```xml
<serviceTask id="appelServiceExterne" flowable:delegateExpression="${connectorServiceTask}" />
```

> Variable lue par `connectorServiceTask` : `connectorInstanceId` (UUID de l'instance créée au
> préalable côté admin). « **c'est cet UUID que le BPMN référence**, jamais le type ou la config
> en dur. »

Cette instance (`ConnectorInstanceData`, voir [06-connecteurs.md](06-connecteurs.md)) se crée
aujourd'hui uniquement via `ConnectorAdminController` (écran admin, config JSON + secrets
`PASSWORD` chiffrés AES-GCM). **Aucun tool MCP ne permet de créer, lister ou consulter une
`ConnectorInstance`, ni de lister les `ConnectorDefinition` disponibles** (types de connecteurs +
`configSchema`, déjà exposé en REST via `ConnectorDefinitionController`).

Conséquence concrète : un agent peut aujourd'hui, via `deploy_workflow`, déployer un diagramme qui
appelle `${connectorServiceTask}` — mais il ne peut pas provisionner l'instance à laquelle ce
`serviceTask` doit s'accrocher. Le flux reste incomplet sans un aller-retour manuel sur l'écran
admin. C'est le trou qui empêche le plus directement de « définir un flux métier complet » de bout
en bout par MCP.

### 2.2 Association template ↔ type de dossier (formulaires de tâche, PR #107)

[PR #107](https://github.com/superjojo79/FlowForge/pull/107) (2026-09-03) ajoute un 2ᵉ kind de
formulaire de tâche reconnu par `flowable:formKey` : `document-generation`, qui présente au porteur
de tâche un choix parmi les templates **associés au type de dossier**
(`DossierType.documentTemplateKeys`, Admin → Types → *Templates de documents autorisés*).

Vérifié dans le code (`DefineDossierTypeTool.java`) : le tool `define_dossier_type`
- en création, force `documentTemplateKeys = null` (`defineCreate`, ligne 150) ;
- en mise à jour, **préserve tel quel** la valeur existante sans jamais la lire depuis les
  arguments de l'appel (`defineUpdate`, ligne 183 : `existing.documentTemplateKeys()`).

**`documentTemplateKeys` est donc le seul champ du modèle `DossierType` inaccessible en écriture
via MCP.** Un agent peut lister les templates disponibles (`list_document_templates`) et créer le
type de dossier, mais ne peut pas les relier — étape pourtant nécessaire dès qu'un `userTask` du
process qu'il vient de déployer porte `formKey = document-generation`.

### 2.3 Questions de tâche indépendantes du dossier (`user-question:<schemaKey>`, TODO §55)

Fonctionnalité close le 2026-09-05 (analyse [23-questions-utilisateur-taches.md](23-questions-utilisateur-taches.md),
implémentation TODO §55) : un 3ᵉ kind de formulaire, `user-question:<schemaKey>`, référence un
nouveau concept admin `TaskQuestionSchema` — mini-formulaire réutilisant `FieldDefinition`,
totalement indépendant d'un `DossierType`, exposé par `TaskQuestionSchemaController`
(`/api/task-question-schemas`, CRUD + lecture runtime).

Cette entité est apparue **après** le cadrage MCP — elle n'a jamais été évaluée pour inclusion.
Aucun tool MCP ne peut la créer, la lister ou la lire.

### 2.4 Pour mémoire — ce qui ne bouge pas

- `schema-champs-dynamiques` (PR #87, écran admin d'édition du schéma `DossierType`) : couvert par
  `define_dossier_type`, même garde-fou de compatibilité (`D-SCHEMA-COMPAT=B`, blocage) — rien à
  ajouter, cité ici seulement pour écarter une fausse piste.
- Injection automatique de `dossierFields` comme variable de process réservée (PR #86) :
  comportement transparent, ne nécessite pas de tool — à signaler dans la description de
  `deploy_workflow` plutôt qu'un nouveau mécanisme, pour qu'un agent qui conçoit un BPMN sache que
  la variable existe déjà sans configuration.

---

## 3. Capacités candidates (à confirmer/affiner en requirements)

### A. Connecteurs

| Tool envisagé | Use case existant réutilisable | Remarque |
|---|---|---|
| `list_connector_definitions` | `ConnectorDefinitionController` (lecture) | catalogue des types + `configSchema` — permet à l'agent de savoir quel JSON de config produire avant `define_connector_instance` |
| `list_connector_instances` | `ConnectorAdminController` (lecture) | secrets (`PASSWORD`) jamais renvoyés en clair — même politique de masquage que l'admin REST (`ConnectorResponseSecretMaskingTest`, déjà en place) |
| `get_connector_instance` | idem | par id, même masquage |
| `define_connector_instance` | création/MAJ d'une `ConnectorInstance` | écriture — nécessite une décision sur le passage des secrets (voir « Risques ») et un éventuel `dryRun` (cohérence avec `define_dossier_type`/`deploy_workflow`, qui l'ont déjà) |

### B. Association templates ↔ type de dossier

Deux options, à trancher en requirements :

- **(a)** Étendre `define_dossier_type` pour accepter `documentTemplateKeys` en entrée (aligné sur
  le principe déjà en place « le schéma complet à chaque appel », comme `fieldDefinitions`).
- **(b)** Un tool dédié `set_dossier_type_document_templates` (plus explicite, évite d'alourdir
  encore `define_dossier_type`).

### C. Questions de tâche

| Tool envisagé | Use case existant réutilisable | Remarque |
|---|---|---|
| `list_task_question_schemas` | `TaskQuestionSchemaController` (`GET`) | lecture |
| `get_task_question_schema` | idem (`GET /{schemaKey}`) | lecture |
| `define_task_question_schema` | idem (`POST`/`PUT`) | écriture — RBAC déjà tranché côté REST (`TENANT_ADMIN`+`WORKFLOW_ADMIN`), garde-fou de collision de noms de variables déjà en place côté domaine (`WorkflowVariables.isReservedOrKnownKey`) à réutiliser tel quel |

---

## 4. Risques et points de vigilance

- **Secrets connecteurs.** `ConnectorInstance` porte des champs `PASSWORD` chiffrés AES-GCM
  (secret HMAC de webhook, credentials SMTP, token d'API...). `define_connector_instance` est le
  premier tool MCP d'écriture qui manipulerait un secret — le pattern REST existant (chiffrement à
  l'écriture, masquage à la lecture) doit être repris à l'identique, jamais réinventé. À trancher :
  le canal MCP est-il jugé assez sûr pour transporter un secret en clair à la création (comme le
  fait l'écran admin), ou faut-il une contrainte supplémentaire (secret fourni hors-bande, référence
  à un secret déjà stocké) ?
- **Rayon d'action en écriture.** Même remarque que celle déjà actée pour `define_dossier_type`/
  `deploy_workflow` ([15-serveur-mcp.md](15-serveur-mcp.md) « Risques et points de vigilance ») :
  `define_connector_instance` engage un `serviceTask` déjà déployé ou à venir — trace d'audit
  (`createdBy`/`updatedBy`, pattern D-AUDIT=C déjà en place) et `dryRun` à reconduire.
- **Cohérence avec `deploy_workflow`.** Un flux métier complet s'assemble dans un ordre logique
  (connecteurs → type de dossier → schémas de questions → BPMN/`WorkflowTypeConfig`) — aucune
  dépendance technique ne l'impose côté moteur (Flowable ne résout `connectorInstanceId`/`formKey`
  qu'à l'exécution), mais la documentation des tools devrait le suggérer pour éviter qu'un agent
  déploie un BPMN référençant un connecteur ou un `schemaKey` qui n'existe pas encore.
- **Portée volontairement non couverte ici.** `create_dossier`, `add_dossier_element`,
  `start_workflow`, `generate_document` restent hors sujet de cette analyse — déjà identifiés et
  volontairement laissés en backlog par [15-serveur-mcp.md](15-serveur-mcp.md) pour une raison
  différente (écriture sur des données métier, pas de la configuration).

---

## 5. Questions ouvertes (à trancher en phase requirements/design)

1. **D-MCP-EXT-SCOPE** — les trois lacunes (connecteurs, association templates, questions de
   tâche) sont-elles traitées dans un seul cycle `dev-lifecycle` ou trois cycles séparés ? Le
   backlog de connecteurs a été livré en plusieurs vagues (§2.1) — un découpage similaire est
   possible côté MCP.
2. **D-MCP-EXT-SECRET** — mécanisme exact d'entrée d'un secret connecteur via
   `define_connector_instance` (voir « Risques »).
3. **D-MCP-EXT-DOCTPL** — option (a) ou (b) pour l'association templates ↔ type de dossier (§3.B).
4. **D-MCP-EXT-DRYRUN** — `dryRun` sur `define_connector_instance` et
   `define_task_question_schema`, par cohérence avec `define_dossier_type`/`deploy_workflow` —
   confirmer que le pattern doit se reconduire partout où un tool écrit.
5. **D-MCP-EXT-NAMING** — noms de tools définitifs (`list_connector_definitions` /
   `list_connector_instances` / `get_connector_instance` / `define_connector_instance` ;
   `list_task_question_schemas` / `get_task_question_schema` / `define_task_question_schema`) —
   posés provisoirement pour cette analyse, à confirmer en requirements.
