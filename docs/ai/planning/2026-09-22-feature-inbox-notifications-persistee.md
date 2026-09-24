---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

> Séquencement en milestones `M1`…`M12` (même convention que TODO §61 `introduction-dmn`), TDD par
> défaut (`tdd` + `verify`). Chaque tâche référence les scénarios de
> [testing](../testing/2026-09-22-feature-inbox-notifications-persistee.md) qu'elle couvre — tout
> scénario `UT-NOTIF-*`/`IT-NOTIF-*`/`E2E-NOTIF-*` apparaît dans au moins une tâche ci-dessous
> (vérifié en fin de document, § *Couverture scénarios → tâches*).

## Milestones

- [ ] **M1** — Modèle domaine + ports + persistance `user_notification` (fondation, bloquant tout le reste)
- [ ] **M2** — `SendNotificationUseCaseImpl` écrit l'inbox + correctif contrat payload `WorkflowStateEventListener`
- [ ] **M3** — REST `NotificationInboxController` + golden OpenAPI
- [ ] **M4** — `NotificationProperties.Inbox` + `NotificationInboxRetentionJob` (purge)
- [ ] **M5** — Producteur : tâche réassignée (`WorkflowLinkVisualizationUseCaseImpl`)
- [ ] **M6** — Producteur : tâche terminée par un tiers (`CompleteTaskUseCaseImpl`)
- [ ] **M7** — Producteur : document généré (`AddDossierElementUseCaseImpl`)
- [ ] **M8** — Frontend : client API + réécriture `stores/notifications.ts`
- [ ] **M9** — Frontend : `NotificationDrawer.vue` en PrimeVue
- [ ] **M10** — Playwright E2E (dont non-régression producteurs existants)
- [ ] **M11** — Documentation (`GUIDE-UTILISATEUR.md`, `README.md`/`analysis doc` si pertinent)
- [ ] **M12** — Vérification globale finale (`./mvnw verify`, Vitest, Playwright, revue fichier par fichier)

## Task Breakdown

### M1 — Fondation domaine/persistance

**Dépendances :** aucune (premier travail du cycle).

- [ ] **M1.1** — `domain.notification.model.UserNotificationRecord` (record, `isRead()`).
- [ ] **M1.2** — `domain.notification.port.out.UserNotificationRepository` (interface, miroir `NotificationOutboxRepository`).
- [ ] **M1.3** — `domain.notification.port.in.NotificationInboxUseCase` (interface `list`/`markRead`/`markAllRead`) + `domain.notification.usecase.NotificationInboxUseCaseImpl`.
- [ ] **M1.4** — Migration Liquibase `023-user-notification.yaml` (table + 2 index, partiel PostgreSQL/fallback H2 non filtré comme `notification_webhook`).
- [ ] **M1.5** — `infrastructure.persistence.notification.UserNotificationJpaEntity` + `SpringDataUserNotificationRepository` + `JpaUserNotificationRepositoryAdapter`.

**Validation :** UT-NOTIF-1, UT-NOTIF-2, UT-NOTIF-3, UT-NOTIF-4, UT-NOTIF-5.

### M2 — Écriture au point de couture + correctif payload

**Dépendances :** M1.

- [ ] **M2.1** — `SendNotificationUseCaseImpl` : injecte `UserNotificationRepository`, écrit `user_notification` pour le canal `BROWSER` dans la même transaction que le fan-out outbox existant.
- [ ] **M2.2** — `WorkflowStateEventListener` : ajoute `title`/`body` au payload (contrat standardisé, § design).

**Validation :** UT-NOTIF-6, UT-NOTIF-7, UT-NOTIF-8, UT-NOTIF-16.

**Risque signalé** : M2.1 touche un chemin déjà en production (2 producteurs BROWSER existants) — non-régression stricte sur EMAIL/WEBHOOK exigée par UT-NOTIF-7 avant de continuer.

### M3 — REST

**Dépendances :** M1, M2 (les tests d'intégration ont besoin de données réellement écrites).

- [ ] **M3.1** — `infrastructure.web.notification.NotificationInboxController` (`GET`, `PATCH /{id}/read`, `POST /read-all`), `UserNotificationResponse` DTO.
- [ ] **M3.2** — `ListSorts.USER_NOTIFICATIONS` + branchement `PageQueryParser`.
- [ ] **M3.3** — Golden OpenAPI régénéré.

**Validation :** IT-NOTIF-1, IT-NOTIF-2, IT-NOTIF-3, IT-NOTIF-4, IT-NOTIF-5, IT-NOTIF-7, UT-NOTIF-9, UT-NOTIF-10.

### M4 — Rétention

**Dépendances :** M1.

- [ ] **M4.1** — `NotificationProperties` : nouveau record `Inbox(Duration retention, Duration pollDelay)`, défaut 30j/1j.
- [ ] **M4.2** — `NotificationInboxRetentionJob` (`@Scheduled`, miroir `WorkflowRetentionJob`).

**Validation :** UT-NOTIF-17, UT-NOTIF-18, IT-NOTIF-6 (Testcontainers PostgreSQL, ciblé).

**Parallélisable** avec M3 (surfaces disjointes — pas de conflit sur les mêmes fichiers).

### M5 — Producteur : tâche réassignée

**Dépendances :** M2 (le canal BROWSER doit déjà écrire l'inbox).

- [ ] **M5.1** — `WorkflowLinkVisualizationUseCaseImpl` : +dépendance `SendNotificationUseCase`, envoi `TASK_REASSIGNED` après `visualizationPort.reassignTask(...)`.

**Validation :** UT-NOTIF-11, E2E-NOTIF-4.

### M6 — Producteur : tâche terminée par un tiers

**Dépendances :** M2.

- [ ] **M6.1** — `CompleteTaskUseCaseImpl` : +dépendances `DossierWorkflowLinkRepository`, `WorkflowParticipantPort`, `SendNotificationUseCase`.
- [ ] **M6.2** — Résolution du lien actif par `dossierId`, garde défensive si absent (pas d'exception propagée, § edge case).
- [ ] **M6.3** — Exclusion de `actor.userId()` de la liste des destinataires.

**Validation :** UT-NOTIF-12, UT-NOTIF-13, E2E-NOTIF-5.

### M7 — Producteur : document généré

**Dépendances :** M2.

- [ ] **M7.1** — `AddDossierElementUseCaseImpl` : +dépendances `DossierWorkflowLinkRepository`, `WorkflowParticipantPort`, `SendNotificationUseCase`.
- [ ] **M7.2** — Effet de bord limité à `type == PRODUCED_DOC` (vérifier explicitement la non-régression des 4 autres types).

**Validation :** UT-NOTIF-14, UT-NOTIF-15, E2E-NOTIF-6.

**Note de risque (déjà actée en design)** : couvre à la fois `AttachGeneratedDocumentTask` (BPMN) et `GenerateDocumentTool` (MCP) puisque les deux passent par `AddDossierElementUseCaseImpl.add()` — UT-NOTIF-14 doit exercer les deux contextes (`AccessContext.system` et `AccessContext.User`), pas seulement un des deux producteurs.

**M5/M6/M7 sont parallélisables entre eux** (3 fichiers distincts, aucune couture partagée) une fois M2 clos — même logique que les vagues parallèles déjà pratiquées dans ce dépôt (Vague A), à condition de ne pas les découper en 3 PR séparées si le cycle reste mono-PR (à confirmer en Phase 5 selon la préférence de session, comme les cycles précédents).

### M8 — Frontend : client + store

**Dépendances :** M3 (le contrat REST doit exister).

- [ ] **M8.1** — `frontend/src/api/notifications.ts` (nouveau, miroir `admin.ts`) : `listNotifications`, `markNotificationRead`, `markAllNotificationsRead`.
- [ ] **M8.2** — `frontend/src/stores/notifications.ts` réécrit : `connect()` fait le backfill REST avant d'ouvrir le SSE ; dédoublonnage par `id` entre backfill et push live ; `markRead`/`markAllRead` appellent le backend (optimistic + rollback).
- [ ] **M8.3** — Retrait du plafond client à 100 items (remplacé par la pagination serveur).

**Validation :** E2E-NOTIF-1, E2E-NOTIF-2, E2E-NOTIF-3, UT côté Vitest sur le store (mocks `axios`/`EventSource`, pattern déjà utilisé par `stores/notifications.spec.ts` existant).

### M9 — Frontend : `NotificationDrawer.vue` en PrimeVue

**Dépendances :** M8 (le store doit exposer la nouvelle forme de données avant de réécrire le composant qui le consomme).

- [ ] **M9.1** — Réécriture en composants PrimeVue 5 : `OverlayBadge` (cloche + badge), `Popover` (tiroir), `Button`, `ScrollPanel`.
- [ ] **M9.2** — Vérification manuelle du rendu réel (premier usage PrimeVue du dépôt, § risque design) — pas seulement Vitest monté.

**Validation :** E2E-NOTIF-7, `NotificationDrawer.spec.ts` mis à jour (Vitest).

### M10 — Playwright E2E + non-régression

**Dépendances :** M5, M6, M7, M8, M9 (tout le fonctionnel doit être en place).

- [ ] **M10.1** — Implémenter E2E-NOTIF-1 à E2E-NOTIF-8.
- [ ] **M10.2** — Vérifier explicitement la non-régression des 2 producteurs déjà en production (E2E-NOTIF-8) avant de considérer le cycle fonctionnellement clos.

**Validation :** E2E-NOTIF-1 à E2E-NOTIF-8 (liste complète).

### M11 — Documentation

**Dépendances :** M9 (le comportement UI final doit être stable avant de le documenter).

- [ ] **M11.1** — `GUIDE-UTILISATEUR.md` : section notifications mise à jour (persistance, pas de nouvel écran admin donc pas de nouvelle ligne de rôles §0).
- [ ] **M11.2** — `analysis doc/27-notifications-inbox.md` : statut mis à jour (« absent » → « livré »), pour ne pas laisser un document d'analyse obsolète comme source de vérité après cette feature (même défaut que celui qui a motivé le constat corrigé en Phase 1).
- [ ] **M11.3** — `CHANGELOG.md` : entrée `[Non publié]` au moment de l'ouverture/mise à jour de la PR (pas avant, par convention).

### M12 — Vérification globale finale

**Dépendances :** tout ce qui précède.

- [ ] **M12.1** — `./mvnw verify` complet (pas d'exclusion `Test*`/`*IT.java`), Jacoco LINE/BRANCH tenus.
- [ ] **M12.2** — `npm run test` (Vitest) + `npm run test:e2e` (Playwright) complets.
- [ ] **M12.3** — Revue fichier par fichier des 8 fichiers backend modifiés/créés + 3 fichiers frontend (Phase 7 du cycle `dev-lifecycle`, doctrine H1/H1-bis du dépôt : preuve d'exécution réelle, pas seulement lecture statique, en particulier pour M6/M7 dont les gardes defensives — lien absent, type ≠ PRODUCED_DOC — sont faciles à laisser non testées).
- [ ] **M12.4** — Golden OpenAPI diff final.

## Dependencies

```
M1 → M2 → { M3, M4 } → { M5, M6, M7 } (parallélisables) → M8 → M9 → M10 → M11 → M12
                  ↳ M4 parallélisable avec M3 dès M1 clos
```

- Aucune dépendance externe nouvelle (pas de nouvelle librairie backend ; PrimeVue déjà dans `package.json`, juste jamais consommé — voir risque M9).
- Pas de dépendance à un autre cycle en cours (file produit vide au démarrage de ce cycle, TODO §66).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| M2.1 régresse les 2 producteurs BROWSER déjà en production | UT-NOTIF-7/8 avant tout producteur nouveau ; ne pas toucher au fan-out EMAIL/WEBHOOK existant |
| M6/M7 : garde défensive (lien absent) jamais réellement exercée | UT-NOTIF-13 explicite, revue fichier par fichier M12.3 dédiée à ce point |
| M7 : notification câblée sur un seul des 2 producteurs `PRODUCED_DOC` | UT-NOTIF-14 paramétré sur les deux contextes (`system` et `User`), pas un test par producteur |
| M9 : premier usage réel de PrimeVue, pas de précédent local | Vérification manuelle explicite (M9.2), pas seulement Vitest monté — E2E-NOTIF-7 dédié |
| Volume `user_notification` (30j) vs `notification_outbox` (7j) | Signalé en design comme point de vigilance volumétrique, pas un blocage — à surveiller en observabilité existante (pas de nouvelle métrique demandée ce cycle) |

## Resources Needed

- Aucune ressource externe nouvelle : mêmes outils que le reste du dépôt (`./mvnw verify`, Vitest, Playwright, Testcontainers PostgreSQL déjà configuré).
- Mode `no-worktree` sur la branche de session (cohérent avec le choix acté en Phase 1/dev-worktree).

## Couverture scénarios → tâches

Vérification exhaustive demandée par le contrat de skill (« every test-plan scenario has at least
one implementation task ») :

| Scénario | Tâche(s) |
|---|---|
| UT-NOTIF-1 à 5 | M1 |
| UT-NOTIF-6, 7, 8, 16 | M2 |
| UT-NOTIF-9, 10 | M3 |
| UT-NOTIF-17, 18 | M4 |
| UT-NOTIF-11 | M5 |
| UT-NOTIF-12, 13 | M6 |
| UT-NOTIF-14, 15 | M7 |
| IT-NOTIF-1, 2, 3, 4, 5, 7 | M3 |
| IT-NOTIF-6 | M4 |
| E2E-NOTIF-1, 2, 3 | M8, M10 |
| E2E-NOTIF-4 | M5, M10 |
| E2E-NOTIF-5 | M6, M10 |
| E2E-NOTIF-6 | M7, M10 |
| E2E-NOTIF-7 | M9, M10 |
| E2E-NOTIF-8 | M10 |

Aucun scénario orphelin.
