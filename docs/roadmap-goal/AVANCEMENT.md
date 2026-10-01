# Avancement — objectif « roadmap complète »

> Mis à jour à chaque phase ([CHARTE.md](CHARTE.md) §5). Statuts : À faire · En cours · Livré · Bloqué · Non traité.

| # | Lot | Statut | Phase | Dernier commit | Tests (verify / Vitest / Playwright) | Revue adversariale | Décisions ⚠ |
|---|---|---|---|---|---|---|---|
| 1 | LC1-suite (tables DMN dans la palette) | **Livré** (TODO §74) | 10 | `b4c26cf` | verify 2658/0 échec · Vitest 499 · Playwright 106 | 1 passe : 0 Crit./Haut, 1 Moy. + 3 Basses traitées | aucune |
| 2 | LC2 langage d'expression métier | **Livré** (TODO §75) | 10 | voir `git log` (feat(expression), fix(expression)) | verify 2734/0 échec · Vitest 499 · Playwright 106 | 3 passes : 4 Critiques + 1 Haute trouvées puis corrigées, passe 3 = 0 Crit./Haut. | ⚠ D-EXPR-LANGUAGE, -ALLOWLIST, -WEIGHT-FUEL, -BOUNDED-FUNCTIONS, -AST-BRIDGE, -ALLOC-GUARD, -UNDEFINED, -CONTEXT, -REQUEST-SIZE |
| 3 | D1 types de champs | **Livré** (TODO §76) | 10 | voir `git log` (feat(champs)) | verify 2780/0 échec · Vitest 539 · Playwright 110 | 1 passe : 0 Crit./Haut., Moyens et Bas traités ; résidu M4 | ⚠ D-CHAMPS-MONEY (texte décimal), -REPEAT, -SENSITIVE, -USERQ, M4 plafond de corps HTTP |
| 4 | D3a OAuth2 client credentials | **Livré** (TODO §77) | 10 | voir `git log` (feat(connecteurs)) | verify 2825/0 échec · SPA inchangée (Vitest 539 · Playwright 110 au dernier run) | 1 passe : 0 Crit./Haut., 2 Moyens + 6 Bas traités | ⚠ D-INTEG-OAUTH-SCOPE (certificat), -HOSTS (hôtes obligatoires), -WILDCARD |
| 5 | D5 audit jobs planifiés | **Livré** (TODO §79) | 10 | voir `git log` (feat/fix(jobs)) | verify 2921/0 échec · Vitest 558 · Playwright 113 · PostgreSQL 16 réel (revue) | 2 passes : 1ʳᵉ 1 Haut + Moyens traités ; 2ᵉ 0 Crit./Haut., 1 Moyen (bail non renouvelé) + 4 Bas (3 traités, 1 accepté : outbox workflow) | ⚠ D-JOBS-TRIGGER, D-JOBS-LEASE |
| 6 | D3b mapping + D1 champs calculés / visibilité | **Livré** (TODO §78 + §80) | 10 + 10 | voir `git log` (feat(workflow), feat(champs)) | verify 2921/0 échec · Vitest 558 · Playwright 113 | D3b : 3 Moyens + 4 Bas traités ; D1 règles : 0 Crit./Haut., 3 Moyens traités + 1 Bas, 3 Bas acceptés | ⚠ D-MAP-RESERVED, ⚠ D-CHAMPS-COMPUTED (valeur fournie refusée) |
| 7 | D2 recherche champs dynamiques | **Livré** (TODO §81) | 10 | voir `git log` (feat(recherche)) | verify : voir ligne finale · PostgreSQL 16 réel (5 tests) · Vitest · Playwright E2E-SEARCH-1 | 1 passe : 0 Crit./Haut., 2 Moyens (fuite de schéma avant ACL, NUL → 409) traités + 1 Bas (dates non chaîne) ; 3 Bas acceptés | ⚠ D-RECH-STORAGE (migration jsonb : réécriture de la table) |
| 8 | D6 socle MCP low-code | À faire | – | – | – | – | – |
| 9 | D4 promotion de configuration | À faire | – | – | – | – | – |
| 10 | LC4b templates WYSIWYG, LC4a formulaires | À faire | – | – | – | – | – |
| 11 | LC3 assistant de conception IA | À faire | – | – | – | – | – |
| 12 | D3c e-mail entrant + D3d bus Azure | À faire | – | – | – | – | – |
| 13 | LC5 concepteur de parcours | À faire | – | – | – | – | – |
| 14 | LC6 spike (lot conditionnel) | À faire | – | – | – | – | – |
| 15 | D7 internationalisation UI | À faire | – | – | – | – | – |

**Non traité (hors périmètre, cf. CHARTE §2) :** fournisseur réel de signature / eIDAS, export BI JDBC,
vérifications d'environnement (ClamAV sous Docker, smoke Keycloak réel).
