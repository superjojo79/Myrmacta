---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
---

# System Design & Architecture

> Phase 3, basée sur
> [docs/ai/requirements/2026-09-22-feature-inbox-notifications-persistee.md](../requirements/2026-09-22-feature-inbox-notifications-persistee.md)
> (6 décisions D-NOTIF-* déjà tranchées). Ce document couvre l'architecture concrète ; aucune
> nouvelle décision produit n'a été nécessaire — 2 précisions techniques trouvées en investiguant
> le code réel sont documentées en § *Design Decisions* comme corollaires directs des décisions
> déjà actées, pas comme de nouveaux arbitrages.

## Architecture Overview

```mermaid
graph TD
    subgraph Producteurs BROWSER existants
        A1[WorkflowStateEventListener]
        A2[EvaluateSlaAlertsUseCaseImpl]
    end
    subgraph Producteurs BROWSER nouveaux ce cycle
        B1["WorkflowLinkVisualizationUseCaseImpl.reassign()"]
        B2[CompleteTaskUseCaseImpl.complete]
        B3["AddDossierElementUseCaseImpl.add(PRODUCED_DOC)"]
    end
    A1 --> SEND
    A2 --> SEND
    B1 --> SEND
    B2 --> SEND
    B3 --> SEND
    SEND["SendNotificationUseCaseImpl.send()<br/>payload masqué (D-WEBHOOK-MASK)"]
    SEND -->|"1 ligne / canal"| OUTBOX[(notification_outbox<br/>retry 7j, transparent)]
    SEND -->|"canal == BROWSER uniquement"| INBOX[(user_notification<br/>historique 30j)]
    OUTBOX --> RELAY[NotificationOutboxRelay.dispatchBrowser]
    RELAY -->|push best-effort| SSE[SseNotificationAdapter]
    SSE -->|EventSource| STORE[stores/notifications.ts]
    INBOX --> REST["GET /api/notifications<br/>PATCH /api/notifications/-id-/read"]
    REST -->|backfill au montage| STORE
    STORE --> DRAWER[NotificationDrawer.vue — PrimeVue]
    PURGE[NotificationInboxRetentionJob<br/>@Scheduled, miroir WorkflowRetentionJob] -->|purge > 30j| INBOX
```

**Principe directeur** : `user_notification` est écrite **en plus** de `notification_outbox`, au
même point de couture (`SendNotificationUseCaseImpl.send()`), pas à la place. Les deux tables
restent indépendantes dans leur cycle de vie (rétention 7j technique vs 30j utilisateur, statuts
PENDING/PROCESSING/FAILED/PROCESSED/DEAD vs simple `read_at` nullable) — conforme à
D-NOTIF-PERSISTENCE.

## Data Models

### Nouvelle table `user_notification`

Migration Liquibase `023-user-notification.yaml` (prochain id libre après `022-*`), schéma par
tenant comme `notification_outbox` (`006-notification-tables.yaml`, même convention de colonnes).

| Colonne | Type | Contrainte |
|---|---|---|
| `id` | uuid | PK |
| `tenant_id` | varchar(64) | not null |
| `recipient` | varchar(255) | not null — `userId` OIDC (`OidcUser.getSubject()`, même identité que `SseNotificationAdapter.register`) |
| `payload_json` | json | not null — même payload déjà masqué que celui écrit dans `notification_outbox` pour BROWSER |
| `created_at` | timestamp with time zone | not null |
| `read_at` | timestamp with time zone | nullable — `NULL` = non lu |

Index : `ix_user_notification_recipient_created` sur `(tenant_id, recipient, created_at DESC)`
(liste paginée la plus fréquente) et `ix_user_notification_recipient_unread` sur
`(tenant_id, recipient)` filtré `WHERE read_at IS NULL` (comptage badge) — miroir du partiel index
déjà utilisé par `notification_webhook` (`006-notification-webhook-active-index-*.sql`) pour un
index partiel PostgreSQL avec fallback H2 non filtré.

### Modèle domaine

```java
// domain.notification.model
public record UserNotificationRecord(
        UUID id,
        String tenantId,
        String recipient,
        Map<String, Object> payload,
        Instant createdAt,
        Instant readAt) {

    public boolean isRead() {
        return readAt != null;
    }
}
```

### Contrat de payload standardisé (corollaire de D-NOTIF-SCOPE)

**Constat en investiguant le code** : les 2 producteurs BROWSER existants n'ont pas le même
contrat. `EvaluateSlaAlertsUseCaseImpl.payload(...)` écrit déjà `type`/`title`/`body`/`dossierId`/
`reference`/`dossierTypeCode`/`slaStatus` — auto-descriptif. `WorkflowStateEventListener` n'écrit
que `type`/`linkId` — **pas de `title`/`body`**, ce que `stores/notifications.ts` compense
aujourd'hui par un titre générique `'Notification'` (fallback silencieux, jamais remarqué car
l'historique était éphémère). Une fois persistant, cet écart devient visible dans le tiroir
rouvert plus tard, pas seulement au moment du push live — à corriger dans ce cycle, pas laissé en
dette.

**Contrat retenu pour les 5 producteurs**, appliqué uniformément :

```json
{
  "type": "WORKFLOW_STATE_CHANGED | SLA_THRESHOLD_CROSSED | TASK_REASSIGNED | TASK_COMPLETED_BY_OTHER | DOCUMENT_GENERATED",
  "title": "string — affiché en gras dans le tiroir",
  "body": "string — texte secondaire",
  "linkId": "uuid, optionnel",
  "dossierId": "uuid, optionnel",
  "taskId": "string, optionnel"
}
```

`WorkflowStateEventListener` est retouché pour ajouter `title`/`body` (ex. « Workflow mis à jour »
/ référence du dossier) — seul changement sur un producteur déjà en production, pas de nouveau
comportement métier, juste l'alignement du contrat.

## API Design

### Port sortant (miroir `NotificationOutboxRepository`)

```java
// domain.notification.port.out
public interface UserNotificationRepository {
    UUID save(String tenantId, String recipient, String payloadJson, Instant now);
    PageResult<UserNotificationRecord> page(String tenantId, String recipient, PageQuery query);
    long countUnread(String tenantId, String recipient);
    /** No-op si {@code id} n'appartient pas à {@code recipient} — scoping recipient en base, pas seulement au contrôleur. */
    boolean markRead(UUID id, String tenantId, String recipient, Instant now);
    int markAllRead(String tenantId, String recipient, Instant now);
    int purgeOlderThan(Instant cutoff);
}
```

`markRead` retourne `boolean` (trouvé et appartenait au recipient, ou non) plutôt que de lancer une
exception — un `id` inconnu ou appartenant à quelqu'un d'autre doit répondre 404, jamais 403 avec
fuite d'information sur l'existence de la ligne (même discipline IDOR que le reste du dépôt,
décision n°20).

### Port entrant

```java
// domain.notification.port.in
public interface NotificationInboxUseCase {
    PageResult<UserNotificationRecord> list(String tenantId, String recipient, PageQuery query);
    void markRead(UUID id, String tenantId, String recipient);
    void markAllRead(String tenantId, String recipient);
}
```

Une seule interface pour les 3 opérations (miroir `ManageNotificationWebhookUseCase`, pas 3
interfaces séparées — cohérence de granularité avec le port `in` déjà établi dans ce bounded
context).

### REST

Nouveau contrôleur `NotificationInboxController` (`infrastructure.web.notification`), **distinct**
de `NotificationSendController` déjà présent sur le même préfixe `/api/notifications`
(`POST /api/notifications/send`, `@PreAuthorize hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')`) — le
nouveau contrôleur n'a **aucune restriction de rôle**, seulement `authenticated()` (chaîne
`SecurityConfig` déjà `anyRequest().authenticated()` par défaut, comme
`NotificationSseController.stream`), portée self-scoped par construction (`recipient` = principal
courant, jamais un paramètre client).

| Méthode | Chemin | Auth | Comportement |
|---|---|---|---|
| `GET` | `/api/notifications` | tout rôle authentifié | `PageResponse<UserNotificationResponse>`, `recipient = OidcUser.getSubject()`, tri par défaut `ListSorts.USER_NOTIFICATIONS = "createdAt,desc"` |
| `PATCH` | `/api/notifications/{id}/read` | tout rôle authentifié | 204 si trouvé et appartenait au recipient courant, 404 sinon (jamais 403 — pas de fuite d'existence) |
| `POST` | `/api/notifications/read-all` | tout rôle authentifié | 204, marque tout l'historique du recipient courant lu |

```java
@GetMapping
public PageResponse<UserNotificationResponse> list(
        @AuthenticationPrincipal OidcUser user,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size) {
    return PageResponse.from(inboxUseCase
            .list(tenantProperties.id(), user.getSubject(),
                    PageQueryParser.parse(page, size, null, ListSorts.USER_NOTIFICATIONS))
            .map(UserNotificationResponse::from));
}
```

`GET /api/notifications/stream` (SSE existant) est inchangé — reste le canal de livraison live,
`user_notification` devient la source de vérité pour l'historique et le read/unread.

## Component Breakdown

### Backend — nouveaux fichiers

| Fichier | Rôle |
|---|---|
| `domain.notification.model.UserNotificationRecord` | Modèle domaine |
| `domain.notification.port.out.UserNotificationRepository` | Port sortant |
| `domain.notification.port.in.NotificationInboxUseCase` | Port entrant |
| `domain.notification.usecase.NotificationInboxUseCaseImpl` | Implémentation (délègue au port sortant) |
| `infrastructure.persistence.notification.UserNotificationJpaEntity` | Entité JPA, miroir `NotificationOutboxJpaEntity` |
| `infrastructure.persistence.notification.SpringDataUserNotificationRepository` | Spring Data |
| `infrastructure.persistence.notification.JpaUserNotificationRepositoryAdapter` | Adaptateur du port |
| `infrastructure.web.notification.NotificationInboxController` | REST |
| `infrastructure.flowable.NotificationInboxRetentionJob` (ou `infrastructure.notification`) | `@Scheduled`, miroir `WorkflowRetentionJob` |
| `db/changelog/changes/023-user-notification.yaml` | Migration |

### Backend — fichiers modifiés

| Fichier | Changement |
|---|---|
| `SendNotificationUseCaseImpl` | Injecte `UserNotificationRepository` ; après validation des canaux, si `channels` contient `BROWSER`, écrit une ligne `user_notification` en plus de la ligne `notification_outbox` (même `payload` masqué, même transaction) |
| `WorkflowStateEventListener` | Ajoute `title`/`body` au payload (contrat standardisé) |
| `WorkflowLinkVisualizationUseCaseImpl` | +dépendance `SendNotificationUseCase` ; après `visualizationPort.reassignTask(...)`, envoie `TASK_REASSIGNED` au nouvel `assigneeId` |
| `CompleteTaskUseCaseImpl` | +dépendances `DossierWorkflowLinkRepository`, `WorkflowParticipantPort`, `SendNotificationUseCase` ; après `workflowEnginePort.completeTask(...)`, résout le lien actif du dossier, envoie `TASK_COMPLETED_BY_OTHER` à chaque participant **≠ `actor.userId()`** |
| `AddDossierElementUseCaseImpl` | +dépendances `DossierWorkflowLinkRepository`, `WorkflowParticipantPort`, `SendNotificationUseCase` ; après `elementRepository.save(element)`, si `type == PRODUCED_DOC`, résout `findActiveByDossierId(dossier.id())` et notifie tous les participants (pas d'exclusion d'acteur — couvre `AttachGeneratedDocumentTask`, contexte `AccessContext.system(...)`, **et** `GenerateDocumentTool` MCP, seul point de couture commun aux deux producteurs de `PRODUCED_DOC`) |
| `NotificationProperties` | +record `Inbox(Duration retention, Duration pollDelay)`, défaut `retention = 30j` (D-NOTIF-RETENTION), `pollDelay = 1j` (miroir `Outbox`/`WorkflowProperties`) |
| `ListSorts` | +`USER_NOTIFICATIONS = "createdAt,desc"` |

**Aucun changement `SecurityConfig`** — le chemin `/api/notifications/**` est déjà couvert par
`anyRequest().authenticated()`.

### Frontend — fichiers modifiés

| Fichier | Changement |
|---|---|
| `frontend/src/api/notifications.ts` (nouveau) | `listNotifications(params)`, `markNotificationRead(id)`, `markAllNotificationsRead()` — miroir `admin.ts` (axios direct + `Page<T>`) |
| `frontend/src/stores/notifications.ts` | `connect()` déclenche d'abord un `GET` de backfill (page 0, taille 20) qui remplace `items`/`read`, **puis** ouvre le SSE comme aujourd'hui pour les événements live (dédoublonnage par `id`, un événement déjà backfillé ne duplique pas) ; `markRead`/`markAllRead` appellent le `PATCH`/`POST` puis mettent à jour l'état local (pas d'attente réseau bloquante côté UI — optimistic, rollback si l'appel échoue) ; plus de plafond arbitraire à 100 (pagination serveur) |
| `frontend/src/components/notifications/NotificationDrawer.vue` | Réécrit en composants PrimeVue 5 : `OverlayBadge` (bell + badge non-lu), `Popover` (PrimeVue 5 — successeur de l'ancien `OverlayPanel`) pour le tiroir, `Button`, `ScrollPanel` pour la liste scrollable, `Tag`/classe pour l'état non-lu |

## Design Decisions

**Pourquoi `AddDossierElementUseCaseImpl` et pas `AttachGeneratedDocumentTask` directement** :
vérifié dans le code que `PRODUCED_DOC` a **2 producteurs réels** — le delegate Flowable
(`AttachGeneratedDocumentTask`, contexte `AccessContext.system("flowable-delegate")`) **et** le
tool MCP `GenerateDocumentTool` (`AddDossierElementUseCase.add(..., PRODUCED_DOC, ...)` direct,
sans passer par le moteur BPMN). Coupler la notification à `AttachGeneratedDocumentTask` seul
aurait silencieusement raté le chemin MCP. `AddDossierElementUseCaseImpl.add()` est le seul point
commun aux deux — évite la duplication de la résolution de participants dans 2 producteurs, même
raisonnement que le masquage centralisé dans `SendNotificationUseCaseImpl.buildPayload()`.

**Pourquoi pas de nouvelle règle ArchUnit de frontière** : les nouvelles dépendances croisées
(`domain.dossier.usecase` → `domain.workflow.port.out.WorkflowParticipantPort`,
`domain.notification.port.in.SendNotificationUseCase`) restent **domain → domain**, jamais
`domain → infrastructure` — couvert par les règles déjà en place
(`domain_never_depends_on_infrastructure`), précédent déjà accepté pour une dépendance croisée de
bounded context (`domain.workflow` → `domain.dossier.model.FieldDefinition`, TODO §55/§56).

**Pourquoi `markRead` scope le `recipient` en base et pas seulement dans le contrôleur** : même
discipline IDOR que le reste du dépôt (décision n°20, ACL de type) — un `id` de notification est
un UUID, mais scoper la requête `UPDATE ... WHERE id = ? AND tenant_id = ? AND recipient = ?`
plutôt que `findById` puis vérifier en mémoire évite une fenêtre où un bug de contrôleur
exposerait une notification d'un autre utilisateur.

**Risque signalé, pas un nouvel arbitrage** : `PrimeVue` est déjà enregistré globalement
(`main.ts`, thème Aura) mais **aucun composant du dépôt ne l'utilise réellement aujourd'hui** — la
plomberie existe, l'usage non. `NotificationDrawer.vue` sera donc la **première utilisation réelle
de composants PrimeVue** dans FlowForge, sans précédent local à copier pour le style d'import ou
l'intégration au thème clair/sombre. Décision D-NOTIF-UI-CONFORMITE déjà actée (migrer), ce
paragraphe documente juste l'absence de précédent, pas une nouvelle question — à vérifier
concrètement en Phase 5 (rendu réel, pas seulement Vitest monté).

## Non-Functional Requirements

- **Sécurité** : payload déjà masqué en amont (D-WEBHOOK-MASK) ; `recipient` scoping IDOR-safe en
  base pour `markRead` ; aucune nouvelle surface d'authentification (BFF/OIDC existant).
- **Performance** : écriture `user_notification` synchrone dans la même transaction que
  `notification_outbox` (`SendNotificationUseCaseImpl.send()` déjà transactionnel via le
  repository) — pas de nouvel appel réseau, un `INSERT` de plus par événement `BROWSER`. Lecture
  paginée (20/page), index dédié au tri `createdAt DESC` et au comptage non-lu.
  Nécessite de vérifier lors des tests de charge que `notification_outbox` (7j) et
  `user_notification` (30j) ne divergent pas trop en volume — la purge à 30j peut retenir jusqu'à
  ~4× plus de lignes que l'outbox pour le même trafic BROWSER.
- **Fiabilité** : purge automatique à 30 jours (job `@Scheduled`, même patron que
  `WorkflowRetentionJob` — idempotent, tolérant à un déploiement multi-run puisqu'il ne fait que
  supprimer des lignes déjà lues ou anciennes).
- **Compatibilité** : aucune rupture de contrat sur `POST /api/notifications/send` ni sur
  `GET /api/notifications/stream` (canaux EMAIL/WEBHOOK inchangés, seul le canal BROWSER gagne un
  effet de bord supplémentaire).
