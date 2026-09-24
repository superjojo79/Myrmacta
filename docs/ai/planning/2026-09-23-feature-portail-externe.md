---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `portail-externe`

> TODO §68. Plan initial (Phase 4, 2026-09-24) dérivé des
> [requirements](../requirements/2026-09-23-feature-portail-externe.md) (`reviewed`), du
> [design](../design/2026-09-23-feature-portail-externe.md) (`reviewed`) et des scénarios du
> [testing](../testing/2026-09-23-feature-portail-externe.md). Toutes les tâches `not started`.
> **Phase 5 livrée le 2026-09-24 — toutes les tâches `done`** (M1→M13 enchaînés, voir
> [implementation](../implementation/2026-09-23-feature-portail-externe.md)). Implémentation en TDD (test rouge → code → vert), `./mvnw verify` avant toute clôture de
> milestone backend, `npm run test:all` pour les milestones frontend.
>
> **Prérequis levé avant ce plan :** TODO §69 (surfaces UEL du BPMN hors allowlist) corrigé le
> 2026-09-24 — le parser portail n'a plus à imposer le caractère littéral du `formKey`, seulement
> sa forme.

## Milestones

| # | Milestone | Dépend de | Parallélisable avec |
|---|---|---|---|
| M1 | Fondation domaine + persistance + acteur externe | — | — |
| M2 | Parser BPMN + garde de déploiement | M1 | M8 |
| M3 | Émission et invalidation (listener moteur) | M1, M2 | M4 |
| M4 | Jeton et envoi de l'invitation + configuration | M1 | M3 |
| M5 | Accès public : chaîne de sécurité, lecture du contexte | M3, M4 | — |
| M6 | Dépôt de pièces et soumission | M5 | M7 |
| M7 | Expiration + gestion instructeur (API) | M3, M4 | M6 |
| M8 | Purge RGPD (liens + réponses) | M1 | M2 → M7 |
| M9 | Frontend portail (build séparé) | contrat API de M5–M6 | M10 |
| M10 | Frontend interne (panneau, éditeur BPMN, inbox) | M7 | M9 |
| M11 | Playwright E2E + non-régression | M9, M10 | — |
| M12 | Documentation | M1–M10 | M11 |
| M13 | Vérification globale | tout | — |

## Task Breakdown

### M1 — Fondation domaine + persistance + acteur externe

- [x] **T1.1** Modèles domaine `PortalLink`, `PortalLinkStatus`, `PortalLinkStatusReason`,
  `PortalRecipientSource`, `PortalTaskDefinition` (records, invariants en constructeur).
  *Validation :* tests unitaires des invariants.
- [x] **T1.2** Port `PortalLinkRepository` + `PortalLinkJpaEntity` + `JpaPortalLinkAdapter` ;
  migration `025-portal-link.yaml` + SQL PostgreSQL/H2 de l'index unique partiel (patron `002`).
  *Validation :* test d'adaptateur JPA ; **T-MANAGE-5** (unicité au niveau base).
- [x] **T1.3** `AccessContext.External(UUID linkId)` ; mise à jour de tous les `switch` scellés
  (le compilateur les liste) avec refus ; `DossierFieldRedactor` traite `External` comme non
  privilégié. *Validation :* **T-ACTOR-1**, ArchUnit vert.
- [x] **T1.4** Propriétés `app.portal.*` (`PortalProperties`, `@Validated`) + fail-fast si
  `enabled` sans `public-base-url` ; port `PortalAvailabilityPort`.
  *Validation :* **T-SEC-3**.

### M2 — Parser BPMN + garde de déploiement

- [x] **T2.1** `FlowableBpmnParserAdapter` : validation des attributs `portalRecipient` /
  `portalLinkTtlDays` (forme `formKey`, syntaxe de source, bornes, pas d'assignation) ;
  `BpmnParserPort.extractPortalTasks`. *Validation :* **T-PARSE-1 à 6**.
- [x] **T2.2** `DeployBpmnProcessUseCaseImpl` rejette une tâche externe si portail désactivé ;
  brouillon permissif. *Validation :* **T-DEPLOY-1, T-DEPLOY-2** (REST + `DeployWorkflowToolTest`).

### M3 — Émission et invalidation

- [x] **T3.1** `PortalRecipientResolverPort` + adaptateur (champ dossier ; variable par nom via
  `RuntimeService.getVariable`, jamais UEL) ; validation du format e-mail.
  *Validation :* **T-ISSUE-7**.
- [x] **T3.2** `IssuePortalLinkUseCaseImpl` (`PENDING_SEND` / `NOT_SENT` + raison + notification).
  *Validation :* **T-ISSUE-2 à 5** (unitaires).
- [x] **T3.3** `PortalTaskEventListener` (`TASK_CREATED`, `TASK_COMPLETED`, `ENTITY_DELETED`),
  cache des `PortalTaskDefinition` par définition de process, conditionnel.
  *Validation :* **T-ISSUE-1, T-ISSUE-6, T-ISSUE-8** (vrai moteur, `portal-smoke.bpmn20.xml`).
- [x] **T3.4** `InvalidatePortalLinksForTaskUseCaseImpl`. *Validation :* **T-INVAL-1, T-INVAL-2**.

### M4 — Jeton et envoi

- [x] **T4.1** `PortalTokenPort` + adaptateur (`SecureRandom` 32 octets, base64url, SHA-256).
  *Validation :* unitaires (longueur, alphabet, unicité statistique, hash déterministe).
- [x] **T4.2** `PortalInviteMessagePort` (sujet/corps texte FR fixes, lien à fragment).
- [x] **T4.3** `SendPortalInvitesUseCaseImpl` + `PortalInviteSenderJob` (retries, nouveau jeton
  par tentative, `NOT_SENT/SEND_FAILED`). *Validation :* **T-SEND-1, T-SEND-2**.
- [x] **T4.4** Masquage de l'adresse dans les logs ; test de non-fuite du jeton (base + logs).
  *Validation :* **T-SEND-3, T-SEND-4**.

### M5 — Accès public

- [x] **T5.1** `PortalSecurityConfig` (`@Order(3)`, conditionnel, stateless, filtre de taille,
  en-têtes A4) ; renumérotation webhook → 4, BFF → 5 et commentaires associés.
  *Validation :* **T-SEC-1, T-SEC-2, T-SEC-4**, suites de sécurité existantes vertes.
- [x] **T5.2** `PortalPreAuthRateLimiter` + journalisation/compteur des refus.
  *Validation :* **T-ACCESS-4, T-ACCESS-5**.
- [x] **T5.3** `ResolvePortalAccessUseCaseImpl` + `GetPortalTaskUseCaseImpl` + `PortalController`
  `GET /api/portal/task` (réponse neutre unique). *Validation :* **T-ACCESS-1, 2, 3, 6**.
- [x] **T5.4** `SpaFallbackConfig` : repli `/portal/**` sur `static/portal/index.html`.
  *Validation :* **T-SEC-5**.

### M6 — Dépôt et soumission

- [x] **T6.1** `AddPortalAttachmentUseCaseImpl` + endpoint multipart (`@Hidden`) ; filtrage des
  pièces par `addedBy`. *Validation :* **T-UPLOAD-1 à 4, T-IDOR-1**.
- [x] **T6.2** Extraction de `UserQuestionAnswerRecorder` depuis `CompleteTaskUseCaseImpl`
  (refactor sans changement de comportement). *Validation :* **T-SUBMIT-5** (suite existante
  verte **avant** d'ajouter le portail).
- [x] **T6.3** `SubmitPortalTaskUseCaseImpl` + endpoint (verrou optimiste, notification).
  *Validation :* **T-SUBMIT-1 à 4**.

### M7 — Expiration + gestion instructeur

- [x] **T7.1** `ExpirePortalLinksUseCaseImpl` + `PortalLinkExpiryJob` (idempotent).
  *Validation :* **T-EXPIRY-1**.
- [x] **T7.2** `ManagePortalLinkUseCaseImpl` (envoyer/renvoyer/révoquer, garde H6).
  *Validation :* **T-MANAGE-1 à 4**.
- [x] **T7.3** `PortalLinkAdminController` + golden OpenAPI. *Validation :* **T-OPENAPI-1**.

### M8 — Purge RGPD

- [x] **T8.1** `deleteAllByDossierId` sur `PortalLinkRepository` et `TaskQuestionAnswerRepository`,
  appelés par `ProcessPurgeOutboxUseCaseImpl`. *Validation :* **T-PURGE-1**.

### M9 — Frontend portail (build séparé)

- [x] **T9.1** `vite.portal.config.ts`, `frontend/portal/index.html`, sortie
  `static/portal/`, script `npm run build` enchaînant les deux builds ; règle ESLint
  `no-restricted-imports` sur `portal/**`. *Validation :* **V-PORTAL-5**, build Maven complet.
- [x] **T9.2** Client HTTP portail (fetch, en-tête `X-Portal-Token`, pas de XSRF) + `PortalApp.vue`
  (lecture/effacement du fragment, écran neutre). *Validation :* **V-PORTAL-1, V-PORTAL-2**.
- [x] **T9.3** Formulaire (`DynamicFields.vue` réutilisé — composant pur, vérifié) + dépôt +
  confirmation. *Validation :* **V-PORTAL-3, V-PORTAL-4**.

### M10 — Frontend interne

- [x] **T10.1** Panneau « Tiers externes » du détail dossier + client API.
  *Validation :* **V-INT-1**.
- [x] **T10.2** `flowablePropertiesProvider` : 2 champs texte. *Validation :* **V-INT-2**.
- [x] **T10.3** Tiroir de notifications : 3 nouveaux types. *Validation :* **V-INT-3**.

### M11 — Playwright

- [x] **T11.1** Configuration Playwright pour servir la page portail (build portail dans le
  serveur de preview) + mocks `/api/portal/**`. *Validation :* **E2E-PORTAL-1, E2E-PORTAL-2**.
- [x] **T11.2** Scénarios instructeur et éditeur BPMN. *Validation :* **E2E-PORTAL-3, 4**.
- [x] **T11.3** Suite complète. *Validation :* **E2E-REG**.

### M12 — Documentation

- [x] **T12.1** `GUIDE-UTILISATEUR.md` (panneau instructeur, notifications),
  `GUIDE-CONCEPTION-BPMN.md` (tâche externe), `GETTING-STARTED.md` + `.env.example`
  (`APP_PORTAL_*`), `DEPLOIEMENT.md` (routes publiques au reverse-proxy, recommandation ClamAV —
  R-SCAN-FAIL-OPEN), `CHANGELOG.md`, TODO §68 « Impacte ».

### M13 — Vérification globale

- [x] **T13.1** `./mvnw verify` complet (Jacoco, ArchUnit), `npm run test:all`, smoke manuel SMTP
  si un outil de capture est disponible ; mise à jour des cases du document de tests.

## Dependencies

- **M6 dépend de T6.2** : le refactor du validateur `user-question` doit être vert sur la suite
  existante avant tout code portail qui l'utilise (évite de mêler refactor et nouveauté).
- **M9 dépend du contrat JSON de M5/M6**, pas de leur implémentation : le front peut démarrer sur
  des mocks dès que T5.3/T6.3 ont figé les DTO.
- **Coutures partagées avec `outillage-dmn` (PR #134)** : aucune dans le code (le portail ne touche
  ni `infrastructure.mcp`, ni `/admin/dmn-editor`). Conflits textuels attendus sur
  `TODO.md`/`FILE-ATTENTE.md`/`ROADMAP.md`/`CHANGELOG.md` uniquement.
- **Couture partagée avec TODO §69** : `FlowableBpmnParserAdapter` — §69 est déjà livré sur cette
  branche, M2 s'appuie dessus.

## Timeline & Estimates

| Bloc | Effort estimé |
|---|---|
| M1–M2 | 1 j |
| M3–M4 | 1,5 j |
| M5–M7 | 2 j |
| M8 | 0,25 j |
| M9–M10 | 1,5 j |
| M11–M13 | 1 j |
| **Total** | **~7 j** de développement assisté |

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Renumérotation des chaînes Spring Security casse une chaîne existante | T5.1 rejoue toutes les suites de sécurité existantes (webhook, callback, CSP, M2M, BFF) avant d'ajouter le portail |
| Listener `TASK_CREATED` ralentit chaque création de tâche | Cache des définitions portail par `processDefinitionId` ; sortie immédiate si la définition n'a aucune tâche externe |
| Build Vite double allonge la CI | Build portail minimal (quelques composants PrimeVue) ; mesure du temps au T9.1 |
| Jacoco : nouveau code sous le seuil | Tests écrits avant le code (TDD) ; contrôle par classe en M13 |
| Conflits avec la PR #134 (DMN) | Fichiers de suivi seulement ; merge de `main` avant PR |
| `public-base-url` mal configurée en prod (lien inutilisable) | Fail-fast au démarrage + section `DEPLOIEMENT.md` + smoke manuel |

## Resources Needed

- JDK 21 suffit dans ce conteneur (`-Dmaven.compiler.release=21`, vérifié le 2026-09-24) ;
  Chromium pré-installé pour Playwright.
- Pas de Docker ici : `T-MANAGE-5` côté PostgreSQL et le smoke SMTP réel restent en CI / manuel.

## Couverture scénarios → tâches

| Scénario | Tâche(s) |
|---|---|
| T-PARSE-1…6 | T2.1 |
| T-DEPLOY-1, 2 | T2.2 |
| T-ISSUE-1, 6, 8 | T3.3 |
| T-ISSUE-2…5 | T3.2 |
| T-ISSUE-7 | T3.1 |
| T-SEND-1, 2 | T4.3 |
| T-SEND-3, 4 | T4.4 |
| T-ACCESS-1, 2, 3, 6 | T5.3 |
| T-ACCESS-4, 5 | T5.2 |
| T-UPLOAD-1…4, T-IDOR-1 | T6.1 |
| T-SUBMIT-1…4 | T6.3 |
| T-SUBMIT-5 | T6.2 |
| T-INVAL-1, 2 | T3.4 |
| T-EXPIRY-1 | T7.1 |
| T-MANAGE-1…4 | T7.2 |
| T-MANAGE-5 | T1.2 |
| T-ACTOR-1 | T1.3 |
| T-SEC-1, 2, 4 | T5.1 |
| T-SEC-3 | T1.4 |
| T-SEC-5 | T5.4 |
| T-PURGE-1 | T8.1 |
| T-OPENAPI-1 | T7.3 |
| V-PORTAL-1, 2 | T9.2 |
| V-PORTAL-3, 4 | T9.3 |
| V-PORTAL-5 | T9.1 |
| V-INT-1 | T10.1 |
| V-INT-2 | T10.2 |
| V-INT-3 | T10.3 |
| E2E-PORTAL-1, 2 | T11.1 |
| E2E-PORTAL-3, 4 | T11.2 |
| E2E-REG | T11.3 |

**65 scénarios (52 backend, 8 Vitest, 5 Playwright), tous rattachés à au moins une tâche ; aucune tâche sans scénario hors T4.2
(couvert par T-SEND-1) et T12.1 (documentation).**
