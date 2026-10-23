# Outillage DMN — évaluation ad hoc, historique/rollback, jeux de tests

> **Statut : livré (2026-09-24)** — feature `outillage-dmn`, TODO §67 clos,
> [PR #134](https://github.com/superjojo79/FlowForge/pull/134) mergée (commit `97b429a`).
> Évaluation, jeux de tests JSON/CSV et redéploiement sont sur `main`. Origine :
> `ROADMAP.md` v1.1 (14/09/2026), items **1.1** (`evaluate_decision` MCP), **1.2**
> (`TestDecisionTool`) et **1.3**/**5.1** (historique/rollback), auparavant « Produit, pas cadré ».
> Regroupés en **un seul** document (pas trois) parce qu'ils touchent la **même** surface
> technique — le port `DmnDeploymentPort`/le versioning Flowable des tables de décision déjà livré
> par `introduction-dmn` (TODO §61) — pas trois fonctionnalités indépendantes.

---

## 1. Constat vérifié (pas supposé)

| Élément | État réel | Preuve |
|---|---|---|
| Moteur DMN activé | **Livré** (TODO §61) | `FlowableDmnDeploymentAdapter`, `DmnDeploymentPort` |
| Éditeur tableur + DRD | **Livré** (TODO §61) | `/admin/dmn-editor`, `dmn-js` Modeler |
| Déploiement d'une table | **Livré** | MCP `deploy_decision_table` (`DeployDecisionTableTool`) |
| Évaluation runtime | **Existe, mais uniquement dans un process en cours** | `<serviceTask flowable:type="dmn">` + `decisionTableReferenceKey` (voir `25-introduction-dmn.md` §1.2) — prouvé par `DmnJuelSecuritySmokeIT`. **Aucun** appel direct `decisionKey` + `inputs` → `outputs` hors d'un process actif. |
| Liste/lecture d'une table | **Toujours la dernière version** | `GetDecisionTableDeploymentInfoUseCaseImpl`/`DecisionTableDeploymentInfo(key, version)` — MCP `list_decision_tables`/`get_decision_table` (`ListDecisionTablesTool`/`GetDecisionTableTool`) n'exposent pas l'historique des versions antérieures |
| Historique des déploiements | **Le moteur le garde, l'app ne l'expose pas** | Flowable versionne nativement chaque déploiement (`ACT_DMN_*`) — confirmé par le mécanisme même de `deploy_decision_table` (chaque déploiement crée une nouvelle version, l'ancienne reste dans les tables du moteur) ; **aucune** UI/tool applicatif ne lit cet historique |
| Rollback | **Absent** | Aucun use case, aucun endpoint, aucun tool MCP |
| Jeu de tests (CSV/JSON d'entrées → sorties attendues) | **Absent** | Aucun use case, aucun tool |

**Conséquence directe** : un agent MCP peut aujourd'hui déployer une table, déployer/démarrer un
process qui l'invoque via `serviceTask type=dmn`, et lire le résultat via les variables de process —
mais ne peut pas évaluer une décision *ad hoc*, hors contexte de process, pour du confort d'usage
(vérifier une règle sans lancer un vrai workflow).

---

## 2. Ce que chaque sous-item impliquerait

### 2.1 `evaluate_decision` (MCP, confort agent)

- Nécessite un appel **headless** au moteur DMN (`org.flowable.dmn.api.DmnDecisionService`,
  l'équivalent DMN du `RuntimeService` déjà injecté ailleurs) — pas une invocation via process.
- **Sécurité déjà couverte à la source** : la discipline JUEL (allowlist stricte,
  `FlowableDmnParserAdapter`, TODO §61 §1.2) s'applique au **parse/déploiement** de la table, pas à
  l'évaluation — donc une table déjà déployée est déjà sûre à évaluer, quel que soit le point
  d'entrée (process ou appel direct). À reconfirmer en Phase 3 avant d'écrire le code, ne pas
  supposer.
- Nouveau use case `EvaluateDecisionUseCase` (domain pur, port vers `DmnDeploymentPort` ou nouveau
  port dédié) + adaptateur MCP `EvaluateDecisionTool`, sur le même patron que les tools déjà livrés
  (`GetDecisionTableTool`).

### 2.2 `TestDecisionTool` (jeu de tests)

- Techniquement, un jeu de tests = une **série** d'appels à l'évaluation ad hoc (2.1) avec un
  résultat attendu par ligne — dépend directement de 2.1, pas une brique indépendante.
- Format d'entrée (CSV/JSON) à définir — cohérent avec le `.dmn`/`dmn-js` existant plutôt qu'un
  format maison si l'éditeur en propose un.

### 2.3 Historique / rollback (5.1, 1.3)

- Le moteur **garde déjà tout** (`ACT_DMN_DEPLOYMENT`, `ACT_DMN_DECISION_TABLE`,
  `ACT_GE_BYTEARRAY` pour le XML brut de chaque déploiement) — pas de nouvelle table applicative à
  créer, juste une lecture plus large de ce que `RepositoryService`/l'équivalent DMN expose déjà.
- « Rollback » au sens Flowable n'est **pas** un retour en arrière in situ : c'est un **redéploiement**
  d'une version antérieure comme nouvelle version courante (le moteur ne permet pas de « supprimer »
  la dernière version pour révéler la précédente). À expliciter dans l'UI pour ne pas induire
  l'utilisateur en erreur (« rollback » ≠ suppression de version).
- Composants : `ListDecisionTableVersionsUseCase` (lit l'historique complet, pas seulement la
  dernière version comme aujourd'hui) + `RollbackDecisionTableUseCase` (récupère le XML d'une version
  antérieure via `RepositoryService`/équivalent, le redéploie) + UI dans `/admin/dmn-editor`.

---

## 3. Pourquoi un seul cycle plutôt que trois

Les trois sous-items partagent :
- le même port d'accès au moteur DMN (`DmnDeploymentPort` à étendre, ou un port frère) ;
- le même adaptateur Flowable (`FlowableDmnDeploymentAdapter`) ;
- la même classe de sécurité à vérifier une seule fois (JUEL/allowlist déjà en place au déploiement).

Découper en trois PR reviendrait à toucher le même fichier catalogue/port trois fois — même logique
que la contrainte « ne pas découper B1 en 3 PRs » de la Vague B connecteurs.

## 4. Points ouverts (Phase 1 `dev-lifecycle`) — **tranchés 2026-09-22**

| ID | Décision |
|---|---|
| D-DMN-EVAL-SURFACE | **C** — MCP + REST + bouton Tester |
| D-DMN-EVAL-VERSION | **B** — version optionnelle, défaut = courante |
| D-DMN-TEST-FORMAT | **C** — JSON **et** CSV |
| D-DMN-ROLLBACK-UX | **A** — « Redéployer la version N » + confirmation |
| D-DMN-HISTORY-RETENTION | **A** — aucune purge applicative dans ce cycle |
| D-DMN-TEST-SURFACE | **C** — MCP + REST + upload UI JSON/CSV (Phase 2, 2026-09-22) |

Détail :
[`docs/ai/requirements/2026-09-22-feature-outillage-dmn.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-22-feature-outillage-dmn.md)
(status **reviewed**, Phase 2 close).

## Implémentation — tables DMN proposées dans la palette BPMN (LC1-suite, TODO §74)

*Synthèse ajoutée lors de la remédiation (TODO §95), d'après le document de travail
[requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-lc1-suite-palette-dmn.md) ; décisions D-LC1S-* dans
[DECISIONS.md](../roadmap-goal/DECISIONS.md).*

- La brique « Décision DMN » de la palette métier (TODO §73, [39](39-palette-metier-bpmn.md)) propose la liste des
  tables déployées du tenant au lieu d'une clé saisie à la main :
  `GET /api/bpmn-building-blocks/references/dmnTables` renvoie une entrée `{value, label}` par clé (libellé
  « clé (vN) », dernière version), triée par clé, sans XML ni règles.
- Accès `TENANT_ADMIN` et `WORKFLOW_ADMIN` (403 pour `DOSSIER_MANAGER`, 401 anonyme), isolé par tenant.
- L'existence de la clé reste vérifiée au déploiement. Si la liste est indisponible, le panneau retombe en saisie
  libre et l'éditeur reste utilisable (D-PALETTE-FALLBACK).
