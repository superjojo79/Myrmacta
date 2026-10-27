# §107 — Tests de mutation (PIT)

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

- **Périmètre** (`pit.targetClasses`) : `be.myrmacta.domain.*` et `be.myrmacta.infrastructure.expression.*` ; tests
  du domaine et de `infrastructure.expression` (`ExpressionLimits` n'est tué que par ceux-ci), hors les deux tests
  `@SpringBootTest` du domaine. Une classe `@Configuration` n'entre pas dans le périmètre (faux survivants).
- **Seuil** (`pit.mutationThreshold`) : cliquet à 75 %. Sur ce périmètre, sous JDK 25 : 4 557 mutants, score
  77,6 %, force des tests 86 %, 15 min sur 4 cœurs ; le seuil est ce score moins 2 points, arrondi par défaut. On le
  remonte quand le score progresse, on ne le baisse pas pour faire passer une PR.
- **CI** ([`mutation.yml`](../../.github/workflows/mutation.yml)) : chaque nuit et à la demande, tout le périmètre
  avec le seuil ; sur une PR, seulement les classes du périmètre modifiées ou dont le test l'est
  (`scripts/mutation-targets.sh`), sans seuil. Rapport HTML en artefact, mutants non tués dans le résumé du job
  (`scripts/mutation-summary.py`).

**Écarté.** PIT dans `verify` (environ 15 min de plus à chaque build) ; toute l'infrastructure (au moins 3 h estimées
sur 4 cœurs, faux survivants des classes Spring) ; seuil bloquant sur les PR (une PR qui touche une classe faible
échouerait sans l'avoir dégradée) ; analyse incrémentale `withHistory` (retirée de PIT libre, réservée à Arcmutate,
commercial).

**Suites, à ouvrir en issues.** Tuer les mutants des trois manques ci-dessus ; étendre le périmètre aux adaptateurs
testés sans Spring (connecteurs, `infrastructure.security` hors `SecurityConfig`) ; StrykerJS pour le frontend.
