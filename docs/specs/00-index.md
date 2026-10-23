# Architecture technique — Moteur de flux métier & génération de documents

> Projet : activité indépendante complémentaire  
> Stack de base : Java 25 LTS · Spring Boot 4.1.x · PostgreSQL · Vue 3  
> Build : mono-module Maven · frontend embarqué (`static/`) — décision n° 27
>
> **Revues :** pré-implémentation (docs, points 1–39) —
> [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md) ;
> post-implémentation alignement × sécurité (2026-08-25, points 40–46) —
> [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md).

---

## Fonctionnalités

| # | Fonctionnalité | Fichier |
|---|---|---|
| 01 | Architecture hexagonale — Ports & Adapters | [01-architecture-hexagonale.md](01-architecture-hexagonale.md) |
| 02 | Multi-tenant | [02-multi-tenant.md](02-multi-tenant.md) |
| 03 | Authentification — OAuth2/OIDC + BFF | [03-authentification.md](03-authentification.md) |
| 04 | Dossiers — entité métier centrale | [04-dossiers.md](04-dossiers.md) |
| 05 | Moteur de workflow — Flowable | [05-workflow-engine.md](05-workflow-engine.md) |
| 06 | Système de connecteurs | [06-connecteurs.md](06-connecteurs.md) |
| 07 | Génération de documents PDF | [07-generation-documents.md](07-generation-documents.md) |
| 08 | Templates de documents | [08-templates-documents.md](08-templates-documents.md) |
| 09 | Notifications | [09-notifications.md](09-notifications.md) |
| 10 | Visualisation des instances de workflow | [10-visualisation-workflow.md](10-visualisation-workflow.md) |
| 11 | Frontend — Vue 3 + OpenVue | [11-frontend.md](11-frontend.md) |
| 12 | Autorisation (RBAC) & conformité des données | [12-autorisation-conformite.md](12-autorisation-conformite.md) |
| 13 | Modèle physique de données consolidé | [13-modele-physique.md](13-modele-physique.md) |
| 14 | Construction/édition de diagrammes BPMN | [14-edition-bpmn.md](14-edition-bpmn.md) |
| 15 | Serveur MCP (Model Context Protocol) — **livré** (PR #82) | [15-serveur-mcp.md](15-serveur-mcp.md) |
| 16 | Reporting & SLA — **livré** (PR #80) | [16-reporting-sla.md](16-reporting-sla.md) |
| 17 | Édition du schéma des champs dynamiques (admin `DossierType`) — **livré** (TODO §47, PR #87) | [17-schema-champs-dynamiques.md](17-schema-champs-dynamiques.md) |
| 18 | Activation du connecteur `http-rest` en rôle SERVICE — **livré** (TODO §48, PR #95) | [18-connecteur-http-rest-service.md](18-connecteur-http-rest-service.md) |
| 19 | Assainissement de la dette technique (reliquats `DEFERRED-FEATURES.md` §1) — **livré** (TODO §49, PR #97) | [19-assainissement-dette-technique.md](19-assainissement-dette-technique.md) |
| 20 | Nouveaux connecteurs hors V1 (`scheduler`, `smtp`, `document-storage`) — **livré** (TODO §50, PR #99) | [20-connecteurs-hors-v1.md](20-connecteurs-hors-v1.md) |
| 21 | Corrélation par clé métier pour callbacks asynchrones (`http-rest`) — analyse initiale | [21-correlation-callback-async.md](21-correlation-callback-async.md) |
| 22 | Formulaires dynamiques rattachés aux tâches BPMN (`userTask`) — analyse initiale | [22-formulaires-dynamiques-taches.md](22-formulaires-dynamiques-taches.md) |
| 23 | Questions/choix utilisateur indépendants du dossier (nouveau kind `user-question`) — **livré** (TODO §55) | [23-questions-utilisateur-taches.md](23-questions-utilisateur-taches.md) |
| 24 | Extension du serveur MCP (connecteurs, association templates, questions de tâche) — analyse initiale | [24-extension-mcp-flux-metier.md](24-extension-mcp-flux-metier.md) |
| 25 | Introduction de DMN — tables de décision, moteur + éditeur intégrés — **livré** (TODO §61) | [25-introduction-dmn.md](25-introduction-dmn.md) |
| 26 | Portail externe tokenisé — **livré** (TODO §68, PR #138) ; scindé de la signature le 2026-09-23 | [26-portail-externe.md](26-portail-externe.md) |
| 27 | Inbox de notifications persistée — **livré** (TODO §66) | [27-notifications-inbox.md](27-notifications-inbox.md) |
| 28 | Outillage DMN — évaluation ad hoc, historique/rollback, tests — **livré** (TODO §67, PR #134) | [28-outillage-dmn.md](28-outillage-dmn.md) |
| 29 | Enrichissement du reporting — alerting SLA, dashboard, export BI — **livré** (TODO §65, PR #132) | [29-enrichissement-reporting.md](29-enrichissement-reporting.md) |
| 30 | Signature électronique — analyse initiale (lot distinct, après le portail) | [30-signature-electronique.md](30-signature-electronique.md) |
| 31 | Positionnement produit (D0) — intégrateurs + couche low-code, **décision actée** (TODO §70) | [31-positionnement-produit.md](31-positionnement-produit.md) |
| 32 | Modèle de champs dynamiques enrichi (D1) — analyse initiale | [32-modele-champs-enrichi.md](32-modele-champs-enrichi.md) |
| 33 | Recherche sur les champs dynamiques (D2) — analyse initiale | [33-recherche-champs-dynamiques.md](33-recherche-champs-dynamiques.md) |
| 34 | Intégrations entreprise (D3a–D3d) — analyse initiale | [34-integrations-entreprise.md](34-integrations-entreprise.md) |
| 35 | Promotion de configuration entre environnements (D4) — analyse initiale | [35-promotion-configuration.md](35-promotion-configuration.md) |
| 36 | Verrou des jobs planifiés (D5) — analyse initiale, audit d'abord | [36-verrou-jobs-planifies.md](36-verrou-jobs-planifies.md) |
| 37 | Socle MCP low-code (D6) — analyse initiale, 4 décisions actées via TODO §71 | [37-socle-mcp-low-code.md](37-socle-mcp-low-code.md) |
| 38 | Internationalisation de l'UI (D7) — analyse initiale | [38-internationalisation-ui.md](38-internationalisation-ui.md) |
| 39 | Palette métier dans l'éditeur BPMN (LC1) — analyse initiale | [39-palette-metier-bpmn.md](39-palette-metier-bpmn.md) |
| 40 | Langage d'expression métier borné (LC2) — analyse initiale | [40-langage-expression-metier.md](40-langage-expression-metier.md) |
| 41 | Assistant de conception IA (LC3) — analyse initiale | [41-assistant-conception-ia.md](41-assistant-conception-ia.md) |
| 42 | Éditeur visuel de formulaires (LC4a) — analyse initiale | [42-editeur-formulaires.md](42-editeur-formulaires.md) |
| 43 | Éditeur WYSIWYG des templates (LC4b) — analyse initiale | [43-editeur-templates-wysiwyg.md](43-editeur-templates-wysiwyg.md) |
| 44 | Concepteur de parcours compilé en BPMN (LC5) — analyse initiale | [44-concepteur-parcours.md](44-concepteur-parcours.md) |
| 45 | Scripts JS/TS en bac à sable (LC6) — analyse initiale, conditionnelle | [45-scripts-sandbox.md](45-scripts-sandbox.md) |
| 46 | Exposition MCP de la couche low-code — cadrage transverse (TODO §71) | [46-exposition-mcp-couche-low-code.md](46-exposition-mcp-couche-low-code.md) |
| 47 | PostgreSQL embarqué (zonky) — tests sans H2/Docker, base persistante démo/QA — **livré** (TODO §91) | [47-postgresql-embarque-tests-demo.md](47-postgresql-embarque-tests-demo.md) |

> Pour concevoir concrètement un diagramme BPMN pour FlowForge (conventions, référence des
> `serviceTask` disponibles, champs dynamiques, connecteurs, intervenants, génération de
> documents), voir le guide pratique [docs/guides/GUIDE-CONCEPTION-BPMN.md](../guides/GUIDE-CONCEPTION-BPMN.md) à la
> racine du dépôt — ce n'est pas un document d'analyse d'architecture, il n'a donc pas de numéro
> dans la table ci-dessus.

---

## Stack synthétique

### Backend (Spring Boot 4.1.x / Java 25 LTS)

| Composant | Librairie | Rôle |
|---|---|---|
| Workflow engine | Flowable 8.0.x | BPMN 2.0 + DMN, triggers, tasks |
| Génération PDF | Flying Saucer 10.4.x + OpenPDF + jsoup 1.23.1 | Thymeleaf → HTML → PDF |
| Sécurité | Spring Security 7 + OAuth2 Client | BFF, session httpOnly |
| Persistence | Spring Data JPA + PostgreSQL | Données métier + Flowable |
| Notifications | Spring Mail + Spring Retry | Email, webhook, SSE |
| Dossiers | Spring Data JPA + PostgreSQL JSONB | Entité centrale, champs dynamiques |

### Frontend (Vue 3 — embarqué)

| Composant | Librairie | Rôle |
|---|---|---|
| Framework | Vue 3 + Vite | SPA, build copié dans `static/` |
| UI | OpenVue | Composants métier (fork MIT de PrimeVue, PR #165) |
| State | Pinia | Store réactif |
| HTTP | Axios | Appels API (même origine) |
| BPMN viewer | bpmn-js | Visualisation workflow |

### Infrastructure

| Composant | Technologie |
|---|---|
| Base de données | PostgreSQL (schéma par tenant) |
| Stockage fichiers | Port S3-compatible / filesystem (MinIO possible en local) |
| Auth server | Keycloak 26.7.x (ou provider OIDC externe) |
| Build | Maven mono-module (ArchUnit pour la règle domaine) |

---

## Dépendances Maven complètes

```xml
<!-- Workflow -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>8.0.x</version>
</dependency>

<!-- Génération PDF -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf</artifactId>
    <version>10.4.x</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.23.1</version>
</dependency>

<!-- Templates -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>

<!-- Sécurité / BFF -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- Persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- Notifications -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<!-- Règle d'architecture (tests) -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

**Frontend (npm) — build puis copie dans `src/main/resources/static/` :**

```bash
npm install bpmn-js
```

---

## Annexe — fonctionnalités livrées et documents de travail archivés

Les documents de travail `dev-lifecycle` (`docs/ai/`) des fonctionnalités livrées ont été retirés de la branche
principale lors de la remédiation (TODO §95, D-REMED-DOCS-AI). Ce tableau indique, pour chacune, la spec qui
décrit son état actuel, et un permalien vers ses documents de travail au commit `e91aa7b` de `main`.

| Fonctionnalité | Démarrée le | Spec de référence | Documents de travail |
|---|---|---|---|
| `admin-connector-form-schema` | 2026-09-08 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-08-feature-admin-connector-form-schema.md) (7 documents) |
| `architecture-hexagonale` | 2026-07-31 | [01-architecture-hexagonale](01-architecture-hexagonale.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-architecture-hexagonale.md) (7 documents) |
| `assainissement-dette-technique` | 2026-09-01 | [19-assainissement-dette-technique](19-assainissement-dette-technique.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-01-feature-assainissement-dette-technique.md) (7 documents) |
| `authentification` | 2026-07-31 | [03-authentification](03-authentification.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-authentification.md) (7 documents) |
| `autorisation-conformite` | 2026-07-31 | [12-autorisation-conformite](12-autorisation-conformite.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-autorisation-conformite.md) (7 documents) |
| `connecteur-callback` | 2026-09-02 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-02-feature-connecteur-callback.md) (7 documents) |
| `connecteur-http-rest-service` | 2026-08-31 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-31-feature-connecteur-http-rest-service.md) (7 documents) |
| `connecteurs` | 2026-07-31 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-connecteurs.md) (7 documents) |
| `connecteurs-cible` | 2026-09-14 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-14-feature-connecteurs-cible.md) (7 documents) |
| `connecteurs-hors-v1` | 2026-09-01 | [20-connecteurs-hors-v1](20-connecteurs-hors-v1.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-01-feature-connecteurs-hors-v1.md) (7 documents) |
| `contrat-api-client` | 2026-08-21 | [27-notifications-inbox](27-notifications-inbox.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-21-feature-contrat-api-client.md) (7 documents) |
| `d1-modele-champs-enrichi` | 2026-09-30 | [32-modele-champs-enrichi](32-modele-champs-enrichi.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-d1-modele-champs-enrichi.md) (5 documents) |
| `d1-regles-champs` | 2026-10-01 | [32-modele-champs-enrichi](32-modele-champs-enrichi.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d1-regles-champs.md) (4 documents) |
| `d2-recherche-champs-dynamiques` | 2026-10-01 | [33-recherche-champs-dynamiques](33-recherche-champs-dynamiques.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d2-recherche-champs-dynamiques.md) (3 documents) |
| `d3a-oauth2-client-credentials` | 2026-09-30 | [34-integrations-entreprise](34-integrations-entreprise.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-d3a-oauth2-client-credentials.md) (5 documents) |
| `d3b-mapping-reponse-connecteur` | 2026-10-01 | [34-integrations-entreprise](34-integrations-entreprise.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d3b-mapping-reponse-connecteur.md) (4 documents) |
| `d3cd-email-entrant-bus-azure` | 2026-10-01 | [34-integrations-entreprise](34-integrations-entreprise.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d3cd-email-entrant-bus-azure.md) (4 documents) |
| `d4-promotion-configuration` | 2026-10-01 | [35-promotion-configuration](35-promotion-configuration.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d4-promotion-configuration.md) (4 documents) |
| `d5-verrou-jobs-planifies` | 2026-10-01 | [36-verrou-jobs-planifies](36-verrou-jobs-planifies.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d5-verrou-jobs-planifies.md) (4 documents) |
| `d6-socle-mcp-low-code` | 2026-10-01 | [37-socle-mcp-low-code](37-socle-mcp-low-code.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d6-socle-mcp-low-code.md) (4 documents) |
| `deploiement-bpmn` | 2026-08-22 | [05-workflow-engine](05-workflow-engine.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-22-feature-deploiement-bpmn.md) (7 documents) |
| `derive-documentaire` | 2026-08-25 | [12-autorisation-conformite](12-autorisation-conformite.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-25-feature-derive-documentaire.md) (7 documents) |
| `dossiers` | 2026-07-31 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-dossiers.md) (7 documents) |
| `durcissement-donnees-sensibles` | 2026-08-21 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-21-feature-durcissement-donnees-sensibles.md) (7 documents) |
| `durcissement-securite-post-revue` | 2026-08-25 | [REVUE-ALIGNEMENT-SECURITE](../analyses/REVUE-ALIGNEMENT-SECURITE.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-25-feature-durcissement-securite-post-revue.md) (7 documents) |
| `edition-bpmn` | 2026-08-24 | [14-edition-bpmn](14-edition-bpmn.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-24-feature-edition-bpmn.md) (7 documents) |
| `edition-champs-dossier` | 2026-08-18 | [17-schema-champs-dynamiques](17-schema-champs-dynamiques.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-18-feature-edition-champs-dossier.md) (7 documents) |
| `elements-dossier` | 2026-08-16 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-16-feature-elements-dossier.md) (7 documents) |
| `enrichissement-reporting` | 2026-09-21 | [29-enrichissement-reporting](29-enrichissement-reporting.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-21-feature-enrichissement-reporting.md) (7 documents) |
| `extension-mcp-flux-metier` | 2026-09-06 | [24-extension-mcp-flux-metier](24-extension-mcp-flux-metier.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md) (7 documents) |
| `fiabilisation-tests-plateforme` | 2026-08-22 | [02-multi-tenant](02-multi-tenant.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-22-feature-fiabilisation-tests-plateforme.md) (7 documents) |
| `formulaires-dynamiques-taches` | 2026-09-03 | [00-index](00-index.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-03-feature-formulaires-dynamiques-taches.md) (7 documents) |
| `frontend` | 2026-07-31 | [00-index](00-index.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-frontend.md) (7 documents) |
| `generation-documents` | 2026-08-03 | [07-generation-documents](07-generation-documents.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-03-feature-generation-documents.md) (7 documents) |
| `headers-http-globaux` | 2026-09-20 | [03-authentification](03-authentification.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-20-feature-headers-http-globaux.md) (7 documents) |
| `i18n-interface` | 2026-10-01 | [38-internationalisation-ui](38-internationalisation-ui.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-i18n-interface.md) (5 documents) |
| `inbox-notifications-persistee` | 2026-09-22 | [27-notifications-inbox](27-notifications-inbox.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-22-feature-inbox-notifications-persistee.md) (7 documents) |
| `incidents-visibles-dossier` | 2026-10-18 | [15-serveur-mcp](15-serveur-mcp.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-18-feature-incidents-visibles-dossier.md) (7 documents) |
| `introduction-dmn` | 2026-09-11 | [05-workflow-engine](05-workflow-engine.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-11-feature-introduction-dmn.md) (7 documents) |
| `lc1-suite-palette-dmn` | 2026-09-30 | [28-outillage-dmn](28-outillage-dmn.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-lc1-suite-palette-dmn.md) (5 documents) |
| `lc2-langage-expression-metier` | 2026-09-30 | [40-langage-expression-metier](40-langage-expression-metier.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-lc2-langage-expression-metier.md) (5 documents) |
| `lc3-assistant-conception` | 2026-10-01 | [41-assistant-conception-ia](41-assistant-conception-ia.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-lc3-assistant-conception.md) (4 documents) |
| `lc4a-concepteur-formulaires` | 2026-10-01 | [42-editeur-formulaires](42-editeur-formulaires.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-lc4a-concepteur-formulaires.md) (4 documents) |
| `lc4b-editeur-visuel-templates` | 2026-10-01 | [43-editeur-templates-wysiwyg](43-editeur-templates-wysiwyg.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-lc4b-editeur-visuel-templates.md) (4 documents) |
| `masquage-donnees-sensibles-logs-webhooks` | 2026-08-24 | [27-notifications-inbox](27-notifications-inbox.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md) (7 documents) |
| `mcp-ecritures-metier` | 2026-09-08 | [15-serveur-mcp](15-serveur-mcp.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-08-feature-mcp-ecritures-metier.md) (7 documents) |
| `modele-physique-consolide` | 2026-08-22 | [13-modele-physique](13-modele-physique.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-22-feature-modele-physique-consolide.md) (7 documents) |
| `multi-tenant` | 2026-07-31 | [02-multi-tenant](02-multi-tenant.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-multi-tenant.md) (7 documents) |
| `notifications` | 2026-07-31 | [09-notifications](09-notifications.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-notifications.md) (7 documents) |
| `notifier-dossier-et-brouillons` | 2026-10-16 | [15-serveur-mcp](15-serveur-mcp.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-16-feature-notifier-dossier-et-brouillons.md) (7 documents) |
| `observabilite` | 2026-08-20 | [16-reporting-sla](16-reporting-sla.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-20-feature-observabilite.md) (7 documents) |
| `openapi-pagination-admin` | 2026-08-19 | [11-frontend](11-frontend.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-19-feature-openapi-pagination-admin.md) (7 documents) |
| `outillage-dmn` | 2026-09-22 | [28-outillage-dmn](28-outillage-dmn.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-22-feature-outillage-dmn.md) (7 documents) |
| `pagination-dossiers` | 2026-08-13 | [15-serveur-mcp](15-serveur-mcp.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-13-feature-pagination-dossiers.md) (7 documents) |
| `palette-metier-bpmn` | 2026-09-29 | [00-index](00-index.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-29-feature-palette-metier-bpmn.md) (7 documents) |
| `parcours-concepteur` | 2026-10-01 | [44-concepteur-parcours](44-concepteur-parcours.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-parcours-concepteur.md) (5 documents) |
| `patch-dossier-fields` | 2026-08-15 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-15-feature-patch-dossier-fields.md) (7 documents) |
| `portail-externe` | 2026-09-23 | [26-portail-externe](26-portail-externe.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-23-feature-portail-externe.md) (7 documents) |
| `preview-documents` | 2026-08-17 | [08-templates-documents](08-templates-documents.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-17-feature-preview-documents.md) (7 documents) |
| `provisioning-tenant` | 2026-09-08 | [02-multi-tenant](02-multi-tenant.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-08-feature-provisioning-tenant.md) (7 documents) |
| `purge-stockage` | 2026-08-19 | [12-autorisation-conformite](12-autorisation-conformite.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-19-feature-purge-stockage.md) (7 documents) |
| `questions-utilisateur-taches` | 2026-09-05 | [00-index](00-index.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-05-feature-questions-utilisateur-taches.md) (7 documents) |
| `relations-dossiers` | 2026-08-14 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-14-feature-relations-dossiers.md) (7 documents) |
| `reporting-sla` | 2026-08-26 | [16-reporting-sla](16-reporting-sla.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-26-feature-reporting-sla.md) (7 documents) |
| `retention-dossier-type` | 2026-08-19 | [12-autorisation-conformite](12-autorisation-conformite.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-19-feature-retention-dossier-type.md) (7 documents) |
| `revue-adversariale-securite` | 2026-09-07 | [06-connecteurs](06-connecteurs.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-07-feature-revue-adversariale-securite.md) (7 documents) |
| `scan-antivirus-clamav` | 2026-09-20 | [04-dossiers](04-dossiers.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-20-feature-scan-antivirus-clamav.md) (7 documents) |
| `schema-champs-dynamiques` | 2026-08-28 | [17-schema-champs-dynamiques](17-schema-champs-dynamiques.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-28-feature-schema-champs-dynamiques.md) (7 documents) |
| `serveur-mcp` | 2026-08-27 | [15-serveur-mcp](15-serveur-mcp.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-27-feature-serveur-mcp.md) (7 documents) |
| `signature-electronique` | 2026-09-29 | [30-signature-electronique](30-signature-electronique.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-29-feature-signature-electronique.md) (7 documents) |
| `templates-documents` | 2026-07-31 | [08-templates-documents](08-templates-documents.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-templates-documents.md) (7 documents) |
| `visualisation-workflow` | 2026-07-31 | [10-visualisation-workflow](10-visualisation-workflow.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-visualisation-workflow.md) (7 documents) |
| `workflow-engine` | 2026-07-31 | [05-workflow-engine](05-workflow-engine.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-workflow-engine.md) (7 documents) |
| `zonky-embedded-postgres` | 2026-10-02 | [47-postgresql-embarque-tests-demo](47-postgresql-embarque-tests-demo.md) | [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-02-feature-zonky-embedded-postgres.md) (5 documents) |
