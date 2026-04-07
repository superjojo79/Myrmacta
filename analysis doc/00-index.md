# Architecture technique — Moteur de flux métier & génération de documents

> Projet : activité indépendante complémentaire  
> Stack de base : Java 21 · Spring Boot 3.x · PostgreSQL · Vue 3

---

## Fonctionnalités

| # | Fonctionnalité | Fichier |
|---|---|---|
| 01 | Architecture hexagonale — Ports & Adapters | [01-architecture-hexagonale.md](01-architecture-hexagonale.md) |
| 02 | Moteur de workflow — Flowable | [02-workflow-engine.md](02-workflow-engine.md) |
| 03 | Système de connecteurs | [03-connecteurs.md](03-connecteurs.md) |
| 04 | Génération de documents PDF | [04-generation-documents.md](04-generation-documents.md) |
| 05 | Templates de documents | [05-templates-documents.md](05-templates-documents.md) |
| 06 | Multi-tenant | [06-multi-tenant.md](06-multi-tenant.md) |
| 07 | Authentification — OAuth2/OIDC + BFF | [07-authentification.md](07-authentification.md) |
| 08 | Frontend — Vue 3 + PrimeVue | [08-frontend.md](08-frontend.md) |
| 09 | Notifications | [09-notifications.md](09-notifications.md) |
| 10 | Visualisation des instances de workflow | [10-visualisation-workflow.md](10-visualisation-workflow.md) |
| 11 | Dossiers — entité métier centrale | [11-dossiers.md](11-dossiers.md) |

---

## Stack synthétique

### Backend (Spring Boot 3.x / Java 21)

| Composant | Librairie | Rôle |
|---|---|---|
| Workflow engine | Flowable 7.x | BPMN 2.0 + DMN, triggers, tasks |
| Génération PDF | Flying Saucer + OpenPDF + jsoup | Thymeleaf → HTML → PDF |
| Sécurité | Spring Security + OAuth2 Client | BFF, session httpOnly |
| Persistence | Spring Data JPA + PostgreSQL | Données métier + Flowable |
| Notifications | Spring Mail + Spring Retry | Email, webhook, SSE |
| Dossiers | Spring Data JPA + PostgreSQL JSONB | Entité centrale, champs dynamiques |

### Frontend (Vue 3)

| Composant | Librairie | Rôle |
|---|---|---|
| Framework | Vue 3 + Vite | SPA |
| UI | PrimeVue | Composants métier |
| State | Pinia | Store réactif |
| HTTP | Axios | Appels API |
| BPMN viewer | bpmn-js | Visualisation workflow |

### Infrastructure

| Composant | Technologie |
|---|---|
| Base de données | PostgreSQL (schéma par tenant) |
| Stockage fichiers | MinIO (compatible S3) |
| Auth server | Keycloak (ou provider OIDC externe) |

---

## Dépendances Maven complètes

```xml
<!-- Workflow -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.x.x</version>
</dependency>

<!-- Génération PDF -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
    <version>9.9.4</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
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
```

**Frontend (npm) :**

```bash
npm install bpmn-js
```
