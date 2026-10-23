# §42 — Actuator `permitAll` et conflit de port 8081

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §42 · [registre](README.md)

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-42 = **L**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — bind `127.0.0.1`,
port défaut **8090**, `permitAll` conservé (DD-MGMT-SEC) parce que loopback.
Keycloak reste `:8081`.

**Énoncé :** chaîne Security Order 0 `permitAll` sur le port management
(`health` + `prometheus`), décision assumée `observabilite` DD-MGMT-SEC.
Aucun `management.server.address`. En local, Keycloak occupe déjà l'hôte
**8081**.

**Options :** bind localhost / réseau scrape ; mTLS ; ne pas publier 8081 ;
départager `MANAGEMENT_PORT` et `KEYCLOAK_PORT`.

**À produire :** durcissement ops documenté (GETTING-STARTED + runbook) et/ou
changement de défaut yaml.

**Impacte :** `SecurityConfig`, `application.yml`,
[`docs/ai/design/2026-08-20-feature-observabilite.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-20-feature-observabilite.md), `docs/guides/GETTING-STARTED.md`.

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §4 S-3.
