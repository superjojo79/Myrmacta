# §108 — Tests de mutation (PIT)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-10-27) : profil Maven `mutation` et workflow [`mutation.yml`](../../.github/workflows/mutation.yml).
Complète la [stratégie de test](028-strategie-de-test.md) (§28).

**Constat.** Jacoco mesure ce que les tests exécutent, pas ce qu'ils vérifient. Un pilote PIT 1.30.1 sur tout
`be.myrmacta.domain` (4 328 mutants, 4 cœurs, 18 min) donne une couverture de lignes de 90 % mais un score de mutation
de 78 % : environ 12 points de code exécuté sans être vérifié. Packages les plus faibles : `promotion` (58 %),
`parcours` (62 %), `assistant` (66 %), `signature` (70 %). Trois manques réels relevés :

- `ParcoursCompiler.validateStep` : retirer `allowOnly(...)` ne fait échouer aucun test pour 11 types d'étape (liste
  blanche des paramètres non vérifiée) ;
- `JsonataExpressionEngine` : `++depth[0]` muté en `--depth[0]` survit ; le test de profondeur maximale passe grâce
  à un autre garde-fou (travail ou délai), la borne de profondeur elle-même n'est pas prouvée ;
- `PrecheckLimits` : bornes (`< 1`, `maxCharsPerPiece > maxCharsTotal`) non testées à la limite.

Sur la couche Spring, le coût par mutant est environ six fois plus élevé (`infrastructure.security` : 198 mutants,
6 min) et les mutants des classes `@Configuration` survivent tous, y compris un `SecurityFilterChain` remplacé par
`null` : le contexte de test mis en cache n'exécute pas les méthodes `@Bean` mutées. Ce sont de faux survivants.

**Décision (utilisateur, 2026-10-27).** Tests de mutation PIT hors `./mvnw verify`, dans un profil dédié :

```bash
./mvnw -Pmutation test-compile pitest:mutationCoverage            # périmètre complet, seuil appliqué
./mvnw -Pmutation test-compile pitest:mutationCoverage \
  -Dpit.targetClasses='be.myrmacta.domain.parcours.*' -Dpit.mutationThreshold=0   # ciblé, sans seuil
```

- **Périmètre** (`pit.targetClasses`) : le domaine et les adaptateurs testés sans contexte Spring
  (`infrastructure.expression`, `connector`, `mcp`, `security`, `notification`, `document`, `pdf`, `storage`,
  `precheck`, `assistant`). Les tests sont ceux de tout le périmètre (`pit.targetTests`, même liste écrite en entier :
  une PR surcharge les classes, jamais les tests ; `ExpressionLimits` n'est tué que depuis `infrastructure.expression`),
  hors les tests à contexte Spring, base embarquée ou navigateur. Restent exclues les classes `@Configuration`
  (`SecurityConfig`, `PortalSecurityConfig` : faux survivants) et `McpDesignToolCatalog` (testée seulement sous Spring).
- **Seuil** (`pit.mutationThreshold`) : cliquet à 76 %. Sur ce périmètre, sous JDK 25 : 7 023 mutants, score
  79,0 %, force des tests 87 %, 23 min sur 4 cœurs ; le seuil est ce score moins 2 points, arrondi par défaut. On le
  remonte quand le score progresse, on ne le baisse pas pour faire passer une PR.
- **CI** ([`mutation.yml`](../../.github/workflows/mutation.yml)) : chaque semaine (dimanche, 02:23 UTC) et à la demande, tout le périmètre
  avec le seuil ; sur une PR, seulement les classes du périmètre modifiées ou dont le test l'est
  (`scripts/mutation-targets.sh`), sans seuil. Rapport HTML en artefact, mutants non tués dans le résumé du job
  (`scripts/mutation-summary.py`).

**Extension aux adaptateurs (issue #244, 2026-10-27).** Mesure par package, tests Spring exclus :

| Package | Mutants | Score | Force des tests | Durée |
|---|---|---|---|---|
| `connector` | 1 071 | 79 % | 87 % | 8 min 37 |
| `mcp` | 366 | 87 % | 91 % | 2 min 26 |
| `security` (sans `@Configuration`) | 169 | 89 % | 96 % | 1 min 11 |
| `notification` | 294 | 71 % | 84 % | 1 min 05 |
| `document`, `pdf`, `storage` | 379 | 71 % | 81 % | 1 min 26 |
| `precheck`, `assistant` (sans `McpDesignToolCatalog`) | 187 | 75 % | 82 % | 1 min 42 |
| `signature` — écarté | 99 | 61 % | 71 % | 45 s |

`signature` reste hors périmètre : 9 de ses 17 tests sont à contexte Spring, ses classes non couvertes
(`AfterCommitSignatureOutcomeListener`, tâches planifiées) ne le sont que par eux. `web`, `persistence`, `flowable` et
`config` aussi, faute de tests sans Spring.

**Écarté.** PIT dans `verify` (environ 25 min de plus à chaque build) ; l'infrastructure testée sous Spring (au moins
3 h estimées sur 4 cœurs, faux survivants des classes Spring) ; seuil bloquant sur les PR (une PR qui touche une classe
faible échouerait sans l'avoir dégradée) ; analyse incrémentale `withHistory` (retirée de PIT libre, réservée à
Arcmutate, commercial).

**Suites.** Tuer les mutants des trois manques ci-dessus : issues
[#241](https://github.com/superjojo79/FlowForge/issues/241) (bornes JSONata),
[#242](https://github.com/superjojo79/FlowForge/issues/242) (liste blanche du compilateur de parcours) et
[#243](https://github.com/superjojo79/FlowForge/issues/243) (`PrecheckLimits`). Étendre le périmètre aux adaptateurs
testés sans Spring : [#244](https://github.com/superjojo79/FlowForge/issues/244), fait. StrykerJS pour le frontend :
[#245](https://github.com/superjojo79/FlowForge/issues/245).
