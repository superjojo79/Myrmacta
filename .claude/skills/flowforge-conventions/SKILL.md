---
name: flowforge-conventions
description: FlowForge · Conventions projet (architecture hexagonale, multi-tenant, stack, commandes). À utiliser pour toute implémentation, revue ou planification dans ce dépôt.
---

# FlowForge — conventions projet

Appliquer ces règles **avant** les skills génériques Java/Spring/Vue. En cas de conflit, la doc `analysis doc/` et `TODO.md` priment.

## Stack figée

| Couche | Choix |
|--------|--------|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8 Community (BPMN 2.0) |
| Persistence | PostgreSQL + JPA + Liquibase · schéma **par tenant** |
| PDF | Flying Saucer + OpenPDF (+ jsoup) — pas iText |
| Auth | OAuth2/OIDC BFF (Keycloak par défaut) — pas de JWT dans le browser |
| Frontend | Vue 3 + TypeScript + Vite + PrimeVue + Pinia, embarqué dans `src/main/resources/static/` |
| Tests FE | Vitest + Playwright · ESLint/Prettier |
| Stockage fichiers | Port abstrait S3 / filesystem (pas MinIO couplé en dur) |
| Package root | `com.flowforge` |

## Architecture (non négociable)

Packages :

```
com.flowforge
├── domain          # pur Java — zéro Spring/JPA/Flowable
├── application     # orchestration transversale légère (hors ports)
└── infrastructure  # adaptateurs (web, persistence, flowable, pdf, …)
```

Règles :

1. **Dépendance unidirectionnelle** : infrastructure → domain. Jamais l’inverse.
2. **Domaine pur** : pas d’annotation Spring/JPA/Flowable dans `com.flowforge.domain`.
3. **Entités JPA ≠ modèles domaine** : `*JpaEntity` + adaptateur (`*JpaAdapter`) qui mappe (décision n° 17). Les modèles domaine sont des `record` / types immuables.
4. **Ports** : `domain.<bc>.port.in` (use cases) et `port.out` (repos, gateways). Implémentations dans `infrastructure`.
5. **ArchUnit en CI** : toute violation de frontière fait échouer `./mvnw verify`.
6. **Use cases** : classes domaine `*UseCase` / `*UseCaseImpl`, câblées par constructeur — pas de `@Service` dans le domaine.

Référence : `analysis doc/01-architecture-hexagonale.md`.

## Multi-tenant (décisions n° 13–14)

- **Une instance Spring Boot = un tenant.** `app.tenant.id` est une propriété de démarrage, jamais résolue dynamiquement par requête.
- Isolation PostgreSQL par **schéma** dédié ; pas de `ThreadLocal` / `ScopedValue` pour le tenant.
- Routage reverse-proxy par chemin (config statique) vers l’instance.
- Les appels non authentifiés (webhooks, triggers) restent scopés à l’instance / schéma courant.

Référence : `analysis doc/02-multi-tenant.md`.

## Sécurité & conformité

- Pattern **BFF** : session serveur ; le SPA n’expose pas de token access.
- Rôles V1 : `TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`, `API_SERVICE`.
- Toujours se méfier des **SSRF** (webhooks sortants, rendu PDF avec URLs externes) — vérifier résolution DNS + blocage des plages privées / `file:`.
- Secrets connecteurs chiffrés ; pas de secrets en clair dans les logs ni les templates PDF.

## Frontend

- SPA **même origine** servie depuis `static/` (décision n° 27) — pas de CORS applicatif en prod.
- TypeScript obligatoire pour le nouveau code FE.
- State : Pinia. UI : PrimeVue. Viewer BPMN : `bpmn-js`.
- Respecter l’UI PrimeVue / patterns existants du dépôt.

Référence : `analysis doc/11-frontend.md`.

## Workflow de feature (AI DevKit)

1. Lire `FILE-ATTENTE.md` (prochaine feature) + `DEFERRED-FEATURES.md` + docs `docs/ai/**` de la feature.
2. Suivre `dev-lifecycle` (requirements → design → planning → implementation → testing → review → PR).
3. Implémentation en **TDD** (`tdd` + `verify`) : test qui échoue → code → `./mvnw verify` (ou scope de tests ciblé puis verify avant claim).
4. Pour le FE : **Vitest et Playwright sont tous les deux obligatoires** dès qu’une
   feature touche le SPA ou un contrat API consommé par le SPA.
   - `cd frontend && npm run test` (unitaires)
   - `cd frontend && npm run test:e2e` (ou `npm run test:all`)
   - Mettre à jour les mocks/assertions `frontend/e2e/**` quand les shapes API changent.
   - **Interdit** de déclarer Playwright « hors scope » pour contourner Phase 8/9.
5. Points ouverts : `TODO.md` / `REVUE-PRE-IMPLEMENTATION.md` — ne pas « trancher » silencieusement une décision ouverte.
6. **Doc admin/utilisateur à jour avant de clore une feature** — si la feature ajoute/modifie un
   écran `/admin/**` ou change une variable lue par le backend au démarrage :
   - nouvel écran ou champ visible en admin → section correspondante de `GUIDE-UTILISATEUR.md`
     (et tableau de rôles §0 si le rôle requis ou la route sont nouveaux) ;
   - nouvelle variable d'environnement consommée par l'app (`${MA_VAR}` dans `application.yml`)
     → `.env.example` **et** `GETTING-STARTED.md` ;
   - lister ces fichiers dans la section « Impacte » du point `TODO.md` de la feature, comme fait
     pour le point 20 (workflow-engine) — les points 21/22/29 (rétention, purge, observabilité) ne
     l'ont pas fait et ont laissé `GUIDE-UTILISATEUR.md` en retard sur l'UI réelle, ne pas répéter
     cet oubli.
7. **`CHANGELOG.md` à jour avant d'ouvrir ou de mettre à jour une pull request** — toute PR qui
   touche du code, de la configuration ou de la documentation utilisateur/admin ajoute (ou met à
   jour si la PR est déjà ouverte) **une entrée** dans la section `[Non publié]` :
   - format court : `- **PR #<numéro>** — résumé en une phrase, détail optionnel en une seconde` ;
   - catégorie parmi Ajouté / Modifié / Corrigé / Sécurité / Documentation / Déprécié / Supprimé ;
   - **synthétique** — 2 à 5 lignes maximum, lisible sans contexte projet ; le détail technique
     (décisions, alternatives écartées, bugs trouvés) reste dans `TODO.md`/`FILE-ATTENTE.md`, pas
     dans `CHANGELOG.md` ;
   - une entrée par PR, pas par commit — regrouper les commits successifs d'une même PR sous sa
     ligne existante plutôt que d'en ajouter une nouvelle à chaque push.

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
```

> Le wrapper Maven du dépôt peut imposer `-Dmaven.compiler.release=21` selon l’environnement ; respecter le `pom.xml` / CI.

## Sources de vérité

| Besoin | Fichier |
|--------|---------|
| Vision / stack | `README.md` |
| Décisions & points ouverts | `TODO.md` |
| Revues (pré-code / post-implémentation) | `REVUE-PRE-IMPLEMENTATION.md`, `REVUE-ALIGNEMENT-SECURITE.md` |
| Prochaine feature (file d’attente) | `FILE-ATTENTE.md` |
| Backlog différé | `DEFERRED-FEATURES.md` |
| Architecture détaillée | `analysis doc/*.md` |
| Artefacts lifecycle feature | `docs/ai/{requirements,design,planning,implementation,testing,...}/` |
| Historique synthétique par PR | `CHANGELOG.md` |

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
  `GUIDE-UTILISATEUR.md` / `GETTING-STARTED.md` / `.env.example` en conséquence (voir §
  « Workflow de feature » point 6).
- Ouvrir ou merger une PR sans entrée `CHANGELOG.md` correspondante, ou y écrire un pavé
  technique au lieu d'un résumé synthétique (voir § « Workflow de feature » point 7).
