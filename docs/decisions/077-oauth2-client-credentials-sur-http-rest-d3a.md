# §77 — OAuth2 client credentials sur `http-rest` (D3a) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §77 · [registre](README.md)

**Énoncé :** sous-lot **D3a** de la Vague D, analyse [34-integrations-entreprise.md](../specs/34-integrations-entreprise.md). Docs SDD :
[requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-d3a-oauth2-client-credentials.md), [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-30-feature-d3a-oauth2-client-credentials.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-30-feature-d3a-oauth2-client-credentials.md), [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-30-feature-d3a-oauth2-client-credentials.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-30-feature-d3a-oauth2-client-credentials.md). Journal : [DECISIONS.md](roadmap-2026/DECISIONS.md).

**Décisions (mandat « roadmap complète », 2026-09-30).** D-INTEG-OAUTH-SCOPE = secret (POST/Basic) **et** certificat `private_key_jwt` ; D-INTEG-OAUTH-MODE = `authMode`
(défaut `STATIC_HEADER`, `authHeaderValue` refusé en mode OAuth) ; D-INTEG-OAUTH-CACHE = mémoire du pod, 256 entrées, single-flight, clé par empreinte de configuration ;
D-INTEG-OAUTH-RETRY = un `401` invalide le jeton et rejoue une fois ; D-INTEG-OAUTH-HOSTS = `oauthAllowedHosts` obligatoire (le jeton ne part que vers ces hôtes) ;
D-INTEG-OAUTH-ERRORS = messages neutres (statut + code `error` normalisé).

**Livré.** `OAuth2TokenProvider` (package `builtin`), schéma `http-rest` étendu (8 champs optionnels, secrets `PASSWORD`/`SECRET_TEXTAREA` donc chiffrés et masqués sans code
spécifique), validation (cohérence par méthode, URL du jeton via le garde SSRF, hôtes), exécution (jeton posé, `Authorization` de l'appelant ignoré, rejeu sur `401`). Aucune
migration, aucune variable d'environnement, aucun changement de SPA (formulaire généré depuis `configSchema`). **Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` (Admin → Connecteurs),
`docs/specs/06`, `docs/specs/34`, `ROADMAP.md`, `CHANGELOG.md`. **Revue adversariale** : voir le document d'implémentation.
