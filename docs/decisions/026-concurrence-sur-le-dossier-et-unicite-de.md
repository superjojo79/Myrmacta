# §26 — Concurrence sur le dossier et unicité de la référence

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §26 · [registre](README.md)

**Constat révisé (feature `fiabilisation-tests-plateforme`, 2026-08-22) :** relecture du code avant
implémentation a montré que 2 des 3 points listés ici étaient déjà résolus et jamais mis à jour :
verrouillage optimiste `@Version` déjà présent sur `Dossier` (`DossierJpaEntity.java`) ; le bug
allégué (`Instant` ne supporte pas `%tY`) n'existe pas dans `SequentialReferenceGenerator.java`
(utilise `clock.instant().atZone(ZoneOffset.UTC).getYear()`, un `int`). Seul le 3ᵉ point —
`Dossier.reference` sous accès concurrent multi-pods réel — restait ouvert.

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :** `SequentialReferenceMultiPodIT`
(Testcontainers PostgreSQL, 8 pods simulés par 8 `DataSource`/`TransactionTemplate` indépendants,
80 générations concurrentes) prouve l'absence de doublon et de trou dans la séquence sous charge
réelle. Écrit et compilé ; non exécuté dans le bac à sable de développement (Docker absent) —
exécution/vérification en CI (`ubuntu-latest`, Docker natif). Voir
[`docs/ai/testing/2026-08-22-feature-fiabilisation-tests-plateforme.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-08-22-feature-fiabilisation-tests-plateforme.md).

**Impacte :** [04-dossiers.md](../specs/04-dossiers.md).
