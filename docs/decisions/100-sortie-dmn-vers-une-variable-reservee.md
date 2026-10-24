# §100 — Une table DMN ne peut pas écrire une variable réservée

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] corrigé (issue [#204](https://github.com/superjojo79/FlowForge/issues/204)). Constat L2 de la revue adversariale du §97 ;
pendant DMN du [§99](099-recopie-bpmn-vers-une-variable-reservee.md).

**Constat.** `FlowableDmnParserAdapter` vérifiait les cellules (liste blanche JUEL, §61) mais aucun nom de variable écrit.
D'après le bytecode Flowable 8.0.0, `DmnActivityBehavior` (BPMN `serviceTask flowable:type="dmn"`) pose dans le process :
- pour un résultat unique, chaque sortie sous le nom de sa colonne (`OutputClause.getName()`) ;
- pour un résultat multiple (`COLLECT` à plusieurs règles vérifiées), la liste sous la clé appelée, c'est-à-dire l'id de la
  décision ou d'un **service de décision** (`decisionService`, DMN 1.2 et plus, déployé comme une décision appelable).

Une table qui nomme ainsi `generatedDocumentId`, `dossierId`, `dossierFields` ou `connectorResult` écrase une valeur que les
délégués lisent comme sûre. Reproduit sur le moteur par `ReservedDmnOutputIT` :
- **A** : la sortie `generatedDocumentId` rattache comme `PRODUCED_DOC` le document d'un autre dossier ;
- **B** : une décision `COLLECT` d'id `connectorResult` y pose sa liste ;
- **C** : un service de décision d'id `dossierFields` remplace l'instantané du dossier.

La valeur écrite est un littéral de la table : il faut un concepteur qui connaît la valeur cible.

**D-DMN-RESERVED-OUTPUT** ⚠ — `FlowableDmnParserAdapter.extractDecisionKey` refuse (400) un nom réservé
(`WorkflowVariables.reservedKeys()`, valeur sans espaces de bord, casse respectée comme au §99) dans :
- le `name` de toute colonne de sortie ;
- l'`id` de la décision ;
- l'`id` de tout `decisionService`.

Le contrôle couvre tous les chemins de mise en ligne, qui passent par ce parseur : brouillon, proposition, déploiement
(REST, MCP `deploy_decision_table`, publication d'un brouillon importé), retour à une version antérieure. Les tables livrées
(`src/main/resources/decisions`) restent vérifiées par `FlowableDmnParserAdapterShippedDecisionsTest`.

Choix fait sans arbitrage : l'issue proposait `isReservedOrKnownKey`, qui refuse aussi `documentType`, `documentVariant` et
`documentLocale`. Ces trois noms restent permis en sortie : une complétion de tâche peut déjà les poser, donc une table qui
les écrit n'élève aucun droit, et choisir le modèle de document par une table est un usage légitime. Seuls les noms
réservés sont refusés.

**Tests.** `FlowableDmnParserAdapterReservedOutputTest` : 11 refus (les six noms réservés en sortie, espaces de bord, seconde
colonne, id de décision en `COLLECT` et `UNIQUE`, id de service de décision), tous rouges avant le correctif, et 3
acceptations (`documentType`, `parentDossierId`, `DossierId`). `ReservedDmnOutputIT` : scénarios A, B et C déployés
directement dans le moteur, puis refusés par le parseur.

**Revue adversariale (2026-10-24, sur le bytecode Flowable 8.0.0).**
- Constat **C1**, corrigé dans ce lot : le parseur ne compte que les `decision`. Un `decisionService` d'id réservé passait,
  et le BPMN peut l'appeler par `decisionTableReferenceKey` (scénario C prouvé sur le moteur). Son id est désormais contrôlé.
  Les sorties d'un service sont celles de l'unique décision, déjà contrôlées.
- Le BPMN n'accepte sur la tâche DMN que le champ `decisionTableReferenceKey`, sous forme littérale (§61 D1) : pas de
  `resultVariable` ni de gestionnaire de variables à surcharger. Aucun `DecisionTableVariableManager` n'est configuré.
- `evaluate_decision`, le jeu de tests et l'aperçu de l'éditeur évaluent la table hors de tout process : rien n'est écrit.
- Les tables livrées sont déployées au démarrage sans passer par le parseur. Elles sont vérifiées par un test et aucune
  n'est concernée.

**Avant de déployer.** Une table déjà déployée n'est pas recontrôlée ; un BPMN qui l'appelle continue de l'évaluer. Requête
de contrôle, sur le schéma du tenant (doit être vide) :
`SELECT d.name_, r.name_ FROM act_dmn_deployment_resource r JOIN act_dmn_deployment d ON d.id_ = r.deployment_id_ WHERE convert_from(r.resource_bytes_, 'UTF8') ~ '(name|id)="\s*(dossierId|dossierWorkflowLinkId|tenantId|dossierFields|connectorResult|generatedDocumentId)\s*"';`
Un résultat n'est pas forcément un défaut (une entrée ou une règle peut porter un tel `id`) : relire l'élément. Une table
concernée se corrige par une nouvelle version au nom de sortie changé, puis par la mise à jour des BPMN qui la lisent.

**Impacte :** `docs/specs/05-workflow-engine.md` (variables réservées), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§7bis et
variables réservées), §97 (constat L2), §99 (renvoi). Aucune migration, aucune route, golden OpenAPI inchangé. Aucune table
livrée, exemple ou e2e n'est concerné.
