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

**Garde runtime (TODO §57 H1).** L'éditeur expose encore `flowable:expression`, mais le backend
refuse au parse / sauvegarde / déploiement : `scriptTask`, `flowable:class`, `flowable:expression`,
listeners script, types Flowable built-in (`mail`/`http`/`shell`), et tout
`delegateExpression` hors allowlist
(`${connectorServiceTask}`, `${documentGenerationTask}`, `${attachGeneratedDocumentTask}`,
`${notificationDispatchTask}`, `${createChildDossierTask}`, `${updateDossierStatusTask}`,
`${updateWorkflowLinkStatusTask}`). Payload interdit → `InvalidBpmnException` / HTTP 400.

**Expressions `${…}` / `#{…}` : deux emplacements seulement (TODO §69).** Le moteur Flowable évalue
en expression (UEL) bien plus d'attributs qu'il n'y paraît — nom, documentation, `formKey`,
échéance, priorité, catégorie d'un `userTask`, timers, `calledElement` d'un `callActivity`,
multi-instance, `skipExpression`… — et une expression peut y invoquer un bean Spring. Le backend
refuse donc **toute** expression dans le document BPMN, sauf :

- `flowable:delegateExpression` d'une valeur de l'allowlist ci-dessus ;
- le texte d'une `conditionExpression` de flux, limité à une comparaison simple entre
  variables et littéraux (`${decision == 'approve'}`, `${montant > 1000}` — ni point, ni
  parenthèse, ni appel).

Tous les autres attributs sont des **littéraux** : nom de tâche fixe, `formKey` fixe
(`user-question:avis`), timer `PT48H` et non `${delai}`, multi-instance par
`flowable:collection="nomDeVariable"` (nom de variable, sans `${}`). Les attributs `class`,
`language` (condition écrite en script Groovy/JavaScript) et `scriptFormat` sont refusés partout. Le contrôle s'applique aussi à l'alias d'espace de noms `activiti:` et aux entités XML
(`&#36;{`). Même le texte d'une annotation contenant `${` est refusé (règle volontairement
stricte). Refus → `InvalidBpmnException` / HTTP 400 à la sauvegarde du brouillon comme au
déploiement.

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

> **Briques métier (TODO §73).** Pour les onze cas d'usage courants, la palette de l'éditeur propose
> un groupe **« Briques métier »** et le panneau affiche des champs typés (listes alimentées par
> l'API) à la place des attributs bruts — voir §3quinquies. Le tableau ci-dessus décrit la section
> **« Avancé »**, désormais repliée pour une brique reconnue.

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
Accès depuis un template Thymeleaf ou un `serviceTask` Java : `${dossierFields.montantSinistre}`.
**Pas depuis une condition de passerelle** : la grammaire de `conditionExpression` interdit le
point (TODO §57 H1, §69) — pour brancher sur un champ du dossier, un `serviceTask` doit d'abord
copier la valeur dans une variable simple (`montantSinistre`), puis la condition la compare
(`${montantSinistre > 1000}`).

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

Trois valeurs reconnues aujourd'hui :

| `flowable:formKey` | Formulaire affiché | Variables de complétion |
|---|---|---|
| `dossier-fields` | Schéma complet du `DossierType` du dossier lié (`DynamicFields.vue`) | aucune — soumis via `PATCH /api/dossiers/{id}/fields`, la tâche se complète ensuite sans variable |
| `document-generation` | Sélection d'un template parmi ceux associés au type de dossier (Admin → Types → *Templates de documents autorisés*) | `documentType`, `documentVariant`, `documentLocale` — lues par le `serviceTask` `${documentGenerationTask}` suivant (§5, mêmes noms que le tableau des variables d'entrée), la génération reste où elle est |
| `user-question:<schemaKey>` (TODO §55) | Mini-formulaire admin-défini (Admin → Questions de tâche), **indépendant du dossier** — un ou plusieurs champs (texte/nombre/date/choix, mêmes types que `FieldDefinition`, `FILE` non supporté) | Une variable de process par champ (nom = `FieldDefinition.name` du schéma) — lisible par une passerelle exclusive ou un `serviceTask` en aval ; jamais écrite dans le dossier |

Une tâche sans `formKey`, ou avec une valeur non reconnue, garde le comportement actuel (bouton
« Compléter » sans saisie) — jamais bloquant. Pour `document-generation`, si aucun template n'est
associé au type de dossier, le porteur de tâche voit un message plutôt qu'un picker vide ; associer
au moins un `templateKey` actif dans Admin → Types avant de déployer un process qui l'utilise.

### `user-question:<schemaKey>` — poser un choix indépendant du dossier

1. Créer le schéma dans **Admin → Questions de tâche** : `schemaKey` (identifiant stable, unique
   par tenant), libellé, un ou plusieurs champs (le nom de chaque champ devient le nom de la
   variable de process produite — évitez `documentType`/`documentVariant`/`documentLocale` et les
   4 variables plateforme réservées, rejetés à la sauvegarde du schéma).
2. Dans l'éditeur BPMN, sur le `userTask` concerné, poser `flowable:formKey =
   "user-question:<schemaKey>"` (champ texte libre du panel de propriétés — pas de sélecteur dédié
   pour l'instant).
3. En aval, référencer le nom de champ choisi dans une condition de passerelle exclusive (ex.
   `${decision == 'approve'}`) ou dans un `serviceTask` qui lit la variable.

Le schéma n'est **pas versionné** : le modifier s'applique immédiatement, y compris aux instances
de process déjà démarrées référençant le même `schemaKey` ; le supprimer alors qu'un process en
cours le référence encore dégrade comme un `formKey` inconnu (bouton « Compléter » aveugle).

---

## 3ter. Tâche externe — réponse d'un tiers sans compte (portail, TODO §68)

Un `userTask` devient une **tâche externe** quand il porte `flowable:portalRecipient` : à sa
création, FlowForge envoie au tiers un lien personnel vers une page publique où il remplit un
formulaire `user-question` (§3bis) et dépose des pièces. Sa réponse complète la tâche et produit
les mêmes variables de process qu'une complétion interne.

```xml
<userTask id="avisTiers" name="Avis du fournisseur"
          flowable:formKey="user-question:avis-fournisseur"
          flowable:portalRecipient="dossierField:emailFournisseur"
          flowable:portalLinkTtlDays="10">
  <documentation>Merci de confirmer le délai et de joindre le devis signé.</documentation>
</userTask>
```

| Attribut | Règle |
|---|---|
| `flowable:portalRecipient` | **Obligatoire** — `dossierField:<champ>` (champ du dossier) ou `variable:<nom>` (variable de process), nom simple sans point ni expression |
| `flowable:formKey` | **Obligatoire** — `user-question:<schemaKey>` uniquement (le tiers n'écrit jamais dans les champs du dossier) |
| `flowable:portalLinkTtlDays` | Facultatif — durée de validité en jours (1 à 365, et au plus le plafond de l'instance) ; défaut 7 |
| `documentation` | Facultatif — consignes affichées au tiers (texte simple, littéral) |
| `assignee` / `candidateUsers` / `candidateGroups` | **Interdits** — une tâche externe n'est pas assignée en interne (elle reste complétable par un admin) |

Le déploiement est **refusé** tant que le portail n'est pas activé sur l'instance
(`APP_PORTAL_ENABLED`). Adresse absente ou invalide à la création de la tâche : **aucun incident de
process** — le lien est marqué *Non envoyé*, les participants sont prévenus et l'instructeur le
renvoie après correction (GUIDE-UTILISATEUR §11). Prévoir un **timer boundary** si le process doit
réagir à une absence de réponse (le lien expiré laisse la tâche ouverte).

Les attributs se saisissent dans l'éditeur BPMN (panneau *Flowable* d'un `userTask` :
*Tiers externe — adresse*, *Tiers externe — validité du lien*).

## 3quater. Demander une signature électronique (TODO §72)

Un `serviceTask` `${signatureRequestTask}` **demande** la signature d'un document par un ou plusieurs
signataires, puis le process **attend** le message `signatureCompleted` et reprend avec le résultat.
Le bloc ne bloque jamais le moteur : il enregistre la demande et rend la main.

```xml
<message id="signatureCompletedMsg" name="signatureCompleted"/>
<process id="contrat" isExecutable="true">
  ...
  <serviceTask id="demanderSignature" name="Demander la signature"
               flowable:delegateExpression="${signatureRequestTask}"
               flowable:signatureDocument="variable:generatedDocumentId"
               flowable:signatureMode="SEQUENTIAL"
               flowable:signatureDeadlineDays="14">
    <extensionElements>
      <flowable:signer source="dossierField:emailClient" kind="EXTERNAL" required="true" name="Client"/>
      <flowable:signer source="variable:directeurId" kind="INTERNAL" required="false" name="Directeur"/>
    </extensionElements>
  </serviceTask>
  <intermediateCatchEvent id="attenteSignature">
    <messageEventDefinition messageRef="signatureCompletedMsg"/>
  </intermediateCatchEvent>
  <exclusiveGateway id="routage"/>
  <!-- flux sortants : ${signatureOutcome == 'SIGNED'} / ${signatureOutcome != 'SIGNED'} -->
</process>
```

| Attribut / élément | Règle |
|---|---|
| `flowable:signatureDocument` | **Obligatoire** — désigne un `PRODUCED_DOC` **au format PDF** (tout autre contenu est refusé : `FAILED`, raison « le document à signer n'est pas un PDF ») ; `variable:<nom>` (ex. `generatedDocumentId`, posé par `documentGenerationTask`) ou `dossierField:<nom>` ; nom littéral, jamais évalué |
| `flowable:signatureMode` | `PARALLEL` (défaut : tous peuvent signer tout de suite) ou `SEQUENTIAL` (dans l'ordre des `signer` ; le suivant est invité quand le précédent a signé) |
| `flowable:signatureDeadlineDays` | Facultatif — 1 à 365, au plus le plafond de l'instance (`APP_SIGNATURE_MAX_DEADLINE`) ; défaut `APP_SIGNATURE_DEFAULT_DEADLINE` |
| `<flowable:signer source= kind= required= name=/>` | Un par signataire (au moins un **requis**, sources distinctes, 10 au plus) : `source` = `dossierField:`/`variable:` (adresse e-mail pour `EXTERNAL`, identifiant utilisateur pour `INTERNAL`) ; `kind` = `EXTERNAL` (lien par e-mail, sans compte) ou `INTERNAL` (vue « Mes signatures ») ; `required` (défaut `true`) ; `name` affiché |

**Résultat.** La demande est `SIGNED` quand **tous les signataires requis** ont signé (un facultatif
qui refuse ou n'agit pas n'empêche rien). Un requis qui refuse, l'expiration, l'annulation ou une
erreur du fournisseur clôt la demande. Le process reçoit alors le message `signatureCompleted`
(corrélé par la variable `signatureRequestId`, posée par le bloc) et la variable de process **`signatureOutcome`**
(`SIGNED`, `DECLINED`, `EXPIRED`, `FAILED` ou `CANCELLED`) ; `signatureRequestId` reste lisible en aval sur le même chemin d'exécution. À `SIGNED`, le
**document signé** et la **preuve** sont rattachés au dossier (pièces `PRODUCED_DOC`, métadonnées
`signatureRole` = `SIGNED_DOCUMENT` / `SIGNATURE_PROOF`). La reprise est garantie (nouvelle tentative
automatique) : un process fermé entre-temps annule la demande sans incident.

**Plusieurs blocs, interruptions.** Chaque bloc pose `signatureRequestId` comme variable **locale à son exécution** :
deux blocs en branches parallèles (fork/join) sont corrélés chacun à leur propre attente. Le résultat
`signatureOutcome` est en revanche une variable de process : **routez-le immédiatement** après l'attente de chaque
branche. Si l'attente est interrompue alors que le process continue (frontière interrompante, sous-processus
terminé), la demande est annulée (`CANCELLED`, raison `WAIT_INTERRUPTED`) et ses liens cessent de fonctionner.
L'identifiant du bloc (`id` du `serviceTask`) est limité à 128 caractères.

Contraintes :

- Déploiement **refusé** tant que la fonctionnalité n'est pas activée (`APP_SIGNATURE_PROVIDER`), et
  pour un signataire `EXTERNAL` tant que le portail n'est pas activé (`APP_PORTAL_ENABLED`, §3ter).
- Placer le `intermediateCatchEvent` **immédiatement** après le bloc ; prévoir un routage sur
  `signatureOutcome` (au minimum `SIGNED` / autre) et, si le process doit réagir à une absence de
  réponse, compter sur l'échéance (`EXPIRED`) plutôt que sur un timer.
- Avec le fournisseur `stub`, toute signature est une **SIMULATION sans valeur juridique**, signalée
  dans les PDF produits et dans l'interface.

## 3quinquies. Briques métier de l'éditeur (TODO §73)

L'éditeur (`/admin/bpmn-editor`) charge le **catalogue des briques** exposé par le serveur
(`GET /api/bpmn-building-blocks`, outil MCP `list_bpmn_building_blocks`) : une seule source pour la
palette, le panneau, la validation au déploiement et les agents. Onze briques, chacune = **un seul
élément BPMN** pré-configuré (D-PALETTE-GRANULARITY) :

| Brique | Élément | Marqueur XML reconnu |
|---|---|---|
| Générer un document | `serviceTask` | `${documentGenerationTask}` |
| Joindre le document | `serviceTask` | `${attachGeneratedDocumentTask}` |
| Appeler un connecteur | `serviceTask` | `${connectorServiceTask}` |
| Notifier | `serviceTask` | `${notificationDispatchTask}` |
| Créer un dossier enfant | `serviceTask` | `${createChildDossierTask}` |
| Changer le statut du dossier | `serviceTask` | `${updateDossierStatusTask}` |
| Changer le statut du workflow | `serviceTask` | `${updateWorkflowLinkStatusTask}` |
| Décision DMN | `serviceTask` | `flowable:type="dmn"` |
| Demander une signature | `serviceTask` | `${signatureRequestTask}` |
| Question utilisateur | `userTask` | `flowable:formKey="user-question:<clé>"` |
| Tâche externe (portail) | `userTask` | `formKey` `user-question:` + `flowable:portalRecipient` |

**Où vont les paramètres.** Les six delegates historiques (document, connecteur, notification, dossier
enfant, statut du dossier, statut du workflow) lisent désormais aussi des **attributs littéraux**
`flowable:<nom>` de leur propre activité, par exemple :

```xml
<serviceTask id="cloture" flowable:delegateExpression="${updateDossierStatusTask}"
             flowable:targetDossierStatus="CLOTURE"/>
```

- **L'attribut renseigné prime sur une variable de process de même nom** (D-PALETTE-PRECEDENCE, révisée
  après revue de sécurité) : ce que le concepteur fixe n'est pas modifiable à l'exécution — une variable
  envoyée à la complétion d'une tâche ne peut pas changer le statut, le connecteur ou le destinataire.
  Sans attribut, la variable fonctionne comme avant : rien ne change pour un process existant. Le panneau
  le rappelle sous chaque champ. Valeurs sans espace de bord ni retour à la ligne (sauf le message d'une
  notification) ; valeurs d'énumération exactes (`CLOTURE`, pas `CLOSED`).
- `dossierId` et `dossierWorkflowLinkId` restent **exclusivement** des variables ; les données
  structurées (`connectorInput`, `childDossierFields`, `childWorkflowVariables`, `notificationPayload`)
  restent des variables ou des sorties d'étapes précédentes.
- La table DMN s'écrit comme avant (`<flowable:field name="decisionTableReferenceKey">`), la question
  dans le `formKey`, les signataires en `<flowable:signer …/>` (§3quater).

**Ce qui est vérifié au déploiement** (écran, API et `deploy_workflow` MCP, y compris `dryRun`) :

1. *La forme* — valeurs **littérales** uniquement (`${…}` et `#{…}` refusés), formats UUID / clé /
   énumération / longueur ; sinon `400`.
2. *L'existence* — template de document, connecteur (activé, rôle service), webhook (activé), type de
   dossier, workflow, statut déclaré dans la configuration **de ce process**, table DMN, schéma de
   question ; sinon `404`, comme pour une table DMN inconnue.

Les avertissements de l'éditeur (paramètre obligatoire vide, référence disparue, `${…}` saisi,
signature sans attente `signatureCompleted`, tâche externe sans question, adresse e-mail littérale dans
« Notifier ») sont **non bloquants** : ils n'empêchent ni l'enregistrement du brouillon ni l'export.
Catalogue indisponible → l'éditeur retombe sur la palette et le panneau natifs.

> **Import d'un diagramme sans `xmlns:flowable`.** Dès qu'un paramètre de brique est écrit, l'éditeur
> déclare lui-même `xmlns:flowable` sur `definitions` (D-FLOWABLE-NS) : plus de perte silencieuse du
> premier attribut.

## 4. Instancier / appeler un service externe

Le mécanisme est le **système de connecteurs** ([06-connecteurs.md](analysis%20doc/06-connecteurs.md)), jamais
un appel HTTP écrit en dur dans un delegate ad hoc.

### Étape 1 — créer une instance de connecteur (une fois, côté admin)

Écran d'admin des connecteurs (`ConnectorsAdminView.vue`) : choisir un type (`ConnectorDefinition.getType()`,
ex. `flowable-signal`, ou un connecteur custom ajouté au classpath — voir
[06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Ajout d'un connecteur custom »), puis saisir la
configuration dans le **formulaire généré** depuis `getConfigSchema()` du type
(voir [06-connecteurs.md](analysis%20doc/06-connecteurs.md) « Écran d'admin » et
[GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md) §4). La validation métier reste côté backend
(`ConnectorDefinition.validate(config)`).
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
| `${signatureRequestTask}` | demande une signature électronique (§3quater) | `signatureDocument`, `signatureMode`, `signatureDeadlineDays`, `<flowable:signer>` (attributs du bloc) | `signatureRequestId` ; à la reprise : `signatureOutcome` |
| `${createChildDossierTask}` | crée un dossier enfant/suite et démarre son workflow | `dossierId` (réservée), `childDossierTypeCode`, `childDossierLabel`, `workflowProcessKey`, `relationType` (`PARENT_CHILD` ou `SUBSEQUENT`), `childDossierFields`?, `childWorkflowVariables`? | — |

Toutes ces classes vivent dans `src/main/java/com/flowforge/infrastructure/flowable/` — en cas
de doute sur une variable, elles font foi avant ce document ou avant les `analysis doc/*`
pseudo-code.

### §7bis. `serviceTask flowable:type="dmn"` — invoquer une table de décision (TODO §61)

Mécanisme distinct des `serviceTask` du tableau ci-dessus : pas de `flowable:delegateExpression`,
mais l'attribut natif Flowable `flowable:type="dmn"` **+** un champ d'extension
`decisionTableReferenceKey` portant la clé de la table de décision (id du `<decision>` dans le
`.dmn` déployé séparément, voir §7ter) :

```xml
<serviceTask id="evaluerEligibilite" name="Évaluer l'éligibilité" flowable:type="dmn">
  <extensionElements>
    <flowable:field name="decisionTableReferenceKey">
      <flowable:string>grilleEligibiliteClient</flowable:string>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

Les colonnes de sortie de la table de décision (`<output name="…">`) sont écrites telles quelles
comme variables de process — pas de configuration côté BPMN, le nom de sortie DMN **est** le nom
de la variable. Pas de `businessRuleTask` : cet élément BPMN standard est câblé sur un moteur
Drools absent de ce dépôt et reste explicitement rejeté au parsing (`FlowableBpmnParserAdapter`,
TODO §58).

### §7ter. Quand utiliser une table de décision DMN plutôt qu'une cascade de gateways

| Situation | Choix |
|---|---|
| 2-3 branches, une seule condition simple (`${statut == 'VALIDE'}`) | Passerelle exclusive (`exclusiveGateway`) — pas de table DMN, surcharge inutile |
| Plusieurs critères combinés (ex. type de demandeur **et** montant) produisant un ou plusieurs résultats, règles qui évoluent indépendamment du flux | Table de décision DMN (`serviceTask type="dmn"`) — la logique de décision est isolée, relisible en tableau, modifiable sans retoucher le diagramme de process |
| La règle change plus souvent que le processus lui-même, ou doit être lisible/validée par un non-développeur | Table de décision DMN — édition dans son propre éditeur (`/admin/dmn-editor`), déploiement indépendant du process qui l'invoque |
| Une seule sortie booléenne dérivée d'une seule variable | Passerelle exclusive — une table DMN à une ligne n'apporte rien |

**Sécurité — ne jamais oublier en concevant une table :** Flowable évalue les cellules d'une table
de décision en **JUEL**, pas en FEEL malgré ce que le standard DMN laisse penser (voir
[05-workflow-engine.md](analysis%20doc/05-workflow-engine.md) §« Tables de décision — DMN »). Une
cellule d'entrée/sortie doit rester un littéral (entre guillemets, nombre, booléen), un `-`
(« n'importe quelle valeur »), ou l'un de ces littéraux précédé d'un opérateur de comparaison
(`==`, `!=`, `<`, `>`, `<=`, `>=`) — jamais une expression EL libre ni une navigation de propriété
(`.xxx`). `FlowableDmnParserAdapter` rejette strictement tout le reste au déploiement ; ne pas
chercher à le contourner, la même classe de vulnérabilité que H1-bis (BPMN) s'applique ici.

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
8. Un bloc de signature (§3quater) est suivi d'un `intermediateCatchEvent` `signatureCompleted` et d'un
   routage sur `signatureOutcome` ; document et signataires pointent des champs / variables existants.
