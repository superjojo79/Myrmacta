---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

> Phase 4 (Create Initial Plan) close (2026-09-02). Découpage en milestones TDD (rouge → vert →
> refactor), même patron que les features précédentes (`connecteur-http-rest-service`,
> `connecteurs-hors-v1`, `assainissement-dette-technique`). Chaque milestone est indépendamment
> vérifiable par `./mvnw verify` (ou test ciblé + verify avant claim, cf. skill `verify`).

## Décision de granularité reportée depuis les requirements (résolue ici, puis corrigée en Phase 5)

**Modélisation BPMN de référence** — décision initiale (Phase 4) : pas de process d'exemple livré
dans `src/main/resources/processes/`, fixture `src/test/resources/**` uniquement. **Corrigée en
Phase 5** (cf. implementation doc, écart n°1) : l'hypothèse de départ était fausse
(`DeployBpmnProcessUseCaseImpl` n'est pas sur le chemin des fixtures `*-smoke.bpmn20.xml`, qui sont
auto-déployées par Flowable au démarrage et démarrées directement par `RuntimeService` dans les
tests). `callback-smoke.bpmn20.xml` livré dans `src/main/resources/processes/`, suivant le patron
déjà établi par `signal-smoke.bpmn20.xml`/`smoke-process.bpmn20.xml`/
`create-child-dossier-smoke.bpmn20.xml`.

## Milestones

- [x] M1 — Domaine : `CallbackConnector`, `CallbackResolution`, exceptions, `ConnectorCapability.CALLBACK`
- [x] M2 — `WorkflowEnginePort.correlateMessage` + `FlowableWorkflowEngineAdapter` (moteur réel)
- [x] M3 — `CallbackConnectorRuntime` (port + use case), aucune régression sur `ConnectorTriggerRuntimeImpl`
- [x] M4 — `FlowableMessageCallbackConnector` (connecteur infra, config + résolution JSONPath)
- [x] M5 — `CallbackWebhookController` + gestion d'erreurs 404/409
- [x] M6 — Tests d'intégration bout-en-bout (succès, 404, 409, idempotence, auth)
- [x] M7 — Vérification globale, couverture Jacoco, doc catalogue, `CHANGELOG.md`

## Task Breakdown
**What specific work needs to be done?**

### M1 — Domaine (TDD, aucune dépendance externe)
- [x] T1.1 : `ConnectorCapability` — ajouter la valeur `CALLBACK`, tester `includesTrigger()`/
      `includesService()` renvoient `false` pour cette valeur (garde de non-régression sur les 3
      valeurs existantes).
- [x] T1.2 : `CallbackResolution` (record `messageName`, `correlationVariableName`,
      `correlationValue`, `variables`) — test de construction, `variables` non-null (copie
      défensive comme `ConnectorInstanceData`).
- [x] T1.3 : `CallbackConnector` (interface) — pas de test direct (interface pure).
- [x] T1.4 : `CallbackNoMatchingInstanceException` / `CallbackAmbiguousCorrelationException` —
      messages d'erreur explicites incluant `messageName`/`correlationKey`/`correlationValue` (et
      le nombre de résultats pour l'ambiguïté), testés.
- **Dépendances** : aucune. **Validation** : `./mvnw test -Dtest=ConnectorCapabilityTest,CallbackResolutionTest,CallbackExceptionsTest`.
- **Scénarios testing doc couverts** : unit tests `ConnectorCapability`/exceptions (implicites,
  garde de base avant M4/M5).

### M2 — Moteur Flowable réel (TDD avec le vrai `RuntimeService`, pas mocké)
- [x] T2.1 : `WorkflowEnginePort.correlateMessage(messageName, correlationKey, correlationValue, variables)`
      — signature ajoutée au port.
- [x] T2.2 : Process BPMN synthétique (`callback-smoke.bpmn20.xml`, livré dans
      `src/main/resources/processes/` — cf. correction de Phase 5 ci-dessus, pas
      `src/test/resources/**` comme prévu initialement) avec une variable posée au démarrage puis
      un `intermediateCatchEvent` à message — fixture pour tous les tests d'intégration M2/M6.
- [x] T2.3 : `FlowableWorkflowEngineAdapter.correlateMessage` — implémentation
      `ExecutionQuery.messageEventSubscriptionName(...).processVariableValueEquals(...)` +
      `messageEventReceived(...)`, cf. design. Test rouge d'abord : démarrer le process fixture,
      atteindre le point d'attente, corréler, assert sur la reprise + variables injectées.
- [x] T2.4 : 0 résultat → `CallbackNoMatchingInstanceException` (test avec le vrai moteur : aucun
      process démarré, ou valeur de corrélation ne correspondant à rien).
- [x] T2.5 : >1 résultat → `CallbackAmbiguousCorrelationException` (test avec le vrai moteur : 2
      instances du process fixture démarrées avec la même valeur de corrélation).
- [x] T2.6 : Preuve explicite `processVariableValueEquals` vs `variableValueEquals` — test où la
      variable de corrélation est posée comme variable de **process** (pas locale à l'exécution en
      attente), échouerait avec `variableValueEquals` (régression directe sur la correction Phase 3).
- **Dépendances** : T1 (types d'exception). **Validation** : test d'intégration Spring Boot avec le
  vrai `ProcessEngine` (pattern déjà utilisé pour `assainissement-dette-technique`, TODO §49 —
  jamais mocké pour ce genre de preuve).
- **Scénarios testing doc couverts** : section "Unit Tests → `FlowableWorkflowEngineAdapter.correlateMessage`"
  (3 items), "Integration Tests" item race/404.

### M3 — Use case domaine (TDD, mocks pour les ports)
- [x] T3.1 : `CallbackConnectorRuntime` (port in) — signature `onIncomingCallback(instance, rawPayload)`.
- [x] T3.2 : `CallbackConnectorRuntimeImpl` — connecteur inconnu ou n'implémentant pas
      `CallbackConnector` → exception domaine explicite (miroir `ConnectorTriggerRuntimeImpl`).
- [x] T3.3 : Appel correct de `resolve(...)` puis `correlateMessage(...)` avec les valeurs
      résolues — test avec mocks `ConnectorCatalog`/`WorkflowEnginePort`/`CallbackConnector`.
- [x] T3.4 : Preuve de non-couplage : zero-interaction sur `CreateDossierUseCase`/
      `GetDossierUseCase`/`OutboxRepository` (mock `verifyNoInteractions`).
- **Dépendances** : M1. **Validation** : `./mvnw test -Dtest=CallbackConnectorRuntimeImplTest`.
- **Scénarios testing doc couverts** : section "Unit Tests → `CallbackConnectorRuntimeImpl`" (3 items).

### M4 — Connecteur infra (TDD, `SimpleJsonPathMapper` réel)
- [x] T4.1 : `FlowableMessageCallbackConnector implements ConnectorDefinition, CallbackConnector`
      — `getType()`, `getLabel()`, `getCapability() == ConnectorCapability.CALLBACK`.
- [x] T4.2 : `getConfigSchema()` — `webhookAuthType`, `webhookSecret`, `messageName`,
      `correlationVariableName`, `correlationValuePath`, `mapping.variables` (JSON libre, pattern
      `HttpRestConnector`).
- [x] T4.3 : `validate(config)` — rejette une config sans les champs requis (miroir
      `HttpRestConnector.validate`).
- [x] T4.4 : `resolve(instance, rawPayload)` — extraction `correlationValue` via
      `correlationValuePath` (réutilise `SimpleJsonPathMapper.read`, pas de réimplémentation).
- [x] T4.5 : `resolve(...)` — extraction des `mapping.variables` (réutilise le patron
      `HttpRestConnector.mapPaths`, plusieurs champs, chemins imbriqués).
- [x] T4.6 : Payload sans la clé attendue par `correlationValuePath` → erreur de validation
      explicite, jamais `null` propagé silencieusement dans `correlateMessage`.
- **Dépendances** : M1. **Validation** : `./mvnw test -Dtest=FlowableMessageCallbackConnectorTest`.
- **Scénarios testing doc couverts** : section "Unit Tests → `FlowableMessageCallbackConnector`" (5 items).

### M5 — Endpoint HTTP + gestion d'erreurs (TDD, `MockMvc`/`WebTestClient`)
- [x] T5.1 : `CallbackWebhookController` dans `com.flowforge.infrastructure.web.connector`
      (`POST /api/callbacks/{instanceId}`) — réutilise par injection `WebhookAuthenticator`
      (liste, comme `WebhookController`), `WebhookRequestRateLimiter`, `WebhookIdempotencyStore`,
      `ConnectorInstanceRepository`.
- [x] T5.2 : Auth HMAC/shared-token réutilisée telle quelle — test négatif (signature invalide →
      401), test positif.
- [x] T5.3 : Idempotence — même `Idempotency-Key` deux fois → une seule invocation de
      `CallbackConnectorRuntime.onIncomingCallback`, réponse déjà enregistrée renvoyée en second.
- [x] T5.4 : Rate limit / `maxBodyBytes` — non-régression (mêmes beans que `WebhookController`,
      test de fumée suffisant, pas de réimplémentation de la logique).
- [x] T5.5 : `ConnectorExceptionHandler` — 2 `@ExceptionHandler` ajoutés
      (`CallbackNoMatchingInstanceException` → 404, `CallbackAmbiguousCorrelationException` → 409),
      test par type d'exception.
- **Dépendances** : M3, M4. **Validation** : `./mvnw test -Dtest=CallbackWebhookControllerTest,ConnectorExceptionHandlerTest`.
- **Scénarios testing doc couverts** : section "Integration Tests" (5 items), "API Design" (design doc).

### M6 — Bout-en-bout (intégration complète, moteur réel)
- [x] T6.1 : Process de test démarré → callback HTTP authentifié avec la bonne valeur de
      corrélation → process repris avec les variables du payload (assert sur l'état final du
      process, pas seulement sur le code HTTP retourné).
- [x] T6.2 : Callback reçu avant que le process atteigne le point d'attente → 404, pas d'exception
      non gérée qui fuiterait en 500 (test bout-en-bout, pas juste unitaire sur l'adaptateur).
- [x] T6.3 : Callback rejoué (même `Idempotency-Key`) → pas de double corrélation (assert sur le
      nombre d'invocations moteur, pas seulement sur le code HTTP).
- **Dépendances** : M2, M5. **Validation** : `./mvnw verify` (suite complète).
- **Scénarios testing doc couverts** : "Integration Tests" items 1 et 3.

### M7 — Vérification globale et documentation
- [x] T7.1 : Balayage couverture Jacoco classe par classe sur tout le code neuf (cible 100 % des
      branches nouvelles : succès/404/409/idempotence), gaps documentés et comblés ou justifiés.
- [x] T7.2 : `./mvnw verify` complet (ArchUnit inclus) — sortie fraîche avant toute claim de
      complétion (skill `verify`).
- [x] T7.3 : `analysis doc/06-connecteurs.md` (doc catalogue) mis à jour avec le nouveau connecteur
      callback (cf. TODO §51 « Impacte »).
- [ ] T7.4 : `CHANGELOG.md` — entrée `[Non publié]` avant ouverture de PR (convention projet).
      **Reporté à Phase 9** (revue finale pré-push) : pas de PR ouverte à ce stade, l'implémentation
      peut encore évoluer en Phase 6/7/8 — écrire l'entrée maintenant risquerait de la rendre
      obsolète avant la PR.
- [x] T7.5 : Confirmation finale « zéro fichier `frontend/**` modifié » (`git status` vérifié).
- **Dépendances** : M1–M6. **Validation** : `./mvnw verify` (1526 tests, 0 échec, 2026-09-02) +
  relecture des docs listées.

## Résumé Phase 5 (2026-09-02)

Tous les milestones M1–M7 exécutés en TDD dans la même session, tous verts. `./mvnw verify` :
1526 tests, 0 échec. Zéro fichier `frontend/**` touché. 1 bug réel trouvé et corrigé en route
(NPE sur variable de mapping optionnelle non résolue, `Map.copyOf`) et 1 décision de Phase 4
corrigée avec preuve (fixture BPMN livrée en `src/main/resources/processes/`, pas
`src/test/resources/**` — l'hypothèse initiale sur `DeployBpmnProcessUseCaseImpl` était fausse).
Seul reste ouvert : `CHANGELOG.md` (T7.4), volontairement reporté à la Phase 9 pré-PR. Prochaine
étape : Phase 6 (Update Planning, déjà faite par cette mise à jour) puis Phase 7 (Check
Implementation).

## Dependencies
**What needs to happen in what order?**

- M1 → M2, M3, M4 (types domaine nécessaires partout).
- M2 et M4 sont indépendants entre eux (peuvent être menés dans n'importe quel ordre après M1).
- M3 dépend de M1 uniquement (mocks pour M2/M4 dans ses propres tests) — peut être fait en
  parallèle de M2/M4, câblé ensuite.
- M5 dépend de M3 (use case) et M4 (connecteur concret pour les tests d'intégration du contrôleur).
- M6 dépend de M2 (moteur réel) et M5 (endpoint complet) — bout-en-bout, dernier avant M7.
- M7 dépend de tout le reste.
- **Aucune dépendance externe** : pas de nouvelle librairie, pas de migration Liquibase, pas de
  changement de schéma. Le seul risque d'ordre est humain (ne pas commencer M5 avant que M3/M4
  soient stables, pour éviter de retester deux fois le câblage du contrôleur).

## Timeline & Estimates
**When will things be done?**

- Pas d'estimation calendaire formelle (convention projet : cycles précédents mesurés en
  « même session », pas en jours-personne). Ordre de grandeur relatif aux features comparables
  (`connecteur-http-rest-service`, TODO §48, cycle complet Phases 1–9 en une session) : M1–M4
  triviaux individuellement (types + un connecteur de plus sur un patron déjà 3 fois répété), M2
  et M6 sont le vrai risque de temps (premier usage de `ExecutionQuery`/`messageEventReceived`
  dans ce dépôt, tests d'intégration moteur réel à faire fonctionner du premier coup).

## Risks & Mitigation
**What could go wrong?**

- **Risque technique principal** : `ExecutionQuery`/`messageEventReceived` jamais utilisés dans ce
  dépôt — le comportement exact vis-à-vis du scope de variable, du timing d'abonnement au message
  (l'exécution doit avoir atteint l'`intermediateCatchEvent` pour apparaître dans la query), et de
  la transactionnalité (`messageEventReceived` dans la même transaction que la requête HTTP) doit
  être prouvé par les tests M2/M6 avec le **vrai** moteur, pas supposé depuis la lecture des
  sources seule. Mitigation : T2.3–T2.6 avant tout code applicatif (rouge → vert strict), pas de
  passage à M3+ tant que M2 n'est pas vert avec le vrai `ProcessEngine`.
- **Risque de régression** : `ConnectorCapability` est un `enum` potentiellement utilisé dans des
  `switch` exhaustifs ailleurs dans le dépôt — un ajout de valeur `CALLBACK` peut faire échouer une
  compilation de `switch` non couvert (pattern matching Java) qui devra être élargi. Mitigation :
  `./mvnw verify` complet (pas un test ciblé) après T1.1, avant de continuer.
- **Risque de couplage accidentel** : tentation de brancher `CallbackWebhookController` sur
  `ConnectorTriggerRuntime` par réutilisation hâtive du contrôleur existant. Mitigation : T3.4
  (preuve zero-interaction) + design doc explicite sur la séparation des deux runtimes.
- **Risque de dérive de couverture** : nouvelles branches 404/409 dans `ConnectorExceptionHandler`
  oubliées dans le balayage Jacoco (petites méthodes, faciles à manquer). Mitigation : T7.1 dédié,
  après T7.2 (pas avant, pour balayer sur un `./mvnw verify` complet et à jour).

## Resources Needed
**What do we need to succeed?**

- Aucune ressource externe : pas de nouvelle dépendance Maven, pas de nouveau service, pas de
  nouvelle variable d'environnement. `RuntimeService`/`ExecutionQuery` déjà disponibles via
  l'injection Spring existante (`FlowableWorkflowEngineAdapter`).
- Connaissance acquise en Phase 3 (mécanisme `ExecutionQuery`/`messageEventReceived`, stockée en
  mémoire projet `repo:FlowForge`) — à relire avant de démarrer M2 si la session d'implémentation
  diffère de celle du design.
