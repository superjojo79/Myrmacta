# §99 — Le BPMN ne peut pas recopier une valeur dans une variable réservée

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] corrigé (issue [#203](https://github.com/superjojo79/FlowForge/issues/203)). Constat L1 de la revue adversariale du §97.

**Constat.** `WorkflowVariables.assertNoReservedKeys` refuse les variables réservées (`dossierId`, `dossierWorkflowLinkId`,
`tenantId`, `dossierFields`, `connectorResult`, `generatedDocumentId`) dans toute entrée utilisateur. Le BPMN déployé pouvait
pourtant y recopier une valeur fournie à une complétion, par des attributs sans `${}` que le §69 laisse passer :
- **A** — boucle multi-instance `flowable:collection="refs" flowable:elementVariable="generatedDocumentId"` sur « Joindre le
  document » : Flowable crée une variable locale que le délégué lit en priorité ;
- **B** — `callActivity` avec `<flowable:in source="docRef" target="generatedDocumentId"/>` (ou `target="dossierId"`).

Une complétion qui pose `refs` ou `docRef` (noms libres) suffit alors à rattacher comme `PRODUCED_DOC` le document d'un autre
dossier. Reproduit sur le moteur par `ReservedVariableWriteIT`. Il faut un BPMN écrit dans ce sens et publié par un humain.

**D-BPMN-RESERVED-WRITE** ⚠ — `BpmnReservedVariableWriteScanner`, appelé par `FlowableBpmnParserAdapter` après le §69, donc à
la sauvegarde du brouillon, à la proposition, au déploiement (REST et MCP). Lecture brute du XML, tout espace de noms
(`activiti:` compris), valeur comparée sans espaces de bord. Une écriture vers un nom réservé est refusée (400) :
- tout attribut `…Variable` ou `…VariableName` : `elementVariable`, `elementIndexVariable`, `resultVariable`,
  `taskIdVariableName`, `taskCompleterVariableName`, `errorVariableName`… La règle de suffixe couvre les attributs à venir ;
- `initiator` d'un événement de début ;
- `target` ou `targetExpression` de tout élément (`flowable:in`, `flowable:out`, `eventOutParameter`,
  `variableAggregation`…), **sauf** recopie à l'identique dans un `in` / `out` (`source` = `target`, sans `sourceExpression`
  ni `targetExpression`) : passer `dossierId` ou `tenantId` tel quel au sous-processus reste permis ;
- `name` d'un `dataObject` (variable posée au démarrage, ou locale à l'entrée d'un sous-processus) et d'un `inputDataItem`
  (forme standard de l'`elementVariable`) ;
- `id` ou `variable` d'un `formProperty`.

Les lectures (`source`, `collection`, `in variables="all"`) restent libres. Choix faits sans arbitrage : contrôle générique
sur le XML plutôt qu'une liste d'objets du modèle Flowable (même raisonnement que le §69, et le XML voit les éléments
d'extension que le modèle ne type pas) ; `initiator`, `dataObject`, `formProperty`, variables d'erreur et de tâche ajoutés à
la liste de l'issue, car ils écrivent aussi une variable. Les associations de données BPMN (`dataInputAssociation`) ne sont
lues que par la tâche Web Service, déjà refusée. Les sorties DMN restent l'objet de l'issue
[#204](https://github.com/superjojo79/FlowForge/issues/204).

**Tests.** `FlowableBpmnParserAdapterReservedWriteTest` : 21 refus (rouges avant le correctif, documents valides et acceptés)
et 5 acceptations (recopies à l'identique, lecture sous un autre nom) ; `ReservedVariableWriteIT` : scénarios A et B
déployés directement dans le moteur (le document de la victime est rattaché), puis refusés par le parseur.

**Avant de déployer.** Un BPMN déjà déployé n'est pas recontrôlé ; ses instances continuent. Un redéploiement ou une nouvelle
sauvegarde du brouillon est refusé tant que l'écriture reste. Requête de contrôle, sur le schéma du tenant (doit être vide) :
`SELECT d.name_, b.name_ FROM act_ge_bytearray b JOIN act_re_deployment d ON d.id_ = b.deployment_id_ WHERE b.name_ LIKE '%.bpmn%' AND convert_from(b.bytes_, 'UTF8') ~ '="\s*(dossierId|dossierWorkflowLinkId|tenantId|dossierFields|connectorResult|generatedDocumentId)\s*"';`
Un résultat n'est pas forcément un défaut (une recopie à l'identique ou une lecture le fait aussi remonter) : relire
l'attribut.

**Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (variables réservées), `docs/specs/05-workflow-engine.md`, §97 (constat
L1). Aucune migration, aucune route, golden OpenAPI inchangé. Aucun BPMN livré, exemple ou e2e n'est concerné.
