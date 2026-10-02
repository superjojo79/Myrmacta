---
phase: testing
title: Testing — D3a (OAuth2 client credentials sur http-rest)
status: done
feature: d3a-oauth2-client-credentials
---

| Couche | Preuves |
|---|---|
| Fournisseur de jeton | `OAuth2TokenProviderTest` (20) : secret POST/Basic, encodage, `private_key_jwt` vérifié par signature RSA et en-tête `x5t`/`x5t#S256`, cache, renouvellement, `expires_in` assaini, isolation tenant/instance/configuration, single-flight concurrent, borne du cache, erreurs neutres, refus non-Bearer / CRLF / JSON invalide, garde SSRF, réponse trop grande, clé invalide |
| Connecteur | `HttpRestConnectorOAuthTest` (13) : Bearer posé et réutilisé, `Authorization` appelant ignoré, rejeu unique sur `401`, hôtes autorisés (suffixes, userinfo, wildcard), mode statique inchangé, schéma, validation |
| API réelle (chaîne HTTP + sécurité + chiffrement) | `ConnectorAdminApiTest` : secrets masqués (création et lecture), config ambiguë 400, URL de jeton loopback 400 |
| MCP réel | `McpToolCallIntegrationTest` : secret chiffré en base, absent de get/list/réponse, champs visibles dans `list_connector_definitions` |

Aucune modification du SPA (formulaire généré depuis `configSchema`) : pas de test Vitest/Playwright dédié ; suites complètes rejouées en clôture.
