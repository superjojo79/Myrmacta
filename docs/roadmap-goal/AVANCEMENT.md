# Avancement — objectif « roadmap complète »

> Mis à jour à chaque phase ([CHARTE.md](CHARTE.md) §5). Statuts : À faire · En cours · Livré · Bloqué · Non traité.

| # | Lot | Statut | Phase | Dernier commit | Tests (verify / Vitest / Playwright) | Revue adversariale | Décisions ⚠ |
|---|---|---|---|---|---|---|---|
| 1 | LC1-suite (tables DMN dans la palette) | **Livré** (TODO §74) | 10 | `b4c26cf` | verify 2658/0 échec · Vitest 499 · Playwright 106 | 1 passe : 0 Crit./Haut, 1 Moy. + 3 Basses traitées | aucune |
| 2 | LC2 langage d'expression métier | **Livré** (TODO §75) | 10 | voir `git log` (feat(expression), fix(expression)) | verify 2734/0 échec · Vitest 499 · Playwright 106 | 3 passes : 4 Critiques + 1 Haute trouvées puis corrigées, passe 3 = 0 Crit./Haut. | ⚠ D-EXPR-LANGUAGE, -ALLOWLIST, -WEIGHT-FUEL, -BOUNDED-FUNCTIONS, -AST-BRIDGE, -ALLOC-GUARD, -UNDEFINED, -CONTEXT, -REQUEST-SIZE |
| 3 | D1 types de champs | À faire | – | – | – | – | – |
| 4 | D3a OAuth2 client credentials | À faire | – | – | – | – | – |
| 5 | D5 audit jobs planifiés | À faire | – | – | – | – | – |
| 6 | D3b mapping + D1 champs calculés | À faire | – | – | – | – | – |
| 7 | D2 recherche champs dynamiques | À faire | – | – | – | – | – |
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
