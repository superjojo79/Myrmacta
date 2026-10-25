# §32 — Retrait synchronisé des exclusions autoconfig temporaires (Security/OAuth2/Flowable)

> **Statut :** clos · **Section :** [Décisions actées › Sécurité](section-decisions-securite.md) · ancien `TODO.md` §32 · [registre](README.md)

**Problème (revue de code, phase architecture-hexagonale) :** `src/main/resources/application.yml`
désactivait Security, OAuth2 client et Flowable via des entrées séparées dans
`spring.autoconfigure.exclude` (3 lignes à elles seules pour la sécurité), documentées comme
temporaires « à retirer feature par feature ». Rien n'empêchait un retrait partiel de ces
exclusions lors de l'implémentation de la feature `authentification` — l'application aurait pu
démarrer sans filtre de sécurité actif sans qu'aucun test ne le détecte.

**Décision retenue :** la feature `authentification` (PR #12) a retiré les 3 lignes Security/OAuth2
d'un seul mouvement, en même temps qu'elle câblait `SecurityConfig` (chaîne BFF `oauth2Login` +
CSRF + `TenantResolutionFilter`). Vérifié en revue de code post-merge : plus aucune exclusion
Security/OAuth2 dans `application.yml` ; `BffSecurityTest` couvre le 401/403 anonyme et mismatch de
tenant, donc un retrait partiel futur (ex. lors du retrait des exclusions JPA/Flowable restantes)
serait immédiatement détecté par la suite de tests existante. Pas de garde dédiée supplémentaire
jugée nécessaire au-delà de cette couverture.

**Documenté dans :** [docs/ai/requirements/2026-07-31-feature-authentification.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-07-31-feature-authentification.md),
`src/main/java/be/myrmacta/infrastructure/security/SecurityConfig.java`.
