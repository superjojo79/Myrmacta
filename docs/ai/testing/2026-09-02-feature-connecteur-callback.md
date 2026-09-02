---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Statut : **Phase 8 (Testing) close (2026-09-02)**. Phases 5/7/8 exécutées, tous les scénarios
> ci-dessous implémentés et verts (`./mvnw verify` : **1533 tests, 0 échec**). Feature backend-only
> confirmée (Phase 2, reconfirmée ici) — Playwright/Vitest hors scope avec preuve, pas par omission.

## Test Coverage Goals
**What level of testing do we aim for?**

- Jacoco : mêmes seuils que le reste du dépôt (LINE ≥85 % / BRANCH ≥75 %) — `./mvnw verify` passe.
  Balayage final classe par classe (2026-09-02) :

  | Classe | LINE | BRANCH | Statut |
  |---|---|---|---|
  | `CallbackResolution` | 5/5 | 2/2 | 100 % |
  | `CallbackConnectorRuntimeImpl` | 16/16 | 2/2 | 100 % |
  | `ConnectorCapability` | 7/7 | 8/8 | 100 % |
  | `ConnectorConfig` | 9/9 | n/a | 100 % |
  | `CallbackNoMatchingInstanceException` | 8/8 | n/a | 100 % |
  | `CallbackAmbiguousCorrelationException` | 10/10 | n/a | 100 % |
  | `FlowableMessageCallbackConnector` | 65/65 | 27/28 | 1 branche morte (`paths == null`, jamais atteignable — `asMap(...)` ne renvoie jamais `null`, même garde défensive que `HttpRestConnector.mapPaths`) |
  | `FlowableWorkflowEngineAdapter` | 38/46 | 9/18 | La méthode neuve `correlateMessage` (avec la capture `FlowableException` de Phase 7) est **100 % couverte** ; tous les manques restent dans des méthodes pré-existantes non touchées (`findProcessInstanceIdByBusinessKey` jamais appelée, branche `log.warn` de `deleteProcessInstanceBestEffort`, détail de `isMissingProcessInstance`) |
  | `CallbackWebhookController` | 42/48 | 11/16 | Écart **volontaire et vérifié** : reproduit exactement les mêmes branches défensives non testées que `WebhookController` (payload JSON invalide, `webhookAuthType` manquant/invalide, `rawBody` null) — comparé ligne à ligne au rapport Jacoco de la classe miroir, pattern déjà accepté par le projet |
  | `ConnectorExceptionHandler` | 18/23 | 3/10 | Les 2 nouveaux `@ExceptionHandler` (404/409 callback) sont **100 % couverts** ; tous les manques sont dans des handlers pré-existants non touchés |

  **100 % des branches propres à cette feature sont couvertes** ; tous les écarts restants sont soit
  du code mort partagé avec une classe existante (même garde, jamais atteignable), soit du code
  pré-existant non touché par cette feature.
- Intégration : chemin complet avec le **vrai** moteur Flowable (pas mocké) pour la corrélation —
  `ExecutionQuery.messageEventSubscriptionName(...).processVariableValueEquals(...)` +
  `messageEventReceived(...)`, prouvé par `CallbackSmokeIT` et `CallbackEndToEndIT`.
- Aligné sur les critères de succès du requirements doc : reprise réussie, rejet 404 sans instance
  correspondante, rejet 409 sur ambiguïté, non-double-corrélation sur rejeu idempotent.

## Unit Tests
**What individual components need testing?**

### `FlowableMessageCallbackConnector` (resolve) — `FlowableMessageCallbackConnectorTest`
- [x] Extraction correcte de `correlationValue` via `correlationValuePath` (JSONPath) depuis un
      payload JSON représentatif.
- [x] Extraction des `mapping.variables` configurées (plusieurs champs).
- [x] `messageName`/`correlationVariableName` renvoyés tels que configurés sur l'instance (fixes,
      jamais dérivés du payload) — testé avec un payload tentant de les usurper.
- [x] `validate(config)` rejette une config sans `messageName` / `correlationValuePath` /
      `webhookAuthType` / `webhookSecret` / `correlationVariableName`, `config` null, un
      `webhookAuthType` invalide (non-`IP_ALLOWLIST`), un champ requis **blanc** (pas seulement
      absent).
- [x] **[Phase 8]** `resolve(...)` rejette lui-même un champ requis absent ou blanc dans la config
      (`stringValue`), pas seulement `validate(...)` (`requireString`) — défense en profondeur,
      miroir du patron déjà suivi par `HttpRestConnector`.
- [x] Payload sans la clé attendue par `correlationValuePath` (y compris payload `null`) → erreur
      de validation explicite (pas de `NullPointerException`, pas de corrélation avec `null`).
- [x] **Scénario découvert en Phase 5** : une variable de `mapping.variables` optionnelle qui ne
      résout à rien dans le payload est **omise**, pas propagée comme `null` — un vrai bug
      (`NullPointerException` via `Map.copyOf` dans `CallbackResolution`) a été trouvé et corrigé
      ici (cf. implementation doc, écart n°2).
- [x] **Scénario découvert en Phase 7 (D-CALLBACK-TYPE)** : un `correlationValue` numérique dans le
      payload (`{"orderId": 12345}`) est normalisé en `String` (`"12345"`) — Flowable compare les
      variables de process par type ET valeur, un mésappariement aurait produit un 404 silencieux
      systématique pour toute intégration avec un identifiant numérique côté système externe (cf.
      implementation doc, écart n°3).

### `CallbackConnectorRuntimeImpl` — `CallbackConnectorRuntimeImplTest`
- [x] Connecteur de type inconnu ou n'implémentant pas `CallbackConnector` → exception domaine
      explicite (miroir du comportement `ConnectorTriggerRuntimeImpl` pour `TriggerConnector`).
- [x] Appel correct de `WorkflowEnginePort.correlateMessage` avec les valeurs résolues.
- [x] Aucune interaction avec `CreateDossierUseCase`/`GetDossierUseCase`/`OutboxRepository` (preuve
      explicite de non-couplage à la sémantique dossier — mock verify zero-interaction).

### `FlowableWorkflowEngineAdapter.correlateMessage` — unitaire mocké + réel
- [x] Unitaire (`FlowableWorkflowEngineAdapterCorrelateMessageTest`, `RuntimeService`/
      `ExecutionQuery` mockés) : succès, 0 résultat, >1 résultats — logique de branchement pure.
- [x] Réel (`CallbackSmokeIT`, moteur Flowable non mocké, process `callbackWaitSmoke`) :
      corrélation réussie avec variables injectées et visibles sur l'instance reprise (assert sur
      `runtimeService.getVariable(...)`, pas seulement sur l'absence d'exception), 0 résultat →
      `CallbackNoMatchingInstanceException`, >1 résultats → `CallbackAmbiguousCorrelationException`.
- [x] Utilisation de `processVariableValueEquals` (pas `variableValueEquals`) prouvée par un test
      dédié où la clé de corrélation est une variable de **process** posée au démarrage, jamais une
      variable locale de l'exécution en attente — régression directe sur la correction de Phase 3.
- [x] Sensibilité au type prouvée empiriquement contre le vrai moteur (`CallbackEndToEndIT`) :
      variable de process `String` posée au démarrage, callback avec la même valeur en JSON
      **numérique** → corrélation réussie malgré tout grâce à la normalisation en `String`.
- [x] **Écart design/implémentation trouvé en Phase 7** : race résiduelle entre `list()` et
      `messageEventReceived(...)` (exécution consommée entre-temps par un callback concurrent) —
      le design promettait un 404, le code livré en Phase 5 laissait fuir le `FlowableException`
      brut. Corrigé, testé unitairement (`concurrentRaceOnMessageEventReceived_...`, cf.
      implementation doc, écart n°4).

## Integration Tests
**How do we test component interactions?**

- [x] `CallbackWebhookControllerTest` (MockMvc, ports mockés, miroir de `WebhookControllerTest`) :
      succès 202, idempotence (rejeu sans second dispatch), `Idempotency-Key` manquant → 400,
      payload trop volumineux → 413, rate limit par instance → 429, HMAC invalide → 401, instance
      désactivée → 404, **nouveaux** : aucune exécution en attente → 404
      (`CallbackNoMatchingInstanceException`), ambiguïté → 409
      (`CallbackAmbiguousCorrelationException`).
- [x] `CallbackEndToEndIT` (Spring context réel, connecteur réel, moteur réel, sans la couche HTTP
      — déjà prouvée séparément avec mocks) : `CallbackConnectorRuntime.onIncomingCallback(...)`
      avec un payload JSON réaliste résout et corrèle correctement sur le vrai moteur, variables
      mappées visibles sur l'instance reprise ; callback sans exécution en attente → exception
      remontée à travers toute la chaîne (connecteur → use case → moteur).
- [x] Authentification HMAC/shared-token réutilisée sans régression (`WebhookControllerTest`
      inchangé, toujours vert).
- [x] Rate limiting / `maxBodyBytes` : réutilisation vérifiée sans régression sur
      `/api/webhooks/**` (`WebhookControllerTest` toujours vert) et sur `/api/callbacks/**`
      (nouveaux tests dédiés).

## End-to-End Tests
**What user flows need validation?**

- **Feature backend-only confirmée en Phase 2, reconfirmée en Phase 5 et 8** : zéro fichier
  `frontend/**` modifié (vérifié par `git status`) — aucun flux SPA/Playwright à couvrir.
- **[Phase 8]** Vérifié explicitement, pas juste supposé : aucun contrat API partagé avec le
  frontend n'est impacté. `/api/callbacks/**` est un endpoint pour systèmes externes uniquement (le
  SPA ne l'appelle jamais) ; `OpenApiContractIT`/`OpenApiEnabledApiTest`/`OpenApiDisabledApiTest`
  (golden OpenAPI) passent tous sans régression dans `./mvnw verify` — aucune régénération de
  contrat nécessaire. Conclusion : Playwright/Vitest réellement hors scope, conformément à
  l'interdiction du projet de le déclarer sans preuve.

## Test Data
**What data do we use for testing?**

- **[Corrigé en Phase 5]** Process BPMN synthétique (`callback-smoke.bpmn20.xml`) livré dans
  `src/main/resources/processes/`, pas `src/test/resources/**` comme décidé en Phase 4 — la
  décision de Phase 4 reposait sur une hypothèse fausse (`DeployBpmnProcessUseCaseImpl` exigerait
  un `WorkflowTypeConfig`). Constat en implémentant M2 : le dépôt a déjà 3 fixtures `*-smoke
  .bpmn20.xml` dans `src/main/resources/processes/` (`signal-smoke`, `smoke-process`,
  `create-child-dossier-smoke`), auto-déployées par Flowable au démarrage et démarrées
  **directement** via `RuntimeService` dans les tests, sans jamais passer par
  `DeployBpmnProcessUseCaseImpl`/`WorkflowTypeConfig`. `callback-smoke.bpmn20.xml` suit ce même
  patron établi (cf. implementation doc, écart n°1).
- Payloads JSON de callback réalistes utilisés (`{"orderId": ..., "status": ...}`), y compris cas
  incomplet (variable optionnelle absente) qui a révélé le bug `Map.copyOf`.
- Instances de connecteur de test avec `webhookAuthType=HMAC_SHA256`/`SHARED_SECRET_TOKEN`
  (cohérent avec les tests existants de `WebhookController`).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` (ArchUnit + Jacoco) exécuté et vert : **1533 tests, 0 échec** (2026-09-02,
  Phase 8, dernière exécution après clôture de la couverture).
- Coverage Jacoco classe par classe passée en revue et close (cf. tableau « Test Coverage Goals »)
  — 100 % des branches propres à cette feature couvertes ; 2 écarts restants, tous deux justifiés
  (parité `WebhookController`, code mort partagé avec `HttpRestConnector`), aucun non expliqué.

## Manual Testing
**What requires human validation?**

- Aucun (backend-only confirmé) — pas d'écran admin impacté.

## Performance Testing
**How do we validate performance?**

- Pas de scénario de charge dédié — même ordre de grandeur que les endpoints webhook existants,
  aucun nouveau goulot mesuré ni anticipé.

## Bug Tracking
**How do we manage issues?**

- 3 écarts réels trouvés et corrigés : Phase 5 (NPE sur variable de mapping optionnelle non
  résolue) ; Phase 7 (mésappariement de type `String`/`Integer` sur la corrélation Flowable,
  D-CALLBACK-TYPE ; race résiduelle `list()`/`messageEventReceived(...)` non traduite en 404 comme
  promis par le design) — cf. implementation doc. Suivi via ce doc et l'implementation doc, pattern
  déjà utilisé pour les features précédentes (`connecteur-http-rest-service`,
  `connecteurs-hors-v1`).
