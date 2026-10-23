---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `introduction-dmn`

> Origine : question utilisateur (2026-09-10/11). Analyse préalable :
> [25-introduction-dmn.md](../../specs/25-introduction-dmn.md), TODO §61. 6 décisions
> actées via `AskUserQuestion` le 2026-09-11 (voir « Constraints & Assumptions » et « Questions &
> Open Items »).

## Problem Statement

DMN est explicitement exclu du V1 depuis l'origine (`application.yml`, auto-config Flowable DMN
désactivée) et le `businessRuleTask` BPMN est en plus explicitement rejeté au parsing depuis la
contre-revue adversariale post-merge PR #113 (TODO §58, finding H1-bis). Le seul mécanisme de
décision disponible aujourd'hui dans FlowForge est le **gateway exclusif BPMN** avec
`conditionExpression` — chaque règle de décision, dès qu'elle dépasse quelques conditions, se
traduit par une cascade de gateways imbriqués **dans le diagramme de process lui-même**, non
réutilisable entre process et illisible pour un profil métier non technique.

**Besoin confirmé (2026-09-11, utilisateur) :** gérer des cas/règles métier (barèmes, seuils,
grilles d'éligibilité) comme des artefacts séparés du flux, éditables et versionnés
indépendamment du diagramme BPMN — pas une introduction technologique pour compléter la
couverture du standard Flowable.

**Qui est affecté :** les concepteurs de workflow (`WORKFLOW_ADMIN`/`TENANT_ADMIN`) qui doivent
aujourd'hui traduire toute règle de décision en gateways BPMN, et les agents M2M (canal MCP) qui ne
peuvent aujourd'hui définir un flux métier complet qu'avec cette même limite.

## Goals & Objectives

### Primaire

1. **Activer le moteur DMN Flowable** — retirer `DmnEngineAutoConfiguration`/
   `DmnEngineServicesAutoConfiguration` de `spring.autoconfigure.exclude`.
2. **Durcir les expressions FEEL avant de lever le rejet `businessRuleTask`** dans
   `FlowableBpmnParserAdapter` — allowlist stricte (même discipline que
   `assertSafeConditionExpression`), **dans ce même cycle** (D-DMN-SECURITY-OWNER = « même cycle »,
   pas de fenêtre où le moteur est activé sans garde-fou). Preuve par exécution réelle contre le
   moteur, pas seulement par lecture statique de l'AST — leçon directe de H1-bis (une première
   restriction « littéral simple » s'était révélée contournable à l'exécution).
3. **Livrer un éditeur DMN intégré** dès ce cycle (D-DMN-DEPLOY = éditeur `dmn-js` immédiatement,
   pas de palier « upload seul » intermédiaire comme cela avait été fait pour BPMN) — miroir de
   `/admin/bpmn-editor` (`edition-bpmn`, PR #71, TODO §39) : `dmn-js` Modeler, toolkit sœur de
   `bpmn-js` (même éditeur bpmn.io, licence identique vérifiée).
4. **Prouver le mécanisme de bout en bout sur un scénario illustratif** — une table de décision
   « grille d'éligibilité » (D-DMN-SCOPE, voir « User Stories »), pas un cas métier réel spécifique
   encore identifié chez l'utilisateur.
5. **Exposer le mécanisme au canal MCP dès ce cycle** (D-DMN-MCP = « même cycle ») — un agent M2M
   doit pouvoir définir/consulter une table de décision sans sortir du canal MCP, cohérent avec la
   doctrine déjà actée pour les connecteurs et le BPMN (TODO §56/§60).

### Secondaire

- Documentation à jour : `README.md`, `docs/specs/05-workflow-engine.md`,
  `docs/specs/00-index.md`, `FILE-ATTENTE.md` (retrait définitif de la mention « hors V1 »),
  `GUIDE-CONCEPTION-BPMN.md` (nouvelle section « quand utiliser une table DMN vs un gateway »).

### Non-goals

- **Pas de Flowable Modeler ni de Cockpit** (webapps séparées) — cohérent avec la décision déjà
  actée (`DEFERRED-FEATURES.md` §1.5, [14-edition-bpmn.md](../../specs/14-edition-bpmn.md)
  §Options, option (d) écartée). `dmn-js` est une librairie front embarquée dans le SPA FlowForge,
  pas une application à opérer.
- **Pas de cas métier réel spécifique dans ce cycle** — le scénario « grille d'éligibilité » est
  illustratif (D-DMN-SCOPE), réutilisable ensuite pour un vrai cas une fois le mécanisme prouvé.
- **Pas de simulation/exécution de table depuis l'éditeur** (cohérent avec le non-goal équivalent de
  `edition-bpmn`).
- **Pas de collaboration temps réel multi-utilisateur** ni d'historique de versions de brouillon au
  delà d'un slot unique — hypothèse par défaut, alignée sur `D-EDIT-DRAFT-MODEL`/`D-EDIT-UNSAVED`
  déjà tranchés pour BPMN ; à confirmer/contredire explicitement en Phase 3 (design) plutôt que
  supposé silencieusement s'il y a une raison de diverger.
- **Hit policies au-delà de `UNIQUE`/`FIRST`/`COLLECT`** (ex. `PRIORITY`, `RULE ORDER`,
  `OUTPUT ORDER`) restent hors périmètre de ce cycle (D-DMN-HITPOLICY).

## User Stories & Use Cases

Scénario retenu (D-DMN-SCOPE = cas illustratif générique, « grille d'éligibilité ») :

- **En tant que** `WORKFLOW_ADMIN`, **je veux** construire une table de décision d'éligibilité
  (plusieurs critères — ex. type de demandeur, montant, statut — combinés, hit policy `FIRST` ou
  `COLLECT` selon la règle) dans un éditeur intégré à FlowForge, **afin de** produire
  `ELIGIBLE`/`NON_ELIGIBLE` + un ou plusieurs motifs, sans écrire de cascade de gateways BPMN.
- **En tant que** concepteur de process, **je veux** référencer cette table depuis un
  `businessRuleTask` (`decisionRef`) dans un diagramme déployé via `/admin/bpmn-editor` ou
  `/admin/workflow-types`, **afin que** le résultat de la décision alimente une ou plusieurs
  variables de process consommées plus loin (ex. par un gateway exclusif existant).
- **En tant qu'**agent M2M (canal MCP), **je veux** définir/consulter une table de décision sans
  sortir du canal MCP, **afin de** pouvoir assembler un flux métier complet (type de dossier +
  connecteurs + BPMN + DMN) par API, cohérent avec `deploy_workflow`/`define_connector_instance`.

**Cas limites à couvrir (Phase 2/3) :**

- `decisionRef` d'un `businessRuleTask` sans table DMN connue → rejet strict au déploiement (miroir
  `UnknownWorkflowKeyException` → 404 pour les process).
- Table DMN dont une expression FEEL sort de l'allowlist → rejet strict, message d'erreur explicite
  (miroir `InvalidBpmnException`).
- Modification d'une table déjà déployée et référencée par un process en cours d'exécution — même
  question déjà tranchée côté BPMN (versionnement Flowable au déploiement, pas de blocage applicatif
  supplémentaire) : à confirmer que le comportement DMN de Flowable est symétrique.
- Résultat de la table de décision contenant une valeur qui devrait être masquée avant d'atteindre
  un canal externe (webhook/notification) — voir « Constraints ».

## Success Criteria

- `businessRuleTask` accepté au parsing **uniquement** quand `decisionRef` référence une table
  connue et que ses expressions FEEL respectent l'allowlist — toute autre forme reste rejetée.
- Test de régression direct de la classe de faille H1-bis côté DMN : une expression FEEL construite
  pour ressembler à une référence de variable ne doit **pas** pouvoir résoudre un bean Spring —
  prouvé par exécution réelle contre le vrai moteur Flowable DMN, pas seulement par lecture
  statique (même exigence que `FlowableBpmnAllowlistGapTest` côté BPMN).
- Non-régression : les 7 process BPMN livrés avec le dépôt continuent de se déployer sans
  changement de comportement (miroir `FlowableBpmnParserAdapterShippedProcessesTest`).
- Table de décision « grille d'éligibilité » démontrée de bout en bout : édition dans l'éditeur
  intégré → déploiement → invocation par un `businessRuleTask` réel → résultat lisible en variable
  de process (test d'intégration réel, miroir `UserQuestionFormSmokeIT`/`FormKeySmokeIT`).
- MCP : au moins un tool de déploiement/lecture de table de décision, RBAC aligné sur
  `deploy_workflow` (garde domaine, pas de raccourci `API_SERVICE` = admin de facto sans le
  durcissement déjà acté ailleurs, TODO §56/§60).
- `./mvnw verify` vert (ArchUnit compris — nouveaux ports/adaptateurs DMN respectent la même
  frontière domaine/infrastructure que BPMN) ; Vitest + Playwright verts sur le nouvel écran éditeur
  (au moins un scénario E2E réel, pas seulement mocké — leçon du bug latent trouvé en E2E réel sur
  `edition-bpmn`, TODO §39).
- Documentation à jour (voir Goals secondaires) avant clôture — pas de dérive documentaire comme
  celle corrigée par TODO §45.

## Constraints & Assumptions

- **Sécurité FEEL = risque dominant, pas un détail d'implémentation** (voir analyse §4.1). Le
  périmètre exact de l'allowlist (quels types/opérateurs autoriser dans les entrées/sorties de la
  table) se dimensionne à partir du scénario réel retenu (grille d'éligibilité), pas d'une
  grammaire FEEL générique — même méthode que `assertSafeConditionExpression`, construite à partir
  de l'usage réel du dépôt.
- **Licence `dmn-js` vérifiée** : MIT + clause de filigrane bpmn.io obligatoire (même licence que
  `bpmn-js`, déjà acceptée implicitement en production pour l'éditeur BPMN). Contrainte héritée,
  pas nouvelle — pas de masquage du filigrane à prévoir.
- **Architecture hexagonale inchangée** : domaine pur (pas de dépendance Flowable/Spring dans
  `com.flowforge.domain`), nouveaux ports `port.in`/`port.out` pour le parsing/déploiement DMN sur
  le même modèle que `BpmnParserPort`/`WorkflowDeploymentPort`, ArchUnit doit rester vert.
- **Multi-tenant inchangé** : une instance = un tenant (décision n° 13), les tables `ACT_DMN_*`
  suivent le même schéma PostgreSQL par tenant que `ACT_RU_*`/`ACT_HI_*`, pas de changelog
  Liquibase applicatif nécessaire (le moteur gère son propre schéma).
- **Résultat de décision = variable(s) de process** : par cohérence avec `dossierFields` (PR #86)
  et les réponses `user-question` (TODO §55), toute valeur potentiellement sensible produite par une
  table de décision doit transiter par le même mécanisme de masquage
  (`SendNotificationUseCaseImpl`) avant toute écriture outbox — contrainte actée, pas une question
  ouverte (D-DMN-RESULT de l'analyse initiale est traité ici comme une exigence technique confirmée,
  pas une décision produit à retrancher).
- **Assumption à confirmer en Phase 3** : le mécanisme de brouillon (persistance, cycle de vie)
  reprend par défaut les choix déjà actés pour BPMN (`D-EDIT-SAVE`, `D-EDIT-DRAFT-MODEL`,
  `D-EDIT-UNSAVED` — brouillon persisté côté backend, un slot par clé, pas d'autosave) sauf raison
  technique de diverger identifiée en design (ex. structure DMN différente de BPMN sur ce point).

## Questions & Open Items

Décisions déjà actées via `AskUserQuestion` (2026-09-11) — **ne pas rouvrir sans raison
explicite** :

| ID | Décision |
|---|---|
| D-DMN-SCOPE | Cas illustratif générique — grille d'éligibilité (pas de cas métier réel encore identifié) |
| D-DMN-SECURITY-OWNER | Durcissement FEEL dans le même cycle que l'activation du moteur |
| D-DMN-DEPLOY / D-DMN-EDITOR-SCOPE (périmètre séquencement) | Éditeur intégré `dmn-js` dès ce cycle, pas de palier upload-seul intermédiaire |
| D-DMN-MCP | Tool(s) MCP dans le même cycle |
| D-DMN-HITPOLICY | `UNIQUE`/`FIRST` + `COLLECT` dès ce cycle |

Restent à trancher en Phase 3 (design) — questions **techniques**, pas des décisions produit :

1. **D-DMN-EDITOR-SCOPE (détail technique)** — `dmn-js` Modeler seul (DRD + table standard DMN
   1.3) suffit-il, ou le DMN de Flowable 8 a-t-il des attributs `flowable:*` propres nécessitant un
   provider custom (miroir `flowablePropertiesProvider.ts`) ? Non confirmé dans l'analyse initiale
   — à vérifier contre la doc/le code Flowable réel en Phase 3, pas en Phase 1.
2. **Noms de tools MCP** — `deploy_decision_table` a été utilisé comme nom provisoire dans
   l'analyse ; à confirmer/aligner sur la convention `list_*`/`get_*`/`define_*`/`deploy_*` déjà
   établie (cf. [15-serveur-mcp.md](../../specs/15-serveur-mcp.md)).
3. **Clé de brouillon éditeur** — composite `(tenant_id, decisionKey)`, miroir
   `BpmnDraftJpaEntity`/`@IdClass` — à confirmer en design comme pour BPMN plutôt que supposé.
4. **Grammaire FEEL exacte de l'allowlist** — dépend des types de données (numérique, chaîne,
   booléen, intervalle) et opérateurs réellement nécessaires à la grille d'éligibilité retenue —
   à établir en design à partir du scénario concret, pas en Phase 1.

**Aucune question bloquante restante pour clore la Phase 1** — toutes les décisions produit
matérielles ont été tranchées par l'utilisateur ; les 4 points ci-dessus sont des détails
techniques d'implémentation propres à la Phase 3.
