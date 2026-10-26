# §91 — PostgreSQL embarqué (zonky) pour les tests et les démos/QA (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §91 · [registre](README.md)

**Énoncé :** remplacer H2 (émulation) et Testcontainers-PostgreSQL (Docker requis) par un PostgreSQL
embarqué ; offrir une base **persistante** pour les démos/QA. Décision : [47-postgresql-embarque-tests-demo.md](../specs/47-postgresql-embarque-tests-demo.md) —
remplace D-TESTCONTAINERS-EXEC. Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-02-feature-zonky-embedded-postgres.md),
[design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-02-feature-zonky-embedded-postgres.md), [planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-10-02-feature-zonky-embedded-postgres.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-02-feature-zonky-embedded-postgres.md), [testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-10-02-feature-zonky-embedded-postgres.md) ;
prompt source : [docs/goals/zonky-embedded-postgres.md](../goals/zonky-embedded-postgres.md).

**Livré.** zonky 2.2.2 / PostgreSQL 17 (aligné docker-compose) ; une instance par JVM, une base par
contexte Spring ; H2, `testcontainers-postgresql`, branches `dbms: h2` et `sql/*-h2.sql` retirés (checksums
PostgreSQL inchangés, gardés par `ChangelogChecksumStabilityIT`) ; `*IT` inclus dans `verify` ;
profil `embedded-db` persistant (Maven + Spring), `scripts/embedded-db.sh`, Dockerfile `MAVEN_PROFILES`,
garde-fou CI « pas de zonky dans le jar de production ». Vérification : `./mvnw verify` **2747 tests, 0 échec**,
Jacoco tenu (avant : 2653 tests H2, ITs hors `verify`).

**Constats corrigés.** Bug de production `SequentialReferenceGenerator` (transaction PostgreSQL avortée dès
que la ligne du compteur existe ; masqué par H2) ; les 34 `*IT` n'étaient jamais exécutés par la CI.

**Point ouvert (non tranché).** `DocumentTemplateJpaEntity.htmlContent` est un `@Lob String` sur une colonne
`text` : Hibernate y stocke un OID de large object (HTML réel dans `pg_largeobject`). Changer le mapping exige une
migration des données existantes — à décider avant toute évolution du modèle de templates.

**Impacte :** `docs/guides/GETTING-STARTED.md` (§17, §18), `docs/guides/DEPLOIEMENT.md` (§18), `.env.example` (`APP_EMBEDDED_DB_*`, `FLOWFORGE_TEST_PG_*`),
`CHANGELOG.md`, `Dockerfile`, `.github/workflows/ci.yml`, skill `myrmacta-conventions`.
