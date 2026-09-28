# Langage d'expression métier borné (LC2)

> **Statut : première analyse (2026-09-28), aucune décision technique tranchée.** Sous-lot
> **LC2** `langage-expression-metier` de la couche low-code (D0 = C,
> [31](31-positionnement-produit.md), rang 2). Prérequis des champs calculés de D1
> ([32](32-modele-champs-enrichi.md)), du mapping D3b ([34](34-integrations-entreprise.md)) et
> de LC5. À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Conditions BPMN | JUEL, restreint à une grammaire sûre : littéraux, comparaisons, identifiants nus (pas de point, pas d'appel) | §57 H1, `FlowableBpmnParserAdapter` |
| Autres surfaces UEL | Toutes refusées sauf `delegateExpression` allowlistée et `conditionExpression` revalidée ; `language` / `scriptFormat` refusés | §69, `BpmnExpressionSurfaceScanner` |
| DMN | Cellules en JUEL (Flowable n'implémente pas FEEL), allowlist stricte ; `inputExpression` = identifiant nu | §61, `FlowableDmnParserAdapter` |
| Templates | Évaluateur allowlist Thymeleaf (`#vars`, `#dates`, `#numbers`, `#strings`, `#lists` + chemins du modèle) | §40, `SafeThymeleafExpressionSupport` |
| Champs du dossier dans une condition | `${dossierFields.x}` **interdit** (le point est refusé) | §69, `GUIDE-CONCEPTION-BPMN.md` |
| Moteurs de script | Aucun sur le classpath (459 jars vérifiés) | §69 |

**Lecture :** la sécurité a été obtenue en **retirant** de l'expressivité à JUEL. Il n'existe
aucun moyen sûr d'exprimer `montant > 5000 et pays = "FR"` sur les champs du dossier, ni un calcul,
ni un mapping de JSON.

## 2. Objectif

Un langage **non Turing-complet ou borné**, sans accès au serveur, évalué **hors JUEL**, pour :

1. conditions de passerelle et règles de visibilité / obligation ;
2. **champs calculés** (D1) ;
3. **mapping** d'une réponse de connecteur vers les champs du dossier (D3b).

## 3. Options

| Option | Licence | Forces | Limites |
|---|---|---|---|
| **JSONata** (portages Java, p. ex. dashjoin, IBM JSONata4Java) | Apache 2.0 | Conçu pour transformer du JSON ; couvre les trois usages | Bornage temps / profondeur à vérifier selon le portage |
| **CEL** (`cel-java`) | Apache 2.0 | Coût d'évaluation borné par conception, typage | Faible pour transformer du JSON (mapping) |
| FEEL (moteur séparé) | Apache 2.0 | Standard DMN | Flowable l'évalue en JUEL : deux sémantiques voisines, source de confusion |

**Recommandation (à trancher) : JSONata**, sous réserve du bornage vérifié en Phase 1.

## 4. Périmètre à poser (Phase 1)

- **Où s'évalue l'expression** : dans le domaine (port d'évaluation) ou dans un delegate dédié
  ajouté à `AllowedBpmnDelegates`. Jamais dans une `conditionExpression` libre : une condition
  LC2 produit une variable que la passerelle compare avec la grammaire sûre existante.
- **Contexte exposé** : champs du dossier (valeurs masquées si `sensitive` ?), `connectorResult`,
  variables de process allowlistées.
- **Bornes** : temps, profondeur, taille du résultat — identiques en évaluation à blanc et en
  exécution réelle.
- **Validation statique** : syntaxe et existence des champs référencés sur le type.
- **Éditeur** : saisie avec autocomplétion des champs (LC1 / Monaco).

## 5. Exposition MCP

`validate_expression` et `evaluate_expression` (contexte d'exemple fourni, sans effet de bord),
bornés comme l'exécution réelle ; les expressions s'écrivent via les tools qui les portent
(`define_dossier_type`, brique de mapping). Voir [46](46-exposition-mcp-couche-low-code.md) §4.

## 6. Risques

- Réintroduire une surface d'injection si l'expression est un jour concaténée dans du JUEL :
  règle ArchUnit ou test garantissant qu'elle ne l'est jamais.
- DoS par expression coûteuse : bornes obligatoires.
- Deux langages (JUEL restreint et LC2) à expliquer aux concepteurs.

## 7. Décisions à trancher

1. **D-EXPR-LANGUAGE** — JSONata ou CEL (portage et version).
2. **D-EXPR-EVAL-SITE** — port domaine ou delegate dédié.
3. **D-EXPR-CONTEXT** — données accessibles, traitement des champs sensibles.
4. **D-EXPR-LIMITS** — valeurs des bornes.

## 8. Documentation impactée

`GUIDE-CONCEPTION-BPMN.md` (nouvelle syntaxe), `GUIDE-UTILISATEUR.md` (champs calculés),
`05-workflow-engine.md`.
