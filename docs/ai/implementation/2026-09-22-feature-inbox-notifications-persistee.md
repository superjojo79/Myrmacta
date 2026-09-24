---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

> Phase 7 (check implementation) — comparaison du code réellement livré (M1-M12, commits
> `3cd0d22`…`6a4a6d3` sur `claude/brave-curie-w7kocb`) contre
> [design](../design/2026-09-22-feature-inbox-notifications-persistee.md) et
> [requirements](../requirements/2026-09-22-feature-inbox-notifications-persistee.md).

## Development Setup

Aucun prérequis nouveau : mêmes outils que le reste du dépôt (`./mvnw verify`, `npm run test`,
`npm run test:e2e` avec `PLAYWRIGHT_CHROMIUM_EXECUTABLE` si besoin). Migration Liquibase
`023-user-notification.yaml` appliquée automatiquement au démarrage, aucune variable
d'environnement nouvelle (`app.notification.inbox.retention`/`poll-delay` ont des défauts
30j/1j).

## Code Structure

Suit exactement l'architecture hexagonale existante, aucun nouveau package :

- `domain.notification.model` — `UserNotificationRecord` (record, `payloadJson` en String brut,
  même convention que `NotificationOutboxRecord`), `UserNotificationNotFoundException`.
- `domain.notification.port.{in,out}` — `NotificationInboxUseCase`, `UserNotificationRepository`.
- `domain.notification.usecase` — `NotificationInboxUseCaseImpl`.
- `infrastructure.persistence.notification` — `UserNotificationJpaEntity`,
  `SpringDataUserNotificationRepository`, `JpaUserNotificationRepositoryAdapter` (miroir exact du
  triplet `notification_outbox`).
- `infrastructure.web.notification` — `NotificationInboxController` (nouveau), `UserNotificationResponse`
  (dans `NotificationApiDtos.java`, à côté des DTOs existants).
- `infrastructure.notification` — `NotificationInboxRetentionJob` (miroir `WorkflowRetentionJob`).
- `frontend/src/api/notifications.ts` (nouveau, miroir `admin.ts`).

## Implementation Notes

### Core Features

- **Persistance du canal BROWSER** — `SendNotificationUseCaseImpl.send()` écrit `user_notification`
  après le batch `notification_outbox`, uniquement si `channels` contient `BROWSER`. Le payload
  stocké est le même `Map<String, Object>` déjà masqué (D-WEBHOOK-MASK) que celui envoyé aux
  autres canaux, sérialisé sans wrapper (`NotificationPayloadJson.plain(...)`, nouvelle méthode).
- **5 producteurs BROWSER** — 2 existants retouchés pour le contrat `title`/`body`
  (`WorkflowStateEventListener`) + inchangé (`EvaluateSlaAlertsUseCaseImpl`) ; 3 nouveaux
  (`WorkflowLinkVisualizationUseCaseImpl.reassign`, `CompleteTaskUseCaseImpl.complete`,
  `AddDossierElementUseCaseImpl.add` filtré sur `PRODUCED_DOC`). Les 3 nouveaux réutilisent tous
  `WorkflowParticipantPort`/`DossierWorkflowLinkRepository` déjà injectés ou ajoutés en
  constructeur — aucun nouveau port créé pour la résolution de destinataires.
- **Lecture self-scoped** — `NotificationInboxController` n'a aucune restriction de rôle
  (`@PreAuthorize` absent), `recipient` est toujours `OidcUser.getSubject()`, jamais un paramètre
  client. Scoping IDOR-safe au niveau SQL (`markRead` retourne `false`, jamais d'exception fuitant
  l'existence d'une notification d'un autre utilisateur → 404, jamais 403).
- **Frontend** — `stores/notifications.ts` : `connect()` déclenche `backfill()` (non attendu) puis
  ouvre le SSE ; `NotificationDrawer.vue` réécrit avec `Button`/`OverlayBadge`/`ScrollPanel`
  PrimeVue, panneau `v-if` local (pas de `Popover`, voir § Déviations).

### Patterns & Best Practices

- Chaque nouveau composant est un miroir direct d'un composant existant analogue
  (`JpaUserNotificationRepositoryAdapter` ↔ `JpaNotificationOutboxRepositoryAdapter`,
  `NotificationInboxRetentionJob` ↔ `WorkflowRetentionJob`) — pas de nouveau pattern introduit.
- Les 3 nouveaux producteurs BROWSER **ne catchent jamais** l'exception de
  `sendNotificationUseCase.send(...)` (contrairement à `WorkflowStateEventListener`, un listener
  d'événement moteur asynchrone) : ce sont des use cases domaine appelés en synchrone dans une
  requête déjà en cours, et le domaine ne peut pas dépendre d'`org.slf4j` (ArchUnit) — donc pas de
  `catch` sans log possible depuis ces classes. Une vraie erreur d'envoi remonte comme le reste de
  l'écriture, cohérent avec les autres use cases d'écriture du dépôt.

## Integration Points

- `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/read-all`
  — golden OpenAPI régénéré et stable (`payload` exposé en `Map<String, Object>`, pas en
  `JsonNode` — voir § Déviations pour pourquoi).
- `NotificationConfig`/`WorkflowConfig`/`DossierConfig` : 3 beans `@Bean` modifiés
  (`sendNotificationUseCase`, `completeTaskUseCase`, `workflowLinkVisualizationUseCaseImpl`,
  `addDossierElementUseCase`) + 1 nouveau (`notificationInboxUseCase`).

## Error Handling

- `UserNotificationNotFoundException` → 404 via `NotificationExceptionHandler` (même contrôleur
  advice que le reste de `infrastructure.web.notification`).
- Backfill frontend : échec réseau → `backfilled` remis à `false`, le store reste vide mais le SSE
  continue de fonctionner ; pas d'erreur visible bloquante (catch silencieux assumé, cohérent avec
  `fail-soft` déjà pratiqué dans ce store).
- `markRead`/`markAllRead` frontend : optimistes avec rollback sur échec serveur (voir tests
  `rolls back the optimistic read state when the server call fails`).

## Performance Considerations

- Écriture `user_notification` : un `INSERT` de plus par événement `BROWSER`, pas de round-trip
  réseau supplémentaire côté domaine.
- Lecture paginée (20/page par défaut), tri `createdAt DESC`, index dédié
  `ix_user_notification_recipient_created (tenant_id, recipient, created_at)`.
- Rétention 30j vs 7j pour l'outbox — signalé en design comme point de vigilance volumétrique, pas
  mesuré en charge réelle dans ce cycle (pas de Testcontainers de charge demandé).

## Security Notes

- Aucune nouvelle chaîne d'authentification — `SecurityConfig` couvre déjà `/api/notifications/**`
  via `anyRequest().authenticated()` (vérifié explicitement par IT-NOTIF-5, pas seulement supposé).
- CSRF : `PATCH`/`POST` passent par la même protection CSRF que le reste de l'app (vérifié par les
  tests `NotificationInboxApiTest`, qui échoueraient sans `.with(csrf())` si la protection avait
  été contournée par erreur).
- IDOR : `markRead` scope `(id, tenant_id, recipient)` en une seule requête SQL, jamais un
  `findById` suivi d'une vérification en mémoire — élimine la fenêtre où un bug de contrôleur
  exposerait une notification d'un autre utilisateur (même discipline que la décision n°20 du
  dépôt).

## Déviations trouvées (Phase 5 + Phase 7 + Phase 10 + Phase 11)

| # | Trouvaille | Phase | Sévérité | Traitement |
|---|---|---|---|---|
| 1 | Écriture outbox+inbox en 2 transactions séparées, pas « la même transaction » du design — pas de gestionnaire de transaction accessible depuis `SendNotificationUseCaseImpl` (classe domaine pure, ArchUnit interdit Spring). | 5 | Faible — un échec de l'inbox n'orpheline jamais une ligne outbox qui délivrerait quand même ; seul l'historique persistant manque cette entrée. | Assumé, documenté dans le design/TODO §66. |
| 2 | `Popover` PrimeVue ne s'ouvre pas de façon fiable sous jsdom (Teleport + floating-ui, pas de polyfill `ResizeObserver` ni `attachTo document.body`) — vérifié empiriquement (3 tests rouges), pas supposé. | 5 | Aucune (choix de conception, pas un défaut) | Remplacé par un panneau `v-if` local ; `Button`/`OverlayBadge`/`ScrollPanel` PrimeVue conservés. |
| 3 | **Race condition réelle** — `stores/notifications.ts.backfill()` faisait `items.value = page.content.map(fromDto)` (remplacement complet). `connect()` déclenche `backfill()` sans l'attendre puis ouvre immédiatement le flux SSE : un événement live arrivé (et `unshift`-é dans `items`) avant la résolution du `fetch` de backfill était silencieusement écrasé par cette affectation. Reproduit en TDD (test rouge sur l'ancien code, contrôle explicite de l'ordre de résolution des deux promesses), pas seulement supposé. | 7 (revue fichier par fichier) | **Moyenne** — perte silencieuse d'une notification réellement reçue, dans une fenêtre de course plausible en production (un push SSE peut arriver avant qu'un round-trip HTTP de backfill ne se termine). | **Corrigé** : `backfill()` fusionne désormais (`[...items.value, ...fresh]`, dédoublonné par id) au lieu de remplacer. Test de régression permanent ajouté (`does not lose a live notification that arrives before the backfill request resolves`). |
| 4 | **F1 — id SSE ≠ id inbox (bloquant).** L'événement SSE `BROWSER` portait `id = record.id()` (ligne `notification_outbox`), jamais l'id de la ligne `user_notification` correspondante (générée indépendamment par l'adaptateur). Trouvé par un subagent d'analyse adversariale indépendant (Opus 5.5), preuve empirique : `send()` retourne l'outbox id, `GET /api/notifications` retourne un autre id, `PATCH` avec l'outbox id répond 404. | 10 (analyse adversariale) | **Élevée — bloquant** : marquage lu d'une notification reçue en direct impossible (404 silencieux), dédup backfill/SSE cassée (doublons), test existant et guide utilisateur validaient un comportement faux. | **Corrigé** (D-NOTIF-SSE-ID) : l'id `user_notification` est généré dans `SendNotificationUseCaseImpl.send()` avant l'écriture outbox, embarqué dans le snapshot BROWSER (`NotificationPayloadJson.browser(userId, payload, inboxId)`), extrait par `NotificationOutboxRelay.dispatchBrowser` comme id SSE. `UserNotificationRepository.save` prend l'id en paramètre. |
| 5 | **F2 — reconnexion SSE ne relance jamais le backfill.** `backfilled` restait `true` pour toujours ; `connect(true)` (reconnexion après erreur, ou après le timeout horaire du SSE) n'appelait jamais `backfill()`. Le relais marque pourtant `PROCESSED` qu'un client soit connecté ou non — tout ce qui arrive pendant le trou de reconnexion était perdu jusqu'au rechargement complet. | 10 (analyse adversariale) | **Moyenne** — contredit le but même de la feature (rattraper ce qui a été manqué pendant une déconnexion). | **Corrigé** : `backfilled` remis à `false` dans `source.onerror` ; la fusion dédoublonnée (déviation n°3) rend le re-backfill sûr. |
| 6 | **F3 — `unreadCount` est une estimation client, pas un compte serveur.** Les exigences Phase 1 demandent explicitement un compte cohérent côté serveur ; `unreadCount` comptait les items chargés (≤20 sans pagination dans le tiroir). `countUnread` existait côté port/adapter mais n'était exposé par aucun endpoint. | 10 → 11 | Moyenne | **Corrigé (Phase 11)** : `GET /api/notifications/unread-count` nouveau, store frontend passé de `computed` à `ref` synchronisé au backfill (avec garde anti-régression `Math.max` contre un live event arrivé pendant la requête de comptage), `markAllRead` gardé sur `unreadCount === 0` plutôt que `previouslyUnread.length === 0`, bouton « Charger plus » ajouté au tiroir, index `024-user-notification-unread-index` (équivalent portable H2/PostgreSQL d'un index partiel). |
| 7 | **F4 — sémantique transactionnelle incohérente entre les 3 nouveaux producteurs BROWSER.** `reassign` n'était pas `@Transactional` (échec de notif → 500 après réassignation déjà effective côté Flowable) ; `complete` est `@Transactional` (échec de notif → rollback de la complétion de tâche, la Javadoc affirmant le contraire était fausse) ; `add` hérite du comportement de son appelant. | 10 → 11 | Moyenne | **Corrigé (Phase 11)** : `WorkflowLinkVisualizationController#reassign` et `GenerateDocumentTool#generateDocument` rendus `@Transactional`, alignés sur le patron déjà établi par `complete` (rollback ensemble avec l'échec de notification — même compromis déjà accepté sur `AttachGeneratedDocumentTask` : objet de stockage orphelin, pas un problème d'intégrité). Javadoc fausse de `CompleteTaskUseCaseImpl` corrigée. |
| 8 | **F5 — purge inbox en N `DELETE` individuels.** `deleteByCreatedAtBefore` (dérivé Spring Data) chargeait toutes les lignes en mémoire puis émettait un `DELETE` par id, contrairement à l'outbox déjà en bulk. | 10 → 11 | Faible | **Corrigé (Phase 11)** : remplacé par un `@Modifying @Query` bulk delete, miroir de `NotificationOutboxRepository#deleteTerminalOlderThan`. |
| 9 | **F6 — `TASK_COMPLETED_BY_OTHER` manqué si la tâche complétée est la dernière du workflow.** `findActiveByDossierId` était appelé après `completeTask` ; si cette complétion faisait elle-même passer le lien à inactif (`endedAt` renseigné), la résolution après coup trouvait silencieusement « pas de lien actif » et sautait la notification. | 10 → 11 | Faible | **Corrigé (Phase 11)** : résolution du lien déplacée avant `completeTask(...)`. Test de régression avec `InOrder` verrouillant l'ordre. |
| 10 | **F7 — `markRead` sur une notification déjà lue écrasait `readAt`.** Sans garde, un second appel (client bavard, deux onglets) glissait le timestamp de lecture en avant à chaque clic. | 10 → 11 | Faible | **Corrigé (Phase 11)** : `UserNotificationJpaEntity.markRead` idempotent (`if (this.readAt == null)`). Les 3 autres points F7 (rétention non gardée, races d'optimistic update, destinataires non humains) restent non traités — la revue elle-même les qualifiait de non-régression/non-fuite, pas d'un défaut à corriger. |

## Concerns / follow-ups non bloquants

- `NotificationDrawer.vue` n'a pas de fermeture au clic extérieur ni d'`aria-expanded` — limitation
  déjà présente dans la version précédente (pas une régression), laissée telle quelle.
- Volumétrie `user_notification` (30j) non mesurée en charge réelle — signalé en design, pas
  bloquant pour ce cycle.
- Overlap `WORKFLOW_STATE_CHANGED` (tous les participants) / `TASK_REASSIGNED` (nouvel assignee
  seul) sur une réassignation — documenté et assumé (TODO §66), pas corrigé : modifier
  `WorkflowStateEventListener` pour distinguer `TASK_ASSIGNED` nécessiterait d'extraire l'entité
  Flowable de l'événement (reflection sur `getEntity()`), risqué à faire sans IT sur le vrai moteur
  (doctrine H1/H1-bis du dépôt) — hors périmètre de ce cycle.
