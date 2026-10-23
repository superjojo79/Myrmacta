# §31 — Découpage en lots d'implémentation

> **Statut :** clos · **Section :** [Points ouverts › Processus](section-points-processus.md) · ancien `TODO.md` §31 · [registre](README.md)

**Énoncé initial :** la documentation décrivait une cible, pas un chemin — il n'existait ni lots,
ni séquencement, ni critères d'acceptation.

**Clos (2026-08-20) :** le découpage en lots se fait en pratique, en continu, via le trio
[FILE-ATTENTE.md](../../FILE-ATTENTE.md) (ordre recommandé + critères de sélection) /
[DEFERRED-FEATURES.md](../../DEFERRED-FEATURES.md) (inventaire consolidé) / `docs/ai/{requirements,
design, planning, …}` par feature (support `dev-lifecycle`, un lot = un dossier daté) — quatorze
lots lifecycle (`workflow-engine` → `observabilite`) puis six features de suivi livrées à ce jour
(voir `FILE-ATTENTE.md` § Récemment livré, [DEFERRED-FEATURES.md](../../DEFERRED-FEATURES.md) §2). Pas
de plan statique unique à produire en plus : le séquencement de
[docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md) §9 reste la référence pour l'ordre
initial, `FILE-ATTENTE.md` prend le relais pour la suite et se met à jour à chaque clôture.

**Volet outillage provisioning (lot A1, 2026-09-08) — clos :** `Dockerfile` (multi-stage
`eclipse-temurin:25-jdk`/`-jre`, utilisateur non-root, heap `-XX:MaxRAMPercentage`) et
`scripts/provision-tenant.sh` (schéma + rôle PostgreSQL par tenant, D-41 point 41) livrés ;
`server.servlet.context-path` (décision n°14) activé par défaut hors profil `local`. Validation
réelle : `./mvnw verify` (1738 tests, 0 échec), `hadolint`/`shellcheck` propres, smoke PostgreSQL
réel (isolation cross-tenant vérifiée). **Limite assumée** : `docker build`/`docker run` réel non
exécutable dans le bac à sable de ce cycle (politique réseau bloquant Docker Hub) — Dockerfile
validé par `hadolint` + revue statique uniquement, à confirmer par un build réel avant premier
déploiement. Détail :
[docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md).

**Documenté dans :** [FILE-ATTENTE.md](../../FILE-ATTENTE.md), [DEFERRED-FEATURES.md](../../DEFERRED-FEATURES.md).
