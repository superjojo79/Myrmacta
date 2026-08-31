---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup
**How do we get started?**

Aucun prérequis nouveau — `./mvnw verify` (backend uniquement, feature confirmée backend-only,
zéro fichier `frontend/**`). Pas de migration Liquibase, pas de nouvelle dépendance Maven.

## Code Structure
**How is the code organized?**

Fichiers modifiés/créés/supprimés (M1–M6, tous dans `com.flowforge.infrastructure`) :

| Fichier | Changement |
|---|---|
| `infrastructure/config/ConnectorProperties.java` | Nouveau sous-record `Http(connectTimeout, requestTimeout)`, défauts 5 s/5 s |
| `infrastructure/config/ConnectorConfig.java` | Nouveau `@Bean HttpClient` (aucun n'existait avant) |
| `infrastructure/connector/builtin/HttpRestTriggerConnector.java` | **Renommé** → `HttpRestConnector.java` ; ajoute `ServiceConnector`, capability `BOTH`, `execute(...)`, `authHeaderValue` au schéma |
| `infrastructure/connector/builtin/HttpRestServiceConnector.java` | **Supprimé** (logique fusionnée) |
| `infrastructure/web/connector/ConnectorApiDtos.java` | `ExecuteConnectorRequest` étendu (`url`, `method`, `body`, `headers`) — **écart design corrigé, voir Design Deviations** |
| `analysis doc/06-connecteurs.md` | Tableau catalogue mis à jour |
| `README.md` | § Système de connecteurs mis à jour |
| `openapi/openapi.yaml` | Golden régénéré (`-Dopenapi.update=true`) — **conséquence de l'écart ci-dessus** |

Tests :

| Fichier | Changement |
|---|---|
| `test/.../builtin/HttpRestTriggerConnectorTest.java` | **Renommé** → `HttpRestConnectorTest.java`, fusionné avec `HttpRestServiceConnectorTest.java` (supprimé) — 33 tests, dont les 2 tests « non activé » inversés |
| `test/.../config/ConnectorPropertiesTest.java` | +3 tests `Http` (défauts, override, override partiel) ; 2 appels constructeur existants passés à 3 arguments |
| `test/.../config/ConnectorConfigTest.java` | **Nouveau** — bean `HttpClient` présent avec le bon `connectTimeout` |
| `test/.../web/connector/ConnectorExecuteApiTest.java` | +4 tests (`http-rest` via BFF/M2M — `url`/`method`/`body` atteignent réellement le connecteur, régression `url` manquant) |
| `test/.../connector/crypto/EnvMasterKeyCipherTest.java`, `.../persistence/connector/JpaWebhookIdempotencyStoreAdapterTest.java`, `.../web/connector/WebhookControllerTest.java` | Appels `new ConnectorProperties(...)` mis à jour (2→3 arguments, `null` pour `http`) — casse mécanique de l'ajout du sous-record |

## Implementation Notes
**Key technical details to remember:**

### Core Features
- **Fusion de capacité (D-CAPABILITY-SCOPE)** : `HttpRestConnector` est le seul bean `ConnectorDefinition` pour le type `"http-rest"` (contrainte `ConnectorCatalog`, un bean par type) — `getCapability()` retourne `BOTH`, `execute(...)` cohabite avec `onEvent(...)` dans la même classe.
- **`execute(...)` (M2–M4)** : `url` (obligatoire), `method` (`GET` par défaut, `GET`/`POST`/`PUT` seuls acceptés, insensible à la casse), `body` (rejeté sur `GET`, sérialisé en JSON via `ObjectMapper` sauf si déjà une `String`), `headers` (`Map` appliquée après l'`Authorization` de config, peut l'écraser). `OutboundUrlGuard.resolveSafe` appelé avant toute construction de requête, indépendamment de la méthode.
- **`authHeaderValue`** : champ `PASSWORD` optionnel, chiffré au repos par le mécanisme générique existant (aucune évolution de `ConnectorSecretCipher`), jamais renvoyé dans le résultat (`Map.of("status", ..., "body", ...)` uniquement).

### Patterns & Best Practices
- `HttpRequest.Builder.setHeader(...)` (pas `.header(...)`) pour l'`Authorization` de config et les `headers` d'appel — évite un doublon d'en-tête si le même nom est posé deux fois.
- Sérialisation JSON avec capture de `tools.jackson.core.JacksonException` → `IllegalStateException`, même pattern que `WebhookRestAdapter.send(...)`.

## Integration Points
**How do pieces connect?**

- `ConnectorServiceTask` (JavaDelegate), `ConnectorExecuteController` (BFF), `M2mConnectorExecuteController` (M2M) — **aucun changé**, tous appellent `ExecuteConnectorUseCase`, inchangé lui aussi. Seul `ExecuteConnectorRequest` (DTO du BFF/M2M) a dû être étendu — voir Design Deviations.
- Preuve d'intégration bout en bout : `ConnectorExecuteApiTest` crée une vraie instance `http-rest` via l'API admin, l'exécute via les deux endpoints, avec une URL bloquée par `OutboundUrlGuard` pour obtenir un résultat déterministe sans appel réseau réel (`400` avec message distinctif "not allowed" vs "url is required" — prouve que `url` atteint bien le connecteur, pas seulement que la requête échoue).

## Error Handling
**How do we handle failures?**

- `IllegalArgumentException` (`url` manquant, méthode non supportée, corps sur `GET`, `headers` mal typé, URL bloquée SSRF) → `400` via `ConnectorExceptionHandler.handleBadRequest` (déjà générique, aucun changement).
- **Correction d'une erreur de fait du doc requirements** : avant cette feature, une tentative d'exécution SERVICE sur `http-rest` répondait **501** (`UnsupportedOperationException` → `ConnectorExceptionHandler.handleUnsupported`), pas 500 comme énoncé initialement dans `docs/ai/requirements/2026-08-31-feature-connecteur-http-rest-service.md`. Correction sans impact sur le comportement livré (le point important — plus de rejet automatique — reste vrai).

## Performance Considerations
**How do we keep it fast?**

- `HttpClient` avec `connectTimeout` (5 s par défaut) au niveau du bean ; `requestTimeout` (5 s par défaut) appliqué par requête via `HttpRequest.Builder.timeout(...)`. Les deux configurables (`app.connector.http.connect-timeout`/`request-timeout`).

## Security Notes
**What security measures are in place?**

- SSRF : `OutboundUrlGuard.resolveSafe` inchangé, appelé pour toute méthode HTTP (pas seulement `GET`).
- **Redirect policy explicite (trouvé en Phase 7, `ConnectorConfig.httpClient`)** : le bean
  `HttpClient` fixe désormais `.followRedirects(HttpClient.Redirect.NEVER)` explicitement, plutôt
  que de s'appuyer sur le défaut implicite de la JDK (qui est déjà `NEVER`, donc aucun changement
  de comportement livré). Justification : une redirection suivie n'est jamais re-vérifiée contre
  `OutboundUrlGuard` — un serveur externe validé pourrait rediriger vers une adresse interne et
  contourner entièrement le pin d'IP. Rendu explicite avec commentaire + test dédié
  (`httpClientBean_neverFollowsRedirects`) pour qu'un changement futur ne puisse pas régresser
  cette garantie silencieusement.
- Anti-fuite : `authHeaderValue` jamais échoé dans le résultat — prouvé par test dédié
  (`execute_authHeaderValueNeverLeaksInResult`), même contrat que l'ancien skeleton
  (`execute_neverEchoesInstanceConfigInResult`, conservé).
- Chiffrement au repos : `authHeaderValue` suit le mécanisme `PASSWORD` générique déjà en place, aucune liste blanche par nom de champ à mettre à jour.

## Design Deviations
**Écarts réels trouvés en implémentation, absents du design (Phase 3)**

1. **`ExecuteConnectorRequest` (DTO REST) câblé en dur sur `flowable-signal`.** Le design (§ API Design) affirmait « aucun nouvel endpoint, aucun changement de contrat » en se fondant sur le fait que `ConnectorExecuteController`/`M2mConnectorExecuteController` n'ont pas changé — vrai, mais incomplet : leur DTO de requête (`ExecuteConnectorRequest`, `ConnectorApiDtos.java`) ne portait que `signalName`/`processInstanceId`/`variables`, sans moyen de transmettre `url`/`method`/`body`/`headers`. Sans correction, `http-rest` SERVICE restait **inatteignable** via le BFF/M2M malgré la capacité activée côté connecteur — trouvé par un test délibérément conçu pour distinguer « url atteint le connecteur et est rejetée (bloquée) » de « url jamais transmise » (`execute_bffHttpRestInstance_urlReachesConnector`), rouge avant la correction. Corrigé en ajoutant les 4 champs au DTO.
2. **Golden OpenAPI (`openapi/openapi.yaml`) — conséquence directe de (1).** Le design affirmait « pas de régénération attendue » ; l'extension du DTO ajoute 4 propriétés optionnelles au schéma de requête des deux endpoints `execute`. Régénéré (`-Dopenapi.update=true`), diff de 8 lignes, exactement les 4 nouveaux champs × 2 endpoints partageant le même schéma de composant.
3. **Erreur de fait mineure dans le doc requirements** (pas le design) : le code HTTP avant cette feature pour une tentative SERVICE sur `http-rest` est **501**, pas 500 comme énoncé — `ConnectorExceptionHandler.handleUnsupported` existait déjà et mappait `UnsupportedOperationException` explicitement. N'affecte aucune décision ni aucun comportement livré.

Aucun de ces écarts ne remet en cause une décision produit (D-CAPABILITY-SCOPE, D-HTTP-METHODS,
D-HTTP-CONFIG, D-HTTP-AUTH-SHAPE, D-HTTP-CONTENT-TYPE, D-HTTP-NAMING, D-HTTP-TESTS restent tous
valides tels que tranchés) — uniquement des détails de câblage manqués en Phase 3, corrigés en
TDD avec un test rouge avant chaque correction.

## Phase 7 — Check Implementation

**Relecture fichier par fichier** (`HttpRestConnector.java`, `ConnectorConfig.java`,
`ConnectorProperties.java`, `ConnectorApiDtos.java`, `HttpRestConnectorTest.java`,
`ConnectorConfigTest.java`, `ConnectorExecuteApiTest.java`) contre le design et les requirements.

**1 écart réel trouvé** (sécurité, sévérité faible — pas d'impact comportemental livré) : bean
`HttpClient` reposant sur le défaut implicite `Redirect.NEVER` de la JDK au lieu de le rendre
explicite — voir « Security Notes » ci-dessus pour le détail et la correction (commentaire + test
dédié, aucun changement de comportement).

**Vérifications négatives effectuées** (rien trouvé, mentionné pour traçabilité) :
- Recherche exhaustive des ~40 références à `"http-rest"` dans le reste de la base de tests —
  toutes utilisent un `ConnectorDefinition` mocké/stubé (`ConnectorRegistryTest`,
  `ConnectorTriggerRuntimeImplTest`, `ExecuteConnectorUseCaseImplTest`,
  `ManageConnectorInstanceUseCaseImplTest`, etc.), aucune ne teste le vrai bean
  `HttpRestConnector` — aucune ne pouvait donc être cassée par ce changement (confirmé par
  `./mvnw verify` complet, 0 échec).
- Confirmation que `ConnectorInstanceData.config()` n'est jamais `null` (compact constructor,
  défaut `Map.of()`) — pas de risque de `NullPointerException` sur
  `instance.config().get("authHeaderValue")`.
- Confirmation que `ConnectorProperties.http()`/`Http.requestTimeout()` ne sont jamais `null`
  (compact constructors en cascade) — pas de risque de `NullPointerException` sur
  `HttpRequest.Builder.timeout(...)`.
- Le risque déjà noté en design (§ Non-Functional Requirements) — connexion HTTPS vers l'IP
  pinnée pouvant échouer la vérification TLS/SNI pour un hébergement virtuel — reste inchangé,
  hérité du skeleton, non aggravé par cette feature ; pas retraité ici (déjà consciemment différé
  au test manuel optionnel, `docs/ai/testing/**` § Manual Testing).

**Alignement design/requirements : conforme.** Aucune déviation majeure, aucune décision produit
remise en cause. `./mvnw verify` vert après correction (voir preuve de vérification ci-dessous).
