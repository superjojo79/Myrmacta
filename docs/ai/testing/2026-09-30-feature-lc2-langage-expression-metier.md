---
phase: testing
title: Testing — LC2 langage d'expression métier borné
description: Scénarios et résultats de vérification
status: verified
created: 2026-09-30
updated: 2026-09-30
feature: lc2-langage-expression-metier
lot: LC2
---

# Testing — `lc2-langage-expression-metier`

## Scénarios (dérivés des critères de succès)

| Critère | Tests | Niveau |
|---|---|---|
| S1 condition → passerelle | `BusinessExpressionSmokeIT` (vrai moteur Flowable : résultat routé par `${highValue == true}`) ; `JsonataExpressionEngineTest` (conditions, calculs, mapping, lambdas) | unitaire + moteur réel |
| S2 refus à la validation | `JsonataExpressionEngineTest` (regex, fonctions hors liste blanche — comparée au catalogue réel de la bibliothèque —, `__*`, `${`, contrôle, arbre, longueur) ; `FlowableBpmnParserAdapterBlockTest` (au déploiement) ; `BlockAttributeRulesTest` | unitaire |
| S3 refus à l'exécution | délai, profondeur, mémoire, taille d'entrée/sortie, `$pad`, `$replace`, collections, **poids déplié**, **travail cumulé** (les expressions exactes des sondes adversariales) | unitaire |
| S4 sensibles / variable ≠ formule | `BusinessExpressionContextTest`, `BusinessExpressionDelegateTest`, `BusinessExpressionSmokeIT` | unitaire + moteur réel |
| S5 indéfini / null | `JsonataExpressionEngineTest`, `BusinessExpressionSmokeIT`, `BusinessExpressionDelegateTest` | unitaire |
| S6 API et MCP | `ExpressionApiTest` (chaîne de sécurité réelle : rôles, 401/403, CSRF, 400), `McpEndpointApiTest` (appels réels via le transport M2M), `ExpressionUseCasesTest` (gardes) | HTTP réel |
| Architecture | `ArchitectureRulesTest` : bibliothèque confinée à `infrastructure.expression`, jamais d'UEL ni de Flowable dans le moteur | ArchUnit |
| Contrat | `OpenApiContractIT` (golden **additif** : 60 insertions, 0 suppression ; les `operationId` existants inchangés) | contrat |
| Documentation | `theExamplesOfTheDesignGuideRunAsWritten` (les exemples du guide §3sexies s'exécutent tels qu'écrits) | unitaire |

Tests de régression **issus de la revue adversariale** (expressions reproduites à l'identique) : callbacks masqués par `__*` (boucle 12,8 s / OOM),
`$replace` à 4 arguments (ReDoS 13,5 s), `[1..9999999]`, `$shuffle`, fuite de contexte dans les messages, type de dossier introuvable, nom de
variable réservé à l'exécution, aplatissement `[1..9999].$l` (OOM), graphe partagé 2²⁸ (5,3 s de CPU), index derrière un prédicat, champ stocké non déclaré.
Trouvailles par les tests eux-mêmes : attribut natif `resultVariable`, grammaire de condition à opérateur obligatoire, `$pad` natif quadratique,
remplacement silencieux d'une édition après renommage (détecté par `BusinessExpressionDelegateTest`).
Vérification indépendante sous `-Xmx256m` : les expressions de la passe 2 sont refusées en 1 à 130 ms.

## Résultats (HEAD du lot, 2026-09-30)

| Commande | Résultat |
|---|---|
| `./mvnw verify -Dmaven.compiler.release=21` (frontend construit) | **BUILD SUCCESS**, 2734 tests, 0 échec, 0 erreur, 3 ignorés (préexistants) |
| `cd frontend && npm run test:all` | Vitest **499/499** ; Playwright **106/106** |
| `npx vue-tsc -b` ; `npx eslint .` | sans erreur |

Environnement : JDK 21 (`-Dmaven.compiler.release=21`), Docker absent (ITs Testcontainers non rejouées). Aucun fichier `frontend/**` modifié par ce lot.
