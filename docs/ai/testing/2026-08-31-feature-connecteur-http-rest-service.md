---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend uniquement (feature confirmée backend-only, requirements Phase 2) : seuils bundle
  existants inchangés à tenir (Jacoco LINE ≥ 85% / BRANCH ≥ 75%), 100% des branches nouvelles
  couvertes (`HttpRestConnector.execute` method/body/headers/auth, `ConnectorConfig`,
  `ConnectorProperties.Http`).
- Frontend : **zéro fichier touché** — pas de nouveau test Vitest/Playwright attendu ; la suite
  existante doit rester verte en régression pure (preuve que rien n'a cassé, pas une exigence
  Playwright au sens de `flowforge-conventions` puisque ni le SPA ni un contrat API qu'il consomme
  ne changent — `GET /api/connector-definitions` ne renvoie pas `getConfigSchema()` au frontend).
- Alignement avec les success criteria du document requirements : appel SERVICE `http-rest`
  fonctionnel (serviceTask + BFF + M2M), SSRF inchangé, anti-fuite de `authHeaderValue`, docs à jour.

## Unit Tests
**What individual components need testing?**

### `HttpRestConnector` — capacité et enregistrement (D-CAPABILITY-SCOPE, D-HTTP-NAMING)
- [x] T-CAP-1 : implémente `ConnectorDefinition` (inversion de
      `connector_doesNotImplementConnectorDefinition`, D-HTTP-TESTS)
- [x] T-CAP-2 : implémente `ServiceConnector`
- [x] T-CAP-3 : `getCapability() == ConnectorCapability.BOTH`
- [x] T-CAP-4 : annoté `@Component` (inversion de `connector_isNotASpringBean`)
- [x] T-CAP-5 (régression) : `getType()` retourne toujours `"http-rest"`

### `HttpRestConnector` — rôle TRIGGER (régression pure, comportement inchangé)
- [x] T-TRIG-1..N : tous les tests existants de `HttpRestTriggerConnectorTest` migrent tels quels
      dans `HttpRestConnectorTest` et continuent de passer sans modification d'assertion

### `HttpRestConnector.execute` — méthode HTTP (D-HTTP-METHODS)
- [x] T-EXEC-1 (régression) : `method` absent → `GET` par défaut, comportement du skeleton inchangé
- [x] T-EXEC-2 : `method="GET"` explicite fonctionne
- [x] T-EXEC-3 : `method="POST"` + `body` → requête `POST` avec le corps
- [x] T-EXEC-4 : `method="PUT"` + `body` → requête `PUT` avec le corps
- [x] T-EXEC-5 : `method` insensible à la casse (`"post"` équivalent à `"POST"`)
- [x] T-EXEC-6 : `method` non supporté (ex. `"DELETE"`, `"PATCH"`) → `IllegalArgumentException`,
      aucun appel `httpClient.send`
- [x] T-EXEC-7 : `body` fourni avec `method="GET"` → `IllegalArgumentException`, aucun appel
      `httpClient.send`

### `HttpRestConnector.execute` — sérialisation du corps
- [x] T-BODY-1 : `body` de type `Map`/objet → sérialisé en JSON via `ObjectMapper`
- [x] T-BODY-2 : `body` déjà une `String` → envoyé tel quel (pas de double-encodage JSON)
- [x] T-BODY-3 : `POST`/`PUT` sans `body` → corps vide (`HttpRequest.BodyPublishers.noBody()`)

### `HttpRestConnector.execute` — en-têtes (D-HTTP-CONTENT-TYPE)
- [x] T-HDR-1 : `connectorInput.headers` (`Map<String,Object>`) appliqués sur la requête
- [x] T-HDR-2 : `headers` présent mais pas une `Map` → `IllegalArgumentException`
- [x] T-HDR-3 : pas de clé `headers` → aucun en-tête supplémentaire (hormis `Authorization` si
      `authHeaderValue` configuré)
- [x] T-HDR-4 : aucun `Content-Type` ajouté par défaut sur `POST`/`PUT` avec corps si non fourni
      explicitement dans `headers` (comportement D-HTTP-CONTENT-TYPE, pas une omission)

### `HttpRestConnector.execute` — `authHeaderValue` (D-HTTP-CONFIG, D-HTTP-AUTH-SHAPE)
- [x] T-AUTH-1 : `instance.config().authHeaderValue` renseigné → en-tête `Authorization` envoyé
      avec exactement cette valeur
- [x] T-AUTH-2 (régression) : `authHeaderValue` absent/blanc → aucun en-tête `Authorization` ajouté
      (rétrocompatible avec les instances TRIGGER-seules existantes)
- [x] T-AUTH-3 : `connectorInput.headers` fournit explicitement `Authorization` → écrase la valeur
      issue de la config (précédence design)
- [x] T-AUTH-4 (anti-fuite) : `authHeaderValue` n'apparaît jamais dans le résultat retourné, quel
      que soit le chemin (extension de `execute_neverEchoesInstanceConfigInResult`)

### `HttpRestConnector.execute` — SSRF / `OutboundUrlGuard` (régression pure, inchangé)
- [x] T-SSRF-1..N : tous les tests SSRF/anti-fuite hérités de `HttpRestServiceConnectorTest`
      (URL non sûre rejetée sans appel réseau, IP pinnée réutilisée sans second lookup, `url`
      manquant rejeté, `IOException`/`InterruptedException` correctement enveloppées) migrent
      inchangés — exercés pour `GET` **et** au moins un cas `POST`/`PUT` (le guard s'applique
      indépendamment de la méthode)

### `getConfigSchema()`
- [x] T-SCHEMA-1 (régression) : les 4 champs TRIGGER existants (`webhookAuthType`, `webhookSecret`,
      `processKey`, `dossierTypeCode`) inchangés
- [x] T-SCHEMA-2 : nouveau champ `authHeaderValue`, `type=PASSWORD`, `required=false`

### `ConnectorConfig` (câblage Spring)
- [x] T-CFG-1 : le contexte Spring démarre avec le nouveau bean `HttpClient`
- [x] T-CFG-2 : `HttpRestConnector` est construit avec ses 4 dépendances (`SimpleJsonPathMapper`,
      `OutboundUrlGuard`, `HttpClient`, `ObjectMapper`)

### `ConnectorProperties.Http` (nouveau)
- [x] T-PROP-1 : `connectTimeout`/`requestTimeout` par défaut = 5 s quand non configurés
- [x] T-PROP-2 : surchargeables via `app.connector.http.connect-timeout`/`request-timeout`

## Integration Tests
**How do we test component interactions?**

- [x] T-INT-1 : couvert par T-INT-3/T-INT-4 (`ConnectorExecuteApiTest`, exercent le vrai bean
      `ExecuteConnectorUseCaseImpl` bout en bout) — pas de test unitaire dédié séparé, redondant
- [x] T-INT-2 : `ConnectorServiceTaskTest` existant (mock générique de `ExecuteConnectorUseCase`,
      indépendant du type de connecteur) reste vert sans modification — la logique de lecture des
      variables de process ne dépend pas du type ; pas de nouveau test nécessaire
- [x] T-INT-3 : `execute_bffHttpRestInstance_urlReachesConnector` (`ConnectorExecuteApiTest`) —
      `url` transmis par le BFF atteint réellement `HttpRestConnector.execute` (prouvé par un
      rejet SSRF déterministe distinguant « url reçue et bloquée » de « url jamais transmise »,
      cf. T-INT-7 ci-dessous)
- [x] T-INT-4 : `execute_m2mHttpRestInstance_urlReachesConnector` — idem via M2M, avec `method`/`body`
- [x] T-INT-5 (régression) : `execute_bffHttpRestInstance_missingUrl_returns400WithUrlRequiredDetail`
      → `400` via le gestionnaire générique existant (`IllegalArgumentException` →
      `handleBadRequest`), aucun nouveau mapping d'exception nécessaire
- [x] T-INT-6 (régression) : les 8 tests existants de `ConnectorExecuteApiTest` (tous sur
      `flowable-signal`) restent verts sans modification
- [x] T-INT-7 (**scénario découvert en implémentation, absent du plan initial**) :
      `ExecuteConnectorRequest` (DTO REST BFF/M2M) était câblé en dur sur les champs
      `flowable-signal` (`signalName`/`processInstanceId`/`variables`) — `url`/`method`/`body`/
      `headers` n'existaient pas dans le DTO, donc silencieusement perdus avant d'atteindre
      `connectorInput`. Sans ce test (`url` bloquée par SSRF ⇒ message "not allowed" distinct de
      "url is required"), la régression aurait été invisible : les deux cas produisent un `400`,
      seul le message `detail` diffère. Corrigé (`ConnectorApiDtos.java`), golden OpenAPI
      régénéré (voir `docs/ai/implementation/**`).

## End-to-End Tests
**What user flows need validation?**

**Non applicable — feature backend-only confirmée (requirements Phase 2), zéro fichier
`frontend/**` prévu.** Pas de nouveau scénario Playwright. La suite E2E existante (`npm run
test:e2e`) doit rester verte en régression complète avant de clore Phase 8, comme preuve qu'aucun
comportement frontend n'a été affecté — pas une obligation `flowforge-conventions` au sens strict
ici (ni le SPA ni un contrat API qu'il consomme ne changent), mais une vérification de non-régression
standard.

## Test Data
**What data do we use for testing?**

- Fixture `ConnectorInstanceData` existante (`HttpRestServiceConnectorTest.INSTANCE`), étendue avec
  `authHeaderValue` dans `config` pour les tests T-AUTH-*.
- `HttpClient`/`HttpResponse` mockés (Mockito), pattern déjà en place dans
  `HttpRestServiceConnectorTest`.
- `OutboundUrlGuard.HostResolver` test double pour les cas de résolution DNS (pattern déjà en place,
  `execute_pinsResolvedIpWithoutSecondLookup`).
- `ObjectMapper` réel (pas mocké) pour vérifier la sérialisation JSON effective de `body`.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test -Dtest=HttpRestConnectorTest,ExecuteConnectorUseCaseImplTest,ConnectorConfigTest   # ciblé, TDD
./mvnw verify                                                                                    # suite complète + Jacoco + ArchUnit
cd frontend && npm run test && npm run test:e2e   # régression complète, aucun nouveau scénario attendu
```

- Seuils Jacoco bundle existants (LINE ≥ 85% / BRANCH ≥ 75%) tenus — aucun changement de seuil,
  contrairement à `fiabilisation-tests-plateforme` qui les avait relevés. `./mvnw verify` final :
  **1420 tests, 0 échec**, `jacoco:check` vert.
- **Balayage classe par classe (Phase 8), 3 lacunes réelles trouvées et comblées** (sur les
  fichiers touchés par cette feature ; les branches manquantes dans `onEvent()`/`validate()`
  — logique TRIGGER pré-existante, non touchée — restent hors périmètre) :
  1. `HttpRestConnector.execute` L208 — `authHeaderValue` présent mais **blanc** (chaîne vide/espaces)
     jamais testé (seuls « absent » et « valeur non vide » l'étaient). Ajouté
     `execute_authHeaderValueBlank_noAuthorizationHeader`.
  2. `ConnectorProperties.Http` L39 — branche `connectTimeout == null → true` jamais exercée : le
     seul chemin testé pour les défauts (`ConnectorProperties(null,null,null)`) construit `Http`
     avec les deux valeurs déjà résolues, sans jamais passer par le compact constructor de `Http`
     lui-même avec `connectTimeout=null`. Ajouté `httpPartialOverride_fillsMissingConnectTimeoutWithDefault`
     (miroir de `httpPartialOverride_fillsMissingFieldWithDefault`, qui ne couvrait que
     `requestTimeout=null`).
  3. `ExecuteConnectorRequest.toInputParams()` (`ConnectorApiDtos.java`) — **aucun test unitaire
     dédié n'existait**, uniquement des vérifications indirectes via `ConnectorExecuteApiTest`
     (MockMvc). `url`/`method`/`headers` (les 3 champs ajoutés par cette feature) n'étaient jamais
     exercés en valeur blanche, et `headers` jamais exercé du tout. Nouveau fichier
     `ConnectorApiDtosTest.java` (8 tests), couvre les 7 champs du DTO individuellement.
- **1 lacune identifiée et sciemment laissée non couverte**, cohérent avec un précédent déjà
  établi dans le dépôt : `HttpRestConnector.serializeBody` L252-253 (capture `JacksonException` →
  `IllegalStateException`) — même pattern que `WebhookRestAdapter.send` (déjà en prod, jamais
  testé non plus pour la même branche). Difficile à déclencher de façon fiable sans mocker
  `ObjectMapper` pour un gain de couverture marginal sur du code strictement défensif.

## Manual Testing
**What requires human validation?**

- Optionnel, non bloquant : exercer un `serviceTask` `${connectorServiceTask}` contre un endpoint
  HTTP réel externe (ex. `httpbin.org/post`) via l'environnement local (`GETTING-STARTED.md`), pour
  une preuve end-to-end au-delà des mocks — dans l'esprit des vérifications manuelles menées pour
  `serveur-mcp`/`schema-champs-dynamiques`.

## Performance Testing
**How do we validate performance?**

- Pas de test de charge/stress prévu — hors périmètre V1 (cf. requirements Non-goals, retry/backoff
  également hors périmètre). Les timeouts (`ConnectorProperties.Http`) sont couverts par tests
  unitaires ciblés (T-PROP-1/2), pas par un test de performance dédié.

## Bug Tracking
**How do we manage issues?**

- Process standard du projet : TDD (rouge confirmé avant chaque nouvelle méthode/comportement),
  `./mvnw verify` avant toute déclaration de succès (skill `verify`), écarts réels trouvés en
  Phase 7 (check implementation) documentés dans `docs/ai/implementation/**` comme pour les
  features précédentes.
