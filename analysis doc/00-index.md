# Architecture technique — Moteur de flux métier & génération de documents

> Projet : activité indépendante complémentaire  
> Stack de base : Java 25 LTS · Spring Boot 4.1.x · PostgreSQL · Vue 3  
> Build : mono-module Maven · frontend embarqué (`static/`) — décision n° 27
>
> **Revues :** pré-implémentation (docs, points 1–39) —
> [REVUE-PRE-IMPLEMENTATION.md](../REVUE-PRE-IMPLEMENTATION.md) ;
> post-implémentation alignement × sécurité (2026-08-25, points 40–46) —
> [REVUE-ALIGNEMENT-SECURITE.md](../REVUE-ALIGNEMENT-SECURITE.md).

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
| 11 | Frontend — Vue 3 + PrimeVue | [11-frontend.md](11-frontend.md) |
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

> Pour concevoir concrètement un diagramme BPMN pour FlowForge (conventions, référence des
> `serviceTask` disponibles, champs dynamiques, connecteurs, intervenants, génération de
> documents), voir le guide pratique [GUIDE-CONCEPTION-BPMN.md](../GUIDE-CONCEPTION-BPMN.md) à la
> racine du dépôt — ce n'est pas un document d'analyse d'architecture, il n'a donc pas de numéro
> dans la table ci-dessus.

---

## Stack synthétique

### Backend (Spring Boot 4.1.x / Java 25 LTS)

| Composant | Librairie | Rôle |
|---|---|---|
| Workflow engine | Flowable 8.0.x | BPMN 2.0 (DMN auto-config **exclue** en V1), triggers, tasks |
| Génération PDF | Flying Saucer 10.4.x + OpenPDF + jsoup 1.23.1 | Thymeleaf → HTML → PDF |
| Sécurité | Spring Security 7 + OAuth2 Client | BFF, session httpOnly |
| Persistence | Spring Data JPA + PostgreSQL | Données métier + Flowable |
| Notifications | Spring Mail + Spring Retry | Email, webhook, SSE |
| Dossiers | Spring Data JPA + PostgreSQL JSONB | Entité centrale, champs dynamiques |

### Frontend (Vue 3 — embarqué)

| Composant | Librairie | Rôle |
|---|---|---|
| Framework | Vue 3 + Vite | SPA, build copié dans `static/` |
| UI | PrimeVue | Composants métier |
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
