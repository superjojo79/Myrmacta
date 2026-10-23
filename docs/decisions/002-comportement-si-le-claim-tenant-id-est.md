# §2 — Comportement si le claim `tenant_id` est absent du token OIDC

> **Statut :** clos · **Section :** [Décisions actées › Sécurité](section-decisions-securite.md) · ancien `TODO.md` §2 · [registre](README.md)

**Problème :** Le `TenantResolutionFilter` ne rejetait pas la requête si le claim `tenant_id` était absent, et le `TenantIdentifierResolver` tombait sur le fallback `"public"`, rendant le schéma public accessible.

**Décision retenue :** Le `TenantResolutionFilter` rejette la requête (403) si le claim est absent ou vide. Le `TenantIdentifierResolver` ne fait plus de fallback silencieux : il lève une exception si aucun tenant n'est présent dans le contexte. Le schéma `public` reste réservé à un contexte système explicite (jobs internes, migration), jamais atteint via une requête API.

> **Mise à jour** — avec la décision n° 13, le `TenantResolutionFilter` ne sélectionne plus de
> schéma : il vérifie que le claim `tenant_id` du token correspond au tenant configuré de l'instance,
> et rejette (403) sinon ou si le claim est absent. `TenantIdentifierResolver` disparaît avec le SPI
> multi-tenant Hibernate, devenu inutile. Répercuté dans
> [02-multi-tenant.md](../specs/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](../specs/02-multi-tenant.md).
