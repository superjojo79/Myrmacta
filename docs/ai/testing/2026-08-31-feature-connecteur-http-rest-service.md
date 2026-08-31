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
- [ ] T-CAP-1 : implémente `ConnectorDefinition` (inversion de
      `connector_doesNotImplementConnectorDefinition`, D-HTTP-TESTS)
- [ ] T-CAP-2 : implémente `ServiceConnector`
- [ ] T-CAP-3 : `getCapability() == ConnectorCapability.BOTH`
- [ ] T-CAP-4 : annoté `@Component` (inversion de `connector_isNotASpringBean`)
- [ ] T-CAP-5 (régression) : `getType()` retourne toujours `"http-rest"`

### `HttpRestConnector` — rôle TRIGGER (régression pure, comportement inchangé)
- [ ] T-TRIG-1..N : tous les tests existants de `HttpRestTriggerConnectorTest` migrent tels quels
      dans `HttpRestConnectorTest` et continuent de passer sans modification d'assertion

### `HttpRestConnector.execute` — méthode HTTP (D-HTTP-METHODS)
- [ ] T-EXEC-1 (régression) : `method` absent → `GET` par défaut, comportement du skeleton inchangé
- [ ] T-EXEC-2 : `method="GET"` explicite fonctionne
- [ ] T-EXEC-3 : `method="POST"` + `body` → requête `POST` avec le corps
- [ ] T-EXEC-4 : `method="PUT"` + `body` → requête `PUT` avec le corps
- [ ] T-EXEC-5 : `method` insensible à la casse (`"post"` équivalent à `"POST"`)
- [ ] T-EXEC-6 : `method` non supporté (ex. `"DELETE"`, `"PATCH"`) → `IllegalArgumentException`,
      aucun appel `httpClient.send`
- [ ] T-EXEC-7 : `body` fourni avec `method="GET"` → `IllegalArgumentException`, aucun appel
      `httpClient.send`

### `HttpRestConnector.execute` — sérialisation du corps
- [ ] T-BODY-1 : `body` de type `Map`/objet → sérialisé en JSON via `ObjectMapper`
- [ ] T-BODY-2 : `body` déjà une `String` → envoyé tel quel (pas de double-encodage JSON)
- [ ] T-BODY-3 : `POST`/`PUT` sans `body` → corps vide (`HttpRequest.BodyPublishers.noBody()`)

### `HttpRestConnector.execute` — en-têtes (D-HTTP-CONTENT-TYPE)
- [ ] T-HDR-1 : `connectorInput.headers` (`Map<String,Object>`) appliqués sur la requête
- [ ] T-HDR-2 : `headers` présent mais pas une `Map` → `IllegalArgumentException`
- [ ] T-HDR-3 : pas de clé `headers` → aucun en-tête supplémentaire (hormis `Authorization` si
      `authHeaderValue` configuré)
- [ ] T-HDR-4 : aucun `Content-Type` ajouté par défaut sur `POST`/`PUT` avec corps si non fourni
      explicitement dans `headers` (comportement D-HTTP-CONTENT-TYPE, pas une omission)

### `HttpRestConnector.execute` — `authHeaderValue` (D-HTTP-CONFIG, D-HTTP-AUTH-SHAPE)
- [ ] T-AUTH-1 : `instance.config().authHeaderValue` renseigné → en-tête `Authorization` envoyé
      avec exactement cette valeur
- [ ] T-AUTH-2 (régression) : `authHeaderValue` absent/blanc → aucun en-tête `Authorization` ajouté
      (rétrocompatible avec les instances TRIGGER-seules existantes)
- [ ] T-AUTH-3 : `connectorInput.headers` fournit explicitement `Authorization` → écrase la valeur
      issue de la config (précédence design)
- [ ] T-AUTH-4 (anti-fuite) : `authHeaderValue` n'apparaît jamais dans le résultat retourné, quel
      que soit le chemin (extension de `execute_neverEchoesInstanceConfigInResult`)

### `HttpRestConnector.execute` — SSRF / `OutboundUrlGuard` (régression pure, inchangé)
- [ ] T-SSRF-1..N : tous les tests SSRF/anti-fuite hérités de `HttpRestServiceConnectorTest`
      (URL non sûre rejetée sans appel réseau, IP pinnée réutilisée sans second lookup, `url`
      manquant rejeté, `IOException`/`InterruptedException` correctement enveloppées) migrent
      inchangés — exercés pour `GET` **et** au moins un cas `POST`/`PUT` (le guard s'applique
      indépendamment de la méthode)

### `getConfigSchema()`
- [ ] T-SCHEMA-1 (régression) : les 4 champs TRIGGER existants (`webhookAuthType`, `webhookSecret`,
      `processKey`, `dossierTypeCode`) inchangés
- [ ] T-SCHEMA-2 : nouveau champ `authHeaderValue`, `type=PASSWORD`, `required=false`

### `ConnectorConfig` (câblage Spring)
- [ ] T-CFG-1 : le contexte Spring démarre avec le nouveau bean `HttpClient`
- [ ] T-CFG-2 : `HttpRestConnector` est construit avec ses 4 dépendances (`SimpleJsonPathMapper`,
      `OutboundUrlGuard`, `HttpClient`, `ObjectMapper`)

### `ConnectorProperties.Http` (nouveau)
- [ ] T-PROP-1 : `connectTimeout`/`requestTimeout` par défaut = 5 s quand non configurés
- [ ] T-PROP-2 : surchargeables via `app.connector.http.connect-timeout`/`request-timeout`

## Integration Tests
**How do we test component interactions?**

- [ ] T-INT-1 : `ExecuteConnectorUseCaseImpl.execute(...)` sur une instance `http-rest` réussit —
      ne lève plus `UnsupportedOperationException` (régression du comportement actuel, cœur de la
      feature)
- [ ] T-INT-2 : `ConnectorServiceTask` (JavaDelegate) bout-en-bout avec une instance `http-rest`
      pose `connectorResult` en variable de process
- [ ] T-INT-3 : `POST /api/connectors/{instanceId}/execute` (BFF, `ConnectorExecuteController`) sur
      une instance `http-rest` → `200`
- [ ] T-INT-4 : `POST /api/connectors/{instanceId}/execute` (M2M, `M2mConnectorExecuteController`)
      sur une instance `http-rest` → `200`
- [ ] T-INT-5 (régression) : `connectorInput` malformé (`url` invalide) → `400` via le gestionnaire
      générique existant (`IllegalArgumentException` → `handleBadRequest`), aucun nouveau mapping
      d'exception nécessaire
- [ ] T-INT-6 (régression) : `flowable-signal` (seul autre connecteur `BOTH` aujourd'hui) continue
      de fonctionner sans changement — aucune régression croisée introduite par la modification de
      `ConnectorCatalog`/`ExecuteConnectorUseCaseImpl` (non modifiés, mais à vérifier explicitement)

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

- Seuils Jacoco bundle existants (LINE ≥ 85% / BRANCH ≥ 75%) à tenir — aucun changement de seuil
  attendu, contrairement à `fiabilisation-tests-plateforme` qui les avait relevés.
- Coverage gaps : à balayer classe par classe en Phase 8 (pas juste le total bundle), même
  discipline que les features précédentes (`durcissement-donnees-sensibles`, `schema-champs-dynamiques`).

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
