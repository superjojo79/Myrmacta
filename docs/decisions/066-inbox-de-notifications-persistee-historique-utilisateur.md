# §66 — Inbox de notifications persistée — historique utilisateur

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §66 · [registre](README.md)

**Énoncé :** item **3.2** de `ROADMAP.md` (« Produit, pas cadré »), analyse initiale
[27-notifications-inbox.md](../specs/27-notifications-inbox.md). Origine : question
utilisateur (2026-09-22), après épuisement de la file produit (Vagues A/B/C + A4 +
`enrichissement-reporting` toutes livrées).

**Statut :** **clos** — [PR #135](https://github.com/superjojo79/FlowForge/pull/135) mergée sur
`main` (2026-09-23, squash `b3f03f9`), Phases 1–11 closes (historique ci-dessous).

**Constat corrigé en Phase 1 (2026-09-22) :** l'analyse d'origine sous-estimait l'état réel du
code — écrite avant la PR #132 (`enrichissement-reporting`, TODO §65), qui a introduit un canal
`NotificationChannel.BROWSER` (2 producteurs : `WorkflowStateEventListener`,
`EvaluateSlaAlertsUseCaseImpl`) et un composant frontend déjà monté globalement
(`NotificationDrawer.vue` — cloche, badge non-lu, tiroir), sans le documenter comme une brique
d'inbox. Le mécanisme est cependant **100% éphémère côté client** (état en mémoire d'onglet,
perdu au reload, plafonné à 100 items) — `NotificationOutboxRelay.dispatchBrowser` marque la
ligne `PROCESSED` que le push SSE ait atteint quelqu'un ou non, donc une notification manquée
pendant une déconnexion est perdue définitivement. Pas une introduction ex nihilo : rendre
persistant un mécanisme déjà branché et déjà visible en UI.

**Phase 1 (requirements) close (2026-09-22).** 6 décisions tranchées une par une via
`AskUserQuestion` : **D-NOTIF-PERSISTENCE** = nouvelle table `user_notification`
(`tenant_id`/`recipient`/`payload`/`created_at`/`read_at`), écrite dans
`SendNotificationUseCaseImpl.send()` pour le canal `BROWSER`, indépendante de
`notification_outbox` (pas de couplage retry technique / historique de lecture) ;
**D-NOTIF-SCOPE** = 5 producteurs ce cycle (les 2 existants + 3 nouveaux : tâche réassignée via
`ReassignWorkflowTaskUseCase`, tâche terminée par un tiers via `CompleteTaskUseCaseImpl`,
document généré via `GenerateDocumentUseCaseImpl`) — **« dossier affecté à l'utilisateur »
explicitement retiré du périmètre**, aucun champ `assignee`/`manager` n'existe sur `Dossier`
aujourd'hui, introduire ce concept serait un changement de domaine à part entière ;
**D-NOTIF-RETENTION** = 30 jours, purge automatique (même patron que `WorkflowRetentionJob`) ;
**D-NOTIF-SCOPE-VS-TASKS** = vue strictement séparée de l'inbox tâches (`/rapports`), aucune
fusion ; **D-NOTIF-SOURCE-VERITE** = `GET /api/notifications` (paginé) hydrate le store au
montage puis le flux SSE existant continue en direct, `markRead`/`markAllRead` passent par
`PATCH /api/notifications/{id}/read` (source de vérité serveur, cohérente entre onglets/appareils)
au lieu de muter uniquement l'état local actuel ; **D-NOTIF-UI-CONFORMITE** = migration de
`NotificationDrawer.vue` vers PrimeVue dans ce cycle (actuellement CSS scoped fait main, non
conforme à `docs/specs/11-frontend.md`), coût marginal vu qu'il est de toute façon réécrit pour
le branchement REST.

**Impacte (prévisionnel) :** `domain.notification` (nouveau port/use cases lecture +
marquage lu), migration Liquibase `user_notification` (schéma par tenant), nouveau contrôleur REST
`GET /api/notifications` + `PATCH /api/notifications/{id}/read`, `SendNotificationUseCaseImpl`
(écriture du 4ᵉ point de persistance), job de purge (miroir `WorkflowRetentionJob`),
`frontend/src/stores/notifications.ts` + `frontend/src/components/notifications/NotificationDrawer.vue`
(réécriture PrimeVue + branchement REST), golden OpenAPI, `docs/guides/GUIDE-UTILISATEUR.md` (aucun nouvel
écran admin, mais comportement utilisateur changé). Suite : Phase 3 (design).

**Phase 3 (design) close (2026-09-22).** Architecture détaillée dans
[docs/ai/design/2026-09-22-feature-inbox-notifications-persistee.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-22-feature-inbox-notifications-persistee.md) :
nouveau port `UserNotificationRepository` (miroir `NotificationOutboxRepository`), table
`user_notification` (migration `023-user-notification.yaml`), `SendNotificationUseCaseImpl`
écrit désormais 2 lignes pour le canal BROWSER (outbox + inbox, même transaction), nouveau
contrôleur `NotificationInboxController` (`GET`/`PATCH /read`/`POST /read-all`, self-scoped,
aucune restriction de rôle — distinct de `NotificationSendController` déjà présent sur le même
préfixe), `NotificationInboxRetentionJob` (miroir `WorkflowRetentionJob`). 2 précisions
techniques trouvées en investiguant le code réel, documentées comme corollaires directs des
décisions Phase 1 (pas de nouvel arbitrage produit) : (1) contrat de payload `type`/`title`/`body`
non uniforme entre les 2 producteurs BROWSER existants — `WorkflowStateEventListener` n'écrit pas
`title`/`body` contrairement à `EvaluateSlaAlertsUseCaseImpl`, corrigé dans ce cycle puisque
l'écart devient visible une fois l'historique persistant ; (2) `PRODUCED_DOC` a 2 producteurs
réels (`AttachGeneratedDocumentTask` BPMN **et** `GenerateDocumentTool` MCP) — la notification
« document généré » est câblée dans `AddDossierElementUseCaseImpl.add()`, seul point commun aux
deux, pas dans le delegate Flowable seul (aurait raté le chemin MCP). Risque signalé (pas un
arbitrage) : `NotificationDrawer.vue` sera la **première utilisation réelle de composants
PrimeVue** du dépôt — la dépendance est déjà enregistrée globalement (`main.ts`, thème Aura) mais
aucun composant existant ne l'utilise, donc pas de précédent local à copier. Suite : Phase 4
(planning).

**Phase 4 (planning) close (2026-09-22).** Plan de tâches
[docs/ai/planning/2026-09-22-feature-inbox-notifications-persistee.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-22-feature-inbox-notifications-persistee.md) :
12 milestones séquencés (M1 fondation domaine/persistance → M2 écriture au point de couture +
correctif payload `WorkflowStateEventListener` → M3 REST + golden OpenAPI → M4 rétention,
parallélisable à M3 → M5/M6/M7 les 3 nouveaux producteurs, parallélisables entre eux une fois M2
clos → M8 client + store frontend → M9 `NotificationDrawer.vue` en PrimeVue → M10 Playwright
(dont non-régression explicite des 2 producteurs déjà en production) → M11 documentation → M12
vérification globale). Jeu de tests tracé en amont
([docs/ai/testing/2026-09-22-feature-inbox-notifications-persistee.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-22-feature-inbox-notifications-persistee.md),
18 `UT-NOTIF-*`, 7 `IT-NOTIF-*`, 8 `E2E-NOTIF-*`) — chaque scénario tracé vers au moins une tâche,
table de couverture exhaustive en fin de doc planning. Suite : Phase 5 (implémentation),
démarrage par **M1**.

**Phase 5 (implémentation) — livrée (2026-09-22), M1 à M12, en continu sur demande explicite
utilisateur.** Backend (M1-M7) : table `user_notification` (migration `023`) + port
`UserNotificationRepository` + `NotificationInboxUseCase` ; `SendNotificationUseCaseImpl` écrit
désormais l'inbox pour le canal BROWSER (après l'outbox, transactions séparées — pas de
gestionnaire de transaction partagé accessible depuis le domaine, déviation assumée du « même
transaction » du doc design) ; `NotificationInboxController` self-scoped
(`GET`/`PATCH .../read`/`POST .../read-all`) ; `NotificationInboxRetentionJob` (30j). Les 3
nouveaux producteurs : `WorkflowLinkVisualizationUseCaseImpl.reassign` (`TASK_REASSIGNED`),
`CompleteTaskUseCaseImpl.complete` (`TASK_COMPLETED_BY_OTHER`, exclut l'acteur),
`AddDossierElementUseCaseImpl.add` (`DOCUMENT_GENERATED`, `PRODUCED_DOC` uniquement — couvre les 2
producteurs réels via le seul point commun). Frontend (M8-M9) : `stores/notifications.ts` réécrit
(backfill REST avant SSE, dédoublonnage par id, `markRead`/`markAllRead` optimistes avec
rollback) ; `NotificationDrawer.vue` réécrit avec de vrais composants PrimeVue (`Button`,
`OverlayBadge`, `ScrollPanel`). Playwright (M10) : 2 nouveaux scénarios, régression E2E-8
confirmée. Doc (M11) : `docs/guides/GUIDE-UTILISATEUR.md` §6, statut mis à jour dans
`docs/specs/27-notifications-inbox.md` et `00-index.md`.

**2 déviations trouvées en implémentation, non silencieuses :**
- **M2** — le design décrivait une écriture outbox+inbox « dans la même transaction » ;
  `SendNotificationUseCaseImpl` est une classe domaine pure (pas de `@Transactional` accessible,
  ArchUnit interdit Spring dans `domain`) — les deux écritures sont donc deux appels
  transactionnels indépendants au niveau des adaptateurs, l'inbox après l'outbox (un échec de
  l'inbox n'orpheline jamais une ligne outbox qui délivrerait quand même).
- **M9** — `Popover` PrimeVue (Teleport + mesure DOM/floating-ui) ne s'ouvre pas de façon fiable
  sous jsdom sans polyfill `ResizeObserver` ni `attachTo document.body` ; vérifié empiriquement
  (3 tests rouges), remplacé par un panneau `v-if` local — toujours de vrais composants PrimeVue
  (`Button`/`OverlayBadge`/`ScrollPanel`) pour le reste, juste pas d'overlay flottant. Premier
  usage réel de PrimeVue du dépôt, aucun précédent local pour trancher ce point en amont.

**Preuves M12 (avant Phase 7/8) :** `./mvnw verify` (suite complète, aucune exclusion) → **2112
tests, 0 échec**, Jacoco tenu, ArchUnit conforme, golden OpenAPI stable. `npm run test` →
360/360 Vitest. `npm run test:e2e` → 80/80 Playwright (chromium pré-installé,
`PLAYWRIGHT_CHROMIUM_EXECUTABLE`).

**Phase 7 (check implementation) — close (2026-09-23), sur demande explicite utilisateur.**
Revue fichier par fichier de tout le diff (pas seulement les fichiers de M12.3, revérifiés). **1
bug réel trouvé et corrigé, pas seulement supposé absent comme l'affirmait la clôture M12** :
`stores/notifications.ts.backfill()` faisait un remplacement complet de `items.value` au lieu
d'une fusion — `connect()` déclenche `backfill()` sans l'attendre puis ouvre le SSE
immédiatement, donc un événement live arrivé avant la résolution du `fetch` de backfill était
silencieusement écrasé. Reproduit en TDD (test rouge confirmé sur l'ancien code en contrôlant
explicitement l'ordre de résolution des deux promesses), corrigé par une fusion dédoublonnée par
id. Reste du diff production relu sans autre écart (notamment vérifié empiriquement, pas
supposé, que le `UUID.fromString(dossierId)` non gardé de `CompleteTaskUseCaseImpl` est toujours
pré-validé par `WorkflowTaskController` avant l'appel).

**Phase 8 (testing) — close (2026-09-23).** Couverture Jacoco des 4 classes modifiées vérifiée
ligne par ligne via le rapport HTML (`target/site/jacoco/**/*.java.html`) : les 6 branches
manquées signalées appartiennent toutes à du code préexistant hors périmètre, le nouveau code du
cycle est intégralement couvert — pas de test ajouté pour éviter la dérive de périmètre. 3 écarts
plan/livré traités explicitement plutôt que laissés de côté : `IT-NOTIF-6` (Testcontainers
PostgreSQL) jugé superflu au vu de la politique du dépôt (réservé aux scénarios à comportement
PostgreSQL réel, ce qui n'est pas le cas d'une purge par date) et déjà couvert en H2 ; 2 scénarios
E2E du plan initial (persistance après reload complet, cohérence entre deux contextes navigateur)
non testables de façon significative dans le harnais de mocks Playwright de ce dépôt, propriété
déjà prouvée autrement (IT-NOTIF-3/4 + test store de rollback). 1 nouveau scénario Playwright
ajouté (`E2E-NOTIF-3`, rendu des 3 nouveaux types de payload) pour combler la partie testable de
cet écart.

**Phase 9 (revue finale) — close (2026-09-23).** Revue holistique du diff complet (59 fichiers) :
alignement design confirmé, pas de dépendance circulaire (le contexte Spring complet boote sans
erreur dans `./mvnw verify`), migration Liquibase réversible (`rollback: dropTable`), aucune
rupture de contrat (tous les appelants des constructeurs modifiés mis à jour dans le même commit
que le changement de signature). Incohérence trouvée et corrigée dans cette clôture même : les
compteurs de tests et le constat « aucun écart trouvé » de M12 dataient d'avant les Phases 7/8 et
ne reflétaient plus l'état réel — corrigés ci-dessus plutôt que laissés stables par erreur.

**Preuves finales (après Phase 7/8) :** `./mvnw verify` inchangé (backend non retouché en Phase
7/8) — 2112 tests, 0 échec. `npm run test` → **361/361** Vitest (+1, régression race condition).
`npm run test:e2e` → **81/81** Playwright (+1, `E2E-NOTIF-3`). **Feature prête pour PR** — aucun
blocage identifié. Pas de PR ouverte à ce stade (non demandé) — branche de session
`claude/brave-curie-w7kocb` poussée, à jour.

**Phase 10 (analyse adversariale, subagent Opus 5.5) — close (2026-09-23), sur demande
explicite utilisateur.** Revue indépendante lancée via `Agent(model: "opus")`, méthode empirique
(compilation/tests réels, pas seulement lecture statique — JDK 25 indisponible dans ce conteneur,
contournement `-Dmaven.compiler.release=21 -Dskip.frontend=true` en mode hors ligne, comme pour le
reste de ce cycle). **1 bug bloquant confirmé et corrigé (F1)**, 1 gap de fiabilité confirmé et
corrigé (F2), 2 écarts moyens documentés comme décision produit à trancher (F3, F4), 3 findings
faibles actés en dette non bloquante (F5-F7). Sécurité jugée saine — pas d'IDOR, injection, XSS ni
désérialisation dangereuse, vérifié empiriquement (scoping SQL `(id, tenant_id, recipient)`
partout, `@Query` paramétrées, `PageQuery` bornée 1-100, interpolation Vue jamais `v-html`).

- **F1 (Élevée, bloquant, corrigé)** — l'id porté par l'événement SSE `BROWSER` était celui de la
  ligne `notification_outbox`, jamais celui de la ligne `user_notification` correspondante
  (générée indépendamment par l'adaptateur, `UUID.randomUUID()` propre). Preuve : un clic sur une
  notification reçue en direct envoyait `PATCH /api/notifications/{outboxId}/read` → 404 (rollback
  optimiste silencieux côté store) ; la dédup backfill/SSE ne matchait jamais → doublons visibles,
  badge faux. Corrigé par **D-NOTIF-SSE-ID** : l'id `user_notification` est désormais généré dans
  `SendNotificationUseCaseImpl.send()` *avant* l'écriture outbox, embarqué dans le snapshot BROWSER
  (`NotificationPayloadJson.browser(userId, payload, inboxId)`, nouveau champ `inboxId`), extrait
  par `NotificationOutboxRelay.dispatchBrowser` comme id de l'événement SSE au lieu de
  `record.id()`. `UserNotificationRepository.save` prend maintenant l'id en paramètre au lieu de le
  générer lui-même. Nouveau test `send_browserChannel_outboxSnapshotCarriesSameInboxIdAsUserNotificationSave`
  (`SendNotificationUseCaseImplTest`) + `NotificationOutboxRelayTest` mis à jour pour vérifier l'id
  transmis, jamais celui de l'outbox.
- **F2 (Moyenne, corrigé)** — une reconnexion SSE (timeout horaire, coupure réseau) ne relançait
  jamais `backfill()` (`backfilled` restait `true` pour toujours), alors que le relais marque
  `PROCESSED` qu'un client soit connecté ou non — tout ce qui arrive pendant le trou de reconnexion
  était perdu jusqu'au rechargement complet, contraire au but même de la persistance. Corrigé :
  `backfilled` remis à `false` dans `source.onerror`, la fusion dédoublonnée déjà existante (fix
  Phase 7) gère le merge en toute sécurité. Nouveau test « re-backfills after an SSE reconnect,
  merging with what is already present ».
- **F3 (Moyenne, non traité — décision produit à soumettre)** — `unreadCount` frontend est une
  estimation locale (items chargés, ≤20 sans pagination dans le tiroir), pas un compte serveur,
  alors que les exigences Phase 1 demandaient explicitement un compte cohérent. `countUnread`
  existe côté port/adapter mais n'est exposé par aucun endpoint.
- **F4 (Moyenne, non traité — décision produit à soumettre)** — sémantique transactionnelle
  incohérente entre les 3 nouveaux producteurs : `reassign` n'est pas `@Transactional` (Flowable
  commite avant l'appel notif, un échec de notif renvoie 500 après une réassignation déjà
  effective) ; `complete` est `@Transactional` (un échec de notif annule la complétion de tâche —
  la Javadoc affirmant le contraire est fausse) ; `add` hérite du comportement de son appelant
  BPMN/MCP. Aucune sémantique unique choisie ce cycle.
- **F5-F7 (Faible, dette actée, non bloquante)** — purge inbox en N `DELETE` individuels au lieu
  d'un bulk `@Modifying @Query` (l'outbox utilise déjà le bulk) ; edge case
  `TASK_COMPLETED_BY_OTHER` manqué si la tâche complétée est la dernière du workflow (atténué par
  `WORKFLOW_STATE_CHANGED`) ; `markRead` sur une notification déjà lue écrase `readAt` sans garde ;
  rétention négative/nulle purgerait tout (même comportement que l'outbox, pas une régression) ;
  petites races sur l'optimistic update qui se corrigent au rechargement.

**Preuves F1/F2 :** `test-compile` du module complet propre après les deux correctifs ; 63 tests
backend ciblés (package notification) verts — `SendNotificationUseCaseImplTest` 22,
`NotificationOutboxRelayTest` 8, `NotificationPayloadJsonTest` 3,
`JpaUserNotificationRepositoryAdapterTest` 11, `NotificationInboxUseCaseImplTest` 4,
`NotificationInboxApiTest` 6, `NotificationSensitiveFieldMaskingEndToEndTest` 1,
`NotificationPersistenceIT` 8. Suite Vitest complète **362/362** (+1). Suite backend complète
(2112+ tests) non rejouée dans ce conteneur (JDK 25 indisponible, même limite d'environnement que
pour la revue elle-même), contournée par compilation/tests ciblés en `-Dmaven.compiler.release=21`.

**Feature bloquée par F1 avant ce cycle, débloquée après (F1/F2 corrigés).**

**Phase 11 (correction de tous les findings restants) — close (2026-09-23), sur demande explicite
utilisateur (« Corrige tous les findings »).** F3 et F4 étaient documentés comme décisions produit
à soumettre — l'utilisateur a tranché en demandant leur correction plutôt qu'une discussion
supplémentaire ; F5-F7 traités dans la foulée.

- **F3 (corrigé)** — nouveau endpoint `GET /api/notifications/unread-count`
  (`NotificationInboxUseCase.unreadCount`, délègue à `countUnread` déjà existant) : le badge
  frontend est désormais une valeur serveur, plus une estimation locale. `stores/notifications.ts`
  la récupère dans `backfill()` (en parallèle du fetch de liste), l'ajuste en optimiste sur
  `markRead`/`markAllRead`/réception SSE live, avec la même discipline « ne jamais régresser en
  dessous de ce qui est déjà visible localement » (`Math.max(count, unread visible localement)`)
  que la fusion d'items — un live event arrivé pendant que la requête de comptage est en vol ne
  peut plus être écrasé par un instantané serveur pris avant que sa ligne ne soit visible. `markAllRead`
  se déclenche désormais sur `unreadCount === 0` plutôt que `previouslyUnread.length === 0` (l'ancien
  garde-fou sautait l'appel serveur quand tout ce qui était *chargé* était déjà lu, même si des
  éléments non lus existaient au-delà des 20 derniers). Migration `024-user-notification-unread-index`
  (index `(tenant_id, recipient, read_at)`, équivalent portable H2/PostgreSQL d'un index partiel
  PostgreSQL, puisque `countUnread` passe d'un mort-code à une requête réellement invoquée à chaud).
  Tiroir : bouton « Charger plus » (`store.loadMore()`), l'historique au-delà de 20 notifications
  était auparavant inaccessible depuis l'UI. Synchronisation multi-onglets reste au rechargement
  seul (limitation déjà documentée, pas un F-numéroté).
- **F4 (corrigé)** — sémantique transactionnelle unifiée sur les 3 producteurs, alignée sur le
  patron déjà établi de `WorkflowTaskController#complete` (rollback ensemble) plutôt que celui de
  `reassign` (commits indépendants) : `WorkflowLinkVisualizationController#reassign` et
  `GenerateDocumentTool#generateDocument` sont désormais `@Transactional`, avec le même compromis
  déjà accepté pour `AttachGeneratedDocumentTask` (objet de stockage externe orphelin en cas de
  rollback après écriture du PDF, pas un problème d'intégrité des données). Javadoc fausse de
  `CompleteTaskUseCaseImpl` corrigée (affirmait que la complétion était « already committed » avant
  l'appel de notification — faux depuis que le contrôleur est `@Transactional`).
- **F5 (corrigé)** — `SpringDataUserNotificationRepository.deleteByCreatedAtBefore` remplacé par un
  `@Modifying @Query` bulk delete, miroir de `deleteTerminalOlderThan` déjà utilisé côté outbox
  (évite le chargement en mémoire de toutes les lignes purgées suivi d'un `DELETE` par id).
- **F6 (corrigé)** — `CompleteTaskUseCaseImpl.complete()` résout désormais le lien actif *avant*
  `completeTask(...)`, pas après : si la tâche complétée est la dernière du workflow, la
  transition d'état déclenchée par `completeTask` peut faire passer le lien à inactif avant que la
  méthode ne retourne — une résolution après coup manquait alors silencieusement les autres
  participants à notifier. Test de régression avec `InOrder` verrouillant l'ordre
  lien-avant-complétion.
- **F7 (corrigé, uniquement le point actionnable)** — `UserNotificationJpaEntity.markRead` est
  désormais idempotent (ne réécrit plus `readAt` si déjà renseigné). Les 3 autres points listés en
  Phase 10 sous F7 (rétention négative non gardée, petites races d'optimistic update qui se
  corrigent au rechargement, notifications à destinataires non humains) restaient explicitement
  qualifiés de non-régression/non-fuite par la revue elle-même — laissés tels quels, pas de
  changement de comportement pour ces trois-là.

**Preuves Phase 11 :** golden OpenAPI régénéré et stable (ajout net : endpoint
`GET /api/notifications/unread-count` + schéma `UnreadCountResponse`, diff propre de 18 lignes).
135 tests backend ciblés verts (les 8 classes notification de la Phase 10 + `CompleteTaskUseCaseImplTest`
19, `WorkflowLinkVisualizationUseCaseImplTest` 19, `GenerateDocumentToolTest` 8,
`AddDossierElementUseCaseImplTest` 21, `NotificationInboxRetentionJobTest` 1), plus `WorkflowApiTest`
10 et `McpEndpointApiTest` 4 (endpoints `@Transactional` touchés) et `ArchitectureRulesTest`/
`ArchitectureRulesFixtureTest` (8, domaine toujours pur). Suite Vitest complète **366/366** (+4 :
race-safety du compteur, rollback `markAllRead`, garde `markAllRead` au-delà des items chargés,
« Charger plus »). Playwright complet **81/81** (route `/api/notifications/unread-count` ajoutée à
`mockSession()` — sans elle, `backfill()`'s `Promise.all` échoue sur le 404 et plus aucune
notification ne charge, même celles déjà mockées ; les 4 tests notifications + les 77 autres
scénarios rejoués intégralement, pas seulement les 4 touchés). Suite backend complète non rejouée
dans ce conteneur (JDK 25 indisponible, même limite que Phase 10). **Feature prête pour PR — tous
les findings de la Phase 10 traités**, aucun report vers un suivi post-merge.

**Détail :** [`docs/ai/requirements/2026-09-22-feature-inbox-notifications-persistee.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-22-feature-inbox-notifications-persistee.md),
[`docs/ai/design/2026-09-22-feature-inbox-notifications-persistee.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-22-feature-inbox-notifications-persistee.md),
[`docs/ai/planning/2026-09-22-feature-inbox-notifications-persistee.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-22-feature-inbox-notifications-persistee.md),
[`docs/ai/testing/2026-09-22-feature-inbox-notifications-persistee.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-22-feature-inbox-notifications-persistee.md),
[`docs/ai/implementation/2026-09-22-feature-inbox-notifications-persistee.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-22-feature-inbox-notifications-persistee.md).
