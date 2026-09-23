---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Scénarios dérivés des critères de succès de
> [requirements](../requirements/2026-09-22-feature-inbox-notifications-persistee.md) et des
> composants de [design](../design/2026-09-22-feature-inbox-notifications-persistee.md). Chaque
> scénario est référencé par un ID stable (`IT-NOTIF-*` backend, `E2E-NOTIF-*` Playwright,
> `UT-NOTIF-*` Vitest) repris tel quel dans `docs/ai/planning/**` pour tracer scénario → tâche.
>
> **Phase 8 close (2026-09-23).** Statuts ci-dessous = état réel vérifié (`./mvnw verify` M12 :
> 2112 tests ; Vitest 361/361 ; Playwright 81/81), pas la liste de souhaits de la Phase 4. 3 écarts
> plan → livré trouvés et traités explicitement (pas silencieusement laissés de côté) : voir
> notes sous IT-NOTIF-6, E2E-NOTIF-2/3/4/5/6 et § *Couverture Jacoco*.

## Test Coverage Goals

- Backend : Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % tenus (seuils déjà en place, `fiabilisation-tests-plateforme`) — vérifié sur le run complet M12, pas seulement supposé.
- Frontend : Vitest **et** Playwright obligatoires (le SPA et le contrat `GET/PATCH /api/notifications` sont impactés — convention `flowforge-conventions`).
- Alignement direct avec les 6 critères de succès du doc requirements (persistance hors connexion, cohérence multi-onglets, 5 producteurs au même point de couture, purge 30j, conformité PrimeVue, non-régression).

## Unit Tests

### `JpaUserNotificationRepositoryAdapter` / `UserNotificationJpaEntity`

- [x] **UT-NOTIF-1** à **UT-NOTIF-5** — `JpaUserNotificationRepositoryAdapterTest` (11 tests, dont l'isolation multi-tenant `page_scopedToTenant_...` ajoutée pour IT-NOTIF-2, voir plus bas).

### `SendNotificationUseCaseImpl` (modifié)

- [x] **UT-NOTIF-6** — `send_browserChannel_alsoPersistsUserNotification` + `send_browserChannelPayload_persistedWithoutUserIdWrapper`. **Précision par rapport au plan initial** : pas « même transaction » comme écrit ici en Phase 4 — corrigé en Phase 5/design (déviation documentée dans le doc implementation) : deux écritures transactionnelles indépendantes, l'inbox après l'outbox. Une vraie exception de `userNotificationRepository.save(...)` se propage normalement (pas de `try/catch` dans `send()`), pas besoin d'un test dédié pour une propriété qui découle directement de l'absence de `catch`.
- [x] **UT-NOTIF-7** — `send_emailOrWebhookOnly_neverPersistsUserNotification`.
- [x] **UT-NOTIF-8** — `send_multiChannelIncludingBrowser_writesOneOutboxRowPerChannelAndOneInboxRow`.

### `NotificationInboxUseCaseImpl`

- [x] **UT-NOTIF-9** — `markRead_repositoryReturnsFalse_throwsNotFound` (`NotificationInboxUseCaseImplTest`).
- [x] **UT-NOTIF-10** — `markAllRead_idempotent_secondCallUpdatesNothing` (au niveau repository, propriété identique côté use case car pure délégation).

### Producteurs (3 nouveaux)

- [x] **UT-NOTIF-11** — `reassign_notifiesNewAssigneeOnly` (`WorkflowLinkVisualizationUseCaseImplTest`).
- [x] **UT-NOTIF-12** — `complete_notifiesOtherParticipantsButNotTheActor` (`CompleteTaskUseCaseImplTest`).
- [x] **UT-NOTIF-13** — `complete_noActiveLink_completesWithoutNotifyingAnyone`.
- [x] **UT-NOTIF-14** — `add_producedDocWithActiveLink_notifiesAllParticipants_systemContext` **et** `..._userContext` (2 tests séparés plutôt qu'un seul paramétré, plus lisible en JUnit 5 sans dépendance supplémentaire) — les deux contextes réels (`AttachGeneratedDocumentTask`/`AccessContext.system` et `GenerateDocumentTool`/`AccessContext.User`) sont couverts.
- [x] **UT-NOTIF-15** — `add_note_neverTriggersNotification` (`AddDossierElementUseCaseImplTest`).
- [x] **UT-NOTIF-16** — `WorkflowStateEventListenerTest.onEvent_fansOutBrowserWorkflowStateChangedToParticipants` étendu (assertions `title`/`body` non vides + `dossierId`).

### `NotificationInboxRetentionJob`

- [x] **UT-NOTIF-17/18** — couverts au niveau repository par `JpaUserNotificationRepositoryAdapterTest.purgeOlderThan_deletesOnlyOlderRows_readOrUnread` (borne exacte exercée avec des lignes lues et non lues) ; `NotificationInboxRetentionJobTest` prouve la délégation `properties.inbox().retention()` → `repository.purgeOlderThan(cutoff)`.

### Race condition frontend (trouvée en Phase 7)

- [x] **Nouveau, hors plan initial** — `does not lose a live notification that arrives before the backfill request resolves` (`stores/notifications.spec.ts`). Confirmé en TDD : rouge sur le code pré-Phase-7 (`items.value = page.content.map(...)` écrasait un événement SSE déjà reçu), vert après le correctif (fusion dédoublonnée par id).

## Integration Tests

- [x] **IT-NOTIF-1** — `NotificationInboxApiTest.list_returnsOnlyCurrentRecipientsNotifications`.
- [x] **IT-NOTIF-2** — **traité différemment du plan initial**, avec raison explicite : l'app est mono-tenant par instance (décision n°13), donc deux tenants ne peuvent jamais coexister derrière un même contexte REST pour un aller-retour HTTP réel. Couvert à la place au niveau SQL/repository (`JpaUserNotificationRepositoryAdapterTest.page_scopedToTenant_sameRecipientSubjectAcrossTenantsStaysIsolated`, deux `tenant_id` réels, même `recipient`) — même limite déjà documentée pour ce type de scénario ailleurs dans le dépôt (`JpaBpmnDraftRepositoryAdapterTest.twoTenantsWithSameProcessDefinitionKey...`).
- [x] **IT-NOTIF-3** — `markRead_ownNotification_returns204...` + `markRead_belongsToAnotherRecipient_returns404NotForbidden` + `markRead_unknownId_returns404`.
- [x] **IT-NOTIF-4** — `readAll_marksAllOfCurrentRecipientsUnreadNotifications`.
- [x] **IT-NOTIF-5** — `list_withoutAuthentication_returns401`.
- [x] **IT-NOTIF-6** — **retiré du périmètre, avec raison explicite** (pas silencieusement abandonné) : le plan initial demandait un test Testcontainers PostgreSQL dédié pour le job de purge. Revu en Phase 8 : la requête de purge (`DELETE ... WHERE created_at < cutoff`) n'a aucun comportement spécifique à PostgreSQL (pas de JSON interrogé, pas d'index partiel, pas de séquence) — le dépôt réserve délibérément Testcontainers aux 3 scénarios qui en ont réellement besoin (`TenantSchemaIsolationIT`, `TenantRoleIsolationIT`, `SequentialReferenceMultiPodIT`) et garde H2 pour le reste. `JpaUserNotificationRepositoryAdapterTest.purgeOlderThan_...` (H2, repository réel non mocké) suffit et évite une IT redondante.
- [x] **IT-NOTIF-7** — `OpenApiContractIT` vert (golden régénéré, diff stable re-vérifié une 2ᵉ fois sans `-Dopenapi.update`).

## End-to-End Tests (Playwright)

- [x] **E2E-NOTIF-1** — livré tel quel : notification jamais reçue en SSE, visible uniquement via le backfill REST.
- [x] **E2E-NOTIF-2** — **renommé/consolidé par rapport au plan** : couvre en réalité le scénario prévu à l'origine sous ce numéro (« clic → `PATCH .../read` ») plutôt que « persistance après reload complet de la page » (celui-ci nécessiterait un vrai rechargement de page dans un harnais qui mocke les routes par test — moins de valeur ajoutée que de re-tester `markRead`, déjà prouvé côté store par `rolls back the optimistic read state when the server call fails`).
- [x] **E2E-NOTIF-3** (nouveau, ajouté en Phase 8) — les 3 payloads des nouveaux producteurs (`TASK_REASSIGNED`/`TASK_COMPLETED_BY_OTHER`/`DOCUMENT_GENERATED`) s'affichent correctement dans le tiroir. Couvre ce que les scénarios E2E-4/5/6 du plan visaient, sans dupliquer la preuve déjà apportée côté backend (`UT-NOTIF-11/12/14`, qui prouvent que les *bons destinataires* reçoivent l'événement) — un seul test de rendu suffit puisque le chemin d'affichage est identique quel que soit le producteur.
- [x] **E2E-NOTIF « régression »** — confirmé via le `E2E-8` déjà existant, toujours vert après la réécriture complète du store/drawer.
- [ ] **Scénarios du plan explicitement non couverts, avec raison** :
  - *Persistance après reload complet de la page* (plan E2E-NOTIF-2 d'origine) et *cohérence entre deux contextes navigateur* (plan E2E-NOTIF-3 d'origine) — non testables de façon significative dans ce harnais Playwright (routes mockées par test, pas de backend réel partagé entre deux pages/contextes). La propriété réelle qu'ils visaient à prouver (persistance serveur du `read_at`) est déjà couverte par IT-NOTIF-3/4 (backend réel) et par le test store `rolls back the optimistic read state when the server call fails` (le fait que l'état revient à son état serveur en cas d'échec prouve que le client ne fait pas foi).
  - *Thème sombre PrimeVue* (mentionné dans le plan E2E-NOTIF-7 d'origine) — l'app ne bascule pas de thème aujourd'hui (`main.ts` n'a qu'un seul preset Aura, pas de toggle), rien à tester.

## Test Data

- Fixtures : réutiliser les dossiers/workflows de démonstration déjà déployés (process `-smoke`, `grille-eligibilite-client`) plutôt que d'en créer de nouveaux — cohérent avec les producteurs 5/6/7 qui s'appuient sur des liens workflow et tâches déjà exercés par les suites existantes.
- Deux utilisateurs de test avec rôles différents (participant A complète, participant B reçoit) pour **UT-NOTIF-12** (niveau unitaire, pas E2E — voir ci-dessus).
- Deux tenants réels (même pattern que `JpaBpmnDraftRepositoryAdapterTest`) pour **IT-NOTIF-2**.

## Test Reporting & Coverage

- `./mvnw verify` (M12) : **2112 tests, 0 échec**, Jacoco LINE/BRANCH tenus.
- `cd frontend && npm run test` : **361/361** (Vitest, +1 vs M9 — test de régression race condition).
- `cd frontend && npm run test:e2e` : **81/81** (Playwright, +1 vs M10 — `E2E-NOTIF-3`).
- Golden OpenAPI diff vérifié 2 fois (génération + re-vérification stable sans `-Dopenapi.update`).

### Couverture Jacoco — vérifiée ligne par ligne, pas supposée

Le rapport `jacoco.csv` du run M12 montrait des branches manquées sur 4 classes touchées
(`AddDossierElementUseCaseImpl`, `CompleteTaskUseCaseImpl`, `WorkflowLinkVisualizationUseCaseImpl`,
`SendNotificationUseCaseImpl`). Vérification ligne par ligne (rapport HTML Jacoco,
`target/site/jacoco/**/*.java.html`) en Phase 8 : les **6 lignes signalées appartiennent toutes à
du code préexistant, jamais touché par ce cycle** (`assertDocumentTypeAllowed`, la garde
cross-tenant de `AddDossierElementUseCaseImpl.add`, la validation `taskId`/`tenantId` déjà
présente avant M5/M6/M7). Le nouveau code de ce cycle (les 3 méthodes `notify*`/`notifyOtherParticipants`
et l'écriture inbox dans `send()`) est intégralement couvert. Pas de nouveau test ajouté pour ces
6 lignes — elles ne font pas partie du périmètre TODO §66, les ajouter serait de la dérive de
périmètre plutôt qu'un vrai gap de cette feature.

## Manual Testing

- Rendu PrimeVue (`Button`/`OverlayBadge`/`ScrollPanel`) confirmé dans un vrai navigateur Chromium via Playwright (E2E-NOTIF-1/2/3 + E2E-8), pas seulement Vitest monté — répond au risque signalé en design (premier usage réel de PrimeVue du dépôt).
- `GUIDE-UTILISATEUR.md` §6 relu : section Notifications présente, cohérente avec le comportement livré.

## Performance Testing

- Pas de test de charge dédié pour ce cycle (volume attendu du même ordre que `notification_outbox`, déjà éprouvé) — seul point à surveiller signalé en design : `user_notification` (30j) peut retenir ~4× plus de lignes que l'outbox (7j) au même trafic BROWSER, à confirmer par la volumétrie réelle plutôt qu'un test de charge synthétique.

## Bug Tracking

- 1 bug réel trouvé et corrigé en Phase 7 (race condition backfill/SSE, `stores/notifications.ts`) — détail complet dans `docs/ai/implementation/2026-09-22-feature-inbox-notifications-persistee.md` § Déviations, pas dupliqué ici.
- Aucun autre écart trouvé en Phase 8 au-delà des 3 ajustements de périmètre de test documentés ci-dessus (IT-NOTIF-6, E2E-NOTIF-2/3, couverture Jacoco).
