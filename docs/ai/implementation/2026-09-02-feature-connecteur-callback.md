---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

> Phases 5 (Execute Plan), 7 (Check Implementation), 8 (Testing) et 9 (Review) closes (2026-09-02) :
> milestones M1–M7 exécutés en TDD, revue fichier par fichier faite (2 écarts réels trouvés et
> corrigés en Phase 7), couverture Jacoco fermée en Phase 8 (100 % des branches propres à cette
> feature — cf. testing doc), revue holistique finale en Phase 9 (1 duplication triviale et 1 doc
> catalogue oubliée trouvées et corrigées). `./mvnw verify` final : 1533 tests, 0 échec. Zéro
> fichier `frontend/**` modifié (confirmé, y compris via le contrat OpenAPI en Phase 8).

## Development Setup
**How do we get started?**

- Aucun prérequis nouveau : `RuntimeService`/`ExecutionQuery` de Flowable 8.0.0 déjà sur le
  classpath et injectés (`FlowableWorkflowEngineAdapter`). Aucune dépendance Maven ajoutée.
- `./mvnw verify -Dmaven.compiler.release=21` reproduit la suite complète (backend + ArchUnit +
  Jacoco).

## Code Structure
**How is the code organized?**

- `domain/connector/model/` : `CallbackConnector` (interface), `CallbackResolution` (record),
  `CallbackNoMatchingInstanceException`, `CallbackAmbiguousCorrelationException`,
  `ConnectorCapability.CALLBACK` (valeur ajoutée).
- `domain/connector/port/in/CallbackConnectorRuntime.java` + `usecase/CallbackConnectorRuntimeImpl.java`.
- `domain/workflow/port/out/WorkflowEnginePort.java` : méthode `correlateMessage(...)` ajoutée.
- `infrastructure/flowable/FlowableWorkflowEngineAdapter.java` : implémentation
  `correlateMessage` (`ExecutionQuery` + `messageEventReceived`).
- `infrastructure/connector/builtin/FlowableMessageCallbackConnector.java` : connecteur concret
  (`ConnectorDefinition` + `CallbackConnector`, capability `CALLBACK`).
- `infrastructure/web/connector/CallbackWebhookController.java` : endpoint `POST
  /api/callbacks/{instanceId}`, même package que `WebhookController` (partage
  `ConnectorExceptionHandler`).
- `infrastructure/config/ConnectorConfig.java` : bean `callbackConnectorRuntime`.
- `src/main/resources/processes/callback-smoke.bpmn20.xml` : fixture de smoke-test auto-déployée
  au démarrage (voir « Écart n°1 » ci-dessous).

## Implementation Notes
**Key technical details to remember:**

### Core Features
- **M1 — Domaine** : types purs, aucune dépendance framework. `CallbackResolution` copie
  défensivement `variables` (`Map.copyOf`), même patron que `WorkflowTriggerEvent`.
- **M2 — Moteur** : `correlateMessage` interroge `ExecutionQuery.messageEventSubscriptionName(...)
  .processVariableValueEquals(...)` (scope **process**, pas `variableValueEquals` qui est local à
  l'exécution), décide 0/1/plusieurs sur la liste retournée, puis
  `RuntimeService.messageEventReceived(messageName, executionId, variables)` sur l'unique
  résultat. Prouvé par `CallbackSmokeIT` (moteur réel, process `callbackWaitSmoke`).
- **M3 — Use case** : `CallbackConnectorRuntimeImpl` n'interagit avec aucun port dossier
  (`CreateDossierUseCase`/`GetDossierUseCase`/`OutboxRepository`), preuve par
  `verifyNoInteractions` dans `CallbackConnectorRuntimeImplTest`.
- **M4 — Connecteur** : `FlowableMessageCallbackConnector.resolve(...)` lit `messageName`/
  `correlationVariableName` **uniquement** depuis la config de l'instance (jamais depuis le
  payload entrant, testé explicitement — un payload ne peut pas usurper le canal/la variable
  cible). `correlationValuePath` obligatoire (rejet explicite si absent du payload) ;
  `mapping.variables` optionnel.
- **M5 — Endpoint** : `CallbackWebhookController` réutilise par injection les mêmes beans que
  `WebhookController` (`WebhookAuthenticator`, `WebhookRequestRateLimiter`,
  `WebhookIdempotencyStore`) — aucune logique d'auth/rate-limit/idempotence dupliquée.

### Patterns & Best Practices
- Chaque nouvelle classe infra mirrore un pattern déjà établi dans le dépôt (`HttpRestConnector`
  pour le mapping JSONPath, `WebhookController` pour l'endpoint, `ConnectorTriggerRuntimeImpl`
  pour la structure du use case) — zéro nouvelle abstraction.
- Tests real-engine (`*IT`) systématiques pour tout comportement Flowable non trivial, jamais
  mocké quand le comportement réel est la question (cf. `SignalSmokeIT` pour le précédent).

## Integration Points
**How do pieces connect?**

- `ConnectorRegistry` découvre `FlowableMessageCallbackConnector` automatiquement (`@Component`,
  injecté dans `List<ConnectorDefinition>`) — zéro changement de `ConnectorRegistry`/
  `ConnectorCatalog`.
- `ConnectorConfig` câble `CallbackConnectorRuntime` par-dessus `ConnectorCatalog` +
  `WorkflowEnginePort` (déjà existants), même patron que `connectorTriggerRuntime`.

## Error Handling
**How do we handle failures?**

- `CallbackNoMatchingInstanceException` → 404, `CallbackAmbiguousCorrelationException` → 409,
  ajoutés dans `ConnectorExceptionHandler` existant (même `basePackages`, aucun nouveau
  `@RestControllerAdvice`).
- **Écart n°2 (bug réel trouvé en Phase 5, corrigé)** : `mapPaths` dans
  `FlowableMessageCallbackConnector` plantait avec une `NullPointerException` (pas une exception
  domaine propre) quand un chemin JSONPath de `mapping.variables` (optionnel) ne résolvait à rien
  dans le payload — `Map.copyOf` dans le record `CallbackResolution` rejette toute valeur `null`.
  Corrigé en omettant simplement la variable non résolue (`mapPaths` ne met plus en `Map` une
  valeur `null`) plutôt que de la propager. **Non corrigé dans le code pré-existant** analogue
  (`HttpRestConnector.mapPaths`/`WorkflowTriggerEvent`, même faille latente) — hors scope de cette
  feature, à signaler séparément si confirmé un jour comme un vrai risque (TODO à ouvrir si
  besoin, pas fait ici pour ne pas élargir cette PR).

## Performance Considerations
**How do we keep it fast?**

- `ExecutionQuery.list()` + `messageEventReceived(...)` restent synchrones dans la requête HTTP
  entrante, même ordre de grandeur que les endpoints webhook existants — aucun nouveau goulot
  mesuré ni anticipé.

## Security Notes
**What security measures are in place?**

- Authentification HMAC/shared-token et idempotence strictement réutilisées (mêmes beans injectés
  que `WebhookController`), aucune nouvelle surface.
- `messageName`/`correlationVariableName` toujours lus depuis la config d'instance (jamais depuis
  le payload) — empêche un payload malveillant de cibler un canal/une variable arbitraire.

## Écarts trouvés en Phase 5, 7 et 9 (documentés, pas masqués)

1. **Décision de Phase 4 revue** : la planification (Phase 4) avait tranché « pas de process BPMN
   d'exemple dans `src/main/resources/processes/`, fixture de test uniquement ». En implémentant
   M2, découverte que le dépôt a déjà un pattern établi et *différent* : des process
   `*-smoke.bpmn20.xml` (`signal-smoke.bpmn20.xml`, `smoke-process.bpmn20.xml`,
   `create-child-dossier-smoke.bpmn20.xml`) vivent dans `src/main/resources/processes/`
   (auto-déployés par Flowable au démarrage, y compris en usage réel) et sont démarrés
   **directement** via `RuntimeService` dans les tests d'intégration, **sans jamais passer** par
   `DeployBpmnProcessUseCaseImpl`/`WorkflowTypeConfig` — le raisonnement de Phase 4
   (« un `WorkflowTypeConfig` serait nécessaire ») était incorrect pour ce chemin de démarrage.
   Suivi le pattern établi : `callback-smoke.bpmn20.xml` ajouté au même endroit, même convention
   de nommage. Aucun risque de scope créé (c'est exactement le même patron que 3 fixtures déjà en
   production).
2. **Bug réel trouvé et corrigé** : cf. « Error Handling » ci-dessus (`mapPaths`/`Map.copyOf` sur
   valeur `null`).
3. **Bug réel trouvé et corrigé en Phase 7 (Check Implementation)** : sondage empirique contre le
   vrai moteur (`ExecutionQuery.processVariableValueEquals("k", 123)` sur une variable de process
   `String "123"`) a confirmé que Flowable compare par type ET valeur — 0 résultat malgré une
   égalité logique. Un payload de callback externe porte fréquemment un identifiant sous forme de
   nombre JSON, alors que la variable de process posée par le `serviceTask` sortant est typiquement
   une `String`. Sans correction, ce mésappariement aurait produit un **404 silencieux et
   systématique** pour toute intégration avec un identifiant numérique côté système externe — pas
   un cas limite, un mode de défaillance courant. Corrigé : `FlowableMessageCallbackConnector
   .resolve(...)` normalise désormais `correlationValue` en `String` (`String.valueOf(...)`) avant
   de construire `CallbackResolution`. Documenté sur le champ de config `correlationVariableName`
   (la variable de process doit aussi être une `String`) — ne résout que la moitié du problème que
   ce connecteur contrôle. Prouvé par un test unitaire (`FlowableMessageCallbackConnectorTest
   .resolve_numericCorrelationValueInPayload_coercedToString`) et un test bout-en-bout avec le vrai
   moteur (`CallbackEndToEndIT
   .onIncomingCallback_numericOrderIdInPayload_stillMatchesStringProcessVariable`).
4. **Écart design/implémentation trouvé et corrigé en Phase 7** : le design doc (section
   Non-Functional Requirements, écrite en Phase 3) promettait que la race résiduelle entre
   `ExecutionQuery.list()` et `RuntimeService.messageEventReceived(...)` (deux callbacks
   concurrents visant la même exécution) serait « traitée comme 404, pas comme 500 » — mais le
   code livré en Phase 5 n'implémentait aucune capture d'exception autour de
   `messageEventReceived(...)`. Si l'exécution correspondante était consommée entre le `list()` et
   l'appel (fenêtre étroite mais réelle), le `FlowableException` levé par Flowable (javadoc :
   « if the execution has not subscribed to the signal ») aurait fui tel quel, sans handler dédié
   dans `ConnectorExceptionHandler` → 500 non géré, contredisant directement le design. Corrigé en
   Phase 7 : `correlateMessage` capture désormais ce `FlowableException` et le traduit en
   `CallbackNoMatchingInstanceException` (404), prouvé par un test unitaire dédié
   (`FlowableWorkflowEngineAdapterCorrelateMessageTest
   .concurrentRaceOnMessageEventReceived_treatedAsNoMatchingInstance_neverPropagatesRawFlowableException`).
5. **Couverture Jacoco** : `CallbackWebhookController` reproduit volontairement les branches
   défensives non testées de `WebhookController` (payload JSON invalide, `webhookAuthType`
   manquant/invalide, `rawBody` null) — vérifié que `WebhookController` lui-même a exactement les
   mêmes lignes non couvertes dans le rapport Jacoco actuel (pattern déjà accepté par le projet
   pour cette classe). Fermé sur `FlowableMessageCallbackConnector` en revanche (code propre à
   cette feature, sans précédent à respecter) : `validate(null)`, auth type invalide non-allowlist,
   payload `null` en entrée de `resolve(...)` — tous désormais testés.
6. **Simplification trouvée en Phase 9 (revue holistique)** : `FlowableMessageCallbackConnector`
   avait deux méthodes privées strictement identiques (`stringValue` et `requireString`, même
   validation, même message d'erreur) — duplication interne triviale, pas un pattern à préserver
   (contrairement à `HttpRestConnector`, où la même duplication existe mais n'est pas la
   responsabilité de cette feature). Fusionnées en une seule (`stringValue`), zéro risque (usage
   strictement privé, aucun appelant externe), tests inchangés et toujours verts (15/15).
7. **Doc catalogue oubliée en Phase 7, trouvée en Phase 9** : `README.md` § « Système de
   connecteurs » liste le catalogue de connecteurs livrés (`http-rest`, `flowable-signal`,
   `scheduler`, `smtp`, `document-storage`) mais n'avait pas été mis à jour avec `flowable-callback`
   — seul `analysis doc/06-connecteurs.md` l'avait été en Phase 7. Corrigé : `README.md` mentionne
   désormais le rôle **callback** et le connecteur `flowable-callback`.

## Correctifs post-merge (passe de contrôle adversariale, 2026-09-02)

Une revue adversariale demandée explicitement par l'utilisateur **après** le merge de PR #102 a
trouvé 5 écarts réels sur le code déjà en production, tous corrigés dans le même correctif de
suivi. Aucun n'avait été détecté par les 9 phases précédentes ni par les 1533 tests alors verts —
détail de la cause racine ci-dessous, pas juste le symptôme.

1. **F0 (critique) — `/api/callbacks/**` inatteignable par tout appelant externe réel.**
   `SecurityConfig` définit 4 chaînes de filtres par `@Order` : `/api/m2m/**`, `/api/webhooks/**`
   (anonyme, pas de CSRF — c'est là que vit `WebhookController`), puis un **catch-all** BFF
   (`anyRequest().authenticated()`, CSRF actif, session OIDC requise). `/api/callbacks/**` n'a
   jamais été ajouté au `securityMatcher` de la chaîne webhook — la requête tombait dans le
   catch-all BFF et recevait un **403 CSRF, corps vide, avant même d'atteindre
   `CallbackWebhookController`**. Prouvé empiriquement (`@SpringBootTest @AutoConfigureMockMvc`,
   pas `MockMvc.standaloneSetup` qui contourne Spring Security entièrement) : `POST
   /api/callbacks/{uuid}` anonyme → 403. **Aucun test du diff original ne faisait une vraie requête
   HTTP à travers la vraie chaîne de sécurité** — `CallbackWebhookControllerTest` utilise
   `standaloneSetup` (contourne Security), `CallbackEndToEndIT` appelle le use case directement
   (contourne HTTP). Corrigé : `securityMatcher("/api/webhooks/**", "/api/callbacks/**")` sur la
   même chaîne. Test de régression permanent ajouté :
   `WebhookAndCallbackRoutesSecurityTest` (nouveau, `infrastructure/security`), qui couvre aussi
   `/api/webhooks/**` (jamais testé contre la vraie chaîne non plus avant ce correctif).
2. **F1 (haute) — pas de garde `Content-Length` sur `/api/callbacks/**`.**
   `WebhookBodySizeLimitFilter` (javadoc explicite : *« sur cette chaîne anonyme exposée sur
   internet, [le check applicatif seul] est un vecteur d'épuisement mémoire »*) n'était enregistré
   que sur `/api/webhooks/**`. Corrigé par le même changement que F0 (même chaîne, même filtre).
   Preuve dédiée : `callbacksEndpoint_oversizedDeclaredContentLength_rejectedBeforeBuffering` (413
   avant bufférisation).
3. **F2 (haute) — un callback échoué bloquait tout rejeu avec la même `Idempotency-Key` pendant
   24 h.** `idempotencyStore.complete(...)` n'était appelé que sur le chemin de succès ; toute
   exception laissait la ligne `IN_FLIGHT` en base, et un rejeu avec la même clé (pratique standard
   de retry idempotent) levait `WebhookIdempotencyInFlightException` (409) au lieu d'être
   retraité — annulant la garantie de fiabilité de D-CALLBACK-RACE (« le système externe doit
   retenter »). Corrigé : `CallbackWebhookController.receive()` capture désormais toute
   `RuntimeException`, appelle `idempotencyStore.complete(...)` avec le statut déterministe pour
   les échecs dont l'issue est stable sur le même contenu de requête
   (`CallbackNoMatchingInstanceException`→404, `CallbackAmbiguousCorrelationException`→409,
   `ConnectorValidationException`/`IllegalArgumentException`→400), puis relance l'exception —
   `ConnectorExceptionHandler` construit toujours le `ProblemDetail` normalement. Une exception non
   reconnue reste volontairement `IN_FLIGHT` plutôt que de deviner un statut (testé explicitement :
   `receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`). **Même défaut latent
   présent dans `WebhookController`** (composant `JpaWebhookIdempotencyStoreAdapter` partagé,
   pattern identique) — pas corrigé ici (hors du périmètre F0–F4 explicitement approuvé), tracé en
   dette dans `TODO.md`.
4. **F3 (moyenne) — ambiguïté de corrélation totalement silencieuse côté serveur.** Le design doc
   promettait *« loguée en ERROR »* ; le code livré ne loguait rien. Corrigé : nouvel événement
   `SecurityAuditLog.callbackAmbiguousCorrelation(...)` (WARN, canal `SECURITY_AUDIT` déjà établi
   dans le dépôt pour ce type d'événement), appelé depuis
   `ConnectorExceptionHandler.handleCallbackAmbiguousCorrelation`. Ne logue jamais
   `correlationValue` (donnée métier fournie par l'appelant), seulement `messageName`/
   `correlationKey`/`matchCount` — testé explicitement par lecture du log capturé
   (`ListAppender`, pattern déjà utilisé par `DossierApiTest`).
5. **F4 (basse) — pas de garde-fou documenté sur l'entropie de `correlationValue`.**
   L'authentification est par instance de connecteur, pas par transaction : un appelant légitime
   pour une instance peut corréler vers n'importe quelle exécution en attente sur le même
   `messageName` en devinant `correlationValue`. Corrigé : description du champ de config
   `correlationValuePath` recommande désormais explicitement un identifiant à haute entropie
   (UUID), jamais une valeur séquentielle/devinable. Documentation uniquement, pas de changement de
   comportement.

`./mvnw verify` après correctifs : **1539 tests, 0 échec** (+6 tests de régression permanents).
