---
name: myrmacta-conventions
description: Myrmacta · Conventions projet (architecture hexagonale, multi-tenant, stack, commandes). À utiliser pour toute implémentation, revue ou planification dans ce dépôt. Racine de paquet be.myrmacta.
---

# Myrmacta — conventions projet

Nom commercial : Myrmacta. Racine de paquet et `groupId` : `be.myrmacta`. `artifactId` Maven : `myrmacta` (`docs/RENAMING.md`). Le dépôt GitHub n'est pas renommé.

Appliquer ces règles **avant** les skills génériques Java/Spring/Vue. En cas de conflit, la doc `docs/specs/` et le registre des décisions `docs/decisions/` priment.

## Stack figée

| Couche | Choix |
|--------|--------|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8 Community (BPMN 2.0) |
| Persistence | PostgreSQL + JPA + Liquibase · schéma **par tenant** · tests sur **PostgreSQL embarqué zonky** (pas de H2, pas de Docker ; non-root requis, sinon `MYRMACTA_TEST_PG_URL`) · `*IT` inclus dans `verify` · changeSets appliqués **jamais modifiés** (`ChangelogChecksumStabilityIT`) |
| PDF | Flying Saucer + OpenPDF (+ jsoup) — pas iText |
| Auth | OAuth2/OIDC BFF (Keycloak par défaut) — pas de JWT dans le browser |
| Frontend | Vue 3 + TypeScript + Vite + OpenVue + Pinia, embarqué dans `src/main/resources/static/` |
| Tests FE | Vitest + Playwright · ESLint/Prettier |
| Stockage fichiers | Port abstrait S3 / filesystem (pas MinIO couplé en dur) |
| Package root | `be.myrmacta` |

## Architecture (non négociable)

Packages :

```
be.myrmacta
├── domain          # pur Java — zéro Spring/JPA/Flowable
├── application     # orchestration transversale légère (hors ports)
└── infrastructure  # adaptateurs (web, persistence, flowable, pdf, …)
```

Règles :

1. **Dépendance unidirectionnelle** : infrastructure → domain. Jamais l’inverse.
2. **Domaine pur** : pas d’annotation Spring/JPA/Flowable dans `be.myrmacta.domain`.
3. **Entités JPA ≠ modèles domaine** : `*JpaEntity` + adaptateur (`*JpaAdapter`) qui mappe (décision n° 17). Les modèles domaine sont des `record` / types immuables.
4. **Ports** : `domain.<bc>.port.in` (use cases) et `port.out` (repos, gateways). Implémentations dans `infrastructure`.
5. **ArchUnit en CI** : toute violation de frontière fait échouer `./mvnw verify`.
6. **Use cases** : classes domaine `*UseCase` / `*UseCaseImpl`, câblées par constructeur — pas de `@Service` dans le domaine.

Référence : `docs/specs/01-architecture-hexagonale.md`.

## Multi-tenant (décisions n° 13–14)

- **Une instance Spring Boot = un tenant.** `app.tenant.id` est une propriété de démarrage, jamais résolue dynamiquement par requête.
- Isolation PostgreSQL par **schéma** dédié ; pas de `ThreadLocal` / `ScopedValue` pour le tenant.
- Routage reverse-proxy par chemin (config statique) vers l’instance.
- Les appels non authentifiés (webhooks, triggers) restent scopés à l’instance / schéma courant.

Référence : `docs/specs/02-multi-tenant.md`.

## Sécurité & conformité

- Pattern **BFF** : session serveur ; le SPA n’expose pas de token access.
- Rôles V1 : `TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`, `API_SERVICE`.
- Toujours se méfier des **SSRF** (webhooks sortants, rendu PDF avec URLs externes) — vérifier résolution DNS + blocage des plages privées / `file:`.
- Secrets connecteurs chiffrés ; pas de secrets en clair dans les logs ni les templates PDF.

## Frontend

- SPA **même origine** servie depuis `static/` (décision n° 27) — pas de CORS applicatif en prod.
- TypeScript obligatoire pour le nouveau code FE.
- State : Pinia. UI : OpenVue (fork MIT de PrimeVue). Viewer BPMN : `bpmn-js`.
- Respecter l’UI OpenVue / patterns existants du dépôt.

Référence : `docs/specs/11-frontend.md`.

## Workflow de feature (AI DevKit)

1. Partir de l'issue GitHub de la feature (backlog : issues ouvertes, libellés `priorite-*`, `type-*`,
   `vague-*` ; la prochaine feature est l'issue ouverte de plus haute priorité, sauf consigne contraire), de la
   spec du domaine (`docs/specs/`) et des points ouverts liés (`docs/decisions/`). Deux agents en parallèle ne
   démarrent pas deux lots qui écrivent dans les mêmes fichiers ; une issue `type-arbitrage` se tranche avant
   d'être implémentée. La PR qui livre la feature ferme son issue (`Closes #N`).
2. Suivre `dev-lifecycle` (requirements → design → planning → implementation → testing → review → PR).
3. Implémentation en **TDD** (`tdd` + `verify`) : test qui échoue → code → `./mvnw verify` (ou scope de tests ciblé puis verify avant claim).
4. Pour le FE : **Vitest et Playwright sont tous les deux obligatoires** dès qu’une
   feature touche le SPA ou un contrat API consommé par le SPA.
   - `cd frontend && npm run test` (unitaires)
   - `cd frontend && npm run test:e2e` (ou `npm run test:all`)
   - Mettre à jour les mocks/assertions `frontend/e2e/**` quand les shapes API changent.
   - **Interdit** de déclarer Playwright « hors scope » pour contourner Phase 8/9.
5. Points ouverts : registre `docs/decisions/` (statut `ouvert`, index `docs/decisions/README.md`) — ne pas « trancher »
   silencieusement une décision ouverte. Un nouveau point est un fichier `NNN-<titre>.md` au numéro suivant ; un point
   se clôt en changeant son statut, jamais en le supprimant ni en le renumérotant (« TODO §N » = `docs/decisions/NNN-*.md`),
   puis `scripts/check-docs.sh --write-decisions-index`.
6. **Doc admin/utilisateur à jour avant de clore une feature** — si la feature ajoute/modifie un
   écran `/admin/**` ou change une variable lue par le backend au démarrage :
   - nouvel écran ou champ visible en admin → section correspondante de `docs/guides/GUIDE-UTILISATEUR.md`
     (et tableau de rôles §0 si le rôle requis ou la route sont nouveaux) ;
   - nouvelle variable d'environnement consommée par l'app (`${MA_VAR}` dans `application.yml`)
     → `.env.local.example` **et** `docs/guides/GETTING-STARTED.md`, plus `.env.example` (gabarit d'un
     environnement réel) et `docs/guides/DEPLOIEMENT.md` §2 si elle compte hors dev ;
   - lister ces fichiers dans la section « Impacte » du point du registre (`docs/decisions/NNN-*.md`) de la feature, comme fait
     pour le point 20 (workflow-engine) — les points 21/22/29 (rétention, purge, observabilité) ne
     l'ont pas fait et ont laissé `docs/guides/GUIDE-UTILISATEUR.md` en retard sur l'UI réelle, ne pas répéter
     cet oubli.
7. **Titre de PR = entrée du `CHANGELOG.md`** (TODO §95) — on n'écrit plus d'entrée à la main : à chaque
   release, `scripts/changelog-release.sh <version>` génère la section depuis les PR mergées (git-cliff,
   `cliff.toml`). Le titre de la PR doit donc :
   - suivre Conventional Commits, qui choisit la catégorie (`feat:` Ajouté, `fix:` Corrigé, `fix(security):`
     Sécurité, `docs:` Documentation, autres types : Modifié) ;
   - se lire seul, en une phrase, sans contexte projet ; le détail technique (décisions, alternatives,
     bugs trouvés en revue) va dans le registre `docs/decisions/`, pas dans le titre ;
   - aperçu des PR pas encore publiées : `scripts/changelog-release.sh --preview`.
8. **Fin de cycle : vider `docs/ai/`** (TODO §95) — `docs/ai/` est la mémoire de travail de la feature
   **en cours**, pas une documentation de référence. Dans la PR qui livre la feature :
   - ce qui reste vrai du système remonte dans la spec du domaine (`docs/specs/NN-*.md`, section
     « Implémentation », quelques lignes : comportement, garde-fous, tests de référence) ;
   - chaque identifiant de décision (`D-…`, `DD-…`) cité par le code reçoit sa ligne dans
     `docs/decisions/identifiants.md`, et les décisions structurantes vont dans le registre ;
   - les fichiers `docs/ai/*/AAAA-MM-JJ-feature-<lot>.md` de la feature sont supprimés (l'historique git
     les garde) ; seuls les `README.md` de phase (modèles ai-devkit) restent ;
   - `scripts/check-docs.sh` doit être vert (liens, identifiants, skills).

## Commandes utiles

```bash
./mvnw verify -Dmaven.compiler.release=21   # suite backend + ArchUnit + couverture
./mvnw test -Dtest=NomDuTest                 # test ciblé
cd frontend && npm run test                  # Vitest
cd frontend && npm run test:e2e              # Playwright (aligné job CI frontend)
cd frontend && npm run test:all              # Vitest + Playwright
npx playwright install --with-deps chromium  # navigateurs Playwright si manquants
npx ai-devkit@latest skill list              # skills installés
./scripts/vendor-ai-devkit-skills.sh         # recopier les skills en fichiers portables
scripts/check-docs.sh                        # cohérence de la doc (job CI « docs »)
scripts/check-docs.sh --write-decisions-index  # régénérer docs/decisions/README.md
scripts/changelog-release.sh --preview       # entrées CHANGELOG des PR pas encore publiées
```

> Point d'entrée court pour tout agent : `AGENTS.md` (lu aussi par Claude Code via `CLAUDE.md`).

> Le wrapper Maven du dépôt peut imposer `-Dmaven.compiler.release=21` selon l’environnement ; respecter le `pom.xml` / CI.

## Sources de vérité

| Besoin | Fichier |
|--------|---------|
| Vision / stack | `README.md` |
| Décisions & points ouverts (« TODO §N ») | `docs/decisions/NNN-*.md`, index `docs/decisions/README.md` |
| Revues (pré-code / post-implémentation) | `docs/analyses/REVUE-PRE-IMPLEMENTATION.md`, `docs/analyses/REVUE-ALIGNEMENT-SECURITE.md` |
| Vision produit | `ROADMAP.md` (une page) |
| Backlog, prochaine feature, sujets hors V1 | issues GitHub (libellés `priorite-*`, `type-*`, `vague-*`) |
| Architecture détaillée | `docs/specs/*.md` |
| Identifiants de décision cités par le code | `docs/decisions/identifiants.md` |
| Artefacts lifecycle de la feature **en cours** (vidés à la livraison) | `docs/ai/{requirements,design,planning,implementation,testing,...}/` |
| Historique synthétique par PR | `CHANGELOG.md` (généré à chaque release, `cliff.toml`) |

## Anti-patterns à refuser

- Annoter le domaine avec Spring/JPA « pour aller plus vite ».
- Réintroduire un tenant résolu par requête / `ThreadLocal`.
- Utiliser iText ou une stack PDF AGPL.
- Servir le frontend depuis un serveur Node distinct en prod (contredit décision n° 27).
- Ajouter des dépendances framework dans `domain`.
- Claim « c’est bon » sans sortie fraîche de `./mvnw verify` / tests FE (skill `verify`).
- Claim Phase 8/9 FE sans preuve Playwright (`npm run test:e2e` / `test:all`) alors
  que le SPA ou ses mocks E2E sont impactés.
- Livrer un écran admin ou une variable d'environnement sans mettre à jour
  `docs/guides/GUIDE-UTILISATEUR.md` / `docs/guides/GETTING-STARTED.md` / `.env.local.example` en conséquence (voir §
  « Workflow de feature » point 6).
- Laisser les documents `docs/ai/` d'une feature livrée dans la branche principale, ou citer dans le code un
  identifiant de décision absent de `docs/decisions/identifiants.md` (voir § « Workflow de feature » point 8).
- Écrire une entrée `CHANGELOG.md` à la main dans une PR, ou donner à une PR un titre hors Conventional
  Commits ou illisible sans contexte : c'est lui qui devient l'entrée (voir § « Workflow de feature » point 7).
- Créer un nouveau fichier de suivi (file d'attente, backlog) : le backlog vit dans les issues GitHub.
