# §27 — Versions figées, structure du build et packaging du frontend

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §27 · [registre](README.md)

**Problème :** les versions de stack n'étaient pas épinglées (`Flowable 7.x.x`, `Spring Boot 3.x`, Java 21) ; les seules versions figées (`flying-saucer-pdf-openpdf 9.9.4`, `jsoup 1.17.2`) dataient ; ni structure Maven ni packaging du frontend n'étaient définis — ce dernier point conditionnait CORS / `SameSite` / CSRF de la décision n° 15.

**Décision retenue — trois orientations :**

1. **Versions de stack.** Java **25** LTS · Spring Boot **4.1.x** · Flowable **8.0.x** · `flying-saucer-pdf` **10.4.x** (remplace `flying-saucer-pdf-openpdf`, abandonné) · jsoup **1.22.x** · Keycloak **26.7.x**. Points de vigilance actés : Jackson 3 est le défaut de Flowable 8 (compatibilité Jackson 2 encore disponible via `flowable.variable-json-mapper=jackson2` si besoin) ; l'ordre d'initialisation Liquibase / Flowable sous Boot 4 doit être vérifié dès le premier démarrage d'intégration.

2. **Build mono-module Maven.** Un seul module applicatif. La règle « domaine pur » (décision n° 17) est déjà garantie par ArchUnit en CI — un module `domain` séparé n'apporterait qu'une redondance au niveau du build, au prix d'une découpe figée trop tôt.

3. **Frontend embarqué.** Le build Vue 3 est servi depuis `src/main/resources/static/` de chaque instance Spring Boot dédiée (décision n° 13). Même origine API / SPA → confirme `SameSite=Lax` sans CORS de la décision n° 15. Chaque instance embarque le même artefact front (N copies identiques) — acceptable au regard de la simplicité opérationnelle.

> **Mise à jour** — deux angles morts de l'analyse initiale du frontend ont été comblés lors d'une
> revue technique ultérieure : (1) absence de typage statique, corrigée par l'adoption de
> **TypeScript** (`vue-tsc --noEmit` en CI, gabarit `vue-ts`) — d'autant plus pertinent que le point
> ouvert n° 24 (contrat OpenAPI vis-à-vis du frontend) ouvre la voie à un client généré typé ; (2)
> absence d'outillage qualité, corrigée par l'ajout de **Vitest** (unitaire/composants), **Playwright**
> (e2e) et **ESLint/Prettier** (`@vue/eslint-config-typescript`) comme gate CI, sur le même principe que
> `./mvnw verify` côté backend. Répercuté dans [11-frontend.md](../specs/11-frontend.md) et
> [README.md](../../README.md).

**Documenté dans :** [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [00-index.md](../specs/00-index.md), [05-workflow-engine.md](../specs/05-workflow-engine.md), [07-generation-documents.md](../specs/07-generation-documents.md), [03-authentification.md](../specs/03-authentification.md) (`SameSite` confirmé), [11-frontend.md](../specs/11-frontend.md) (packaging) et [README.md](../../README.md).
