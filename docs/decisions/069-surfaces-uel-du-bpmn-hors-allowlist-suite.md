# §69 — Surfaces UEL du BPMN hors allowlist — suite de H1-bis (High)

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §69 · [registre](README.md)

**Énoncé :** trouvé pendant la Phase 3 de `portail-externe` (§68, 2026-09-23) en lisant les sources
`flowable-engine` 8.0.0 : `UserTaskActivityBehavior` évalue en UEL `name`, `documentation`,
`formKey`, `dueDate`, `priority`, `category`, `owner`, `businessCalendarName`,
`taskIdVariableName` d'un `userTask` ; ailleurs, timers, `callActivity calledElement`,
multi-instance, `skipExpression`… sont aussi des expressions. L'allowlist H1-bis (§57/§58) ne
contrôlait que `assignee`/`candidateUsers`/`candidateGroups`, `conditionExpression`, les listeners
et `delegateExpression`. Même classe de faille que H1-bis : un `WORKFLOW_ADMIN` (écran de
déploiement) ou un client M2M `API_SERVICE` (MCP `deploy_workflow`) pouvait faire invoquer une
méthode d'un bean Spring vivant, avec chaînage de réflexion possible.

**Preuve d'exécution (2026-09-24), pas seulement lecture :** `FlowableBpmnUelSurfaceGapTest`
déploie sur le vrai moteur un `userTask` `name`/`flowable:formKey` =
`${connectorServiceTask.getClass().getName()}` ; `task.getName()` et `task.getFormKey()` valent
le nom de classe du bean — méthode effectivement invoquée. Le même document passait
`BpmnParserPort` (test rouge avant correctif). 20 vecteurs unitaires rouges avant correctif
(`FlowableBpmnParserAdapterUelSurfaceTest`).

**Correctif — règle inversée, allowlist générique :** `BpmnExpressionSurfaceScanner` lit le XML
brut (StAX sans DTD ni entité externe) et refuse **tout** `${…}`/`#{…}`, attribut ou texte, tout
espace de noms (alias `activiti:` compris), entités décodées (`&#36;{`), sauf
`delegateExpression` allowlistée et `conditionExpression` revalidée par la grammaire sûre. Tout
attribut `class` est refusé. Appelé par `FlowableBpmnParserAdapter` **après** les règles ciblées
existantes (messages d'erreur inchangés). Couvre brouillon, déploiement admin et MCP (même
parseur). Aucun BPMN livré impacté (`FlowableBpmnParserAdapterShippedProcessesTest` vert).
Faux positif assumé : texte d'annotation contenant `${`.

**Vecteur latent fermé en défense en profondeur (relecture adversariale du correctif) :** une
`conditionExpression` portant `language="groovy"` (ou tout langage JSR-223) est exécutée comme un
**script** par Flowable (`ConditionUtil` → `ScriptCondition`), sans `${` — invisible pour la
grammaire de condition H1 comme pour la nouvelle règle. **Non exploitable aujourd'hui** : aucun
moteur de script sur le classpath (vérifié sur les 459 jars du build : ni Groovy, ni GraalJS,
ni Rhino, ni Jython ; Nashorn absent du JDK depuis la 15). Mais l'ajout futur, même transitif,
d'un tel moteur l'aurait transformé en exécution de code arbitraire : les attributs `language` et
`scriptFormat` sont désormais refusés partout (`scriptConditionLanguage_isRejected`).

**Documentation :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` — règle des deux emplacements ; correction d'une
affirmation déjà fausse depuis H1-bis (`${dossierFields.x}` présenté comme utilisable « depuis une
expression BPMN » alors que la grammaire de condition interdit le point).

**Impact sur §68 :** le design du portail n'a plus besoin d'une règle `formKey` littéral propre
aux tâches externes — la règle est désormais générale.

**Impacte :** `FlowableBpmnParserAdapter`, `BpmnExpressionSurfaceScanner` (nouveau), tests
`FlowableBpmnUelSurfaceGapTest`/`FlowableBpmnParserAdapterUelSurfaceTest`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md`, `CHANGELOG.md`.
