---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

> Origine : `ROADMAP.md` v1.1, item **3.2** (« Produit, pas cadré »), analyse initiale
> [analysis doc/27-notifications-inbox.md](../../../analysis%20doc/27-notifications-inbox.md).
> Cadrage Phase 1 mené le 2026-09-22, décisions tranchées une par une via `AskUserQuestion`
> (voir « Questions & Open Items »).

## Problem Statement

**Constat vérifié dans le code (pas seulement dans l'analyse d'origine), à jour au 2026-09-22
(`main` @ PR #132) :**

Le mécanisme de notification navigateur existe déjà et est **plus avancé que ne le décrivait
l'analyse `27-notifications-inbox.md`** — celle-ci a été écrite avant la PR #132
(`enrichissement-reporting`, TODO §65) qui a introduit un canal `BROWSER` sans le documenter
comme une brique d'inbox :

- `NotificationChannel.BROWSER` existe déjà (`SendNotificationUseCaseImpl`) et est utilisé par
  **2 producteurs réels** : `WorkflowStateEventListener` (changement d'état d'un lien
  dossier/workflow) et `EvaluateSlaAlertsUseCaseImpl` (franchissement de seuil SLA), tous deux
  vers `WorkflowParticipantPort.findParticipantUserIds(link)`.
- Un composant frontend **`NotificationDrawer.vue`** (cloche + badge non-lu + tiroir liste) est
  déjà monté globalement dans `AppShell.vue`, pour tous les rôles authentifiés, alimenté par un
  store Pinia (`stores/notifications.ts`) qui ouvre `GET /api/notifications/stream` (SSE).
- Mais ce mécanisme est **entièrement éphémère côté client** : le tableau d'items et l'état
  lu/non-lu vivent uniquement dans la mémoire de l'onglet (perdus au reload), plafonnés à 100
  items côté store, sans aucune trace côté serveur au-delà de la ligne `notification_outbox`
  (purgée à 7 jours, `outbox.processed-retention`). Côté serveur,
  `NotificationOutboxRelay.dispatchBrowser` marque systématiquement la ligne `PROCESSED`, que le
  push SSE ait atteint un émetteur connecté ou non (« delivered or skipped »).
- Conséquence directe : si l'utilisateur n'est pas connecté au moment de l'événement (autre
  onglet, autre device, absence), la notification est **perdue définitivement**. Pas d'historique
  consultable après coup, pas d'état lu/non-lu partagé entre sessions/appareils.
- Ce trou est distinct des deux mécanismes voisins déjà livrés : la **file d'émission sortante**
  (`notification_outbox`, retry/DLQ technique vers email/webhook/SSE, transparente pour
  l'utilisateur) et l'**inbox de tâches** (`/rapports`, `ListMyTasksUseCaseImpl` — « ce que
  j'ai à faire », pas « ce qui s'est passé »).

**Qui est affecté :** tout utilisateur authentifié du SPA (`TENANT_ADMIN`, `WORKFLOW_ADMIN`,
`DOSSIER_MANAGER`, `DOSSIER_VIEWER`) — le tiroir de notifications est déjà visible pour tous les
rôles, sans distinction.

**Situation actuelle / contournement :** aucun — un utilisateur qui rate une notification
navigateur (déconnecté, onglet fermé, autre device) n'a aucun moyen de la retrouver. Seuls les
canaux EMAIL/WEBHOOK, quand configurés, laissent une trace exploitable hors de l'application
(boîte mail, système tiers).

## Goals & Objectives

**Objectif principal :** rendre persistant, consultable après coup et synchronisé entre
sessions/appareils ce qui transite déjà sur le canal `BROWSER`, sans dupliquer les mécanismes
existants (`notification_outbox`, inbox de tâches).

**Objectifs secondaires :**
- Étendre la couverture du canal `BROWSER` à 3 nouveaux événements métier à fort signal
  utilisateur (réassignation de tâche, tâche terminée par un tiers, document généré), en
  réutilisant le point de couture déjà éprouvé par les 2 producteurs existants.
- Remettre le composant `NotificationDrawer.vue` en conformité avec les conventions PrimeVue du
  dépôt (actuellement CSS scoped fait main), à l'occasion de sa réécriture pour le branchement
  REST.

**Non-goals (hors périmètre explicite de ce cycle) :**
- Pas de notion de « dossier affecté à un utilisateur » — aucun champ `assignee`/`manager` n'existe
  sur `Dossier` aujourd'hui ; introduire ce concept serait un changement de modèle de domaine à
  part entière, pas une extension de la notification. Voir « Questions & Open Items ».
- Pas de fusion avec l'inbox de tâches (`/rapports`) — deux vues sémantiquement distinctes (« à
  faire » vs « ce qui s'est passé »), restent séparées.
- Pas de persistance des canaux EMAIL/WEBHOOK dans la nouvelle table — un email est déjà son
  propre historique (boîte mail du destinataire) ; seul `BROWSER` alimente `user_notification`.
- Pas de nouveau mécanisme de purge générique — réutilisation du patron déjà établi
  (`WorkflowRetentionJob`/`EvaluateDossierRetentionJob`).

## User Stories & Use Cases

- En tant qu'utilisateur du SPA (tout rôle), je veux retrouver dans un historique les
  notifications que j'ai manquées pendant que je n'étais pas connecté, afin de ne pas perdre
  l'information (dossier suivi, tâche, document, alerte SLA).
- En tant qu'utilisateur, je veux que mon statut lu/non-lu soit le même si je me reconnecte sur un
  autre onglet ou un autre appareil, afin de ne pas retraiter deux fois la même information.
- En tant que participant d'un lien workflow, je veux être notifié quand une tâche que j'aurais pu
  traiter est terminée par un collègue, afin de ne pas la retraiter inutilement (nouveau
  producteur, `CompleteTaskUseCaseImpl`).
- En tant qu'utilisateur à qui une tâche est réassignée, je veux être notifié de cette
  réassignation, afin de savoir qu'une action m'incombe désormais (nouveau producteur,
  `ReassignWorkflowTaskUseCase`).
- En tant que participant d'un lien workflow, je veux être notifié qu'un document a été généré sur
  le dossier suivi, afin de savoir qu'un nouveau livrable existe (nouveau producteur,
  `GenerateDocumentUseCaseImpl`).

**Edge cases identifiés (à couvrir en conception/tests) :**
- Utilisateur jamais connecté en SSE au moment de l'événement → doit quand même retrouver
  l'entrée en rouvrant le tiroir (backfill REST).
- Purge à 30 jours pendant qu'une notification est encore non lue → décision assumée (§ Questions),
  cohérent avec les autres mécanismes de rétention du dépôt.
- Champs de dossier sensibles dans le payload d'un événement (ex. franchissement SLA sur un
  dossier avec champs `sensitive`) → doit rester masqué dans `user_notification` comme il l'est
  déjà pour les autres canaux (`SendNotificationUseCaseImpl.buildPayload`, D-WEBHOOK-MASK, PR #70)
  — le point de couture unique dans `send()` couvre ce garde-fou sans code additionnel si la
  nouvelle écriture se fait sur le même `payload` déjà assaini.
- Plusieurs sessions/onglets ouverts simultanément pour le même utilisateur → `markRead` doit se
  répercuter sur toutes (source de vérité = serveur, pas l'état local d'un onglet).

## Success Criteria

- Une notification `BROWSER` émise pendant que le destinataire est déconnecté est visible et
  marquable lue dans le tiroir à sa prochaine connexion (persistée en base, pas seulement en
  mémoire de session SSE).
- L'état lu/non-lu est identique entre deux onglets/appareils du même utilisateur (source de
  vérité serveur, pas de double-compte côté client).
- Les 5 producteurs `BROWSER` (2 existants + 3 nouveaux) écrivent dans `user_notification` au même
  point de couture (`SendNotificationUseCaseImpl.send`), sans logique dupliquée par producteur.
- Les entrées de plus de 30 jours sont purgées automatiquement, sans purge manuelle.
- `NotificationDrawer.vue` est conforme aux conventions PrimeVue du dépôt à la fin du cycle.
- `./mvnw verify` vert (couverture Jacoco tenue), Vitest + Playwright verts (le SPA et son contrat
  API sont impactés — les deux sont obligatoires par convention projet), pas de régression sur les
  5 canaux/producteurs existants (EMAIL, WEBHOOK, BROWSER × 2 producteurs déjà livrés).

## Constraints & Assumptions

**Contraintes techniques :**
- Architecture hexagonale stricte : nouvelle table via port `domain.notification.port.out`, pas
  d'entité JPA exposée au domaine (miroir `NotificationOutboxRepository`).
- Multi-tenant par schéma : `user_notification` suit le même schéma Liquibase par tenant que le
  reste (miroir `notification_outbox`, `006-notification-tables.yaml`).
- BFF/OIDC : `GET /api/notifications` et `PATCH /api/notifications/{id}/read` s'appuient sur
  l'utilisateur de session déjà authentifié (`OidcUser.getSubject()`, même identité que
  `NotificationSseController.stream`), pas de nouveau mécanisme d'auth.
- Le masquage des champs sensibles (`DossierFieldRedactor`/`buildPayload`) doit rester en amont de
  l'écriture `user_notification`, comme pour les autres canaux — pas de nouveau point de fuite.

**Contraintes produit :**
- Pas de régression sur le comportement actuel du tiroir pour les 2 producteurs déjà en
  production (état de lien workflow, SLA).
- Le badge non-lu doit refléter un compte serveur cohérent, pas une estimation client.

**Hypothèses retenues (assumptions nommées) :**
- Le payload déjà construit par `SendNotificationUseCaseImpl.buildPayload()` (masquage inclus) est
  réutilisé tel quel pour l'écriture `user_notification` — aucune re-sérialisation spécifique au
  canal n'est nécessaire.
- `recipient` dans `user_notification` est l'identité utilisateur déjà utilisée par les 2
  producteurs existants (`userId` de `WorkflowParticipantPort`/participants), cohérente avec le
  `userId` de `SseNotificationAdapter.register`.

## Questions & Open Items

**Décisions tranchées en Phase 1 (une par une, via `AskUserQuestion`) :**

| ID | Décision | Choix retenu | Justification |
|---|---|---|---|
| **D-NOTIF-PERSISTENCE** | Mécanisme de stockage de l'historique | Nouvelle table `user_notification` (`tenant_id`, `recipient`, `payload`, `created_at`, `read_at` nullable), écrite dans `SendNotificationUseCaseImpl.send()` pour le canal `BROWSER`, **indépendante** de `notification_outbox` | Sépare nettement la file de livraison technique (retry/purge 7j, partagée EMAIL/WEBHOOK/BROWSER) de l'historique de lecture utilisateur (rétention propre, mutable `read_at`) — écarté : étendre `notification_outbox` (couplage des deux responsabilités, anti-pattern déjà signalé dans l'analyse d'origine) et persistance navigateur seule (ne rattrape pas les événements manqués hors connexion, le vrai besoin) |
| **D-NOTIF-SCOPE** | Producteurs `BROWSER` couverts ce cycle | 5 au total : les 2 existants (`WorkflowStateEventListener`, `EvaluateSlaAlertsUseCaseImpl`) + 3 nouveaux — tâche réassignée (`ReassignWorkflowTaskUseCase`), tâche terminée par un tiers (`CompleteTaskUseCaseImpl`), document généré (`GenerateDocumentUseCaseImpl`). **« Dossier affecté à l'utilisateur » explicitement retiré du périmètre** — aucun champ `assignee`/`manager` sur `Dossier` aujourd'hui ; introduire ce concept est un changement de domaine à part entière, à traiter comme sa propre feature si confirmé | Réutilise dans les 3 nouveaux cas le même point de couture participants (`WorkflowParticipantPort`) déjà éprouvé par les producteurs existants ; le point le plus ambigu (dossier) est écarté plutôt que tranché silencieusement |
| **D-NOTIF-RETENTION** | Durée de rétention par défaut | 30 jours, purge automatique | Cohérent avec la cadence déjà utilisée ailleurs dans le dépôt (D-CADENCE, PR #132) ; tous les autres mécanismes de rétention du dépôt (`retention-dossier-type`, `purge-stockage`, outbox) ont une purge dès le départ, pas d'exception ici |
| **D-NOTIF-SCOPE-VS-TASKS** | Relation avec l'inbox de tâches (`/rapports`) | Vue strictement séparée, aucune fusion | Deux sémantiques différentes (« à faire » vs « déjà su/passé ») ; fusionner changerait la nature de l'inbox tâches existante (utilisée par `/rapports`, `slaRemainingHours`), hors périmètre de ce cycle |
| **D-NOTIF-SOURCE-VERITE** | Comportement du tiroir au montage/reconnexion | `GET /api/notifications` (paginé, `PageResponse`) hydrate le store au montage, remplaçant l'état 100% éphémère actuel ; le flux SSE existant continue d'insérer les nouveaux événements en direct ; `markRead`/`markAllRead` appellent `PATCH /api/notifications/{id}/read` au lieu de muter uniquement l'état local | Sans backfill REST, le problème initial (rattraper ce qui a été manqué hors connexion) ne serait pas résolu dans le tiroir lui-même — écarté : SSE seul avec écran d'historique séparé |
| **D-NOTIF-UI-CONFORMITE** | Conformité PrimeVue de `NotificationDrawer.vue` | Migration vers PrimeVue dans ce cycle (ex. `OverlayPanel`/`Badge`) | Le composant est de toute façon réécrit pour le branchement REST (D-NOTIF-SOURCE-VERITE) — coût marginal de le faire correctement plutôt que de perpétuer un écart déjà constaté aux conventions du dépôt (`analysis doc/11-frontend.md`) |

**Aucune question matérielle ouverte restante** pour la Phase 1 — prêt pour la Phase 3 (design).
