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
  - run nocturne et à la demande avec seuil `break` en cliquet ;
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
- Validé par l'utilisateur le :

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Périmètre muté | logique ciblée ; élargie (+ dossier, templateEditor, workflow) ; tout le `.ts` | logique ciblée | tranchée (utilisateur, 2026-10-28) |
| Seuil nocturne | cliquet comme le backend ; informatif d'abord ; cible ambitieuse | cliquet | tranchée (utilisateur, 2026-10-28) |
| Mode PR | fichiers modifiés ; incrémental Stryker ; pas de job PR | fichiers modifiés | tranchée (utilisateur, 2026-10-28) |
| Fixture backend dans le bac à sable | chemin robuste ; `inPlace` ; exclure les specs | chemin robuste | tranchée (utilisateur, 2026-10-28) |
| Où consigner la décision | compléter §108 ; nouveau point | compléter §108 (l'issue le demande, sous l'ancien n° §107) | hypothèse |
| Où placer les jobs CI | jobs frontend dans `mutation.yml` ; workflow séparé | même workflow, même horaire | hypothèse |

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
- Composants touchés : `frontend/package.json` et `package-lock.json`, configuration Stryker, un utilitaire de
  test, deux specs, `.github/workflows/mutation.yml`, un script de calcul des fichiers modifiés et de résumé,
  `.gitignore`, §108, skill `myrmacta-conventions`.
- Scénarios de test : à dériver en conception.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| — | à découper en conception | à faire | |

Prochaine action : validation du cadrage par l'utilisateur, puis conception.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-10-28 | pilote Stryker par zone (copie hors dépôt) | voir tableau ci-dessus |
| 2026-10-28 | `npm ci` (npm 10.9.2) sur le lockfile produit par npm 11 | 696 paquets, Stryker et `@bpmn-io/properties-panel` présents |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
