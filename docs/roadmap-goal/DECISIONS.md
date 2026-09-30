# Journal des décisions — objectif « roadmap complète »

> Tenu par l'agent selon [CHARTE.md](CHARTE.md) §4. Une ligne par décision, écrite au moment où elle est
> prise. Les décisions **⚠ à revoir** sont à relire en priorité.

## À revoir en priorité (⚠)

_(aucune pour l'instant)_

## Toutes les décisions

| ID | Lot / phase | Question | Options envisagées | Choix | Justification (valeur produit) | Cohérence (n° / §) | Conséquences, réversibilité | ⚠ |
|---|---|---|---|---|---|---|---|---|
| D-LC1S-SOURCE | LC1-suite / Ph.1 | Par où servir la liste des tables DMN au panneau de la palette ? | (a) étendre `GET /api/bpmn-building-blocks/references/{source}` ; (b) nouvelle API REST publique `GET /api/dmn-decision-deployments` (liste) ; (c) le SPA appelle l'outil MCP | **(a)** | Un seul chemin pour toutes les listes de la palette, accessible à `WORKFLOW_ADMIN`, contrat minimal `{value,label}` (aucune fuite de XML/règles) ; pas de nouvelle surface REST à maintenir ni à documenter dans l'OpenAPI ; le MCP dispose déjà de `list_decision_tables` (principe P1 satisfait sans nouvel outil) | D-PALETTE-REFERENCES, D-PALETTE-VALUE-SOURCE (§73), décision n° 17 (port `out` existant réutilisé) | Additif, réversible (retirer une entrée de `SERVED_SOURCES` rétablit la saisie libre) | – |
| D-LC1S-LABEL | LC1-suite / Ph.1 | Que représentent valeur et libellé d'une option ? | (a) clé seule ; (b) valeur = clé, libellé = « clé (vN) » ; (c) une entrée par version | **(b)** | Le BPMN référence une **clé**, pas une version (Flowable évalue la dernière) : on stocke la clé, on montre la version qui s'exécutera ; (c) induirait l'utilisateur à croire qu'il épingle une version | Sémantique de `decisionTableReferenceKey` (§57, §61) | Libellé purement d'affichage, modifiable sans impact sur les BPMN existants | – |
| D-LC1S-EXISTS | LC1-suite / Ph.10 | `exists("dmnTables")` doit-il être supporté par l'adaptateur ? | (a) laisser lever `IllegalArgumentException` (existence vérifiée ailleurs) ; (b) l'implémenter via `findDeploymentInfo` | **(b)** (issu de la revue adversariale) | Évite un futur 500 si le vérificateur de références couvre un jour les éléments `FIELD_ELEMENT` ; coût nul, cohérent avec les autres sources ; l'existence au déploiement reste portée par `DeployBpmnProcessUseCaseImpl` | D-PALETTE-DEPLOY-REFERENCES (§73) | Additif | – |
