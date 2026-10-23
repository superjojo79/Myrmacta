# FlowForge
> Plateforme de gestion de flux métier, de dossiers et de génération de documents.
>  Solution SaaS multi-tenant, extensible par design.

> **Démarrage local :** voir [docs/guides/GETTING-STARTED.md](docs/guides/GETTING-STARTED.md) — infrastructure Docker/Podman
> (PostgreSQL, Keycloak, stockage S3-compatible en option) + lancement backend/frontend, config minimale incluse.
>  **Utilisation de l'UI :** voir [docs/guides/GUIDE-UTILISATEUR.md](docs/guides/GUIDE-UTILISATEUR.md) — créer un dossier,
> démarrer/suivre un workflow, configurer un connecteur ou un template de document.
>  **Déploiement :** voir [docs/guides/DEPLOIEMENT.md](docs/guides/DEPLOIEMENT.md) — au-delà du poste de dev : artefact,
> reverse-proxy, provisioning d'un tenant, comptes Keycloak (`Dockerfile` et `scripts/provision-tenant.sh` livrés ; démarrage de l'instance, reverse-proxy et Keycloak restent documentés, non automatisés).
>  **Concevoir un BPMN :** voir [docs/guides/GUIDE-CONCEPTION-BPMN.md](docs/guides/GUIDE-CONCEPTION-BPMN.md) — conventions,
> champs dynamiques, connecteurs, intervenants, génération de documents depuis un diagramme.

------

## Vision produit

FlowForge est une plateforme permettant à des organisations de **modéliser, exécuter et superviser leurs processus métier** sans développement sur mesure pour chaque cas d'usage.

**Positionnement (décision du 2026-09-28, [TODO §70](docs/decisions/070-positionnement-produit-d0-integrateurs-couche-low-code.md)) :** produit pour **intégrateurs**,
avec hébergement dédié par client. L'intégrateur conçoit les processus (BPMN/DMN) ; une couche
**low-code / no-code** (palette métier, éditeurs de formulaires et de templates, assistant IA,
concepteur de parcours) est **livrée** (Vague D) pour que les utilisateurs avancés côté client
ajustent formulaires, documents et règles. Suite : [ROADMAP.md](ROADMAP.md) et les
[issues GitHub](https://github.com/superjojo79/FlowForge/issues).

L'idée centrale : un dossier métier suit un workflow adapté à son type, déclenché automatiquement depuis des sources externes, avec production de documents et notification des parties prenantes à chaque étape clé.

**Exemples de cas d'usage :**

- Gestion de dossiers de sinistres — réception, instruction, décision, clôture
- Traitement de demandes de subventions — dépôt, vérification, validation, notification
- Onboarding client — collecte de pièces, vérification, activation
- Gestion de réclamations — réception, escalade, résolution, archivage
- Tout processus métier impliquant des étapes séquentielles, des validations humaines et la production de documents

------

## Proposition de valeur

| Besoin                                 | Réponse FlowForge                                            |
| -------------------------------------- | ------------------------------------------------------------ |
| Modéliser un processus sans coder      | Éditeur BPMN 2.0 visuel intégré (`bpmn-js`, écran admin FlowForge) |
| Exprimer des règles/critères métier sans cascade de gateways | Éditeur de tables de décision DMN intégré (`dmn-js`, `/admin/dmn-editor`), invocable depuis un process via `serviceTask` |
| Gérer des dossiers structurés          | Entité dossier avec signalétique et champs dynamiques par type |
| Produire des documents automatiquement | Génération PDF à partir de templates Thymeleaf versionnés    |
| Intégrer des systèmes existants        | Système de connecteurs extensible (REST/webhook, signal Flowable, cron, SMTP, Kafka, import batch SFTP, e-mail entrant IMAP/Graph, Azure Service Bus, stockage fichier) |
| Adapter à chaque client                | Architecture multi-tenant — schéma de données isolé par organisation |
| Intégrer les outils noCode             | Webhooks sortants compatibles Make, n8n, Zapier              |
| Suivre l'avancement en temps réel      | Visualisation BPMN avec coloration des étapes + notifications navigateur |

------

## Fonctionnalités principales

### Gestion de dossiers

Entité métier centrale de la plateforme. Chaque dossier est constitué d'un **signalétique** (numérotation automatique, type, statut) et d'**éléments hétérogènes** : pièces jointes, documents produits, informations structurées, notes. Les dossiers peuvent être liés entre eux (hiérarchie, subséquence, lien simple). Le schéma de chaque type de dossier est configurable par tenant sans développement.

### Moteur de workflow BPMN 2.0

Orchestration des flux métier via **Flowable** — standard industriel ouvert (pas de cockpit Flowable intégré à FlowForge, hors V1). Les processus se modélisent directement dans FlowForge (éditeur `bpmn-js` intégré à `/admin/bpmn-editor` — création, import, édition d'un diagramme déjà déployé, brouillon persisté) ou avec un outil externe, puis se déploient soit au build/démarrage (scan classpath), soit à chaud depuis l'écran d'administration `/admin/workflow-types` (upload avec prévisualisation) ou directement depuis l'éditeur. Support des gateways, tâches humaines et événements de signal. Le moteur DMN Flowable est activé et un `serviceTask flowable:type="dmn"` peut invoquer une table de décision éditée dans son propre éditeur intégré (`dmn-js`, `/admin/dmn-editor`) — mêmes principes que l'éditeur BPMN (brouillon persisté, déploiement à chaud), grammaire d'expression JUEL restreinte à une allowlist sûre (littéraux/comparaisons, pas d'EL brut) côté serveur.

### Système de connecteurs

Architecture extensible permettant d'intégrer n'importe quelle source ou destination. Un connecteur peut jouer le rôle de **trigger** (déclenche un workflow sur événement entrant), de **service** (exécute un traitement dans le workflow) ou de **callback** (reprend un workflow en attente d'une réponse asynchrone). Catalogue livré : `http-rest` (**TRIGGER + SERVICE**), `flowable-signal` (TRIGGER + SERVICE), `scheduler` (TRIGGER, cron), `smtp` (SERVICE), `document-storage` (SERVICE, via `DocumentStoragePort`), `flowable-callback` (**CALLBACK**), `batch-import` (TRIGGER, SFTP), `kafka` (TRIGGER), `imap-inbound` et `graph-mail-inbound` (TRIGGER, e-mail entrant), `azure-service-bus` (TRIGGER). Des connecteurs custom peuvent être ajoutés au classpath sans modifier le core.

### Génération de documents

Production de PDF à partir de **templates HTML Thymeleaf** versionnés, stockés en base. Support des variants par langue et par contexte métier avec fallback automatique. Preview en temps réel pendant la saisie, aperçu PDF avant validation. Rendu via Flying Saucer + OpenPDF (CSS 2.1, en-têtes/pieds de page, pagination).

### Visualisation des instances

Chaque instance de workflow est visualisable en temps réel dans le browser via **BPMN.js** — le diagramme Flowable est rendu fidèlement avec coloration des étapes actives, terminées et en erreur. Le dossier associé, ses éléments et son historique sont affichés en regard. Les tâches humaines sont réassignables depuis l'interface.

### Notifications multi-canaux

Email, webhook sortant et notification navigateur (SSE) — configurables par tenant, déclenchables depuis n'importe quelle étape du workflow. Les webhooks sortants sont compatibles avec les plateformes noCode (Make, n8n, Zapier) et peuvent réinteragir avec Flowable.

### Multi-tenant

Chaque organisation dispose d'un schéma PostgreSQL dédié — isolation forte des données, migration indépendante, facturation séparée. **Une instance Spring Boot = un tenant** : `app.tenant.id` est fixé au démarrage (décision n° 13), jamais résolu dynamiquement par requête. Le claim OIDC `tenant_id` sert de **garde d'appartenance** : il doit correspondre à l'instance, sinon la requête est refusée. Voir [02-multi-tenant.md](docs/specs/02-multi-tenant.md).

### Authentification flexible

OAuth2/OIDC avec pattern BFF — le browser ne manipule jamais de token JWT. Le provider est interchangeable par configuration : Keycloak (self-hosted), Auth0, Microsoft Entra ID, Google.

### Serveur MCP (outillage agent IA interne)

Un serveur [MCP](https://modelcontextprotocol.io) (Model Context Protocol) expose un sous-ensemble des capacités métier à un agent IA interne (Claude Code ou tout autre client MCP) : définir un type de dossier, déployer un workflow BPMN, consulter un dossier, rechercher des dossiers, consulter les statistiques SLA. Transport Streamable HTTP sur `/api/m2m/mcp`, authentification par le même JWT machine-à-machine que les connecteurs (`Authorization: Bearer`, rôle `API_SERVICE`) — pas de nouveau mécanisme d'authentification, pas d'exposition tenant tierce. Voir [docs/guides/GETTING-STARTED.md](docs/guides/GETTING-STARTED.md#12-serveur-mcp-outillage-agent-ia) pour s'y connecter en local.

------

## Architecture technique

### Principes

L'application est construite sur le modèle **Ports & Adapters (hexagonal)** — le domaine métier est isolé de toute technologie. Chaque composant technique (Flowable, JPA, PDF, notifications) est un adaptateur remplaçable sans impact sur la logique métier. Cette architecture garantit la testabilité du domaine sans infrastructure et la pérennité face aux évolutions des frameworks.

### Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│  Connecteurs (triggers / services / callbacks)                  │
│  REST · signal · cron · SMTP · Kafka · SFTP · e-mail · Azure    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ événements
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Moteur de workflow — Flowable BPMN 2.0 + DMN                   │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Service Tasks│  │ User Tasks  │  │ Gateways │  │  DMN    │ │
│  └──────┬───────┘  └──────┬──────┘  └──────────┘  └─────────┘ │
└─────────┼─────────────────┼───────────────────────────────────-─┘
          │                 │
          ▼                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  Domaine métier                                                 │
│  Dossiers · Documents · Templates · Connecteurs · Notifications │
└──────┬──────────────┬───────────────────┬───────────────────────┘
       │              │                   │
       ▼              ▼                   ▼
  PostgreSQL      Stockage objet   Notifications
  (schéma/tenant) S3 / filesystem  Email · SSE · Webhook
```

### Stack technique

| Couche            | Technologie                     | Choix                                           |
| ----------------- | ------------------------------- | ----------------------------------------------- |
| Backend           | Java 25 LTS · Spring Boot 4.1.x | Virtual Threads, ecosystem mature               |
| Workflow          | Flowable 8.0.x Community        | BPMN 2.0, Apache 2.0, moteur d'exécution seul (pas de Flowable UI) |
| Persistence       | PostgreSQL + Spring Data JPA    | JSONB pour champs dynamiques, schéma par tenant |
| Génération PDF    | Flying Saucer 10.x + jsoup 1.x (versions exactes : `pom.xml`) | LGPL, sans licence commerciale              |
| Sécurité          | Spring Security 7 + OAuth2 Client | BFF intégré, provider-agnostique              |
| Frontend          | Vue 3 + Vite + TypeScript + OpenVue (embarqué `static/`) | SPA même origine, typage statique aligné sur le contrat API, composants métier riches |
| State management  | Pinia                           | Store réactif Vue 3                             |
| Tests frontend    | Vitest + Playwright + ESLint/Prettier | Unitaire/composants **et** e2e (`npm run test:all`) obligatoires pour features FE ; aligné job CI `frontend` |
| BPMN viewer/éditeur | bpmn-js + bpmn-js-properties-panel | Rendu fidèle, overlays d'état, édition (Modeler + panel de propriétés Flowable custom) |
| DMN éditeur       | dmn-js                          | Même écosystème bpmn.io/Camunda, MIT + filigrane, `/admin/dmn-editor` |
| Stockage fichiers | Port S3 / filesystem             | `DocumentStoragePort` ; MinIO possible en local (pas de couplage) |
| Auth server       | Keycloak 26.7.x                 | Self-hosted, OIDC/OAuth2, LDAP                  |
| Build             | Maven mono-module + ArchUnit    | Règle domaine vérifiée en CI                    |

### Licences — aucune contrainte commerciale

Toutes les librairies retenues sont sous licences libres (Apache 2.0, LGPL, MIT) compatibles avec un modèle SaaS commercial fermé. iText 7 (AGPL) a été explicitement écarté pour cette raison.

------

## Modèle de déploiement

L'application est conçue pour un déploiement **SaaS mutualisé au niveau de l'infrastructure**, avec
une isolation applicative complète : chaque organisation est servie par sa **propre instance Spring
Boot dédiée**, configurée avec son identifiant de tenant au démarrage (voir
[02-multi-tenant.md](docs/specs/02-multi-tenant.md), décision n° 13). Chaque instance embarque
également le frontend Vue 3 dans `static/` (décision n° 27) — même origine API / SPA. Toutes les
instances partagent la même base PostgreSQL (isolation par schéma dédié) et le même serveur Keycloak
(realm partagé). Le routage vers la bonne instance est assuré par un reverse-proxy Apache à
configuration statique (décision n° 14). Le `server.servlet.context-path` (`/${app.tenant.id}`)
est la **cible** alignée sur ce préfixe ; il reste commenté dans `application.yml` tant que le
provisioning n'est pas en place (TODO §31). Le déploiement cible des environnements conteneurisés
(Docker / Kubernetes) ; une instance de tenant peut être scalée indépendamment des autres si sa
charge le justifie.

```
┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
│ Instance Spring    │   │ Instance Spring    │   │ Instance Spring    │
│ Boot — tenant_a    │   │ Boot — tenant_b    │   │ Boot — tenant_n    │
│ (BFF+API+Flowable) │   │ (BFF+API+Flowable) │   │ (BFF+API+Flowable) │
└──────────┬──────────┘   └──────────┬──────────┘   └──────────┬──────────┘
           │                         │                         │
           └────────────┬────────────┴────────────┬────────────┘
                         ▼                         ▼
                  ┌────────────┐            ┌──────────────┐
                  │ PostgreSQL │            │   Keycloak   │
                  │ tenant_a   │            │ (OIDC/OAuth2)│
                  │ tenant_b   │            │ realm partagé│
                  │ tenant_n   │            └──────────────┘
                  └─────┬──────┘
                        ▼
                  ┌──────────────┐
                  │ Object store │
                  │ S3 / filesys │
                  └──────────────┘
```

------

## Documentation technique

Mise en place et utilisation en local : [docs/guides/GETTING-STARTED.md](docs/guides/GETTING-STARTED.md). Prise en main
de l'UI (dossiers, workflows, connecteurs, templates) : [docs/guides/GUIDE-UTILISATEUR.md](docs/guides/GUIDE-UTILISATEUR.md).
Déploiement au-delà du poste de dev (reverse-proxy, provisioning tenant, comptes Keycloak) :
[docs/guides/DEPLOIEMENT.md](docs/guides/DEPLOIEMENT.md). Historique synthétique des changements, par pull request :
[CHANGELOG.md](CHANGELOG.md). Conception d'un diagramme BPMN (conventions, `serviceTask`
disponibles, champs dynamiques, connecteurs, intervenants) :
[docs/guides/GUIDE-CONCEPTION-BPMN.md](docs/guides/GUIDE-CONCEPTION-BPMN.md).

La documentation d'architecture est disponible dans le répertoire `docs/specs` :

| Fichier                                                      | Contenu                              |
| ------------------------------------------------------------ | ------------------------------------ |
| [00-index.md](docs/specs/00-index.md)                    | Vue d'ensemble et dépendances        |
| [01-architecture-hexagonale.md](docs/specs/01-architecture-hexagonale.md) | Ports & Adapters, structure packages |
| [02-multi-tenant.md](docs/specs/02-multi-tenant.md)      | Isolation schéma PostgreSQL          |
| [03-authentification.md](docs/specs/03-authentification.md) | OAuth2/OIDC, BFF, providers          |
| [04-dossiers.md](docs/specs/04-dossiers.md)              | Entité métier centrale               |
| [05-workflow-engine.md](docs/specs/05-workflow-engine.md) | Flowable, BPMN, event bus            |
| [06-connecteurs.md](docs/specs/06-connecteurs.md)        | Système de connecteurs extensible    |
| [07-generation-documents.md](docs/specs/07-generation-documents.md) | Stack PDF, CSS print, performances   |
| [08-templates-documents.md](docs/specs/08-templates-documents.md) | Versionning, variants, résolution    |
| [09-notifications.md](docs/specs/09-notifications.md)    | Email, webhook, SSE                  |
| [10-visualisation-workflow.md](docs/specs/10-visualisation-workflow.md) | BPMN.js, historique, réassignation   |
| [11-frontend.md](docs/specs/11-frontend.md)              | Vue 3, OpenVue, Pinia                |
| [12-autorisation-conformite.md](docs/specs/12-autorisation-conformite.md) | RBAC, rétention, conformité RGPD     |

------

## Statut

> Projet en développement actif : le socle (dossiers, workflows BPMN/DMN, documents, connecteurs,
> serveur MCP, portail externe tokenisé, signature électronique générique avec fournisseur de
> simulation, reporting/SLA, notifications) et la Vague D (couche low-code, champs enrichis,
> recherche, intégrations entreprise, promotion, MCP de conception, i18n fr/en) sont livrés sur
> `main`. Prochains lots : [issues GitHub](https://github.com/superjojo79/FlowForge/issues) de priorité 2
> (pièce jointe e-mail #183, unicité de champ #184). Hors périmètre : fournisseur de signature qualifiée réel, export BI JDBC,
> scripts en bac à sable (spike : non nécessaire). Détails : [ROADMAP.md](ROADMAP.md),
> [docs/decisions/roadmap-2026/AVANCEMENT.md](docs/decisions/roadmap-2026/AVANCEMENT.md).
>  Activité indépendante complémentaire.

La documentation d'architecture a fait l'objet d'une [revue pré-implémentation](docs/analyses/REVUE-PRE-IMPLEMENTATION.md).
Les décisions actées et le seul point encore ouvert (§71) sont dans le [registre des décisions](docs/decisions/README.md).
Ce qui reste à faire est suivi dans les [issues GitHub](https://github.com/superjojo79/FlowForge/issues)
(libellés `priorite-*`, `type-*`, `vague-*`).
