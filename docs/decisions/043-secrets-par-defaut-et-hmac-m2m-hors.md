# §43 — Secrets par défaut et HMAC M2M hors profil local

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §43 · [registre](README.md)

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-43 = **F**,
DD-DENYLIST = **L**, [PR #76](https://github.com/superjojo79/FlowForge/pull/76)) —
fail-fast hors `local`/`test` ; denylist vide + `flowforge`/`postgres`/`admin`/
`password` (trim, casse ignorée) ; HMAC M2M interdit hors ces profils.

**Énoncé :** fallback `${DB_PASSWORD:flowforge}` ; Compose Postgres / Keycloak /
MinIO avec mots de passe connus ; `M2M_JWT_SECRET` HMAC HS256 optionnel accepte
des JWT `API_SERVICE` sur `/api/m2m/**`.

**Options :** fail-fast hors `local`/`test` si secrets / issuer absents ;
refuser HMAC hors ces profils ; secrets Compose obligatoires.

**À produire :** décision profil / fail-fast + tests de démarrage.

**Impacte :** `application.yml`, `docker-compose.yml`, `SecurityConfig`.

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §4 S-4.
