---
phase: requirements
title: Requirements — D3a (OAuth2 client credentials sur http-rest)
status: reviewed
created: 2026-09-30
feature: d3a-oauth2-client-credentials
lot: D3a
todo_ref: TODO.md §77 · analyse 34 §2
---

# Requirements — D3a

## Problème
Le connecteur `http-rest` (SERVICE) n'envoie qu'un `authHeaderValue` **statique**. Les API protégées par Microsoft Entra ID (D365 F&O OData,
Dataverse, Graph) exigent un jeton d'accès qui expire en ~1 h : l'intégrateur doit aujourd'hui le renouveler à la main. Utilisateur : intégrateur
(`TENANT_ADMIN`) configurant un connecteur ; le métier n'est pas exposé.

## Objectifs
- G1. Un mode `OAUTH2_CLIENT_CREDENTIALS` : jeton obtenu auprès de l'endpoint du tenant IdP, **mis en cache**, **renouvelé** avant expiration.
- G2. Authentification client : secret (`client_secret_post` par défaut, `client_secret_basic`) **ou** certificat (`private_key_jwt`, exigé par de
  nombreuses politiques Entra qui interdisent les secrets).
- G3. Secrets chiffrés au repos comme `PASSWORD` (`ConnectorSecretCipher`), masqués en API/MCP, jamais journalisés ni renvoyés dans `connectorResult`.
- G4. Rétro-compatibilité totale : une instance sans `authMode` se comporte exactement comme avant (`STATIC_HEADER`).
- G5. Garde SSRF inchangée sur l'URL du jeton (pinned IP, https), réponse bornée, pas de redirection.

## Hors périmètre
Flux `authorization_code` / `password`, OAuth2 côté entrant (webhook), mTLS de transport, rotation automatique du secret client.

## Décisions ouvertes tranchées (DECISIONS.md)
D-INTEG-OAUTH-SCOPE, D-INTEG-OAUTH-MODE, D-INTEG-OAUTH-CACHE, D-INTEG-OAUTH-RETRY, D-INTEG-OAUTH-ERRORS.

## Critères d'acceptation
- Premier appel : 1 requête jeton + 1 appel API avec `Authorization: Bearer <jeton>` ; appels suivants : 0 requête jeton tant que valide.
- Expiration (moins une marge) → nouveau jeton ; `401` de l'API → jeton invalidé et **un** nouvel essai.
- Changement de configuration (secret, scope, URL) → ancien jeton inutilisable. Deux tenants / deux instances ne partagent jamais un jeton.
- Mode OAuth : `authHeaderValue` hérité ignoré (migration possible) ; un `Authorization` fourni par l'appelant est ignoré ; le jeton ne part que vers `oauthAllowedHosts`.
- Erreurs neutres : jamais le secret, le jeton ni le corps de réponse de l'IdP (seul le code `error` RFC 6749 normalisé).
