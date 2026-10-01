---
phase: design
title: Design — D3b (mapping réponse de connecteur → champs du dossier)
status: reviewed
created: 2026-10-01
feature: d3b-mapping-reponse-connecteur
---

# Design — D3b

Faits vérifiés dans le code : `ConnectorServiceTask` pose `connectorResult = {status, body}` (`body` texte) ; `BusinessExpressionContext` (LC2) expose `dossier` (champs déclarés non sensibles), `connectorResult`,
`variables`, `now` ; `UpdateDossierFieldsUseCase.update(command, ctx)` fusionne, valide (`DossierFieldValidator`), normalise et versionne en une écriture ; `AllowedBpmnDelegates` est une liste fermée ;
`BlockAttributeRules` refuse un attribut `flowable:*` géré hors de la brique propriétaire ; le catalogue (`BuildingBlockCatalog`) alimente palette, validation et MCP.

## Composants
- `FieldMappingSpec` (domaine, `palette`) : parse « champ = expression », un par ligne, séparateur = premier `=` (hors `==`), ≤ 20 entrées, ≤ 2000 car./expression, doublons refusés.
- Catalogue : brique `map-fields` (`BlockCategory.DOSSIER`, delegate `fieldMappingTask`, paramètre `fieldMapping` obligatoire, multiligne). `BlockParameter.multiline` (publié) remplace le test sur le nom dans le SPA.
- `BlockAttributeRules` : `fieldMapping` validé par `FieldMappingSpec` ; delegate littéral-seulement (comme `businessExpressionTask`).
- `FlowableBpmnParserAdapter.assertFieldMapping` : chaque expression passe par `ExpressionEnginePort.validate` au déploiement (message : champ + activité).
- `BusinessExpressionContext.forExecution` (partagé avec `BusinessExpressionDelegate`) : charge dossier + définitions (échec fermé), construit le contexte ; `withParsedBody` ajoute `connectorResult.json`
  (JSON valide, ≤ 256 Ko, ≤ 24 niveaux, nombres exacts : > 15 chiffres significatifs ou hors plage → texte décimal exact) ou l'omet silencieusement.
- `FieldMappingDelegate` : relit et revalide l'attribut littéral, refuse les cibles non déclarées, évalue tout, échoue nommant le champ, écrit **un patch** via `UpdateDossierFieldsUseCase` (identité système, auteur
  `workflow:<pid>/<activité>`), traduit un refus de validation en message listant champ/code/message (jamais la valeur), journalise les champs sensibles modifiés (`SecurityAuditLog.sensitiveFieldChangedByWorkflow`).
- `WorkflowVariables.CONNECTOR_RESULT` réservé.

## Sécurité
Aucune nouvelle surface d'expression (JSONata borné, attribut littéral, jamais JUEL) ; allowlist étendue d'un bean ; champs sensibles jamais lus ; écriture par le chemin métier ; erreurs sans valeurs.
