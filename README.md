# FlowForge
> Plateforme de gestion de flux métier, de dossiers et de génération de documents.
>  Solution SaaS multi-tenant, extensible par design.

------

## Vision produit

FlowForge est une plateforme permettant à des organisations de **modéliser, exécuter et superviser leurs processus métier** sans développement sur mesure pour chaque cas d'usage.

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
| Modéliser un processus sans coder      | Designer BPMN 2.0 visuel (Flowable UI)                       |
| Gérer des dossiers structurés          | Entité dossier avec signalétique et champs dynamiques par type |
| Produire des documents automatiquement | Génération PDF à partir de templates Thymeleaf versionnés    |
| Intégrer des systèmes existants        | Système de connecteurs extensible (REST, Kafka, scheduler, ERP…) |
| Adapter à chaque client                | Architecture multi-tenant — schéma de données isolé par organisation |
| Intégrer les outils noCode             | Webhooks sortants compatibles Make, n8n, Zapier              |
| Suivre l'avancement en temps réel      | Visualisation BPMN avec coloration des étapes + notifications navigateur |

------

## Fonctionnalités principales

### Gestion de dossiers

Entité métier centrale de la plateforme. Chaque dossier est constitué d'un **signalétique** (numérotation automatique, type, statut) et d'**éléments hétérogènes** : pièces jointes, documents produits, informations structurées, notes. Les dossiers peuvent être liés entre eux (hiérarchie, subséquence, lien simple). Le schéma de chaque type de dossier est configurable par tenant sans développement.

### Moteur de workflow BPMN 2.0

Orchestration des flux métier via **Flowable** — standard industriel ouvert. Les processus sont dessinés dans le designer visuel intégré, déployés sans redémarrage, et supervisés via le cockpit Flowable. Support des gateways, tâches humaines, règles DMN et événements de signal.

### Système de connecteurs

Architecture extensible permettant d'intégrer n'importe quelle source ou destination. Un connecteur peut jouer le rôle de **trigger** (déclenche un workflow sur événement entrant) ou de **service** (exécute un traitement dans le workflow). Des connecteurs de base sont fournis ; des connecteurs custom peuvent être ajoutés au classpath sans modifier le core. Exemples : webhook REST entrant/sortant, import de fichier batch, appel ERP/D365, scheduler cron.

### Génération de documents

Production de PDF à partir de **templates HTML Thymeleaf** versionnés, stockés en base. Support des variants par langue et par contexte métier avec fallback automatique. Preview en temps réel pendant la saisie, aperçu PDF avant validation. Rendu via Flying Saucer + OpenPDF (CSS 2.1, en-têtes/pieds de page, pagination).

### Visualisation des instances

Chaque instance de workflow est visualisable en temps réel dans le browser via **BPMN.js** — le diagramme Flowable est rendu fidèlement avec coloration des étapes actives, terminées et en erreur. Le dossier associé, ses éléments et son historique sont affichés en regard. Les tâches humaines sont réassignables depuis l'interface.

### Notifications multi-canaux

Email, webhook sortant et notification navigateur (SSE) — configurables par tenant, déclenchables depuis n'importe quelle étape du workflow. Les webhooks sortants sont compatibles avec les plateformes noCode (Make, n8n, Zapier) et peuvent réinteragir avec Flowable.

### Multi-tenant

Chaque organisation dispose d'un schéma PostgreSQL dédié — isolation forte des données, migration indépendante, facturation séparée. Le tenant est résolu depuis le token OIDC à chaque requête.

### Authentification flexible

OAuth2/OIDC avec pattern BFF — le browser ne manipule jamais de token JWT. Le provider est interchangeable par configuration : Keycloak (self-hosted), Auth0, Microsoft Entra ID, Google.

------

## Architecture technique

### Principes

L'application est construite sur le modèle **Ports & Adapters (hexagonal)** — le domaine métier est isolé de toute technologie. Chaque composant technique (Flowable, JPA, PDF, notifications) est un adaptateur remplaçable sans impact sur la logique métier. Cette architecture garantit la testabilité du domaine sans infrastructure et la pérennité face aux évolutions des frameworks.

### Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│  Connecteurs (triggers entrants)                                │
│  REST webhook · Kafka · Scheduler · Import fichier · ERP        │
└───────────────────────────┬─────────────────────────────────────┘
                            │ événements
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  Moteur de workflow — Flowable BPMN 2.0                         │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Service Tasks│  │ User Tasks  │  │ Gateways │  │ DMN     │ │
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
  PostgreSQL      MinIO / S3       Notifications
  (schéma/tenant) (documents)      Email · SSE · Webhook
```

### Stack technique

| Couche            | Technologie                     | Choix                                           |
| ----------------- | ------------------------------- | ----------------------------------------------- |
| Backend           | Java 21 · Spring Boot 3.x       | Virtual Threads, ecosystem mature               |
| Workflow          | Flowable 7.x Community          | BPMN 2.0, Apache 2.0, designer intégré          |
| Persistence       | PostgreSQL + Spring Data JPA    | JSONB pour champs dynamiques, schéma par tenant |
| Génération PDF    | Flying Saucer + OpenPDF + jsoup | LGPL, sans licence commerciale                  |
| Sécurité          | Spring Security + OAuth2 Client | BFF intégré, provider-agnostique                |
| Frontend          | Vue 3 + Vite + PrimeVue         | SPA, composants métier riches                   |
| State management  | Pinia                           | Store réactif Vue 3                             |
| BPMN viewer       | bpmn-js                         | Rendu fidèle, overlays d'état                   |
| Stockage fichiers | MinIO                           | Compatible S3, self-hosted, Apache 2.0          |
| Auth server       | Keycloak                        | Self-hosted, OIDC/OAuth2, LDAP                  |

### Licences — aucune contrainte commerciale

Toutes les librairies retenues sont sous licences libres (Apache 2.0, LGPL, MIT) compatibles avec un modèle SaaS commercial fermé. iText 7 (AGPL) a été explicitement écarté pour cette raison.

------

## Modèle de déploiement

L'application est conçue pour un déploiement **SaaS mutualisé** — une instance unique sert plusieurs organisations. L'isolation est garantie au niveau PostgreSQL (schéma dédié par tenant). Le déploiement cible des environnements conteneurisés (Docker / Kubernetes).

```
┌─────────────────────────────────────┐
│  Instance Spring Boot               │
│  (BFF + API + Flowable + PDF)       │
└──────┬──────────────┬───────────────┘
       │              │
       ▼              ▼
┌────────────┐  ┌──────────┐  ┌──────────────┐
│ PostgreSQL │  │  MinIO   │  │   Keycloak   │
│ tenant_a   │  │ (S3)     │  │ (OIDC/OAuth2)│
│ tenant_b   │  │          │  │              │
│ tenant_n   │  │          │  │              │
└────────────┘  └──────────┘  └──────────────┘
```

------

## Documentation technique

La documentation d'architecture est disponible dans le répertoire `analysis doc` :

| Fichier                                                      | Contenu                              |
| ------------------------------------------------------------ | ------------------------------------ |
| [00-index.md](analysis%20doc/00-index.md)                    | Vue d'ensemble et dépendances        |
| [01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md) | Ports & Adapters, structure packages |
| [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md)      | Isolation schéma PostgreSQL          |
| [03-authentification.md](analysis%20doc/03-authentification.md) | OAuth2/OIDC, BFF, providers          |
| [04-dossiers.md](analysis%20doc/04-dossiers.md)              | Entité métier centrale               |
| [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md) | Flowable, BPMN, event bus            |
| [06-connecteurs.md](analysis%20doc/06-connecteurs.md)        | Système de connecteurs extensible    |
| [07-generation-documents.md](analysis%20doc/07-generation-documents.md) | Stack PDF, CSS print, performances   |
| [08-templates-documents.md](analysis%20doc/08-templates-documents.md) | Versionning, variants, résolution    |
| [09-notifications.md](analysis%20doc/09-notifications.md)    | Email, webhook, SSE                  |
| [10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md) | BPMN.js, historique, réassignation   |
| [11-frontend.md](analysis%20doc/11-frontend.md)              | Vue 3, PrimeVue, Pinia               |
| [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md) | RBAC, rétention, conformité RGPD     |

------

## Statut

> Projet en phase de conception — architecture définie, développement à démarrer.
>  Activité indépendante complémentaire.
