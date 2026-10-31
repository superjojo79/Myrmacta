# Orchestration technique — document de travail

Issue : #260 (PATCH reporté : #261) · Spec du domaine : docs/specs/06-connecteurs.md, docs/specs/18-connecteur-http-rest-service.md ·
Points du registre : [§112](../decisions/112-orchestration-technique-requete-construite-par-le-bpmn.md)

## Cadrage

- **Problème et utilisateurs.** Un intégrateur doit coordonner plusieurs applications REST (SIRH, IAM, ITSM…) depuis un
  workflow, avec des validations humaines (formulaires de tâche) qui orientent la suite. Aujourd'hui le BPMN ne peut pas
  construire une requête : `connectorInput` vient du démarrage ou d'une complétion de tâche (donc forgeable).
- **Périmètre.**
  - attribut littéral `flowable:connectorRequest` (JSONata borné) sur « Appeler un connecteur » ;
  - `http-rest` : `baseUrl` par instance (chemin relatif, paramètres de chemin et de requête encodés), instance
    « service seul » (`webhookEnabled = false`), méthode `DELETE` ;
  - exemple `exemples/onboarding-collaborateur`, rejoué par un test d'intégration.
- **Hors périmètre.**
  - `PATCH` : #261 ;
  - lecture des champs sensibles par l'expression (règle LC2 inchangée) ;
  - stockage nommé de plusieurs résultats : une réponse se recopie dans le dossier (`fieldMappingTask`) ou dans une
    variable (`businessExpressionTask`).
- **Critères de succès.**
  1. Un BPMN déployé construit méthode, chemin, requête et corps à partir du dossier et du dernier résultat, sans
     variable saisie par un utilisateur.
  2. Une instance avec `baseUrl` refuse toute URL absolue et tout chemin qui sortirait de l'hôte.
  3. Une instance « service seul » s'enregistre sans webhook, et son webhook entrant répond 404.
  4. `DELETE` est accepté, sans corps.
  5. L'exemple onboarding est rejoué de bout en bout par un IT : validation du manager (correction, refus),
     incident technique (réessayer), validation IT qui ajoute ou non un appel, compensation par `DELETE`.
- **Contraintes.** Stack figée, aucune dépendance nouvelle, aucune migration ; protection SSRF D-44 inchangée ; expression
  validée au déploiement ; MCP : le catalogue des briques expose le nouveau paramètre (`list_bpmn_building_blocks`) et
  `list_connector_definitions` les nouveaux champs, sans nouvel outil.
- **Validé par l'utilisateur le** 2026-10-31 (scénario onboarding, attribut JSONata, baseUrl + service seul + DELETE,
  PATCH reporté).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Scénario d'exemple | onboarding, mise en production, fournisseur D365 | onboarding | tranchée → §112 |
| Construction de la requête | attribut JSONata, gabarits simples, statu quo | JSONata | tranchée → §112 |
| Extensions `http-rest` | baseUrl, service seul, DELETE, PATCH | les quatre | tranchée → §112, PATCH → #261 |

## Conception

- **Faits vérifiés.**
  - `ConnectorServiceTask` lit `connectorInput` (variable) ;
  - `DeployedActivityParameters.getLiteral` existe pour les paramètres « code » ;
  - `BusinessExpressionContext.forExecution` fournit la racine `dossier` / `connectorResult` (+ `json`) / `variables` ;
  - `FlowableBpmnParserAdapter.assertBusinessExpression` sert de modèle à la validation au déploiement ;
  - `HttpURLConnection` accepte `DELETE` mais pas `PATCH` ;
  - `WebhookController` charge l'instance avant d'authentifier.
- **Composants touchés.**
  - `HttpRestConnector` ;
  - `ConnectorInstanceData` (`acceptsWebhooks`) ;
  - `WebhookController` ;
  - `ConnectorServiceTask` ;
  - `BuildingBlockCatalog` (v6), `BlockParameter.MULTILINE_NAMES` ;
  - `FlowableBpmnParserAdapter` ;
  - exemple et IT.
- **Scénarios de test.**
  - unitaires `HttpRestConnectorTest` (baseUrl, chemin, encodage, refus, DELETE, service seul) ;
  - délégué `ConnectorServiceTask` (requête évaluée, littéral prioritaire, objet exigé) ;
  - parseur (expression invalide refusée) ;
  - `WebhookController` (404 si service seul) ;
  - IT de l'exemple par la chaîne HTTP et la sécurité (MockMvc + OIDC) contre un faux serveur REST local.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| A | `http-rest` : baseUrl, chemin, requête, service seul, DELETE | en cours | — |
| B | attribut `connectorRequest` | à faire | — |
| C | exemple onboarding + IT + documentation | à faire | — |
| D | revue indépendante, clôture | à faire | — |

Prochaine action : lot A en TDD.

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
