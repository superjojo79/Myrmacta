# Inbox de notifications persistée — historique utilisateur

> **Statut : livré** (2026-09-22, TODO §66, cycle `dev-lifecycle` complet). Ce document reste
> l'analyse d'origine (2026-09-14) — conservé pour l'historique du raisonnement, **pas à jour**
> sur l'état du code : voir TODO §66 et
> `docs/ai/{requirements,design,planning,testing}/2026-09-22-feature-inbox-notifications-persistee.md`
> pour l'état livré (table `user_notification`, `NotificationInboxController`, 5 producteurs
> BROWSER, `NotificationDrawer.vue` en PrimeVue). Constat corrigé en Phase 1 : cette analyse
> sous-estimait l'état réel du code au moment de sa rédaction — le canal `NotificationChannel.BROWSER`
> et un premier `NotificationDrawer.vue` existaient déjà (introduits par la PR #132 sans être
> documentés comme une brique d'inbox), mais 100% éphémères côté client avant ce cycle.
> Origine : `ROADMAP.md` v1.1 (14/09/2026), item **3.2**, classé « Produit, pas cadré ».
> Traité **seul** (pas regroupé) : l'audience est l'utilisateur interne connecté au SPA, différente
> du public externe visé par le portail (`26-portail-externe.md`) et de la surface DMN
> (`28-outillage-dmn.md`).

---

## 1. Constat vérifié (pas supposé)

| Canal | État réel | Preuve |
|---|---|---|
| Push temps réel (SSE) | **Livré, JVM-local uniquement** | `NotificationSseController` + `SseNotificationAdapter` — pas de table de persistance, la notification n'existe que pendant que le navigateur est connecté |
| Email sortant | **Livré** | `SendNotificationUseCaseImpl`, `NotificationOutboxRelay`, réutilisé par `smtp` (connecteurs hors V1, PR #99) |
| Webhook sortant | **Livré** | `NotificationWebhookAdminController`, `NotificationOutboxJpaEntity` (table `notification_outbox`, purge déjà gérée par `purge-stockage`/`purge_outbox`, PR #56) |
| **Historique utilisateur consultable** (UNREAD/READ, après coup) | **Absent** | Aucune entité `user_notification`/équivalent — grep confirmé, seules les tables `notification_outbox` (file d'émission sortante, pas un historique de lecture) et `NotificationWebhookJpaEntity` (config admin des webhooks) existent |
| Inbox déjà existante dans le produit | **Oui, mais c'est une inbox de tâches, pas de notifications** | `/rapports` + inbox tâches avec `slaRemainingHours` (reporting-sla, PR #80) — liste les tâches BPMN assignées, pas des événements de notification |

**Point clé à ne pas confondre** : Myrmacta a déjà deux notions voisines mais distinctes —
1. la **file d'émission sortante** (`notification_outbox`) : mécanisme technique de livraison
   fiable (retry, DLQ) vers email/webhook, transparente pour l'utilisateur ;
2. l'**inbox de tâches** (`/rapports`, `ListMyTasksUseCaseImpl`) : ce qu'un utilisateur a **à faire**
   dans le workflow.

Aucune des deux ne répond au besoin ici : un **historique de ce qui s'est passé** (dossier créé,
tâche terminée par quelqu'un d'autre, SLA franchi), consultable après coup, marquable
lu/non-lu, indépendant de la connexion SSE active au moment de l'événement.

---

## 2. Composants à ajouter (proposition, non tranchée)

### Backend

- Nouvelle table `user_notification` (`tenant_id`, `recipient` — identité utilisateur, `payload`,
  `created_at`, `read_at` nullable) — migration Liquibase, schéma par tenant comme le reste.
- `SendNotificationUseCaseImpl` (point d'entrée unique déjà utilisé par tous les canaux, cf.
  masquage-donnees-sensibles-logs-webhooks, PR #70) gagnerait un **4ᵉ canal** interne, au même point
  de couture que SMTP/webhook/SSE — pas de nouvelle voie d'émission à inventer.
- Nouveau use case `ListUserNotificationsUseCase` / `MarkNotificationReadUseCase`.
- Endpoint `GET /api/notifications` (pagination `PageResponse`, pattern déjà standard depuis
  `contrat-api-client`, PR #65) + `PATCH /api/notifications/{id}/read`.
- Purge/rétention : réutiliser le patron déjà en place (`WorkflowRetentionJob`,
  `EvaluateDossierRetentionJob`) plutôt qu'un mécanisme neuf.

### Frontend

- Icône « cloche » + badge non-lu dans la nav (à vérifier : composant équivalent existant ou non
  dans `frontend/src/views/**` — à confirmer en Phase 1, pas supposé ici).
- Vue/panneau liste avec pagination, cohérent avec les autres listes déjà migrées vers
  `PageResponse`.

---

## 3. Risques / points de vigilance

- **Ne pas dupliquer `notification_outbox`** : la nouvelle table est un historique de lecture
  utilisateur, pas une file de livraison — objectif différent, pas de tentative de réutiliser
  `NotificationOutboxJpaEntity` tel quel (statuts `PENDING`/`PROCESSING`/`FAILED` n'ont pas de sens
  ici).
- **Données sensibles** : `SendNotificationUseCaseImpl` masque déjà les valeurs sensibles avant
  écriture outbox (PR #70) — le même garde-fou doit s'appliquer au nouveau canal interne, pas
  seulement aux canaux sortants existants.
- **Volume** : contrairement à l'email/webhook (best-effort, purgé), un historique consultable
  pousse vers une vraie pagination + rétention dès le départ, pas un ajout après coup.

## 4. Points ouverts (Phase 1 `dev-lifecycle`)

| ID | Question |
|---|---|
| D-NOTIF-SCOPE | Quels événements alimentent l'inbox au lancement (tout ce qui passe déjà par `SendNotificationUseCase`, ou un sous-ensemble) ? |
| D-NOTIF-RETENTION | Durée de rétention par défaut, purge automatique (aligné sur `outbox.processed-retention` existant ou distinct) ? |
| D-NOTIF-SCOPE-VS-TASKS | L'inbox notification reste-t-elle strictement distincte de l'inbox tâches (`/rapports`), ou fusionnée en une seule vue à terme ? |
| D-NOTIF-UNREAD-COUNT | Le badge non-lu est-il poussé en temps réel via SSE (canal déjà existant, juste un nouveau type d'event) ou recalculé au chargement ? |

Aucune décision de priorité n'est prise par ce document — il documente uniquement l'écart réel
constaté contre le code.
