---
phase: design
title: Design — D3a (OAuth2 client credentials sur http-rest)
status: reviewed
created: 2026-09-30
feature: d3a-oauth2-client-credentials
---

# Design — D3a

Faits vérifiés dans le code : `HttpRestConnector.execute` résout l'URL via `OutboundUrlGuard.resolveSafe` (IP épinglée), envoie via `HttpSender`
(redirections désactivées, réponse bornée par `maxResponseBytes`) ; les champs `PASSWORD`/`SECRET_TEXTAREA` du schéma sont chiffrés par
`EnvMasterKeyCipher` et masqués par `ConnectorApiDtos`/`ConnectorInstanceMcpView` **sans code spécifique** ; le SPA rend le formulaire depuis `configSchema`.
`ExecuteConnectorUseCaseImpl` passe la config **déjà déchiffrée**.

## Schéma de configuration (ajouts, tous optionnels)
`authMode` (SELECT `STATIC_HEADER` défaut | `OAUTH2_CLIENT_CREDENTIALS`), `oauthTokenUrl` (URL), `oauthClientId`, `oauthClientAuthMethod`
(SELECT `CLIENT_SECRET_POST` défaut | `CLIENT_SECRET_BASIC` | `PRIVATE_KEY_JWT`), `oauthClientSecret` (PASSWORD), `oauthPrivateKey` (SECRET_TEXTAREA, PEM PKCS#8 RSA),
`oauthCertThumbprint` (empreinte hexadécimale SHA-1 → `x5t`, SHA-256 → `x5t#S256`, telle qu'affichée par le portail Entra), `oauthScope`.

## Composants
- `OAuth2TokenProvider` (package `builtin`, réutilise `HttpSender`) : `String accessToken(ConnectorInstanceData)` et `invalidate(instance)`.
  Cache `ConcurrentHashMap<Key, Entry>` **par pod** (Redis interdit, §14), borné à 256 entrées (éviction des expirées puis de la plus ancienne),
  **single-flight** par entrée (un seul appel IdP concurrent), marge de renouvellement `min(60 s, expires_in/2)`. Clé = tenant + instance + SHA-256 de
  (URL|clientId|méthode|scope|secret/clé|empreinte) : toute modification de configuration change la clé, le secret n'est jamais conservé.
- Requête : `POST` `application/x-www-form-urlencoded`, `grant_type=client_credentials`, `scope`, identifiants selon la méthode. `private_key_jwt` : JWT RS256
  (`iss=sub=client_id`, `aud=URL du jeton`, `jti` UUID, `iat/nbf/exp = +5 min`) signé avec la clé PEM, en-tête `x5t`/`x5t#S256`.
- Réponse : JSON borné, `token_type` = `Bearer` (insensible à la casse) exigé, `access_token` non vide sans caractère de contrôle, `expires_in` numérique
  positif borné à 24 h (défaut 3600 si absent).
- `HttpRestConnector` : en mode OAuth, supprime tout `Authorization` fourni par l'appelant, pose `Authorization: Bearer`; sur `401`, invalide et rejoue **une fois**.
  `validate` : cohérence des champs par méthode, refus de `authHeaderValue` en mode OAuth, URL du jeton passée au garde SSRF.

## Sécurité
Aucun secret/jeton dans logs, exceptions ni `connectorResult` (messages neutres, `error` RFC 6749 limité à `[a-z_]{1,40}`). Le jeton ne sort jamais du connecteur.
Isolation tenant par clé de cache et par config chargée par tenant. Pas de redirection. Corps de réponse IdP jamais recopié.
