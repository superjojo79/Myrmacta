---
phase: implementation
title: Implémentation — D3a (OAuth2 client credentials sur http-rest)
feature: d3a-oauth2-client-credentials
---

# Implémentation — D3a (TODO §77)

`OAuth2TokenProvider` (package `infrastructure.connector.builtin`, réutilise `HttpSender`) ; `HttpRestConnector` : schéma (+9 champs optionnels), `validateOutboundAuth`,
`call`/`execute` (jeton, rejeu 401 borné, hôtes autorisés) ; `ManageConnectorInstanceUseCaseImpl` : refus du masque comme secret à la création. Aucune migration, aucune
variable d'environnement, aucun changement de SPA.

## Revue adversariale (Phase 10) — 1 passe, sous-agent indépendant avec sondes exécutées
0 Critique / 0 Haute. Constats traités avec test de régression permanent :

| # | Sév. | Constat | Correctif |
|---|---|---|---|
| 1 | Moyenne | Migration STATIC → OAuth impossible : `authHeaderValue` (masqué, conservé par la fusion) était refusé en mode OAuth | `authHeaderValue` ignoré en mode OAuth (D-INTEG-OAUTH-MODE amendée) ; test API de migration |
| 2 | Moyenne | Rafale de 401 : 11 requêtes IdP et 20 POST pour 10 appels (throttling Entra, rejeu de POST) | Renouvellement forcé seulement si jeton ≥ 30 s, échec IdP mémorisé 5 s (D-INTEG-OAUTH-STORM) |
| 3 | Basse | L'éviction retirait une entrée en cours d'obtention (2 fetch) ; dépassement faible de la borne | Entrées en cours jamais évincées, insertion/éviction sous verrou ; tests de concurrence |
| 4 | Basse | `expires_in` hors `long` → exception Jackson brute ; `≤ 0` = aucun cache | `BigDecimal`, borné à 24 h, `≤ 0` refusé |
| 5 | Basse | `*.com`, joker multi-niveaux, hébergements mutualisés acceptés | Règles D-INTEG-OAUTH-WILDCARD |
| 6 | Basse | `********` accepté comme vrai secret à la création | Refusé (`ConnectorValidationException`) |
| 7 | Basse | `access_token` non borné (4 Mo accepté) | ≤ 16 Ko |
| 8 | Basse | Lecture non atomique `token`/`renewAt` | Record immuable dans un seul champ volatil |
| i | Info | `"Authorization "` (espace final) non filtré | Comparaison après `trim()` |

Non traité, consigné : le SNI n'est pas envoyé vers l'IP épinglée (comportement préexistant partagé avec `WebhookRestAdapter`) — le fonctionnement contre
`login.microsoftonline.com` n'est pas démontrable hors intégration réelle. Passe 2 non requise (aucun Critique/Haut).
