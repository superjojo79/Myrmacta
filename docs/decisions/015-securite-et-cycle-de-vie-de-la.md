# §15 — Sécurité et cycle de vie de la session BFF — CSRF, déconnexion, session par tenant

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §15 · [registre](README.md)

**Problème :** le pattern BFF avec cookie de session était retenu et justifié, mais aucune de ses conséquences directes n'était traitée. Spring Security active la protection CSRF par défaut — sans configuration, toutes les requêtes mutantes de la SPA échoueraient en 403. Le `logout` documenté se limitait à `session.invalidate()`, laissant la session du fournisseur OIDC ouverte et permettant une reconnexion silencieuse de l'utilisateur.

**Décision retenue :** Jeton CSRF porté par un cookie lisible en JavaScript (`CookieCsrfTokenRepository`, pattern double-submit), avec `SameSite=Lax` — hypothèse confirmée par la décision n° 27 (frontend embarqué dans `static/`, même origine) ; un filtre force la résolution du token à chaque requête, contournant le rendu paresseux introduit par Spring Security 6 (comportement toujours présent sous Spring Boot 4 / Security 7). Webhooks et API machine-to-machine ne sont pas exemptés via `ignoringRequestMatchers` mais isolés dans leurs propres chaînes de sécurité (`securityMatcher`, sans session ni CSRF), au même titre que la chaîne `client_credentials` de la décision n° 14 — trois chaînes ordonnées (`@Order`), partitionnées, jamais traversées simultanément par une même requête.

Déconnexion via `OidcClientInitiatedLogoutSuccessHandler`, qui appelle l'`end_session_endpoint` du fournisseur avec l'`id_token_hint` pour fermer la session Keycloak en plus de la session locale — la reconnexion silencieuse disparaît. Effet de bord identifié en corrigeant ce point : le routage par chemin de la décision n° 14 fait varier l'URL de callback OAuth2 et de post-logout par tenant ; une entrée à caractère joker (`https://example.com/*`) côté client Keycloak partagé couvre tous les tenants sans provisioning supplémentaire par tenant.

La session partagée entre répliques reste hors périmètre de la V1 (déjà tranché par la décision n° 13) — non requise par défaut, à réexaminer seulement si un tenant précis est scalé sur plusieurs répliques.

**Documenté dans :** [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [03-authentification.md](../specs/03-authentification.md) (protection CSRF, déconnexion, chaînes de sécurité), [11-frontend.md](../specs/11-frontend.md) (renvoi vers la configuration Axios).
