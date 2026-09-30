---
phase: implementation
title: Implementation — LC2 langage d'expression métier borné
description: Ce qui a été livré pour TODO §75, écarts au design, revue adversariale (deux passes)
feature: lc2-langage-expression-metier
lot: LC2
---

# Implementation — `lc2-langage-expression-metier` (TODO §75)

Documents liés : [requirements](../requirements/2026-09-30-feature-lc2-langage-expression-metier.md) ·
[design](../design/2026-09-30-feature-lc2-langage-expression-metier.md) · [planning](../planning/2026-09-30-feature-lc2-langage-expression-metier.md) ·
[testing](../testing/2026-09-30-feature-lc2-langage-expression-metier.md).

## Setup

Backend : `./mvnw verify -Dmaven.compiler.release=21` (JDK 21 dans l'environnement, le `pom.xml` vise 25). Frontend : `cd frontend && npm ci && npm run test:all`.
Nouvelle dépendance : `com.dashjoin:jsonata:0.9.10` (Apache 2.0). Nouvelles propriétés `app.expression.*` (valeurs par défaut ; aucune variable d'environnement,
aucune migration).

## Code

| Zone | Contenu |
|---|---|
| `domain/expression/model` | `ExpressionLimits` (10 bornes, `DEFAULTS`, `with…`), `ExpressionResult`, `InvalidExpressionException` (position), `ExpressionEvaluationException` |
| `domain/expression/{port,usecase}` | `ExpressionEnginePort` ; `Validate/EvaluateExpressionUseCase(Impl)` gardés `requireTenantOrWorkflowAdminOrApiService` |
| `infrastructure/expression` | `JsonataExpressionEngine` (liste blanche, noms réservés, bornes, fonctions bornées, erreurs neutres), `JsonValues` (copie JSON pure) |
| `com.dashjoin.jsonata.AstInspector` | pont vers l'AST package-private : nœuds, profondeur, regex, variables (références, affectations, paramètres, focus, index), fonctions natives, message neutre |
| `infrastructure/flowable` | `BusinessExpressionDelegate`, `BusinessExpressionContext`, `DeployedActivityParameters.getLiteral`, `FlowableBpmnParserAdapter` (syntaxe de la formule) |
| `domain/workflow/palette` | bloc `evaluate-expression` ; `BlockAttributeRules` (forme, `formulaResult` non réservé, formule littérale obligatoire) |
| `infrastructure/web/workflow`, `mcp/tool` | `ExpressionController` ; `ValidateExpressionTool`, `EvaluateExpressionTool` |
| `infrastructure/config` | `ExpressionProperties`, `ExpressionConfig` |

## Écarts au design

| # | Écart | Justification |
|---|---|---|
| 1 | Attribut `flowable:formulaResult` et non `resultVariable` | `resultVariable` est natif Flowable (absorbé par le convertisseur) — constaté par test |
| 2 | Condition de passerelle `${v == true}` et non `${v}` | la grammaire sûre exige un opérateur |
| 3 | Liste blanche de fonctions au lieu d'une liste noire | revue adversariale (constats 2, 3, 5) |
| 4 | `$pad` réécrit, `$replace` littéral, `$toMillis`/`$fromMillis` sans format | `$pad` natif quadratique ; `$replace` natif interprète le motif en regex avec une limite |
| 5 | Bornes par nœud (liste/objet/texte) ajoutées | `[1..9999999]` paresseux puis consommé en un appel natif |
| 6 | Renommage des messages d'erreur (gabarit sans valeurs), aucune cause conservée | le message brut recopie le document racine |
| 7 | Nom d'attribut `formulaResult` revalidé dans le delegate | BPMN déployé hors parseur |

## Revue adversariale (Phase 10)

### Passe 1 (sous-agent indépendant, sondes exécutées) — **2 Critiques, 1 Haute, 2 Moyennes, 3 Basses**

| # | Sév. | Constat (preuve exécutée) | Traitement |
|---|---|---|---|
| 1 | **Critique** | `$__evaluate_entry := nope` masque les callbacks de bornes : boucle de **12,8 s** (budget 250 ms), `OutOfMemoryError` en 555 ms (budget 64 Mo) ; passe validation, API, MCP, delegate | **Corrigé** : tout nom de variable `__*` refusé (références, affectations, paramètres de lambda, `@`, `#`) ; tests de régression avec les expressions exactes |
| 2 | **Critique** | `$replace("…", "^(.*a){12}$", "z", 1)` : motif interprété en regex dès qu'une limite est donnée — **13,5 s** non interruptibles ; `$replace("a","b","c",10000000)` 1,5 s ; le guide affirmait les motifs texte sûrs | **Corrigé** : liste blanche + `$replace` littéral borné ; guide corrigé |
| 3 | **Haute** | `[1..9999999]` accepté (plafond réel 1e7, la javadoc disait 1e6) puis `$string`/`$distinct`/`$sum` en un seul appel natif : `OutOfMemoryError` (3,8 s à 1 Go) | **Corrigé** : bornes par nœud (liste/objet ≤ 10 000, texte ≤ 65 536) |
| 4 | Moyenne | `OutOfMemoryError` enveloppée renvoyée en 400 ; `$split("abc", 1)` renvoie tout le document racine dans le message, recopié dans le job Flowable et les journaux | **Corrigé** : gabarit neutre, aucune cause, `Error` relancées |
| 5 | Basse | `$shuffle` non déterministe | **Corrigé** (liste blanche) |
| 6 | Basse | `RESERVED_VARIABLES` incomplet (`dossierFields`, `tenantId`, `nrOfInstances`, `loopCounter`, `execution`, `task`, `initiator`…) | **Corrigé** (réutilise `WorkflowVariables`) |
| 7 | Moyenne | Type de dossier introuvable ⇒ aucune définition ⇒ **tous les champs sensibles exposés** | **Corrigé** : échec fermé + test |
| 8 | Basse | Nom de variable de résultat non revalidé à l'exécution | **Corrigé** (et trouvé ensuite : mon premier correctif n'avait pas été appliqué — remplacement silencieux après un renommage global ; le test de régression l'a détecté) |
| 9 | Basse | Corps de requête non borné avant désérialisation | **Tracé, résiduel accepté** (D-EXPR-REQUEST-SIZE) |

Découverte en corrigeant : le `$pad` natif est **quadratique** (49 Mo pour une largeur de 10 000) — réécrit en linéaire.
Trous de preuve comblés : test de délai resserré (< 1,5 s pour 30 ms), tests du `$replace` à 4 arguments, des gros intervalles, des
messages, de la liste blanche (comparée au catalogue réel de la bibliothèque), du delegate (masquage, échec fermé, revalidation).

### Passe 2 (second sous-agent, neuf, chargé de contourner les correctifs) — **2 nouvelles Critiques**, 1 Moyenne, 4 Basses

La passe 2 confirme que les correctifs 1 à 5 de la passe 1 tiennent (liste blanche, noms `__*`, `$replace` littéral, messages
neutres, `Error` relancées) et n'a trouvé aucun contournement de la liste blanche. Mais les bornes « au nœud » étaient elles-mêmes
contournables :

| # | Sév. | Constat (preuve exécutée, `-Xmx256m`) | Traitement |
|---|---|---|---|
| C1 | **Critique** | `($l := [1..9999]; [1..9999].$l)` : `evaluateStep` aplatit 99 M références dans une boucle native ; chaque liste reste sous 10 000 mais le produit non. OOM en 2,7 s (5,5 Go alloués à 2 Go). Variantes par `$join`, `$string`, `$merge`, `$spread`, `$distinct` | **Corrigé** : budget de travail cumulé (Σ tailles des valeurs produites ≤ 3 000 000) — chaque référence est évaluée donc comptée *avant* l'étape native. Refusé en 27–130 ms |
| C2 | **Critique** | `{"a":$c,"b":$c}` imbriqué 28 fois : 28 objets, 2²⁸ chemins ; `=` et `$distinct` parcourent tout : **5,3 s de CPU sans allouer** (délai et garde mémoire aveugles), 12 s et 41 Go alloués avec une feuille `[1..9999]` | **Corrigé** : poids déplié mémoïsé par identité ≤ 1 000 000, vérifié à la sortie de chaque nœud. Refusé à la construction (1–3 ms) |
| M1 | Moyenne | Dépassements bornés du délai (~2,7 s) par `$merge`/`$keys` sur références partagées | Couvert par C1 (budget de travail) |
| B1 | Basse | `items[a>0]#$__q.a` : l'index lié derrière un prédicat n'était pas vu de l'inspecteur (non exploitable : la valeur liée est un entier, `ClassCastException`) | **Corrigé** (nœuds `index` inspectés) + test |
| B2 | Basse | Garde d'allocation silencieusement absente hors HotSpot | **Corrigé** : avertissement au démarrage ; les bornes structurelles (taille, poids, travail) bornent la mémoire indépendamment |
| B3 | Basse | `withoutSensitiveKeys` est une liste d'exclusion : un champ stocké mais plus déclaré est exposé | **Corrigé** : `declaredNonSensitiveFields` (liste blanche) + test. (Le volet « NPE sur valeur nulle » est sans objet : `Dossier` interdit déjà les valeurs nulles) |
| B4 | Basse | Diagnostics perdus (`$number("x")` → message générique) | **Tracé** : ergonomie, aucune fuite |

**Cause racine** des constats de la passe 2 : des valeurs qui *partagent* des sous-structures, consommées par un appel natif
indivisible — ni le délai coopératif ni la garde d'allocation ne voient le travail. Plutôt que de borner fonction par fonction
(jeu sans fin), deux invariants généraux sont vérifiés à chaque sortie de nœud (D-EXPR-WEIGHT-FUEL).

### Passe 3 — voir ci-dessous (complétée au retour du relecteur)
