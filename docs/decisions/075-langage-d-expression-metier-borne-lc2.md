# §75 — Langage d'expression métier borné (LC2) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §75 · [registre](README.md)

**Énoncé :** sous-lot **LC2** de la couche low-code (§70), analyse [40-langage-expression-metier.md](../specs/40-langage-expression-metier.md).
Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-lc2-langage-expression-metier.md), [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-30-feature-lc2-langage-expression-metier.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-30-feature-lc2-langage-expression-metier.md), [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-30-feature-lc2-langage-expression-metier.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-30-feature-lc2-langage-expression-metier.md). Journal : [DECISIONS.md](roadmap-2026/DECISIONS.md).

**Décisions (mandat « roadmap complète », 2026-09-30).** D-EXPR-LANGUAGE = JSONata (`com.dashjoin:jsonata` 0.9.10) ; D-EXPR-EVAL-SITE = port
`ExpressionEnginePort` + delegate `businessExpressionTask` ; D-EXPR-CONTEXT = dossier **sans champs sensibles** + `connectorResult` + variables scalaires + `now` ;
D-EXPR-LIMITS = 2 000 car., 250 ms, 64 Ko de résultat… ; D-EXPR-REGEX = littéraux regex refusés ; D-EXPR-FUNCTIONS = `$eval`/`$random`/`$now`/`$millis` refusés ;
D-EXPR-LITERAL-ONLY = formule lue **uniquement** dans l'attribut littéral ; D-EXPR-UNDEFINED = résultat absent ⇒ échec explicite ; D-EXPR-RESULT ; D-EXPR-ATTR-NAME =
`formulaResult` (`resultVariable` est un attribut natif Flowable) ; D-EXPR-AST-BRIDGE ; D-EXPR-ALLOC-GUARD ; D-EXPR-API.

**Livré.** Domaine `domain/expression/**` ; adaptateur `JsonataExpressionEngine` (inspection d'AST, bornes de délai/mémoire/profondeur/taille, contexte JSON pur) ;
brique « Évaluer une expression » (12ᵉ bloc, delegate allowlisté, formule validée au déploiement) ; `POST /api/expressions/{validate,evaluate}` ; outils MCP
`validate_expression`, `evaluate_expression` ; règles ArchUnit (bibliothèque confinée, jamais d'UEL). Golden OpenAPI additif. Aucune migration.
**Constats du design corrigés :** `outputConvertNulls` par défaut (null explicite ≠ absent), intervalles paresseux, regex non interruptibles, attribut natif
`resultVariable`, grammaire de condition à opérateur obligatoire. **Revue adversariale en 3 passes** (sous-agents indépendants, sondes exécutées) : passe 1 = 2 Critiques (callbacks masquables par `$__evaluate_entry := nope` ; `$replace` à 4 arguments = regex/ReDoS), 1 Haute (`[1..9999999]` + appels natifs → OOM), 2 Moyennes, 3 Basses ; passe 2 = 2 nouvelles Critiques (aplatissement de chemin sur références partagées ; graphe partagé exponentiel pour `=`/`$distinct`) corrigées à la racine par deux invariants à chaque sortie de nœud (poids déplié, travail cumulé) ; passe 3 = **0 Critique, 0 Haute**. Détail, preuves et décisions : document d'implémentation et `DECISIONS.md` (D-EXPR-ALLOWLIST, D-EXPR-WEIGHT-FUEL…).
**Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3sexies, §3quinquies, §7), `docs/guides/GUIDE-UTILISATEUR.md`, `docs/specs/40`, `ROADMAP.md`, `CHANGELOG.md`.
Nouvelles propriétés `app.expression.*` (valeurs par défaut, aucune variable d'environnement requise).
