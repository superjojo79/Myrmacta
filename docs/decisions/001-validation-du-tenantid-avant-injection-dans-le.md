# §1 — Validation du `tenantId` avant injection dans le `search_path`

> **Statut :** clos · **Section :** [Décisions actées › Sécurité](section-decisions-securite.md) · ancien `TODO.md` §1 · [registre](README.md)

**Problème :** `SchemaMultiTenantConnectionProvider` concaténait directement le `tenantId` dans la requête SQL `SET search_path TO {tenantId}, public` sans sanitisation.

**Décision retenue :** Double barrière — (1) validation par regex (`^[a-z][a-z0-9_]{2,62}$`) à la création du tenant, alignée sur les contraintes d'identifiant PostgreSQL, et (2) vérification à chaque résolution contre un registre des tenants actifs mis en cache, plus échappement de l'identifiant via `Connection.enquoteIdentifier()` au lieu d'une concaténation brute (défense en profondeur).

> **Mise à jour** — ce mécanisme de bascule dynamique du schéma par requête est remplacé par le
> modèle d'instance dédiée par tenant de la décision n° 13. La validation par regex reste pertinente
> au provisioning (création du schéma) ; le registre des tenants actifs consulté à chaque requête et
> le routage dynamique du `search_path` n'ont plus lieu d'être. Répercuté dans
> [02-multi-tenant.md](../specs/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](../specs/02-multi-tenant.md).
