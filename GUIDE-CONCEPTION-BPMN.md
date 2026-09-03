# Guide de conception d'un diagramme BPMN pour FlowForge

> Public : quiconque modélise ou modifie un processus `.bpmn20.xml` destiné à tourner sur le
> moteur FlowForge (dans l'éditeur intégré `/admin/bpmn-editor` ou avec un outil externe type
> Camunda Modeler). Ce document répond aux questions pratiques que l'existant
> ([05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md),
> [07-generation-documents.md](analysis%20doc/07-generation-documents.md),
> [08-templates-documents.md](analysis%20doc/08-templates-documents.md), [04-dossiers.md](analysis%20doc/04-dossiers.md))
> documente chacun sous son propre angle, sans jamais les rassembler du point de vue de
> l'auteur d'un diagramme. Tous les noms de variables, de beans et d'attributs cités ci-dessous
> sont vérifiés contre le code réel du dépôt (`src/main/java/com/flowforge/infrastructure/flowable/`),
> pas seulement contre le pseudo-code des autres documents d'analyse.
>
> **Mise à jour — écart `DossierAwareTask` comblé (§3).** Une première version de ce guide
> signalait que les champs dynamiques du dossier n'étaient accessibles depuis aucun processus
> sans câblage manuel de chaque champ. C'est désormais corrigé côté code (variable réservée
> `dossierFields`, injectée automatiquement aux trois points de démarrage d'un workflow) — voir
> §3 pour le détail et ses limites restantes (instantané non rafraîchi, non masqué). Le second
> écart identifié (absence de `serviceTask` pour chaîner un nouveau workflow sur le **même**
> dossier, §2) reste, lui, un simple contournement documenté — pas un défaut corrigé ici.

---

## 1. Convention à utiliser

### Identifiants BPMN

| Élément | Convention | Exemple réel du dépôt |
|---|---|---|
| `process id` | `camelCase`, devient la **`processDefinitionKey`** — doit être **strictement identique** à `WorkflowTypeConfig.workflowKey` (config créée dans `/admin/workflow-types`) et à `DossierType.workflowKey` si ce processus est le workflow par défaut d'un type de dossier | `smokeProcess`, `signalCatchSmoke` |
| `id` des éléments (tâches, événements, passerelles) | `camelCase` descriptif de l'activité, pas de préfixe technique | `smokeTask`, `updateWorkflowLinkStatusTask`, `waitSignal` |
| `id` des `sequenceFlow` | `flow_<origine>_<destination>` | `flow_start_task`, `flow_task_update` |
| `id` d'un `signal` | `camelCase`, portée globale par défaut | `connectorSmokeSignal` (`flowable:scope="global"`) |

Ces conventions sont celles déjà en usage dans `src/main/resources/processes/*.bpmn20.xml`
(processus de smoke test) — les reprendre à l'identique pour tout nouveau processus garde le
dépôt homogène et lisible en diff.

### `serviceTask` — toujours par `delegateExpression`, jamais par classe Java

Chaque tâche de service appelle un bean Spring enregistré sous un nom (`@Component("xxxTask")`
sur une classe qui implémente `JavaDelegate`), référencé via :

```xml
<serviceTask id="monTask" name="Nom lisible"
             flowable:delegateExpression="${nomDuBean}" />
```

Ne jamais utiliser `flowable:class` (couplage classe Java figée dans le XML, incompatible avec
le rechargement Spring et avec les tests) — tous les `serviceTask` du dépôt utilisent
`delegateExpression`. Le nom entre `${...}` est **exactement** la valeur passée à `@Component(...)`
— voir le catalogue des tâches disponibles en §7.

### Ce que l'éditeur BPMN intégré permet réellement d'éditer

L'éditeur (`/admin/bpmn-editor`, voir [14-edition-bpmn.md](analysis%20doc/14-edition-bpmn.md)) est
`bpmn-js` Modeler + `bpmn-js-properties-panel` avec un **provider Flowable volontairement
restreint** (`flowablePropertiesProvider.ts`) — décision **D-EDIT-SCOPE**. Seuls ces attributs
`flowable:*` sont exposés dans le panel de propriétés :

| Élément | Attribut éditable dans l'UI |
|---|---|
| `serviceTask` | `flowable:async` (case à cocher), `flowable:delegateExpression`, `flowable:expression` |
| `signal` | `flowable:scope` |
| `userTask` | `flowable:formKey` — voir §3bis (TODO §52) |

**Explicitement absents de l'UI** : `assignee`, `candidateGroups`. L'assignation d'une tâche
humaine (`userTask`) n'est **pas déclarée dans le BPMN** — voir §6. Un diagramme importé depuis un
outil externe qui porte d'autres attributs `flowable:*` (ex. `assignee` posé dans Camunda Modeler)
les conserve tels quels au ré-export (`bpmn-moddle` préserve les attributs d'espace de noms
inconnus), mais l'éditeur FlowForge ne permet ni de les visualiser ni de les modifier — à
écrire/modifier hors FlowForge si besoin, en connaissance de cause.

> **D-FLOWABLE-NS** — aucune extension moddle n'est enregistrée pour l'espace de noms `flowable`
> dans cet éditeur (attributs traités génériquement par `bpmn-moddle`). Un diagramme qui ne
> déclare **pas encore** `xmlns:flowable="http://flowable.org/bpmn"` perd silencieusement le
> **premier** attribut `flowable:*` posé dessus (async/delegateExpression/expression/scope/formKey)
> au moment de l'enregistrement — corrigé pour tout diagramme démarré vierge dans cet éditeur
> (le gabarit de départ déclare désormais l'espace de noms), mais un diagramme importé depuis un
> outil externe qui n'a jamais utilisé d'attribut `flowable:*` garde ce risque résiduel. En cas de
> doute après import, ouvrir le XML exporté et vérifier la présence de la déclaration
> `xmlns:flowable`.

### Convention de nommage des variables de processus

Il n'existe pas de schéma de variables imposé par Flowable — la convention ci-dessous est celle
suivie par toutes les tâches livrées (voir catalogue §7) :

- **camelCase**, préfixé par le domaine fonctionnel quand plusieurs variables se rapportent au
  même mécanisme : `notification*` (`notificationChannels`, `notificationTo`…), `document*`
  (`documentType`, `documentVariant`, `documentLocale`), `child*` (`childDossierTypeCode`,
  `childDossierLabel`…).
- **Entrée vs sortie** : une tâche documente dans son nom de variable si elle la lit (entrée,
  fixée par le concepteur du diagramme ou une tâche précédente) ou l'écrit (sortie, à consommer
  par une tâche suivante) — voir la colonne dédiée du catalogue.
- **Quatre variables réservées**, injectées automatiquement par la plateforme au démarrage de
  **toute** instance, sur les trois chemins de démarrage (API manuelle, relais outbox,
  réconciliation des liens `RESERVED` orphelins) — jamais à fixer soi-même, une tentative lève
  `IllegalArgumentException` (voir `WorkflowVariables.assertNoReservedKeys`) :

  | Variable | Contenu |
  |---|---|
  | `dossierId` | UUID du dossier manipulé |
  | `dossierWorkflowLinkId` | UUID de l'exécution courante (`DossierWorkflowLink`) |
  | `tenantId` | tenant courant |
  | `dossierFields` | instantané (`Map<String, Object>`) de `Dossier.fields()` **au moment du démarrage** — voir §3 |

  Toute tâche qui a besoin du dossier ou du tenant les lit sur `DelegateExecution`, jamais en
  paramètre statique du BPMN.

---

## 2. Déclenchement d'un workflow

Trois origines possibles — toutes aboutissent à `dossierId`/`dossierWorkflowLinkId`/`tenantId`
injectées, le reste des variables passées par l'appelant :

| Origine | Mécanisme | Détail |
|---|---|---|
| **Connecteur externe** (webhook, signal Flowable…) | `TriggerConnector.onEvent(...)` → outbox → relais (`OutboxRelayScheduler`) | [06-connecteurs.md](analysis%20doc/06-connecteurs.md). `WorkflowTriggerEvent.variables()` devient les variables du processus ; `WorkflowTriggerEvent.dossierFields()` sert à créer le `Dossier` puis revient automatiquement via la variable `dossierFields` (voir §3) — deux routes distinctes vers le même résultat |
| **Manuel — API/UI** | `POST /api/dossiers/{id}/workflows` (en-tête `Idempotency-Key` obligatoire) → `ManualStartOrchestrator.start(...)` | Depuis la fiche dossier, bouton **Démarrer workflow** (`GUIDE-UTILISATEUR.md` §3) |
| **Chaînage — dossier enfant/suite** | `serviceTask` `${createChildDossierTask}` (voir §7) — crée un dossier `PARENT_CHILD` ou `SUBSEQUENT` **et** démarre son workflow via le même `ManualStartOrchestrator` | [04-dossiers.md](analysis%20doc/04-dossiers.md) §« Démarrage d'un nouveau workflow sur un dossier existant » |

> **Chaîner un *nouveau* workflow sur le *même* dossier** (pas un dossier enfant) — utile pour
> enchaîner deux processus BPMN distincts sur le même objet — n'a **pas** de `serviceTask`
> prêt à l'emploi dans le dépôt actuel : seul `createChildDossierTask` existe, et il crée
> systématiquement un `DossierRelation`. Pour ce cas, retomber sur l'appel manuel API une fois
> le workflow précédent clos (statut terminal atteint), ou écrire un delegate dédié appelant
> `ManualStartOrchestrator` avec le `dossierId` courant — ne pas contourner la règle de
> concurrence (« un seul `DossierWorkflowLink` actif par dossier », voir
> [04-dossiers.md](analysis%20doc/04-dossiers.md)).

---

## 3. Référencer les champs dynamiques du dossier

**Résolu.** Les champs dynamiques du dossier (`Dossier.fields`, schéma
`DossierType.fieldDefinitions`) sont **automatiquement disponibles** sous la variable réservée
`dossierFields` (`Map<String, Object>`, voir §1) dès le démarrage de **toute** instance —
appel manuel, connecteur/relais outbox, ou réconciliation d'un lien `RESERVED` orphelin.
Accès depuis une expression BPMN ou un template Thymeleaf : `${dossierFields.montantSinistre}`.

> **Historique — écart désormais comblé.** Cette section signalait jusqu'ici un écart réel entre
> le pseudo-code de [04-dossiers.md](analysis%20doc/04-dossiers.md) (`DossierAwareTask`, chargement
> automatique du dossier avant chaque `ServiceTask`) et le code livré, où cette classe n'existait
> pas et où aucun champ dynamique n'était exposé sans câblage manuel. `WorkflowVariables
> .withPlatformVariables(...)` (le point d'injection commun aux trois chemins de démarrage) porte
> désormais un quatrième paramètre `dossierFields` — `DossierAwareTask` elle-même reste
> volontairement non réintroduite (aucun `ServiceTask` n'en a besoin : l'exposition se fait au
> niveau variable de processus, pas au niveau classe Java de base), mais l'écart fonctionnel
> qu'elle devait couvrir est fermé.

Deux limites à connaître :

- **C'est un instantané pris au démarrage, jamais rafraîchi automatiquement.** Une modification
  du dossier en cours de workflow (`PATCH` via `UpdateDossierFieldsUseCaseImpl`) n'est **pas**
  répercutée sur `dossierFields` pour l'exécution déjà démarrée. Si un `serviceTask` a besoin de
  la valeur la plus récente en cours de route, il doit encore appeler explicitement
  `GetDossierUseCase.get(dossierId, ...)` et faire `execution.setVariable(...)` lui-même — ce
  n'est plus le cas général, seulement le cas « valeur potentiellement modifiée après le
  démarrage ». Dans ce cas précis : `Dossier.fields()` est protégé par un garde-fou ArchUnit
  (`ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST`, `only_allowlisted_classes_read_raw_
  dossier_fields`) — seules les classes explicitement listées peuvent appeler cette méthode, pour
  empêcher qu'un nouveau point de lecture des champs bruts (potentiellement sensibles) apparaisse
  sans revue. Un nouveau `serviceTask` custom qui a besoin des champs bruts doit être ajouté à
  cette allowlist (avec justification), sans quoi `./mvnw verify` échoue.
- **Fields bruts, non masqués.** `dossierFields` transporte les valeurs réelles, y compris les
  champs marqués `sensitive` sur le `DossierType` — cohérent avec le principe déjà appliqué à la
  génération de document et aux triggers connecteurs (`AccessContext.System` voit les vraies
  valeurs, voir `DossierFieldRedactor`). Ne pas reverser tel quel `${dossierFields}` dans
  `notificationPayload`/`notificationBody` (§6c) sans réfléchir à l'exposition — ce chemin ne
  passe **pas** par le masquage `DossierFieldsSnapshot` des notifications (voir §8).

Dans un template Thymeleaf de document (voir §5 et
[08-templates-documents.md](analysis%20doc/08-templates-documents.md)) : `DocumentGenerationTask`
fusionne **toutes** les variables du processus en cours (`execution.getVariables()`, `dossierFields`
compris), moins une allowlist de clés plateforme filtrée par `DocumentVariableFilter` (`dossierId`,
`dossierWorkflowLinkId`, `tenantId`, `generatedDocumentId`, `documentType`, `documentVariant`,
`documentLocale` — `dossierFields` n'y figure pas, il reste donc visible du template).

**Objet `dossier` en tant que tel** (`${dossier.reference}`, mentionné comme exemple générique
dans [08-templates-documents.md](analysis%20doc/08-templates-documents.md)) : reste **différent**
de `dossierFields` et n'existe toujours pas nativement — c'est la signalétique du dossier
(`reference`, `label`, `status`…), pas ses champs dynamiques. Une tâche qui en a besoin doit
encore faire `execution.setVariable("dossier", ...)` explicitement.

**Rappel utile malgré la correction :** un champ absent de `dossierFields` (parce qu'il n'a
jamais été saisi sur ce dossier, ou que le nom est mal orthographié dans le template) continue de
se résoudre silencieusement à une chaîne vide dans le PDF plutôt que de faire échouer la
génération — Thymeleaf ne distingue pas « valeur vide » de « clé absente de la map ». Tester
chaque template avec un jeu de champs réel reste la seule façon fiable de s'en assurer.

---

## 3bis. Formulaire de tâche (`flowable:formKey`) — TODO §52

Un `userTask` peut déclarer qu'il attend une saisie utilisateur avant complétion, via l'attribut
BPMN standard `flowable:formKey` (éditable dans le panel de propriétés, groupe « Flowable »). Le
moteur ne fait que stocker/restituer cette chaîne — c'est une **convention FlowForge**
(`taskFormRegistry.ts` côté frontend), pas un mécanisme Flowable prêt à l'emploi (le module
`flowable-form` n'est pas sur le classpath).

Deux valeurs reconnues aujourd'hui :

| `flowable:formKey` | Formulaire affiché | Variables de complétion |
|---|---|---|
| `dossier-fields` | Schéma complet du `DossierType` du dossier lié (`DynamicFields.vue`) | aucune — soumis via `PATCH /api/dossiers/{id}/fields`, la tâche se complète ensuite sans variable |
| `document-generation` | Sélection d'un template parmi ceux associés au type de dossier (Admin → Types → *Templates de documents autorisés*) | `documentType`, `documentVariant`, `documentLocale` — lues par le `serviceTask` `${documentGenerationTask}` suivant (§5, mêmes noms que le tableau des variables d'entrée), la génération reste où elle est |

Une tâche sans `formKey`, ou avec une valeur non reconnue, garde le comportement actuel (bouton
« Compléter » sans saisie) — jamais bloquant. Pour `document-generation`, si aucun template n'est
associé au type de dossier, le porteur de tâche voit un message plutôt qu'un picker vide ; associer
au moins un `templateKey` actif dans Admin → Types avant de déployer un process qui l'utilise.

---

## 4. Instancier / appeler un service externe

Le mécanisme est le **système de connecteurs** ([06-connecteurs.md](analysis%20doc/06-connecteurs.md)), jamais
un appel HTTP écrit en dur dans un delegate ad hoc.

### Étape 1 — créer une instance de connecteur (une fois, côté admin)

Écran d'admin des connecteurs (`ConnectorsAdminView.vue`) : choisir un type (`ConnectorDefinition.getType()`,
ex. `flowable-signal`, ou un connecteur custom ajouté au classpath — voir
[06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Ajout d'un connecteur custom »), puis saisir la
configuration en JSON libre (un `<textarea>` de JSON brut, validé côté backend par
`ConnectorDefinition.validate(config)` — l'écran n'affiche pas de formulaire généré à partir de
`getConfigSchema()`, voir [06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Écran d'admin »).
Cela produit un `ConnectorInstanceData.id`
(UUID) — **c'est cet UUID que le BPMN référence**, jamais le type ou la config en dur.

### Étape 2 — l'appeler depuis un `serviceTask`

```xml
<serviceTask id="appelServiceExterne" name="Appel connecteur"
             flowable:delegateExpression="${connectorServiceTask}" />
```

Variables lues par `connectorServiceTask` :

| Variable | Obligatoire | Rôle |
|---|---|---|
| `connectorInstanceId` | oui | UUID de l'instance de connecteur créée à l'étape 1 |
| `connectorInput` | non (`Map`, défaut vide) | paramètres passés à `ServiceConnector.execute(instance, input)` |
| `tenantId` | non | override du tenant courant — laisser la valeur injectée par défaut sauf besoin explicite |

Variable écrite : `connectorResult` (`Map<String, Object>`, uniquement si le résultat n'est pas
vide) — à lire dans les tâches suivantes du même processus.

### Étape 3 (facultative) — signal Flowable entre deux processus

Le connecteur `flowable-signal` permet à un `serviceTask` (`${connectorServiceTask}` avec
l'instance configurée en émetteur) de déclencher un `intermediateCatchEvent`/`signalEventDefinition`
dans un **autre** processus, sans dépendance directe entre les deux diagrammes — voir
`src/main/resources/processes/signal-smoke.bpmn20.xml` pour un exemple minimal émetteur/récepteur.

---

## 5. Déclencher la génération d'un document

Deux `serviceTask` distincts, presque toujours chaînés l'un après l'autre :

### `documentGenerationTask` — génère le PDF

```xml
<serviceTask id="genererDocument" name="Générer le document"
             flowable:delegateExpression="${documentGenerationTask}" />
```

| Variable | Obligatoire | Rôle |
|---|---|---|
| `documentType` | oui | `templateKey` — résout le template actif (voir [08-templates-documents.md](analysis%20doc/08-templates-documents.md)) |
| `documentVariant` | non | `variantKey` (ex. `"premium"`, `"B2B"`) — résolution en cascade si absent |
| `documentLocale` | non | langue du destinataire (ex. `"fr"`, `"nl"`) — résolution en cascade si absent |

Toutes les autres variables du processus au moment de l'exécution sont transmises au template
Thymeleaf (voir §3 pour la mécanique exacte et l'allowlist de rendu — pas de `T()`, `#beans`,
`new`, cf. décision **D-40**). Variable écrite : `generatedDocumentId` (référence de stockage).

### `attachGeneratedDocumentTask` — attache le PDF au dossier

```xml
<serviceTask id="attacherDocument" name="Attacher au dossier"
             flowable:delegateExpression="${attachGeneratedDocumentTask}" />
```

Lit `dossierId` (réservée, injectée automatiquement) et `generatedDocumentId` (écrite par la
tâche précédente) — échoue si le document référencé n'existe plus dans le stockage. Crée un
`DossierElement` de type `PRODUCED_DOC`, visible dans la fiche dossier, avec `workflowStepId`
= l'`id` de cette activité BPMN.

> **Toujours enchaîner les deux.** `documentGenerationTask` seul génère le PDF sans le rattacher
> au dossier — il resterait orphelin (accessible uniquement via `generatedDocumentId`, jamais
> listé dans les éléments du dossier).

---

## 6. Référencer les intervenants sur un dossier

FlowForge ne déclare **aucun intervenant dans le BPMN lui-même** — ni `assignee`, ni
`candidateGroups` (retiré explicitement du scope de l'éditeur, décision **D-EDIT-SCOPE**, voir
§1). Trois mécanismes distincts couvrent le besoin :

### a) Assignation d'une tâche humaine (`userTask`) — à l'exécution, pas dans le diagramme

Un `userTask` sans `assignee` reste non assignée jusqu'à une action explicite :
`TaskService.setAssignee(taskId, userId)`, exposé par
`PATCH`/action **Réassigner** sur l'écran de suivi du dossier (`GUIDE-UTILISATEUR.md` §3, tâches
en cours) ou sur `/tasks` — identifiant utilisateur saisi librement, pas de groupe candidat en V1.
Rien à écrire dans le diagramme pour ce mécanisme.

### b) Résolution des « participants » d'une exécution — pour les notifications automatiques

`WorkflowParticipantPort.findParticipantUserIds(link)` calcule, **sans configuration BPMN**, la
liste des utilisateurs concernés par un `DossierWorkflowLink` :

- l'utilisateur qui a démarré l'exécution (`startedBy`, ou l'utilisateur Flowable ayant démarré
  l'instance) ;
- tous les *assignees*, passés et présents, des tâches humaines de l'instance (historique
  compris) ;
- les **observateurs** déclarés sur la `WorkflowTypeConfig` du processus (`observerUserIds`) —
  seul point réellement configurable, dans l'écran **Admin → Workflows**.

Cette liste est utilisée **automatiquement**, sans aucun `serviceTask` à poser dans le
diagramme : `WorkflowStateEventListener` (écouteur d'événements Flowable enregistré
globalement) notifie chaque participant en canal `BROWSER` (SSE) sur `TASK_ASSIGNED`,
`ACTIVITY_COMPLETED` et `PROCESS_COMPLETED`. Si le besoin se limite à « informer les gens déjà
impliqués qu'il se passe quelque chose », il n'y a rien à faire dans le BPMN.

### c) Notification explicite et ciblée — `notificationDispatchTask`

Pour un message métier explicite (pas juste « ça a bougé »), poser :

```xml
<serviceTask id="notifierGestionnaire" name="Notifier"
             flowable:delegateExpression="${notificationDispatchTask}" />
```

| Variable | Obligatoire | Rôle |
|---|---|---|
| `notificationChannels` | oui | liste ou CSV parmi `EMAIL`, `WEBHOOK`, `BROWSER` |
| `notificationTo` | selon canal | destinataire email |
| `notificationSubject` / `notificationBody` | selon canal | contenu email |
| `notificationUserId` | pour `BROWSER` | **un seul** utilisateur — pour notifier plusieurs intervenants (ex. tous les participants), utiliser un `multiInstanceLoopCharacteristics` Flowable standard sur cette tâche, itérant sur une variable liste, ou combiner avec le mécanisme (b) qui couvre déjà le cas générique |
| `notificationWebhookConfigId` | pour `WEBHOOK` | UUID de la config webhook tenant |
| `notificationPayload` | non | `Map` libre, transmis tel quel au canal |

Le contenu peut référencer les champs dynamiques exposés selon la méthode du §3
(`notificationBody` reste une simple variable process — construire la chaîne avec une expression
FEEL/UEL Flowable ou une tâche amont qui la compose).

---

## 7. Catalogue des `serviceTask` livrés

Référence rapide — `delegateExpression`, variables lues (entrée) et écrites (sortie) :

| `delegateExpression` | Rôle | Entrée | Sortie |
|---|---|---|---|
| `${updateDossierStatusTask}` | change `Dossier.status` | `dossierId` (réservée), `targetDossierStatus` (valeur de `DossierStatus`) | — |
| `${updateWorkflowLinkStatusTask}` | change le statut métier de l'exécution courante (`DossierWorkflowLink.status`, validé contre `WorkflowTypeConfig.statusDefinitions`) | `dossierWorkflowLinkId` (réservée), `targetWorkflowStatus` (code déclaré dans la config workflow) | — |
| `${documentGenerationTask}` | génère un PDF depuis un template | `documentType`, `documentVariant`?, `documentLocale`?, + toute variable utile au template | `generatedDocumentId` |
| `${attachGeneratedDocumentTask}` | rattache le PDF généré au dossier (`DossierElement`) | `dossierId` (réservée), `generatedDocumentId` | — |
| `${connectorServiceTask}` | invoque un connecteur en rôle SERVICE | `connectorInstanceId`, `connectorInput`? | `connectorResult`? |
| `${notificationDispatchTask}` | envoie une notification (outbox) | `notificationChannels` + champs selon canal (§6c) | — |
| `${createChildDossierTask}` | crée un dossier enfant/suite et démarre son workflow | `dossierId` (réservée), `childDossierTypeCode`, `childDossierLabel`, `workflowProcessKey`, `relationType` (`PARENT_CHILD` ou `SUBSEQUENT`), `childDossierFields`?, `childWorkflowVariables`? | — |

Toutes ces classes vivent dans `src/main/java/com/flowforge/infrastructure/flowable/` — en cas
de doute sur une variable, elles font foi avant ce document ou avant les `analysis doc/*`
pseudo-code.

---

## 8. Sécurité et bonnes pratiques à respecter en concevant un processus

- **Ne jamais faire confiance à une variable issue d'un webhook** sans validation métier en
  amont (le connecteur `http-rest` authentifie la requête HMAC/token/IP, mais ne valide pas le
  contenu du payload — c'est `DossierFieldValidator` côté création du dossier qui le fait, pas
  le BPMN).
- **URLs sortantes** (webhook, connecteur HTTP) passent toujours par `OutboundUrlGuard` côté
  infrastructure — ne jamais construire un appel HTTP direct dans un delegate custom sans
  réutiliser cette garde (protection SSRF, voir [06-connecteurs.md](analysis%20doc/06-connecteurs.md) et
  [09-notifications.md](analysis%20doc/09-notifications.md)).
- **`dossierFields` (§3) est non masqué** — il porte les valeurs réelles, y compris les champs
  `sensitive`, cohérent avec le fait que le contexte workflow est un appelant système
  (`AccessContext.System`, voir `DossierFieldRedactor`). C'est le comportement voulu pour la
  génération de document (un contrat doit pouvoir imprimer une donnée sensible), **pas** pour une
  notification. Le domaine expose un mécanisme de masquage dédié (`DossierFieldsSnapshot`, résolu
  dans `SendNotificationUseCaseImpl` contre les `FieldDefinition.isSensitive()` du `DossierType`),
  mais **aucun producteur ne le câble aujourd'hui** — ni `NotificationDispatchTask` (le
  `serviceTask` `${notificationDispatchTask}`, §6c) ni les autres émetteurs de `NotificationEvent`
  du dépôt ne construisent d'instance avec ce paramètre renseigné. Conséquence pratique : ne
  jamais copier `${dossierFields}` (ni un champ individuel potentiellement sensible) tel quel dans
  `notificationBody`/`notificationPayload` depuis un diagramme — il n'existe aujourd'hui **aucun**
  chemin BPMN qui applique le masquage avant l'envoi vers un canal (email, webhook, navigateur).
- **Templates de document** : n'utiliser que `${...}` et les objets utilitaires Thymeleaf
  autorisés (`#dates`, `#numbers`, `#strings`, `#lists`) — `T()`, `#beans`, `new`, `#request`
  sont refusés à l'activation du template (décision **D-40**, voir
  [08-templates-documents.md](analysis%20doc/08-templates-documents.md)).
- **Concurrence** : ne jamais tenter de démarrer un second workflow sur un dossier qui en a déjà
  un actif en dehors des mécanismes documentés ici (§2) — la contrainte unique
  `uq_dossier_workflow_link_active` le refuse de toute façon, mais un `serviceTask` custom qui
  l'ignorerait ferait échouer le processus en pleine exécution plutôt qu'à la conception.

---

## 9. Checklist avant de déployer un nouveau processus

1. `process id` = `workflowKey` de la `WorkflowTypeConfig` déjà créée (ou à créer juste après un
   premier déploiement via upload — voir `GUIDE-UTILISATEUR.md` §3).
2. Chaque `serviceTask` référence un `delegateExpression` existant (catalogue §7) — jamais
   `flowable:class`.
3. Les champs dynamiques du dossier sont lus via `${dossierFields.x}` (§3, injecté
   automatiquement) — n'écrire un `serviceTask` de rafraîchissement explicite que si le workflow
   a besoin d'une valeur modifiée **après** son démarrage.
4. Les statuts posés par `targetDossierStatus`/`targetWorkflowStatus` existent bien dans
   `DossierStatus` / dans les `statusDefinitions` de la `WorkflowTypeConfig` du processus.
5. Toute génération de document est suivie d'un rattachement (`attachGeneratedDocumentTask`),
   sauf besoin explicite de PDF non rattaché.
6. Aucun `assignee`/`candidateGroups` attendu du diagramme pour l'assignation des `userTask` —
   c'est un geste opérationnel post-démarrage (§6a).
7. `./mvnw verify` (ArchUnit compris) si un nouveau delegate Java a été ajouté pour ce processus.
