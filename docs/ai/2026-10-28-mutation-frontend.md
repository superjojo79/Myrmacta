# Tests de mutation du frontend (StrykerJS) — document de travail

Issue : #245 · Spec du domaine : docs/specs/11-frontend.md · Points du registre : §108 (tests de mutation, à compléter)

## Cadrage

- Problème et utilisateurs : le backend a des tests de mutation PIT (§108), le frontend non. Vitest dit ce que
  les tests exécutent (couverture), pas ce qu'ils vérifient. Utilisateurs : les développeurs et agents qui écrivent
  les tests du SPA, et la revue de PR.
- Périmètre :
  - StrykerJS (`@stryker-mutator/core` et `@stryker-mutator/vitest-runner` 10.1.1, Apache-2.0, dépendances de
    développement seulement, hors jar) ;
  - fichiers mutés : `src/stores`, `src/composables`, `src/api`, `portal` (`.ts` hors tests et `portal/main.ts`) ;
  - script `npm run test:mutation`, hors `npm run test:all` ;
  - run hebdomadaire et à la demande avec seuil `break` en cliquet (fréquence demandée par l'utilisateur le
    2026-10-28, aussi pour le backend : une fois par semaine au global, et sur les PR) ;
  - job PR sur les seuls fichiers du périmètre modifiés (ou dont la spec l'est), sans seuil ;
  - rapport HTML en artefact, survivants dans le résumé du job ;
  - un utilitaire de test qui retrouve la racine du dépôt, pour les deux specs qui lisent la fixture backend.
- Hors périmètre : composants `.vue` ; `src/dossier`, `src/templateEditor`, `src/workflow` et les `.ts` de
  `src/components` (logique BPMN), trop coûteux à ce jour ; Playwright ; écrire les tests qui tueraient les
  survivants (issues séparées) ; analyse incrémentale de Stryker.
- Critères de succès (vérifiables) :
  1. `npm run test:mutation` produit un rapport HTML et JSON sur le périmètre retenu ;
  2. un run complet tient en environ 30 min sur 4 cœurs ;
  3. le seuil `break` vaut le score mesuré du premier run complet moins 2 points, arrondi par défaut, et un
     score inférieur fait échouer le run ;
  4. sur une PR, seuls les fichiers du périmètre modifiés sont mutés, sans seuil, avec résumé et rapport ;
  5. `htmlSubset.spec.ts` et `extensions.spec.ts` passent avec `npm run test` et dans le bac à sable de Stryker ;
  6. `npm ci` en npm 10.9.2 (CI et build Maven) installe le nouveau `package-lock.json` ;
  7. `npm run lint`, `npm run test:all`, `./mvnw verify` et `scripts/check-docs.sh` verts ; §108 complété.
- Contraintes : stack figée (Vite, Vitest 4, Node 22.14 en CI) ; licence permissive (décision n° 103) ; aucun
  changement d'exécution du SPA ; pas de MCP ; documentation : §108, skill `myrmacta-conventions` (commande),
  `docs/specs/11-frontend.md` (section Implémentation) à la clôture.
- Validé par l'utilisateur le : 2026-10-28

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Périmètre muté | logique ciblée ; élargie (+ dossier, templateEditor, workflow) ; tout le `.ts` | logique ciblée | tranchée (utilisateur, 2026-10-28) |
| Seuil du run complet | cliquet comme le backend ; informatif d'abord ; cible ambitieuse | cliquet | tranchée (utilisateur, 2026-10-28) |
| Mode PR | fichiers modifiés ; incrémental Stryker ; pas de job PR | fichiers modifiés | tranchée (utilisateur, 2026-10-28) |
| Fixture backend dans le bac à sable | chemin robuste ; `inPlace` ; exclure les specs | chemin robuste | tranchée (utilisateur, 2026-10-28) |
| Où consigner la décision | compléter §108 ; nouveau point | compléter §108 (l'issue le demande, sous l'ancien n° §107) | tranchée avec le cadrage (2026-10-28) |
| Où placer les jobs CI | jobs frontend dans `mutation.yml` ; workflow séparé | même workflow, même horaire | tranchée avec le cadrage (2026-10-28) |

## Conception

- Faits vérifiés (pilote du 2026-10-28, copie du frontend hors dépôt, 4 cœurs) :
  - Stryker 10.1.1 accepte Vitest ≥ 2 et Node ≥ 22 ; licence Apache-2.0 ;
  - `npm install -D` échoue en npm 10.9.2 (bug de l'arboriste, `edgesOut`) ; `--legacy-peer-deps` retire
    `@bpmn-io/properties-panel` et casse 4 specs BPMN : exclu. npm 11.6.2 installe proprement (168 paquets ajoutés,
    montées de patch de `@babel/generator` 8.0.x et `@jridgewell/sourcemap-codec`) ; `npm ci` en npm 10.9.2
    accepte ce `package-lock.json` ;
  - coût par zone (run séparé, plafond 12 min) :

    | Zone | Mutants | Durée | Score |
    |---|---|---|---|
    | `stores` | 383 | 7 min 25 | 61 % |
    | `composables` | 339 | 7 min 56 | 48 % |
    | `api` | 789 | ≥ 12 min (784 traités) | — |
    | `portal` | 175 | 1 min 07 | 54 % |
    | `dossier`, `templateEditor`, `workflow` | 1 555 | ≈ 1 h 10 estimée | — |
    | `.ts` de `components` | 1 989 | > 9 h estimées | — |

  - le coût vient du surcoût par relance de spec (jsdom, graphe de modules), pas des tests (16 s en tout) ;
  - les deux specs de `templateEditor` lisent `../src/test/resources/template-editor/editor-output.html` relatif au
    répertoire courant, cassé dans `.stryker-tmp/sandbox-*`.
- Faits vérifiés dans les sources de Stryker 10.1.1 (`@stryker-mutator/api/dist/schema/stryker-core.json`,
  `core/dist/src/config`, `stryker-cli.js`, `vitest-runner/dist/schema`) : `stryker.config.mjs` est lu par défaut ;
  `thresholds.break` vaut `null` par défaut, sinon un pourcentage ; `--mutate` (liste séparée par des virgules)
  remplace la liste de la configuration ; `/reports` et `.stryker-tmp` sont toujours exclus du bac à sable ;
  `vitest.related` vaut `true` (seules les specs liées au fichier muté tournent).
- Composants touchés :
  - `frontend/package.json` (`test:mutation`, deux dépendances de développement) et `package-lock.json`, ajoutés
    avec npm 11 ;
  - `frontend/stryker.config.mjs` : périmètre écrit une fois (`scope`), seuil `break` désactivé si
    `STRYKER_NO_BREAK` est défini (job PR) ;
  - `frontend/src/test/repoRoot.ts` (racine du dépôt = premier parent qui contient `pom.xml`) et ses deux usages ;
  - `scripts/mutation-targets-frontend.sh` (fichiers à muter depuis le diff, `--self-test` qui vérifie aussi la
    lecture du périmètre dans la configuration) ;
  - `scripts/mutation-summary.py` : lit aussi le rapport JSON de Stryker ;
  - `.github/workflows/mutation.yml` : jobs `frontend-changed-files` (PR) et `frontend-full` (hebdomadaire) ;
  - `.gitignore`, §108, skill `myrmacta-conventions`, `docs/specs/11-frontend.md` à la clôture.
- Scénarios de test :
  - `repoRoot.spec.ts` : trouve la racine depuis le frontend et depuis un sous-dossier ; erreur explicite sans
    `pom.xml` parent (critère 5) ;
  - specs `htmlSubset` et `extensions` vertes avec `npm run test` et dans le run initial de Stryker (critère 5) ;
  - `mutation-targets-frontend.sh --self-test` : fichier du périmètre modifié, spec modifiée, fichier hors
    périmètre ignoré, `.vue` ignoré, aucun changement (critère 4) ;
  - run complet : durée, score, rapports (critères 1 et 2) ; run avec un seuil au-dessus du score : échec (critère 3) ;
    run ciblé sans seuil (critère 4) ;
  - `npm ci` en npm 10.9.2 (critère 6) ; `npm run lint`, `npm run test:all`, `./mvnw verify`,
    `scripts/check-docs.sh` (critère 7). Playwright : le SPA ne change pas, `test:all` le couvre.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Tout le périmètre du cadrage (petite feature : un seul lot, une PR) | en cours | |

Prochaine action : clôture (suppression de ce document) et PR.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-10-28 | pilote Stryker par zone (copie hors dépôt) | voir tableau ci-dessus |
| 2026-10-28 | `npm ci` (npm 10.9.2) sur le lockfile produit par npm 11 | 696 paquets, Stryker et `@bpmn-io/properties-panel` présents |
| 2026-10-28 | `npm run test:mutation` (dépôt, 4 cœurs) | 1 686 mutants, score 55,16 %, force 68 %, 27 min 44 ; seuil fixé à 53 |
| 2026-10-28 | `stryker run --mutate src/api/elements.ts` (seuil 53) | score 10,53 % (identique au run complet), exit 1 |
| 2026-10-28 | idem avec `STRYKER_NO_BREAK=1 --ignoreStatic` (mode PR) | exit 0, 46 s, résumé lisible |
| 2026-10-28 | `stryker run --mutate src/api/types.ts` sans `--ignoreStatic` | 94 % ciblé contre 39 % au complet : statiques en Timeout → `--ignoreStatic` en mode PR |
| 2026-10-28 | `scripts/mutation-targets-frontend.sh --self-test`, `scripts/mutation-targets.sh --self-test` | OK |
| 2026-10-28 | `npm run lint` ; `npm run test` | OK ; 132 fichiers, 812 tests |
| 2026-10-28 | `npm run test:e2e` (Chromium 1243 au lieu de 1234, téléchargement bloqué) | 144 tests ; premier essai rouge : `vue-tsc` refusait `repoRoot.ts` (types Node) → vérifié par `tsconfig.node.json` |
| 2026-10-28 | `scripts/check-docs.sh` | OK |
| 2026-10-28 | `./mvnw verify` (JDK 25, PostgreSQL 16 local, `npm ci` en npm 10.9.2) | 3 898 tests, 0 échec, Jacoco OK |
| 2026-10-28 | après la revue : `npm run build`, `lint`, `test` (812), `test:e2e` (144), `stryker run --dryRunOnly`, self-tests, `check-docs.sh` | OK |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| Spec agrégée sans source homonyme : rien à muter sur la PR | moyenne | `spec_sources` lit ses imports ; cas ajouté au `--self-test` (vu échouer avant) |
| Noms avec accent, virgule ou glob mal transmis à `--mutate` | basse | `git -c core.quotePath=false diff -z`, refus signalé ; message si `const scope` illisible |
| PR d'outillage seul jamais exécutée | basse | `--dryRunOnly` dans le job PR |
| Job backend qui installe le JDK sur une PR frontend | basse | JDK installé seulement s'il y a des classes |
| Score arrondi à l'unité face au seuil exact | basse | une décimale, mutants hors score affichés |
| `STRYKER_NO_BREAK=0` désactivait le seuil | basse | `=== '1'` |
| Répertoire temporaire non nettoyé (`repoRoot.spec.ts`) | basse | `rmSync` dans `finally` |
| `repoRoot.ts` vérifié sans `strict` | basse | `strict: true` dans `tsconfig.node.json` |
| `NON_VIABLE`, `RUN_ERROR` de PIT comptés non tués | basse (antérieur) | ajoutés aux détectés |
