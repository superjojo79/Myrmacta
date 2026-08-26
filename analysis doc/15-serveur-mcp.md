# Serveur MCP (Model Context Protocol)

> **Statut : analyse initiale — cadrage, aucune décision actée.** Ce document
> pose le périmètre et les questions à trancher avant design. Il ne décrit
> pas une implémentation retenue et ne doit pas être lu comme telle.
>
> **Décisions de cadrage actées lors de cette analyse (2026-08-26) :**
> consommateur, portée d'écriture V1, sens du mot « dossier », granularité
> de l'insertion BPMN, intégration technique et modèle d'authentification —
> voir [Décisions de cadrage](#décisions-de-cadrage) ci-dessous.
>
> **Extension du périmètre (2026-08-26) :** quatre tools de lecture à
> faible risque ajoutés au V1 (§ [Capacités en périmètre V1](#capacités-en-périmètre-v1),
> points 4 à 7) ; quatre tools d'écriture à risque modéré identifiés et
> placés en [backlog](#backlog--extensions-futures-envisageables-hors-v1),
> pas retenus pour le V1.

---

## Contexte et motivation

FlowForge expose aujourd'hui ses fonctionnalités via une UI Vue (BFF/session)
et une API REST consommée par ce même SPA (voir
[11-frontend.md](11-frontend.md), [03-authentification.md](03-authentification.md)).
Il n'existe pas de canal pensé pour un **agent IA** (Claude Code ou autre
outillage MCP) qui aurait besoin d'opérer sur FlowForge de façon
programmatique : configurer des types de dossier, déployer un processus
BPMN, consulter l'état d'un dossier.

L'objectif de cette feature est d'exposer un **serveur MCP** — un ensemble
d'outils (tools) appelables par un agent IA — couvrant un sous-ensemble
ciblé des capacités métier existantes, sans dupliquer la logique domaine
déjà en place.

Quatre besoins ont été exprimés initialement :

1. Définition d'un dossier et de ses champs.
2. Insertion d'un processus BPMN avec ses paramètres.
3. Obtenir des informations sur un dossier particulier.
4. Obtenir des informations et statistiques sur des dossiers et instances de
   workflow selon des critères fournis.

Le point 4 recoupe une problématique de **reporting/SLA** déjà identifiée
comme hors périmètre V1 ailleurs dans le produit — voir
[Dépendance reporting/SLA](#dépendance-reporting--sla-point-4).

---

## Décisions de cadrage

| Question | Décision |
|---|---|
| **Consommateur** | Outillage **interne** (agents IA type Claude Code utilisés par l'équipe FlowForge pour l'administration/l'automatisation). **Pas** d'exposition aux tenants ou à des systèmes tiers en V1. |
| **Portée d'écriture V1** | Lecture **et** écriture pour les capacités 1 (définition de dossier) et 2 (insertion BPMN). Le point 4 (stats) est retiré du périmètre V1 — voir dépendance ci-dessous. |
| **Sens de « dossier »** | Le point 1 porte sur le **type de dossier** (`DossierType` + `FieldDefinition`, schéma/configuration) — **pas** sur la création d'instances `Dossier`. Équivalent MCP de l'écran admin des types de dossier. |
| **Granularité de l'insertion BPMN** | Un seul outil MCP couvre **déploiement du `.bpmn20.xml` + création/mise à jour du `WorkflowTypeConfig`** associé (statusDefinitions, lien avec un `DossierType`), pas seulement le dépôt du fichier. |
| **Intégration technique** | Nouvel **adaptateur infrastructure** (`infrastructure.mcp`) dans le monolithe Spring Boot existant, qui appelle les use cases domaine déjà en place — cohérent avec l'architecture hexagonale (décision de ne pas créer un service séparé). Un serveur MCP par instance tenant, comme le reste de l'application (décision n° 13). |
| **Authentification** | Réutilisation du rôle **`API_SERVICE`** déjà défini (RBAC V1, [12-autorisation-conformite.md](12-autorisation-conformite.md)) avec un credential dédié pour l'agent — pas de nouveau rôle. |

---

## Capacités en périmètre V1

### 1. Définition d'un type de dossier et de ses champs

Mappe directement sur le modèle `DossierType` / `FieldDefinition` déjà
documenté dans [04-dossiers.md](04-dossiers.md#dossiertype--schéma-dynamique-par-tenant).

Outil envisagé : `define_dossier_type` — création ou mise à jour (par
`code`) d'un `DossierType` : `label`, `workflowKey`, `active`,
`allowUnknownFields`, `fieldDefinitions` (liste de `FieldDefinition`).

**Point de vigilance :** modifier le schéma d'un `DossierType` déjà utilisé
par des dossiers existants peut casser la validation (`DossierFieldValidator`,
voir [04-dossiers.md](04-dossiers.md#validation-des-champs-dynamiques)) de
dossiers en cours si un champ `required` est ajouté ou un type de champ
change. À trancher en design : validation de compatibilité ascendante,
avertissement, ou blocage.

### 2. Insertion d'un processus BPMN avec ses paramètres

S'appuie sur le mécanisme de déploiement déjà livré
(`deploiement-bpmn`, `docs/ai/requirements/2026-08-22-feature-deploiement-bpmn.md`) :
upload `.bpmn20.xml`, validation (BPMN 2.0 bien formé, cohérence
`processDefinitionKey`), `RepositoryService.createDeployment()`. Le
périmètre MCP va plus loin que l'écran admin existant en couvrant en un
seul appel la création/mise à jour du `WorkflowTypeConfig` associé
(`workflowKey`, `statusDefinitions` — voir
[05-workflow-engine.md](05-workflow-engine.md#statuts-métier-configurables-par-workflow--workflowtypeconfig)).

Outil envisagé : `deploy_workflow` — accepte le contenu BPMN (XML ou
base64), le `processDefinitionKey` attendu, et la définition des
`statusDefinitions` ; orchestre validation → déploiement Flowable →
upsert `WorkflowTypeConfig`.

**Point de vigilance :** un redéploiement d'une clé existante crée une
nouvelle version Flowable (les instances en cours restent sur l'ancienne,
comportement natif déjà documenté dans `deploiement-bpmn`) — la mise à
jour simultanée de `statusDefinitions` doit rester compatible avec les
`DossierWorkflowLink` déjà actifs sur l'ancienne version. À trancher en
design.

### 3. Informations sur un dossier particulier

Lecture seule. Mappe sur `GetDossierUseCase` / `GetDossierWorkflowHistoryUseCase`
déjà existants ([04-dossiers.md](04-dossiers.md#contrôle-daccès-au-niveau-type-de-dossier-décision-n-20)).

Outil envisagé : `get_dossier` — par `id` ou `reference` ; retourne le
dossier, ses `DossierElement`, son historique `DossierWorkflowLink` et ses
`DossierRelation`. Soumis à `DossierAccessPolicy` comme tout appelant —
voir [Authentification et RBAC](#authentification-et-rbac).

### 4. Lecture d'un type de dossier

Lecture seule — pendant naturel de `define_dossier_type` (permet à l'agent
de consulter un schéma existant avant de proposer une modification, sans
avoir à la deviner). Mappe sur `DossierTypeRepository`
([04-dossiers.md](04-dossiers.md#dossiertype--schéma-dynamique-par-tenant)).

Outils envisagés : `get_dossier_type` (par `code`) et `list_dossier_types`
(catalogue des types actifs/inactifs du tenant).

### 5. Liste des types de workflow

Lecture seule. Permet à l'agent de connaître les `WorkflowTypeConfig`
existants (`workflowKey`, `statusDefinitions`) avant un `deploy_workflow`,
plutôt que de risquer une incohérence de clé ou de statuts —
[05-workflow-engine.md](05-workflow-engine.md#statuts-métier-configurables-par-workflow--workflowtypeconfig).

Outil envisagé : `list_workflow_types`.

### 6. Recherche de dossiers par critères

Lecture seule, **sans agrégats** — à ne pas confondre avec le point 4
initial (statistiques), qui reste hors périmètre et dépendant du cadrage
reporting/SLA (voir [Dépendance reporting/SLA](#dépendance-reporting--sla-point-4)
ci-dessous, inchangée par cet ajout). Ce tool ne fait que filtrer/lister,
sans calculer de métrique. Mappe sur la recherche/pagination déjà livrée
(`pagination-dossiers`).

Outil envisagé : `search_dossiers` — filtres `dossierTypeCode`, `status`,
plage de dates (`createdAt`/`closedAt`), pagination ; retourne une liste de
résumés de dossiers (pas le détail complet, réservé à `get_dossier`).

### 7. Liste des templates de documents

Lecture seule. Permet à l'agent de connaître les templates disponibles
avant, potentiellement, de proposer une génération de document (tool en
backlog, voir plus bas) — [08-templates-documents.md](08-templates-documents.md).

Outil envisagé : `list_document_templates`.

---

## Hors périmètre V1

### Dépendance reporting / SLA (point 4)

Le point « informations et statistiques sur des dossiers et instances de
workflow selon des critères fournis » n'est **pas traité dans cette V1**.
`DEFERRED-FEATURES.md` classe déjà les écrans Rapports/SLA comme hors
scope V1 produit (mock UI de référence uniquement) — le même constat
s'applique ici : sans modèle de métriques/agrégats ni définition de SLA
actée, un outil MCP de « stats » n'aurait pas de contrat stable à exposer.

**Prérequis avant de rouvrir ce point :** une analyse dédiée « reporting &
SLA » a été produite — voir [16-reporting-sla.md](16-reporting-sla.md).
Elle propose un découpage en deux lots (Lot 1 — comptages/durées, Lot 2 —
seuils SLA et statut de conformité) et doit être **actée puis livrée**
avant que cette feature MCP n'expose un outil de lecture (`query_dossiers` /
`get_workflow_stats` ou équivalent) consommant ce modèle, plutôt que
d'improviser des agrégats ad hoc non réutilisables.

### Autres exclusions actées

- Pas de consommation par des tenants ou systèmes tiers en V1 (voir
  [Décisions de cadrage](#décisions-de-cadrage)) — pas de durcissement
  multi-tenant spécifique au MCP au-delà de l'existant.
- Pas de création d'instances `Dossier` ni d'autres écritures à risque
  modéré via MCP en V1 (`create_dossier`, `add_dossier_element`,
  `start_workflow`, `generate_document`) — identifiées et détaillées en
  [backlog](#backlog--extensions-futures-envisageables-hors-v1).
- Pas de Flowable Modeler/Cockpit ni d'édition visuelle de BPMN via MCP —
  déjà écarté du produit ([DEFERRED-FEATURES.md](../DEFERRED-FEATURES.md) §1.5).

---

## Architecture envisagée

Nouvel adaptateur **entrant** au sens hexagonal, au même titre que
`infrastructure.web` (REST) — appelle les use cases domaine existants
(`port.in`) sans logique métier propre :

```
infrastructure
└── mcp
    ├── McpServerConfig            # câblage du serveur MCP (transport, outils exposés)
    ├── tool
    │   ├── DefineDossierTypeTool     # → CreateDossierTypeUseCase / UpdateDossierTypeUseCase (port.in existant ou à créer)
    │   ├── DeployWorkflowTool        # → use case déploiement BPMN (deploiement-bpmn) + WorkflowTypeConfig upsert
    │   ├── GetDossierTool            # → GetDossierUseCase / GetDossierWorkflowHistoryUseCase
    │   ├── GetDossierTypeTool        # → DossierTypeRepository.findByCode (lecture seule)
    │   ├── ListDossierTypesTool      # → DossierTypeRepository.findAll (lecture seule)
    │   ├── ListWorkflowTypesTool     # → WorkflowTypeConfigRepository (lecture seule)
    │   ├── SearchDossiersTool        # → recherche/pagination existante (pagination-dossiers, lecture seule)
    │   └── ListDocumentTemplatesTool # → repository templates (08-templates-documents, lecture seule)
    └── security
        └── McpAuthenticationFilter  # valide le credential API_SERVICE dédié
```

Un serveur MCP par instance Spring Boot, donc par tenant — cohérent avec
la décision n° 13 (une instance = un tenant, pas de résolution dynamique).
Aucun `ThreadLocal` de tenant à introduire : le tenant est celui de
l'instance qui héberge le serveur MCP, comme pour le reste de l'application.

### Authentification et RBAC

- Credential dédié pour l'agent MCP, mappé au rôle **`API_SERVICE`** déjà
  défini (V1 RBAC, [12-autorisation-conformite.md](12-autorisation-conformite.md)).
- Les appels `get_dossier` passent par `DossierAccessPolicy` comme tout
  appelant `User`/`API_SERVICE` — pas de contournement `AccessContext.system(...)`
  (réservé aux appels internes Flowable).
- Mécanisme exact du credential (OAuth2 `client_credentials` via le
  provider OIDC existant, ou clé API dédiée) — **à trancher en design**,
  cohérent avec le fait que le canal MCP est machine-à-machine et distinct
  du pattern BFF/session navigateur ([03-authentification.md](03-authentification.md)).

### Transport

Non tranché à ce stade. Le pattern BFF (session cookie navigateur) ne
s'applique pas à un canal machine-à-machine : un transport HTTP du
protocole MCP (Streamable HTTP), exposé sur un chemin/port distinct du SPA
et protégé par le credential `API_SERVICE`, semble plus cohérent qu'un
transport stdio (qui suppose un accès process-local, peu adapté à un
déploiement par instance tenant). À confirmer en design.

---

## Risques et points de vigilance

- **Rayon d'action en écriture d'un agent IA.** `define_dossier_type` et
  `deploy_workflow` sont des opérations de configuration structurantes
  (impact sur des dossiers/instances existants). Même réservé à un usage
  interne, un agent pilotant ces outils devrait produire une trace d'audit
  claire (auteur = identité de l'agent/opérateur, pas un `system` générique)
  et gagnerait à supporter un mode de prévisualisation avant application —
  à cadrer en design.
- **Contenu BPMN non fiable.** Le fichier `.bpmn20.xml` transmis à
  `deploy_workflow` doit repasser par les mêmes validations que l'upload
  admin existant (bien formé, schéma BPMN 2.0, pas de résolution d'entités
  externes XML) — pas de nouveau chemin de confiance à inventer.
- **Cohérence avec la feature `deploiement-bpmn`.** Le MCP ne doit pas
  diverger du mécanisme de déploiement déjà livré (double logique de
  validation/déploiement à éviter) — réutilisation directe des use cases
  existants, pas de réimplémentation.

---

## Questions ouvertes

Ces points restent à trancher en phase de conception, une fois cette
analyse validée :

1. **Transport MCP** — HTTP (Streamable HTTP) vs stdio ; exposition réseau
   exacte (port dédié, chemin, filtrage réseau).
2. **Mécanisme du credential `API_SERVICE`** — OAuth2 `client_credentials`
   réutilisant le provider OIDC existant, ou clé API applicative dédiée.
3. **Compatibilité ascendante de `DefineDossierTypeTool`** — bloquer,
   avertir ou autoriser une modification de schéma impactant des dossiers
   existants.
4. **Interaction `deploy_workflow` / versionnement Flowable** — comportement
   exact quand une nouvelle version de `WorkflowTypeConfig.statusDefinitions`
   coexiste avec des `DossierWorkflowLink` actifs sur l'ancienne version.
5. **Audit trail dédié** — faut-il un journal spécifique aux appels MCP
   (au-delà des champs `createdBy`/`updatedBy` déjà portés par les entités),
   pour distinguer un changement fait par un agent IA d'un changement fait
   depuis l'UI admin ?
6. **Périmètre futur** — les tools d'écriture à risque modéré identifiés
   (`create_dossier`, `add_dossier_element`, `start_workflow`,
   `generate_document`, voir [backlog](#backlog--extensions-futures-envisageables-hors-v1))
   nécessiteront chacun leur propre analyse avant d'être retenus pour une
   itération ultérieure (validation des champs dynamiques, création depuis
   trigger vs MCP, etc. — voir
   [04-dossiers.md](04-dossiers.md#création-depuis-un-trigger--unitaire-et-batch)).
7. **Reporting/SLA (point 4)** — analyse disponible
   ([16-reporting-sla.md](16-reporting-sla.md)) ; reste bloqué sur sa
   validation et sa livraison (Lot 1 au minimum) avant de rouvrir ce point
   côté MCP.
8. **Pagination/volumétrie de `search_dossiers`** — reprendre telles
   quelles les bornes déjà actées pour `pagination-dossiers` (taille de
   page max, tri par défaut) plutôt que d'introduire un contrat de
   pagination MCP spécifique.

---

## Backlog — extensions futures envisageables (hors V1)

> Tools d'écriture à **risque modéré** identifiés lors du cadrage — utiles,
> mais pas retenus pour le V1 car ils touchent des données métier au-delà
> de la configuration (dossier type / workflow) déjà en périmètre. Chacun
> nécessiterait sa propre analyse avant d'être implémenté (validation,
> audit, prévisualisation — mêmes réserves que les tools V1 en écriture,
> voir [Risques et points de vigilance](#risques-et-points-de-vigilance)).

| Tool candidat | Use case existant réutilisable | Remarque |
|---|---|---|
| `create_dossier` | `CreateDossierUseCase` / `CreateDossierBatchUseCase` | Passe par `DossierFieldValidator` comme toute création — voir [04-dossiers.md](04-dossiers.md#validation-des-champs-dynamiques). |
| `add_dossier_element` | `AddDossierElementUseCase` | Ajout d'une note/pièce/lien à un dossier existant ; ne couvre pas l'upload de fichier binaire (canal MCP mal adapté à un flux multipart). |
| `start_workflow` | `AttachWorkflowUseCase.reserve` / `confirmStarted` | Équivalent MCP du démarrage manuel déjà exposé en REST ([04-dossiers.md](04-dossiers.md#1-déclenchement-manuel--api--ui)) ; soumis à la même règle de concurrence (un seul workflow actif par dossier). |
| `generate_document` | Génération PDF (voir [07-generation-documents.md](07-generation-documents.md)) | Déclenche une génération à partir d'un template pour un dossier donné ; dépend du point 7 (liste des templates) pour que l'agent sache quoi proposer. |

### Exclusions explicites (risque jugé trop élevé pour un MCP interne)

Ces capacités existent dans FlowForge mais ne sont **pas** candidates à un
tool MCP, même en backlog, au vu de leur impact :

- **Gestion des ACL** (`grant/revoke_dossier_type_access`,
  [12-autorisation-conformite.md](12-autorisation-conformite.md)) — impact
  sécurité transverse, à réserver à l'UI admin humaine.
- **Purge** (`purge-stockage`, TODO §22) — opération destructive par
  nature (suppression PostgreSQL + object store).
- **Mise à jour directe du statut d'un `DossierWorkflowLink`** — c'est un
  rôle de `ServiceTask` piloté par le processus BPMN lui-même
  (`UpdateWorkflowLinkStatusTask`), pas un point d'entrée externe légitime.

---

## Prochaines étapes

Cette analyse initiale ne déclenche pas encore le cycle `dev-lifecycle`
(requirements → design → planning → implementation). Avant de créer une
entrée dans `docs/ai/requirements/` et de la positionner dans
[FILE-ATTENTE.md](../FILE-ATTENTE.md), il est recommandé de faire valider
les décisions de cadrage ci-dessus et de lever au moins les questions
ouvertes n° 1 à 4, qui conditionnent la faisabilité technique du design.
