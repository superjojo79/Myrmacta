# Guide de conception d'un diagramme BPMN pour Myrmacta

> Public : quiconque modélise ou modifie un processus `.bpmn20.xml` destiné à tourner sur le
> moteur Myrmacta (dans l'éditeur intégré `/admin/bpmn-editor` ou avec un outil externe type
> Camunda Modeler). Ce document répond aux questions pratiques que l'existant
> ([05-workflow-engine.md](../specs/05-workflow-engine.md), [06-connecteurs.md](../specs/06-connecteurs.md),
> [07-generation-documents.md](../specs/07-generation-documents.md),
> [08-templates-documents.md](../specs/08-templates-documents.md), [04-dossiers.md](../specs/04-dossiers.md))
> documente chacun sous son propre angle, sans jamais les rassembler du point de vue de
> l'auteur d'un diagramme. Tous les noms de variables, de beans et d'attributs cités ci-dessous
> sont vérifiés contre le code réel du dépôt (`src/main/java/be/myrmacta/infrastructure/flowable/`),
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

**Expressions `${…}` / `#{…}` : trois emplacements seulement (TODO §69, §115).** Le moteur Flowable évalue
en expression (UEL) bien plus d'attributs qu'il n'y paraît — nom, documentation, `formKey`,
échéance, priorité, catégorie d'un `userTask`, timers, `calledElement` d'un `callActivity`,
multi-instance, `skipExpression`… — et une expression peut y invoquer un bean Spring. Le backend
refuse donc **toute** expression dans le document BPMN, sauf :

- `flowable:delegateExpression` d'une valeur de l'allowlist ci-dessus ;
- le texte d'une `conditionExpression` de flux, limité à une comparaison simple entre
  variables et littéraux (`${decision == 'approve'}`, `${montant > 1000}` — ni point, ni
  parenthèse, ni appel) ;
- le `timeDate` d'un minuteur, s'il vaut exactement `${echeanceRappel}` ou `${echeanceDepassee}`, les variables
  réservées posées par la brique « Calculer une échéance » (§« Échéance et minuteur »).

Tous les autres attributs sont des **littéraux** : nom de tâche fixe, `formKey` fixe
(`user-question:avis`), timer `PT48H` et non `${delai}`, multi-instance par
`flowable:collection="nomDeVariable"` (nom de variable, sans `${}`). Les attributs `class`,
`language` (condition écrite en script Groovy/JavaScript) et `scriptFormat` sont refusés partout. Le contrôle s'applique aussi à l'alias d'espace de noms `activiti:` et aux entités XML
(`&#36;{`). Même le texte d'une annotation contenant `${` est refusé (règle volontairement
stricte). Refus → `InvalidBpmnException` / HTTP 400 à la sauvegarde du brouillon comme au
déploiement.

### Ce que l'éditeur BPMN intégré permet réellement d'éditer

L'éditeur (`/admin/bpmn-editor`, voir [14-edition-bpmn.md](../specs/14-edition-bpmn.md)) est
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
inconnus), mais l'éditeur Myrmacta ne permet ni de les visualiser ni de les modifier — à
écrire/modifier hors Myrmacta si besoin, en connaissance de cause.

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
- **Le diagramme ne peut pas écrire une variable réservée** (les quatre ci-dessus, `connectorResult`
  et `generatedDocumentId`, TODO §99). Sont refusés à la sauvegarde comme au déploiement : un
  `elementVariable` / `elementIndexVariable` de multi-instance, un `flowable:in` / `flowable:out` dont
  la cible est réservée, un `dataObject`, un `initiator`, une variable d'erreur ou de tâche
  (`errorVariableName`, `taskIdVariableName`…) portant l'un de ces noms. Seule exception : la recopie
  à l'identique vers un sous-processus, `<flowable:in source="dossierId" target="dossierId"/>`. Même règle
  pour les sorties d'une table DMN (§7bis).

---

## 2. Déclenchement d'un workflow

Trois origines possibles — toutes aboutissent à `dossierId`/`dossierWorkflowLinkId`/`tenantId`
injectées, le reste des variables passées par l'appelant :

| Origine | Mécanisme | Détail |
|---|---|---|
| **Connecteur externe** (webhook, signal Flowable…) | `TriggerConnector.onEvent(...)` → outbox → relais (`OutboxRelayScheduler`) | [06-connecteurs.md](../specs/06-connecteurs.md). `WorkflowTriggerEvent.variables()` devient les variables du processus ; `WorkflowTriggerEvent.dossierFields()` sert à créer le `Dossier` puis revient automatiquement via la variable `dossierFields` (voir §3) — deux routes distinctes vers le même résultat |
| **Manuel — API/UI** | `POST /api/dossiers/{id}/workflows` (en-tête `Idempotency-Key` obligatoire) → `ManualStartOrchestrator.start(...)` | Depuis la fiche dossier, bouton **Démarrer workflow** (`docs/guides/GUIDE-UTILISATEUR.md` §3) |
| **Chaînage — dossier enfant/suite** | `serviceTask` `${createChildDossierTask}` (voir §7) — crée un dossier `PARENT_CHILD` ou `SUBSEQUENT` **et** démarre son workflow via le même `ManualStartOrchestrator` | [04-dossiers.md](../specs/04-dossiers.md) §« Démarrage d'un nouveau workflow sur un dossier existant » |

### Variables de démarrage déclarées (TODO §111)

Pour remplacer la saisie libre du dialogue « Démarrer un workflow » par un formulaire, déclarer chaque
variable sur le **processus** (pas sur une activité) :

```xml
<process id="autorisationRiverain" isExecutable="true">
  <extensionElements>
    <flowable:startVariable name="motif" label="Motif de la demande" required="true"/>
    <flowable:startVariable name="note" label="Note" required="false"/>
  </extensionElements>
  …
</process>
```

`name` est le nom de la variable de process (lettres, chiffres, `_`). `label` est le libellé montré à
l'agent. `required` vaut `true` ou `false`. L'éditeur conserve ces éléments à l'enregistrement ; il ne
propose pas encore de champ pour les ajouter, on les écrit dans le XML avant de déployer.

Dès qu'au moins une déclaration est déployée, le démarrage manuel, l'outil MCP `start_workflow`, le
dossier enfant (`childWorkflowVariables`) et un déclencheur passé par l'outbox n'acceptent **que** ces
noms, en texte. Une obligatoire absente ou blanche fait échouer le démarrage (l'événement d'outbox est
marqué mort). Une facultative blanche n'est pas posée. Sans aucune déclaration, la saisie libre reste
possible.

> **Chaîner un *nouveau* workflow sur le *même* dossier** (pas un dossier enfant) — utile pour
> enchaîner deux processus BPMN distincts sur le même objet — n'a **pas** de `serviceTask`
> prêt à l'emploi dans le dépôt actuel : seul `createChildDossierTask` existe, et il crée
> systématiquement un `DossierRelation`. Pour ce cas, retomber sur l'appel manuel API une fois
> le workflow précédent clos (statut terminal atteint), ou écrire un delegate dédié appelant
> `ManualStartOrchestrator` avec le `dossierId` courant — ne pas contourner la règle de
> concurrence (« un seul `DossierWorkflowLink` actif par dossier », voir
> [04-dossiers.md](../specs/04-dossiers.md)).

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
> le pseudo-code de [04-dossiers.md](../specs/04-dossiers.md) (`DossierAwareTask`, chargement
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
[08-templates-documents.md](../specs/08-templates-documents.md)) : `DocumentGenerationTask`
fusionne **toutes** les variables du processus en cours (`execution.getVariables()`, `dossierFields`
compris), moins une allowlist de clés plateforme filtrée par `DocumentVariableFilter` (`dossierId`,
`dossierWorkflowLinkId`, `tenantId`, `generatedDocumentId`, `documentType`, `documentVariant`,
`documentLocale` — `dossierFields` n'y figure pas, il reste donc visible du template).

**Objet `dossier` en tant que tel** (`${dossier.reference}`, mentionné comme exemple générique
dans [08-templates-documents.md](../specs/08-templates-documents.md)) : reste **différent**
de `dossierFields` et n'existe toujours pas nativement — c'est la signalétique du dossier
(`reference`, `label`, `status`…), pas ses champs dynamiques. Une tâche qui en a besoin doit
encore faire `execution.setVariable("dossier", ...)` explicitement.

**Champ absent ou nul ⇒ la génération échoue.** Un champ absent de `dossierFields` (jamais saisi
sur ce dossier, ou nom mal orthographié dans le template) **ou** présent avec la valeur `null` ne
se résout **pas** en chaîne vide : l'évaluateur des templates (`SafeThymeleafExpressionSupport`)
lève `TemplateProcessingException` — « Unknown template property: dossierFields.x » pour une
propriété, « Unknown template variable: x » pour une variable de processus absente ou nulle — et
`documentGenerationTask` échoue sans produire de PDF ni écrire `generatedDocumentId`. Seule une
valeur réellement présente, y compris une chaîne vide `""`, s'affiche (vide dans ce dernier cas).
L'activation du template ne détecte pas ce cas (elle ne contrôle que la grammaire, sans
variables) : pour un champ facultatif, calculer la valeur en amont (voir §5, « Valeurs
facultatives ») et tester le template avec un jeu de champs réel (`POST /api/documents/preview`
ou l'outil MCP `preview_document`), y compris un jeu où les champs facultatifs manquent.

---

## 3bis. Formulaire de tâche (`flowable:formKey`) — TODO §52

Un `userTask` peut déclarer qu'il attend une saisie utilisateur avant complétion, via l'attribut
BPMN standard `flowable:formKey` (éditable dans le panel de propriétés, groupe « Flowable »). Le
moteur ne fait que stocker/restituer cette chaîne — c'est une **convention Myrmacta**
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
création, Myrmacta envoie au tiers un lien personnel vers une page publique où il remplit un
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
| `flowable:portalPieceTypes` | Facultatif (TODO §106) — types de pièces que le tiers choisit au dépôt : clés du type de dossier séparées par des virgules, 10 au plus (ex. `preuveDomicile, certificat`). Une clé que le type ne déclare pas n'est pas proposée ; sans attribut, les pièces arrivent non classées |
| `documentation` | Facultatif — consignes affichées au tiers (texte simple, littéral) |
| `assignee` / `candidateUsers` / `candidateGroups` | **Interdits** — une tâche externe n'est pas assignée en interne (elle reste complétable par un admin) |

Le déploiement est **refusé** tant que le portail n'est pas activé sur l'instance
(`APP_PORTAL_ENABLED`). Adresse absente ou invalide à la création de la tâche : **aucun incident de
process** — le lien est marqué *Non envoyé*, les participants sont prévenus et l'instructeur le
renvoie après correction (GUIDE-UTILISATEUR §11). Prévoir un **timer boundary** si le process doit
réagir à une absence de réponse (le lien expiré laisse la tâche ouverte).

Les attributs se saisissent dans l'éditeur BPMN (panneau *Flowable* d'un `userTask` :
*Tiers externe — adresse*, *Tiers externe — validité du lien*).

### Exiger l'authentification forte du tiers (TODO §104, issue #210)

`flowable:portalIdentityField="dossierField:<champ>"` sur une tâche externe exige que le tiers s'identifie par FAS
(eID, itsme, eIDAS) ou par itsme avant de voir la tâche ou d'y répondre. Le champ désigné porte son numéro de registre
national (NRN) :

- **vide** : le NRN vérifié y est écrit quand le tiers envoie sa réponse (premier enregistrement) ;
- **rempli** : c'est le NRN attendu ; une autre personne est refusée, sans savoir pourquoi.

```xml
<userTask id="avisDemandeur" name="Confirmation du demandeur"
          flowable:formKey="user-question:confirmation"
          flowable:portalRecipient="dossierField:emailDemandeur"
          flowable:portalIdentityField="dossierField:nrnDemandeur"/>
```

| Règle | Détail |
|---|---|
| Source | `dossierField:<champ>` seulement : le NRN ne transite jamais par une variable de process |
| Champ | Déclaré dans le type, de type **Texte** et **sensible** ; sinon le lien est *Non envoyé — champ d'identité…* (pas d'incident) |
| Activation | Déploiement **refusé** tant que l'authentification forte n'est pas activée (`APP_PORTAL_STRONG_AUTH_ENABLED`, [DEPLOIEMENT.md §19](DEPLOIEMENT.md#19-fournisseurs-didentité-belges-fas-itsme--todo-104)) |
| Niveau | eIDAS substantiel ou élevé ; une identité étrangère sans NRN est refusée si un NRN est attendu, acceptée sans écriture sinon |
| Avant l'identification | Le tiers ne voit que le nom de la tâche et la référence du dossier |

L'attribut se saisit à la main dans le XML : l'éditeur ne le propose pas encore.

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
palette, le panneau, la validation au déploiement et les agents. Dix-huit briques, chacune = **un seul
élément BPMN** pré-configuré (D-PALETTE-GRANULARITY). Douze briques depuis LC2 (§3sexies), treize depuis D3b (§3septies),
quatorze avec « Rechercher des dossiers » (§3octies), quinze avec la pré-vérification IA (§3nonies), dix-sept avec
« Router le courrier » et « Rattacher au fil » (TODO §113), dix-huit avec « Calculer une échéance » (TODO §115) :

| Brique | Élément | Marqueur XML reconnu |
|---|---|---|
| Générer un document | `serviceTask` | `${documentGenerationTask}` |
| Joindre le document | `serviceTask` | `${attachGeneratedDocumentTask}` |
| Appeler un connecteur | `serviceTask` | `${connectorServiceTask}` |
| Notifier | `serviceTask` | `${notificationDispatchTask}` |
| Créer un dossier enfant | `serviceTask` | `${createChildDossierTask}` |
| Rattacher au fil | `serviceTask` | `${attachToThreadTask}` |
| Router le courrier | `serviceTask` | `${routeMailTask}` |
| Calculer une échéance | `serviceTask` | `${computeDeadlineTask}` |
| Changer le statut du dossier | `serviceTask` | `${updateDossierStatusTask}` |
| Changer le statut du workflow | `serviceTask` | `${updateWorkflowLinkStatusTask}` |
| Décision DMN | `serviceTask` | `flowable:type="dmn"` |
| Évaluer une expression | `serviceTask` | `${businessExpressionTask}` |
| Enregistrer des valeurs dans le dossier | `serviceTask` | `${fieldMappingTask}` |
| Rechercher des dossiers | `serviceTask` | `${dossierSearchTask}` |
| Pré-vérification IA | `serviceTask` (asynchrone) | `${aiPrecheckTask}` |
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

**Brouillon proposé par un canal automatisé** (MCP `deploy_workflow`, `dryRun` compris ; compilation d'un
parcours ; import de configuration — TODO §93) : la forme est vérifiée de la même façon, mais une ressource
qui **existe sans être active** est acceptée — template encore en brouillon, type de dossier inactif,
connecteur de rôle service ou webhook désactivé. Le brouillon est enregistré et la réponse la signale
(`inactiveReferences` côté MCP, `warnings` pour un parcours). Une ressource inconnue, supprimée, de mauvais
rôle ou un template seulement archivé reste refusé. **La publication du brouillon reste stricte** : elle est
refusée tant que la ressource n'est pas activée. Un agent peut ainsi proposer ensemble un modèle de document
et le workflow qui l'utilise ; l'humain active le modèle, puis publie le workflow.

Les avertissements de l'éditeur (paramètre obligatoire vide, référence disparue, `${…}` saisi,
signature sans attente `signatureCompleted`, tâche externe sans question, adresse e-mail littérale dans
« Notifier ») sont **non bloquants** : ils n'empêchent ni l'enregistrement du brouillon ni l'export.
Catalogue indisponible → l'éditeur retombe sur la palette et le panneau natifs.

> **Import d'un diagramme sans `xmlns:flowable`.** Dès qu'un paramètre de brique est écrit, l'éditeur
> déclare lui-même `xmlns:flowable` sur `definitions` (D-FLOWABLE-NS) : plus de perte silencieuse du
> premier attribut.

## 3sexies. Évaluer une expression métier (TODO §75, LC2)

Jusqu'ici, brancher un process sur « montant > 5000 **et** pays = FR » était impossible : la condition de
passerelle n'accepte ni point ni appel (§3, §8). La brique **Évaluer une expression** comble ce manque sans
ouvrir de surface d'exécution : une expression d'un langage **borné** (sous-ensemble de
[JSONata](https://jsonata.org), hors JUEL) est évaluée par un `serviceTask` dédié, qui écrit le résultat dans
**une variable**, que la passerelle compare ensuite avec sa grammaire sûre habituelle.

```xml
<serviceTask id="calc" name="Dossier à fort enjeu ?" flowable:delegateExpression="${businessExpressionTask}"
             flowable:formula="dossier.fields.montant &gt; 5000 and dossier.fields.pays = &quot;FR&quot;"
             flowable:formulaResult="fortEnjeu"/>
<exclusiveGateway id="route" default="standard"/>
<sequenceFlow id="fort" sourceRef="route" targetRef="revue">
  <conditionExpression xsi:type="tFormalExpression">${fortEnjeu == true}</conditionExpression>
</sequenceFlow>
```

**Document racine** de l'expression (et seulement lui) :

| Racine | Contenu |
|---|---|
| `dossier` | `reference`, `type`, `status`, `label`, `fields` (un montant est `{amount, currency}` — `dossier.fields.montant.amount` est un **texte décimal**, le convertir avec `$number(...)` pour calculer ; une référence `{system, id, label}` ; un groupe une liste de lignes) — uniquement les champs **déclarés dans le type et non sensibles** (liste blanche) : un champ `sensitive`, ou encore stocké mais plus déclaré, n'est pas exposé (une expression ne peut pas recopier sa valeur vers une variable, une notification ou un champ lisible par un non-administrateur). Pour utiliser un champ dans une expression, il doit être déclaré dans le type de dossier |
| `connectorResult` | résultat du dernier appel de connecteur (JSON) |
| `variables` | variables de process **scalaires** (texte, nombre, booléen, date en ISO, UUID) |
| `now` | instant courant UTC en ISO, à la seconde (`$toMillis(now)`) — seule source d'horloge |

**Exemples.** Condition : `dossier.fields.montant > 5000 and dossier.fields.pays = "FR"` · calcul :
`dossier.fields.montant * 0.2` · seuil avec valeur par défaut : `$exists(dossier.fields.score) ? dossier.fields.score : 0` ·
somme de lignes : `$sum($map(connectorResult.lignes, function($l){ $l.qty * $l.prix }))` · extraction d'une réponse de
connecteur : `connectorResult.data.score`. Une expression qui renvoie un objet ou une liste est rangée en **texte
JSON** dans la variable ; booléen, nombre et texte le sont tels quels.

**Règles strictes** (refusées au déploiement avec le nom de l'activité, sauf mention « à l'exécution ») :

- **Liste blanche de fonctions.** Seules sont permises : `$abs $ceil $floor $round $sqrt $power $number $formatNumber
  $formatBase`, `$sum $count $max $min $average`, `$string $substring $substringBefore $substringAfter $lowercase
  $uppercase $length $trim $contains $split $join $pad $replace $ascii`, `$boolean $not $exists $type $keys $lookup $append
  $reverse $sort $distinct $zip $map $filter $reduce $each $sift $single $spread $merge $clone $assert $error`,
  `$toMillis $fromMillis`. **Toute autre fonction est refusée**, y compris une fonction qu'une future version de la
  bibliothèque ajouterait : `$eval`, les fonctions non déterministes (`$random`, `$shuffle`, `$now()`, `$millis()` — utiliser
  la valeur `now`), `$match` et les formats `$formatInteger`/`$parseInteger`, `$base64*` et `$encodeUrl*`/`$decodeUrl*`.
- **Sémantique littérale.** `$replace(texte, motif, remplacement [, limite])` remplace le motif **tel quel** : ni regex, ni
  `$1` interprété, motif non vide, limite positive ; `$toMillis` et `$fromMillis` n'acceptent **pas** de format
  (ISO 8601 uniquement) ; `$pad` est borné. `$ascii(texte)` retire les accents (décomposition Unicode), translittère
  `ß`→`ss`, `æ`→`ae`, `œ`→`oe`, `ø`→`o`, `ł`→`l`, passe en minuscules et ne garde que `[a-z0-9.-]`. `null` reste
  absent ; un texte sans caractère de login est refusé (pas de chaîne vide). La division `a / b` est permise.
- **Pas de littéral regex** (`/…/`) : le retour arrière d'une expression régulière Java n'est pas interruptible.
  Pas de `${…}` ni `#{…}` dans l'expression (ce n'est pas de l'UEL).
- **Pas de variable nommée `__…`** (référencée ou affectée) : ces noms sont réservés au moteur.
- **L'expression est un attribut littéral** ; le delegate **ne lit jamais** une variable de process nommée `formula` :
  une variable posée à l'exécution (complétion de tâche) ne peut pas devenir du code. `formula` et `formulaResult`
  sont obligatoires.
- **`formulaResult`** : lettres, chiffres, `_`, 60 caractères ; ne peut pas nommer une variable réservée
  (`dossierId`, `dossierFields`, `tenantId`, `connectorResult`, `dossier`, `variables`, `now`, les variables multi-instance
  `nrOfInstances`/`loopCounter`…) **ni un paramètre de brique** (`targetDossierStatus`, `documentType`…) : un delegate
  relit ces noms en repli, une expression ne doit pas pouvoir piloter une autre brique. Le nom d'attribut n'est pas
  `resultVariable` car Flowable en fait un attribut natif. Ces règles sont revérifiées **à l'exécution**.
- **À l'exécution — aucune valeur ⇒ échec explicite.** Un chemin absent (`dossier.fields.inconnu`) ne produit pas de
  valeur : la tâche échoue (« produced no value ») plutôt que de laisser la passerelle router en silence sur une variable
  absente. Donner une valeur par défaut dans l'expression. Le type du dossier doit exister (sinon on ne sait pas quels
  champs sont sensibles : échec, jamais exposition).
- **À l'exécution — les messages d'erreur sont neutres** : le gabarit de la bibliothèque sans les valeurs (jamais le
  document racine ni un nom de classe). Seul le texte de `$error("…")` / `$assert(…, "…")`, choisi par l'auteur, est restitué.

**Bornes** (propriétés `app.expression.*`, mêmes valeurs en validation, évaluation à blanc et exécution) :

| Borne | Défaut |
|---|---|
| longueur de l'expression | 2 000 caractères |
| nœuds / profondeur de l'arbre syntaxique | 500 / 40 |
| durée d'une évaluation | 250 ms |
| évaluations imbriquées : récursion **non terminale** (`1 + $f($n+1)`), chaque appel en consommant plusieurs ; une récursion terminale (`$f($n+1)` en dernier) tourne à profondeur constante, seule la durée l'arrête | 400 |
| éléments d'une liste ou d'un objet **intermédiaire** (contrôlé à chaque nœud, donc `[1..9999999]` refusé) | 10 000 |
| longueur d'un texte **intermédiaire** | 65 536 caractères |
| poids **déplié** d'une valeur intermédiaire (chaque occurrence d'un sous-arbre *partagé* comptée : `{"a":$c,"b":$c}` imbriqué n fois pèse 2ⁿ) | 1 000 000 |
| travail **cumulé** d'une évaluation (somme, à chaque nœud, de la taille de la valeur produite : borne `[1..9999].$l` et les appels natifs sur références partagées) | 3 000 000 |
| contexte d'entrée / résultat | 1 Mo / 64 Ko (≈ 3 000 lignes de 6 champs) |
| largeur de `$pad` | 10 000 caractères |
| mémoire allouée par évaluation | 64 Mo |

Les relever (`app.expression.timeout-millis`, `app.expression.max-result-bytes`…) n'est à faire qu'en connaissance
de cause : ces bornes sont la défense contre l'expression coûteuse.

**Essayer sans déployer.** `POST /api/expressions/validate` (toujours `200`, `{valid, message, position}`) et
`POST /api/expressions/evaluate` (`{expression, context}` → `{defined, value}`) — `TENANT_ADMIN` ou `WORKFLOW_ADMIN` —
ainsi que les outils MCP `validate_expression` et `evaluate_expression`. Le contexte est **fourni par l'appelant** :
rien du tenant n'est lu, aucun effet de bord.

> **Deux langages à ne pas confondre.** La condition de passerelle reste du JUEL restreint (`${fortEnjeu == true}` :
> il faut un opérateur, un booléen nu est refusé) ; l'expression métier est du JSONata borné et ne vit que dans
> `flowable:formula`. Le mapping de réponse de connecteur (§3septies) s'appuie sur ce même moteur.

## 3septies. Enregistrer des valeurs dans le dossier — mapping d'une réponse de connecteur (TODO §78, D3b)

Une réponse de connecteur n'atterrit que dans la variable `connectorResult` (`{status, body}`, `body` = texte). La brique
**« Enregistrer des valeurs dans le dossier »** (`${fieldMappingTask}`) en tire des valeurs et les écrit dans des **champs du dossier**,
par le chemin métier habituel (validation de type, normalisation, version) :

```xml
<serviceTask id="enrichir" flowable:delegateExpression="${fieldMappingTask}"
    flowable:fieldMapping="score = connectorResult.json.score&#10;total = {&quot;amount&quot;: $string(connectorResult.json.total), &quot;currency&quot;: &quot;EUR&quot;}"/>
```

- **Une ligne par champ** : `champ = expression` (JSONata borné, comme §3sexies). Le séparateur est le **premier** `=` ; 20 champs au plus.
  Dans l'éditeur, le paramètre est une zone de texte multiligne.
- **`connectorResult.json`** est le corps de la réponse lu comme JSON (s'il est valide, ≤ 256 Ko, ≤ 24 niveaux) ; `connectorResult.body` reste le texte brut.
  Un nombre qui perdrait de la précision comme double (plus de 15 chiffres significatifs) est fourni en **texte décimal exact** : `$string(connectorResult.json.total)`
  le restitue tel quel (un montant ne s'arrondit pas en silence). Une réponse non JSON ou piégée n'a simplement pas de clé `json`.
- **Tout ou rien** : toutes les expressions sont évaluées d'abord, puis écrites **en un seul patch**. Une expression sans valeur (chemin absent) ou `null` fait échouer l'activité
  en nommant le champ — donnez une valeur par défaut (`connectorResult.json.score ? connectorResult.json.score : 0`). Une valeur refusée par le type (montant, devise…) fait
  échouer l'activité, aucun champ n'est écrit.
- **Cibles déclarées seulement** : un champ non déclaré sur le type du dossier est refusé, même si le type tolère les champs inconnus. Les champs **sensibles** ne sont jamais
  lisibles par l'expression ; ils peuvent en revanche être la cible (l'écriture est tracée dans le journal d'audit : nom du champ, process et activité, jamais la valeur).
- L'écriture est attribuée à `workflow:<processInstanceId>/<activité>`. L'attribut est **littéral** (jamais une variable de process) et validé au déploiement. La variable
  `connectorResult` est **réservée** : une complétion de tâche ou un démarrage ne peut pas la forger.

## 3octies. Rechercher des dossiers (TODO §102, issue #184)

La brique **« Rechercher des dossiers »** (`${dossierSearchTask}`) compte les dossiers **en cours du même type** (ni `CLOTURE`
ni `ARCHIVE`, le dossier courant exclu) qui ont les mêmes valeurs que des champs choisis, et donne la référence du plus
ancien. Une passerelle route ensuite sur le nombre :

```xml
<serviceTask id="voisins" name="Demandes en cours du même demandeur"
             flowable:delegateExpression="${dossierSearchTask}"
             flowable:searchCriteria="email = dossierField:email"
             flowable:searchCountResult="nbDemandes"
             flowable:searchFirstReferenceResult="premiereDemande"/>
<exclusiveGateway id="doublon" default="instruire"/>
<sequenceFlow id="existe" sourceRef="doublon" targetRef="signaler">
  <conditionExpression xsi:type="tFormalExpression">${nbDemandes > 0}</conditionExpression>
</sequenceFlow>
```

- **Critères** : une ligne `champ = dossierField:<champ>` (valeur du dossier courant) ou `champ = variable:<nom>` (variable de
  process), trois au plus, tous à satisfaire. Le champ cherché doit être déclaré **cherchable** sur le type ; une source
  `dossierField:` doit être un champ **déclaré et non sensible** (sinon le nombre révélerait la valeur masquée). Sinon
  l'activité échoue en incident, rejouable après correction du type.
- **Égalité stricte**, typée comme la recherche des dossiers : un nombre se compare par valeur, une date au format ISO
  (`yyyy-MM-dd`). Une valeur vide, ou qu'aucun dossier ne peut tenir (texte dans un champ nombre), donne 0 sans erreur.
- **Sorties** : `searchCountResult` reçoit le nombre (entier), `searchFirstReferenceResult` la référence du plus ancien
  dossier trouvé, ou `null`. Deux noms distincts, jamais une variable réservée. Aucune autre valeur d'un autre dossier n'entre
  dans le process.
- Attributs **littéraux**, validés au déploiement : une variable posée à la complétion ne remplace ni les critères ni les
  sorties.
- **Ce n'est pas une garantie d'unicité** : deux exécutions simultanées peuvent toutes deux trouver 0. Pour interdire un
  doublon, déclarer le champ `unique` sur le type (§102) ; la brique sert à router (« le demandeur a-t-il d'autres demandes
  en cours ? »). L'exemple `exemples/autorisation-riverain` déclare ainsi la clé calculée `plaqueNormalisee` unique :
  la seconde demande ouverte est refusée à la création, et l'agent n'a plus à déclarer qu'il a cherché le doublon.
- La référence trouvée appartient à un autre dossier : ne l'envoyez pas à un tiers externe (notification, portail) sans
  l'avoir voulu.

## 3nonies. Pré-vérification IA d'un dossier (TODO §106, issue #211)

La brique **« Pré-vérification IA »** (`${aiPrecheckTask}`) se place **avant** la tâche d'instruction. Elle contrôle la
complétude du dossier, tire des constats de variables posées avant et pose des questions fermées à un modèle de langage
sur les champs et pièces désignés. L'agent lit et décide sur le panneau de la fiche ; la brique **ne route jamais sur
l'avis du modèle**. Spécification : [spec 48](../specs/48-preverification-ia.md). Elle n'est déployable que si
`APP_PRECHECK_ENABLED=true` (`DEPLOIEMENT.md` §20).

```xml
<serviceTask id="controleCodePostal" name="Contrôle : code postal et commune"
             flowable:delegateExpression="${businessExpressionTask}"
             flowable:formula="…" flowable:formulaResult="codePostalIncoherent"/>
<serviceTask id="preverification" name="Pré-vérification IA"
             flowable:delegateExpression="${aiPrecheckTask}" flowable:async="true"
             flowable:precheckPieces="preuveDomicile : obligatoire, lire&#10;pieceIdentite : obligatoire"
             flowable:precheckFields="nom, prenom, adresseRue, codePostal, commune"
             flowable:precheckQuestions="domicileAuNom = La preuve de domicile est-elle au nom du demandeur ?"
             flowable:precheckRules="variable:codePostalIncoherent est vrai =&gt; INCOHERENCE : Code postal incohérent avec la commune."
             flowable:precheckResult="preverification"/>
```

- **Toujours asynchrone** (`flowable:async="true"`, posé par la palette) : l'appel au modèle prend du temps, et une panne
  du fournisseur devient un **incident rejouable** au lieu de bloquer l'étape précédente.
- **Pièces** (`precheckPieces`) : une ligne par **type de pièce** déclaré sur le type de dossier ; `obligatoire` contrôle
  la présence (constat *Manquant*), `lire` envoie le texte au modèle. Un type **sensible** peut être `obligatoire`, jamais
  `lire` (refus au déploiement). Seules les pièces jointes **classées** comptent : demandez le type au dépôt (fiche,
  portail avec `flowable:portalPieceTypes`).
- **Champs** (`precheckFields`) : ceux que le modèle peut lire, jamais un champ sensible. Les champs obligatoires du type
  sont contrôlés **sans** être listés.
- **Questions** (`precheckQuestions`) : fermées (réponse oui, non ou « impossible de conclure »), une par ligne
  `clé = question`. Le modèle rend pour chacune *Conforme*, *Incohérence* ou *À vérifier*.
- **Règles** (`precheckRules`) : un constat **déterministe** tiré d'une variable posée avant (expression, DMN,
  « Rechercher des dossiers »). Conditions : `est vrai`, `est faux`, `est vide`, `n'est pas vide`, `> N`, `< N`, `= N`,
  `= "texte"`.
- **Résultat** (`precheckResult`, facultatif) : `{status, missing, inconsistent, runId}`, comptes **déterministes**
  seulement. Une passerelle peut router sur `${preverification.missing > 0}`, jamais sur un constat du modèle.
- **Placement** : si la tâche d'instruction est reprise après des compléments, faites repasser le flux par la brique
  (l'exemple riverain le fait) ; l'agent peut aussi **Relancer** depuis la fiche.
- Exemple complet : `exemples/autorisation-riverain/autorisation-riverain-preverification.bpmn20.xml` (généré par
  `generer-bpmn.py`).

## 3decies. Router le courrier entrant (TODO §113, issue #217)

La brique **Router le courrier** (`${routeMailTask}`) suit une table de décision DMN dans le process de tri d'une mail room.
Exemple complet : [`exemples/digital-mail-room/tri-courrier.bpmn20.xml`](../../exemples/digital-mail-room/tri-courrier.bpmn20.xml).

- **Avant la table : préparer les entrées.** Une entrée DMN n'accepte qu'un nom de variable nu (§7bis). On prépare donc les
  entrées par des briques « Évaluer une expression » (§3sexies), par exemple :
  - `canal` à partir de `dossier.fields.canal` ;
  - `domaineExpediteur` à partir de `$lowercase($substringAfter(dossier.fields.expediteur, "@"))`.
- **Sorties de la table**, lues par la brique :
  - `routageAction` : `OUVRIR` ou `TRIER` ;
  - `routageService` : code d'un groupe ;
  - `routageTypeDossier` et `routageProcessus` ;
  - `routageDelaiJours`, facultatif (0 à 3 650) ;
  - `routageDelaiOuvrable`, facultatif : `OUVRABLE` ou `true` saute le week-end et les jours fériés du tenant ;
    absent, `false`, `CALENDAIRE` ou `CALENDAR` comptent les jours calendaires. Toute autre valeur refuse le plan.
- **Paramètre de la brique** : `groupeTri`, le code du groupe de la file de tri (littéral).
- **`OUVRIR`.** La brique :
  - crée le dossier métier lié au courrier (`LINKED`), avec les champs du courrier qui portent le même nom ;
  - l'ouvre, puis y démarre le process avec le groupe `routageService` comme destinataire ;
  - renseigne les champs `service` et `echeance` du courrier, si son type les déclare ;
  - écrit la même échéance dans le champ date que le type du dossier métier désigne comme échéance (TODO §115), ce qui
    déclenche le rappel et l'escalade de la plateforme ;
  - pose `routageResultat = ROUTE`, `dossierMetierId` et `dossierMetierReference`.
- **Sinon** : aucune règle, action `TRIER`, ou valeur hors catalogue (groupe inconnu, type inactif, process non déployé,
  délai invalide, champ obligatoire du type cible absent du courrier).
  - La brique pose `routageResultat = A_TRIER` et `routageMotif`, la raison en clair.
  - Elle donne au process de tri le groupe `groupeTri` : la tâche de tri qui suit lui est destinée.
  - Une erreur de configuration n'arrête jamais le courrier.
- **Aiguiller ensuite.** Après la brique, une passerelle `${routageResultat == 'ROUTE'}` sépare les deux chemins.
- **Rendre le début du tri asynchrone.** Placez `flowable:async="true"` sur la première étape du tri. Un courrier routé
  traverse sinon tout le process pendant son démarrage, avant que celui-ci ne soit confirmé.
- **Ouvrir le courrier.** Un courrier créé par un connecteur naît `DRAFT` : ouvrez-le (`OUVERT`), puis mettez-le en cours
  (`EN_COURS`) avant de pouvoir le clôturer.

### Rattacher une réponse à son fil, attendre un courrier (TODO §113, lots 3a et 3b)

- **Rattacher au fil.** La brique **Rattacher au fil** (`${attachToThreadTask}`, sans paramètre) se place en tête du tri, avant
  la table de routage.
  - **Recherche du fil.** Elle cherche le fil du courrier dans l'index des e-mails reçus et envoyés (`In-Reply-To`, puis
    `References`, puis `conversationId` Graph). En secours, elle lit une référence de dossier dans l'objet
    (`DOS-FACTURE-2026-000007`). Cette référence ne compte que si ce dossier a déjà échangé un e-mail avec le domaine de
    l'expéditeur.
  - **Conditions du rattachement :**
    - l'expéditeur est attesté ;
    - son domaine est celui de l'interlocuteur du fil ;
    - le dossier du fil est encore ouvert.
  - **Effets du rattachement :**
    - un lien `LINKED` vers le dossier du fil ;
    - la copie des pièces (antivirus compris) ;
    - l'avertissement des participants ;
    - le message `courrierRecu`, remis à **toutes** les exécutions de ce dossier qui l'attendent.
  - **Variables posées :** `filResultat` vaut `RATTACHE` ou `NOUVEAU`, avec `filDossierId`, `filDossierReference` et, s'il
    n'y a pas eu rattachement, `filMotif`.
  - **Suite du tri :** une passerelle `${filResultat == 'RATTACHE'}` clôt le courrier rattaché. Les autres vont à la table de
    routage.
- **Attendre un courrier.** Un process métier qui veut réagir à une réponse attend le message `courrierRecu`, par un
  événement intermédiaire ou en bordure. Le message apporte `courrierId` et `courrierReference`. Exemple, non interruptif,
  pris de [`traiter-facture.bpmn20.xml`](../../exemples/digital-mail-room/traiter-facture.bpmn20.xml) :

  ```xml
  <message id="courrierRecuMsg" name="courrierRecu"/>
  …
  <boundaryEvent id="courrierRecu" attachedToRef="traiterFactureTache" cancelActivity="false">
    <messageEventDefinition messageRef="courrierRecuMsg"/>
  </boundaryEvent>
  ```

  Sans attente modélisée, rien ne se perd : la réponse est rangée dans le dossier, et ses participants sont prévenus.
- **E-mails envoyés.** Les e-mails de « Notifier » partis d'un dossier sont inscrits dans l'index par leur Message-ID : une
  réponse du destinataire rejoint ce dossier.
- **Workflow « réponse » du type (lot 3b, D-MAILROOM-REPLY-WORKFLOW).** Le type du dossier du fil peut déclarer un process
  à lancer à chaque réponse rattachée (Admin → Types de dossier, panneau « Workflow réponse », ou
  `PUT /api/dossier-types/{code}/reply-workflow`). Un dossier ne porte qu'un workflow actif à la fois : on choisit donc l'un
  de deux modes.
  - **`SUR_DOSSIER`** : le process démarre sur le dossier du fil, seulement si aucun process n'y tourne (un dossier traité,
    pas encore clôturé, que la réponse relance). Si un process tourne, il faut modéliser l'attente `courrierRecu` ci-dessus.
  - **`DOSSIER_ENFANT`** : chaque réponse ouvre un dossier enfant (`PARENT_CHILD`, libellé « Réponse <référence> ») du type
    déclaré, où le process démarre, en parallèle du process métier. Le type enfant ne doit pas avoir de champ obligatoire
    sans valeur par défaut, et la configuration du workflow doit accepter le statut `OUVERT`.
  - **Le process reçoit** `courrierId` et `courrierReference`. S'il déclare ses variables de démarrage (§111), il doit
    déclarer ces deux-là.
  - **Variables posées dans le tri, après un rattachement :** `filReponseWorkflow` vaut `AUCUN` (le type n'en déclare pas),
    `DEMARRE_SUR_DOSSIER`, `DEMARRE_SUR_ENFANT` (avec
    `filReponseDossierId`) ou `NON` (avec la raison dans `filReponseMotif` : process non déployé, process déjà actif,
    statut refusé…). Le rattachement a lieu dans tous les cas.
  - Exemple : [`traiter-reponse.bpmn20.xml`](../../exemples/digital-mail-room/traiter-reponse.bpmn20.xml) et
    [`config/reponse-facture.json`](../../exemples/digital-mail-room/config/reponse-facture.json).

### Échéance et minuteur — course « traité ou échu » (TODO §115, lot 4b)

Deux mécanismes, au choix ou ensemble :

- **Échéance de la plateforme** (lot 4a) : le type de dossier désigne un champ date ; un job rappelle puis escalade, sans
  rien modéliser (GUIDE-UTILISATEUR, « Échéance »).
- **Minuteur dans le process** (D-DEADLINE-TIMER) : pour qu'un process réagisse lui-même à l'échéance.
  - La brique **Calculer une échéance** (`${computeDeadlineTask}`) prend des paramètres littéraux :
    - `champEcheance` : un champ date non sensible du dossier, relu au moment de l'exécution ;
    - `delaiJours` : aujourd'hui + N jours, utilisé si le champ est absent ou vide ;
    - `rappelJours` : nombre de jours avant l'échéance (0 par défaut) ;
    - `delaiOuvrable` et `rappelOuvrable` : `CALENDAIRE` (défaut) ou `OUVRABLE`.
  - Elle pose `echeance` (`AAAA-MM-JJ`, pour l'affichage) et deux variables réservées : `echeanceRappel` (`rappelJours`
    avant, à minuit dans le fuseau du tenant) et `echeanceDepassee` (lendemain calendaire, même minuit : le jour du
    terme est encore dans les temps). Un rappel ouvrable ne décale pas ce minuteur : il peut sonner un samedi alors
    que l'escalade de la plateforme attend le lundi.
  - Sans date ni délai, la brique échoue : un incident est visible en supervision.
  - Un minuteur porte ensuite `${echeanceDepassee}` ou `${echeanceRappel}` dans son `timeDate`, et rien d'autre. Ni
    `timeDuration`, ni `timeCycle`, ni autre variable, ni appel de méthode.
  - Les deux instants sont réservés : ni un démarrage ni une complétion de tâche ne peut les poser, donc repousser le
    minuteur.
  - Exemple, pris de [`traiter-reponse.bpmn20.xml`](../../exemples/digital-mail-room/traiter-reponse.bpmn20.xml) :

  ```xml
  <serviceTask id="calculerEcheance" flowable:delegateExpression="${computeDeadlineTask}" flowable:delaiJours="10"/>
  <userTask id="traiterReponseTache" name="Traiter la réponse"/>
  <boundaryEvent id="reponseEchue" attachedToRef="traiterReponseTache" cancelActivity="true">
    <timerEventDefinition><timeDate>${echeanceDepassee}</timeDate></timerEventDefinition>
  </boundaryEvent>
  ```

  - Interrompant (`cancelActivity="true"`), le minuteur retire la tâche et suit sa branche. Non interrompant, avec
    `${echeanceRappel}`, il ouvre une tâche de relance à côté de la tâche principale.
  - Jours calendaires (jours ouvrables : issue [#8](https://github.com/superjojo79/Myrmacta/issues/8)).

## 4. Instancier / appeler un service externe

Le mécanisme est le **système de connecteurs** ([06-connecteurs.md](../specs/06-connecteurs.md)), jamais
un appel HTTP écrit en dur dans un delegate ad hoc.

### Étape 1 — créer une instance de connecteur (une fois, côté admin)

Écran d'admin des connecteurs (`ConnectorsAdminView.vue`) : choisir un type (`ConnectorDefinition.getType()`,
ex. `flowable-signal`, ou un connecteur custom ajouté au classpath — voir
[06-connecteurs.md](../specs/06-connecteurs.md) « Ajout d'un connecteur custom »), puis saisir la
configuration dans le **formulaire généré** depuis `getConfigSchema()` du type
(voir [06-connecteurs.md](../specs/06-connecteurs.md) « Écran d'admin » et
[docs/guides/GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md) §4). La validation métier reste côté backend
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

### Construire la requête dans le BPMN : `flowable:connectorRequest` (TODO §112, issue #260)

`connectorInput` ne vient que d'une variable : posée au démarrage ou à la complétion d'une tâche, donc par un utilisateur.
Pour appeler une API avec des données du dossier ou la réponse d'un appel précédent, la brique « Appeler un connecteur »
accepte un attribut **littéral** `flowable:connectorRequest` : une expression métier bornée (JSONata, même langage, même
racine et mêmes règles que §3sexies : `dossier` aux champs déclarés non sensibles, `connectorResult` avec `json`,
`variables`, `now`) qui produit **un objet**, passé tel quel au connecteur.

```xml
<serviceTask id="appelIam" name="Créer le compte IAM" flowable:delegateExpression="${connectorServiceTask}"
    flowable:async="true" flowable:connectorInstanceId="7d1c…"
    flowable:connectorRequest="{'method': 'POST', 'path': '/users', 'headers': {'Content-Type': 'application/json'},
      'body': {'username': dossier.fields.login, 'employeeNumber': variables.matricule}}"/>
```

- **Il prime sur la variable `connectorInput`**, qui est alors ignorée. Il n'est jamais lu depuis une variable, est validé au
  déploiement (syntaxe, fonctions permises, 2 000 caractères, sans `${`) et revalidé à l'exécution.
- Avec `connectorRequest`, **`flowable:connectorInstanceId` doit être écrit dans le BPMN** : la requête ne part jamais vers
  une instance choisie par une variable.
- Appelée depuis un process, une instance **avec `baseUrl` n'accepte que `connectorRequest`** : la variable `connectorInput`
  y est refusée. Sans `baseUrl`, elle reste acceptée, et l'en-tête d'authentification part vers l'URL qu'elle fournit.
- **`dossier.fields` et `variables` ne sont pas des données de confiance par défaut.** Toute personne qui a le droit EDIT
  modifie un champ ordinaire du dossier ; une variable peut être posée au démarrage ou à la complétion d'une tâche qui n'est
  pas une `user-question`. Pour un identifiant technique (cible d'un `PUT` / `DELETE`), déclarez le champ
  **`managedByWorkflow`** sur le type de dossier (TODO §126) : il n'est écrit que par « Enregistrer des valeurs dans le
  dossier », et reste lisible via `dossier.fields` dans les expressions. Sans ce drapeau, retenez l'identifiant dans une
  variable écrite par le BPMN, remise à `null` après la dernière tâche à formulaire libre.
- Le résultat doit être un objet : un texte, une liste ou une expression sans valeur fait échouer l'activité en la nommant.
  Une clé dont la valeur est absente (champ vide ou **sensible**) disparaît de l'objet : un champ sensible n'est jamais envoyé.
- Les apostrophes délimitent les textes JSONata (`'POST'`) : pas besoin d'échapper des guillemets dans l'attribut XML.
  Un retour à la ligne est permis (zone de texte multiligne dans l'éditeur).

Entrée lue par `http-rest` en rôle SERVICE :

| Clé | Rôle |
|---|---|
| `method` | `GET` (défaut), `POST`, `PUT`, `PATCH`, `DELETE`. `PATCH` porte un corps, comme `POST` et `PUT` (TODO §116) |
| `path` | Chemin relatif à l'URL de base de l'instance (`baseUrl`), commençant par un seul `/`. Sans `//`, segment `.` ou `..` (y compris `..;x`), `?`, `#`, espace, `\`, ni encodage de `/ \ . ; %` ou d'un caractère de contrôle. Peut contenir des marqueurs `{nom}` |
| `pathParams` | Valeur de chaque marqueur `{nom}`, encodée comme **un seul segment** (`a b?` devient `a%20b%3F`). Chaque marqueur a sa valeur et chaque valeur son marqueur ; ni vide, ni faite seulement de points une fois retirés un paramètre `;x` et les espaces (`..`, `..;x`, `.. `), ni `/`, `\` ou caractère de contrôle. 20 au plus |
| `query` | Paramètres de requête, encodés (`$filter` reste lisible). Une liste répète le paramètre. 50 au plus |
| `headers` | En-têtes : noms conformes à RFC 7230, valeurs sans caractère de contrôle. Le `Host` fourni est ignoré, et en mode OAuth2 l'`Authorization` fourni aussi ; les en-têtes de cadrage et de connexion (`Transfer-Encoding`, `Content-Length`, `Connection`, `Expect`, `Upgrade`, `TE`, `Trailer`, `Keep-Alive`, `Proxy-*`) sont refusés |
| `body` | Corps : un objet est envoyé en JSON (ajouter `Content-Type` dans `headers`), un texte tel quel. Refusé pour `GET` et `DELETE` |
| `url` | URL absolue : **seulement** pour une instance sans `baseUrl` (comportement d'avant) ; refusée sinon |

Le résultat reste `connectorResult = {status, body}` : un statut HTTP en erreur **ne fait pas échouer** l'activité. Le
processus le contrôle (brique « Évaluer une expression » : `connectorResult.status >= 200 and connectorResult.status < 300`)
et route vers une validation humaine. Une panne réseau ou un délai dépassé, en revanche, fait échouer l'activité : rendre
l'appel **asynchrone** (`flowable:async="true"`) pour qu'elle devienne un incident rejouable sans bloquer l'utilisateur qui a
complété la tâche précédente. Rendez aussi asynchrone l'activité qui **suit** l'appel (le contrôle du statut) : un échec en
aval, par exemple une réponse sans l'identifiant attendu ou un conflit d'écriture du dossier, relance alors cette activité et
non l'appel, et un `POST` n'est jamais rejoué sans décision humaine.

Une étape « connecteur » d'un **parcours** ne porte pas de `connectorRequest` : ne la pointez pas vers une instance à
`baseUrl`, qui refuserait l'appel à l'exécution.

### Orchestration technique : plusieurs applications et des validations humaines

Schéma type, livré et rejoué par un test dans [exemples/onboarding-collaborateur](../../exemples/onboarding-collaborateur/README.md)
(TODO §126) :

1. une instance `http-rest` **par application**, « service seul » (`webhookEnabled` décoché) et avec `baseUrl` ;
2. les champs qui recevront les identifiants techniques sont déclarés **`managedByWorkflow`** sur le type de dossier ;
3. pour chaque appel : « Appeler un connecteur » (asynchrone, `connectorRequest`), « Évaluer une expression » sur le statut,
   passerelle ; en cas de succès, « Enregistrer des valeurs dans le dossier » écrit le champ géré
   (`matriculeSirh = connectorResult.json.id`). L'appel suivant et la compensation relisent `dossier.fields…`, jamais une
   variable posée au démarrage ou à une tâche à formulaire libre ;
4. les validations sont des `user-question` dont la réponse oriente une passerelle (approuver, corriger, refuser ; accès
   étendus ou non) : leurs variables sont déclarées ;
5. un échec mène à une tâche « incident technique » (réessayer, abandonner) ; l'abandon **compense** par des `DELETE` sur les
   champs gérés qui existent.

Réessayer rejoue la requête : prévoir côté application une clé d'idempotence ou une recherche préalable si un `POST` a pu
aboutir malgré une réponse en erreur.

Avec `document-storage`, `RETRIEVE`, `EXISTS` et `DELETE` n'acceptent que ce que le connecteur a stocké (`STORE`) pour le
dossier du process, dans ce workflow ou un autre du même dossier. Une pièce du dossier se lit par les pièces jointes, pas
par ce connecteur (TODO §101).

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
| `documentType` | oui | `templateKey` — résout le template actif (voir [08-templates-documents.md](../specs/08-templates-documents.md)) |
| `documentVariant` | non | `variantKey` (ex. `"premium"`, `"B2B"`) — résolution en cascade si absent |
| `documentLocale` | non | langue du destinataire (ex. `"fr"`, `"nl"`) — résolution en cascade si absent |

Toutes les autres variables du processus au moment de l'exécution sont transmises au template
Thymeleaf (voir §3 pour la mécanique exacte et l'allowlist de rendu — pas de `T()`, `#beans`,
`new`, cf. décision **D-40**). Variable écrite : `generatedDocumentId` (référence de stockage). Elle est **réservée**
(issue #201, TODO §97) : un démarrage, une complétion de tâche, le portail, MCP, un callback ou un signal ne peuvent pas la
poser — seule cette brique l'écrit, et « Joindre le document » ne rattache donc que le PDF réellement généré.

#### Ce qu'un template peut écrire dans `${…}`

Le contenu des accolades n'est **pas** du SpEL ni de l'OGNL : c'est une grammaire restreinte
(`SafeThymeleafExpressionSupport`), détaillée dans
[08-templates-documents.md](../specs/08-templates-documents.md) « Grammaire des expressions ».
En résumé :

| Accepté dans `${…}` | Exemple |
|---|---|
| chemin de propriétés (clé de map, getter `getX`/`isX` public, accesseur de record) | `${dossierFields.montant.amount}` |
| littéral texte, nombre, `true`/`false`/`null` | `${'VALIDE'}`, `${42}`, `${-1.5}` |
| `#vars.<chemin>` | `${#vars.dossierFields.reference}` |
| méthode **déclarée** de `#strings`, `#dates`, `#numbers`, `#lists`, arguments = toute expression acceptée | `${#strings.toUpperCase(#strings.trim(nom))}` |

**Refusé dans `${…}`** (template refusé à l'activation, `Unsupported Thymeleaf expression`) :
comparaison (`${statut == 'VALIDE'}`), `and`/`or`, ternaire (`${a ? 'x' : 'y'}`), indexation
(`${map['x']}`), tout appel de méthode sur une valeur (`${map.get('x')}`, `${map.containsKey('x')}`),
chaînage après un utilitaire (`${#strings.trim(x).length}`), en plus de `T()`, `new`, `#beans`,
`#request`.

**Condition dans un template** : passer par un utilitaire —
`th:if="${#strings.equals(statut, 'VALIDE')}"`. Quand la condition est fausse, l'élément et tout
son contenu ne sont **pas évalués** (un `th:text`/`th:each` à l'intérieur peut référencer une
variable absente sans faire échouer le rendu) ; en revanche la variable testée par le `th:if`
lui-même doit exister et être non nulle.

#### Valeurs facultatives : les calculer en amont

Toute variable ou propriété **absente ou nulle** référencée par un `${…}` évalué fait échouer
`documentGenerationTask` (voir §3) — `#strings.isEmpty(...)`, `#strings.defaultString(...)` ou
l'opérateur Thymeleaf `?:` n'y changent rien, l'argument est résolu avant l'appel. Pour un champ
facultatif, poser **avant** la génération une brique « Évaluer une expression » (§3sexies) qui
fournit une valeur par défaut, et référencer la variable produite dans le template :

```xml
<serviceTask id="calcComplement" name="Complément d'adresse (défaut vide)"
             flowable:delegateExpression="${businessExpressionTask}"
             flowable:formula="$exists(dossier.fields.complement) ? dossier.fields.complement : &quot;&quot;"
             flowable:formulaResult="complementAdresse"/>
<!-- puis documentGenerationTask ; dans le template : th:text="${complementAdresse}" -->
```

La valeur par défaut ne doit pas être `null` (une formule peut produire `null`, qui ferait échouer
le rendu). Rappel : l'expression ne voit que les champs **déclarés et non sensibles** du type de
dossier (§3sexies) — un champ sensible facultatif ne peut pas être traité ainsi.

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

Myrmacta ne déclare **aucun intervenant dans le BPMN lui-même** — ni `assignee`, ni
`candidateGroups` (retiré explicitement du scope de l'éditeur, décision **D-EDIT-SCOPE**, voir
§1). Trois mécanismes distincts couvrent le besoin :

### a) Assignation d'une tâche humaine (`userTask`) — à l'exécution, pas dans le diagramme

Un `userTask` sans `assignee` reste non assignée jusqu'à une action explicite : **Réassigner** ou **M'assigner** sur l'écran de
suivi du dossier (`docs/guides/GUIDE-UTILISATEUR.md` §3), ou **Prendre** dans `/tasks` pour une tâche de groupe. Rien à écrire
dans le diagramme pour ce mécanisme.

**Groupes (TODO §114).**

- **Groupe candidat littéral.** Un `userTask` peut nommer un groupe candidat littéral, `flowable:candidateGroups="COMPTABILITE"`
  (code d'un groupe d'**Admin → Groupes**). Une expression y est refusée.
- **Groupe du lien de workflow (D-GROUP-LINK).** Une tâche sans assigné ni candidat reçoit le **groupe destinataire du
  lien de workflow**, s'il en a un. C'est la brique « Router le courrier » (§3decies) qui le pose, sur le process qu'elle
  démarre. Un process démarré par le routage n'a donc rien à déclarer pour que ses tâches aillent au bon service.
- **Visibilité.** Les membres du groupe voient la tâche dans **Tâches** et peuvent la prendre, s'ils peuvent modifier le
  dossier.

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
| `notificationTo` | selon canal | destinataire email : adresse littérale, ou en attribut `flowable:notificationTo` une source `dossierField:<champ>` / `variable:<nom>` (voir ci-dessous) |
| `notificationSubject` / `notificationBody` | selon canal | contenu email, littéral (exclus si `notificationTemplate` est posé) |
| `notificationTemplate` | non | **attribut seulement** : clé d'un gabarit de notification actif, qui fournit l'objet et le corps (voir « Gabarit de notification » ci-dessous) |
| `notificationVariant` | non | **attribut seulement** : variante du gabarit, littérale ou `variable:<nom>` ; exige `notificationTemplate` |
| `notificationLocale` | non | **attribut seulement** : langue du gabarit (ex. `fr`), littérale ou `variable:<nom>` ; exige `notificationTemplate` |
| `notificationUserId` | pour `BROWSER` | **un seul** utilisateur — pour notifier plusieurs intervenants (ex. tous les participants), utiliser un `multiInstanceLoopCharacteristics` Flowable standard sur cette tâche, itérant sur une variable liste, ou combiner avec le mécanisme (b) qui couvre déjà le cas générique |
| `notificationWebhookConfigId` | pour `WEBHOOK` | UUID de la config webhook tenant |
| `notificationPayload` | non | `Map` libre, transmis tel quel au canal |
| `notificationAttachment` | non | **attribut seulement** : source `variable:<nom>` / `dossierField:<champ>` du PDF à joindre à l'e-mail (voir ci-dessous) |
| `notificationAttachmentName` | non | **attribut seulement** : nom du fichier joint, ex. `autorisation.pdf` |

**Destinataire lu dans le dossier (TODO §93).** Écrit dans l'attribut de la brique,
`flowable:notificationTo="dossierField:email"` envoie à la valeur **actuelle** du champ `email` du dossier
(relue au moment de l'envoi, pas l'instantané `dossierFields` du démarrage) ; `"variable:adresse"` lit la
variable de process `adresse`, qu'une tâche « Évaluer une expression » peut calculer en amont. Même grammaire
que `flowable:portalRecipient` (nom simple, jamais évalué). La source n'est lue que dans l'**attribut** du
BPMN : une variable de process `notificationTo` qui contiendrait `dossierField:…` n'est jamais résolue (un
utilisateur ne peut pas rediriger l'envoi vers un autre champ).

La valeur obtenue — comme une variable `notificationTo` posée au démarrage ou à la complétion d'une tâche — doit
être **une seule adresse** `local@domaine` (ni liste, ni « Nom <adresse> », ni retour à la ligne). Absente, vide,
non textuelle ou mal formée, l'étape **échoue** ; le message nomme la source, jamais l'adresse. Une adresse écrite
en dur dans l'attribut reste libre.

> **Où se voit l'échec ?** La brique « Notifier » est synchrone par défaut : l'échec annule l'action qui l'a
> exécutée — la complétion de la tâche précédente ou le démarrage — avec une réponse **422** qui nomme le champ en
> cause ; il suffit de corriger le dossier et de recommencer. Pour obtenir plutôt un **incident** (tâche suivie par
> le moteur, rejouable) sans bloquer l'utilisateur, cocher **asynchrone** (`flowable:async="true"`) sur la brique.
> L'incident est alors visible sur la fiche dossier (bandeau « Étapes en échec », étape marquée dans le diagramme)
> et dans **Admin → Incidents** ; un administrateur le **rejoue** une fois la cause corrigée (TODO §94).
>
> **Champ sensible.** Une source peut désigner un champ marqué sensible : l'adresse part uniquement à cette adresse
> et n'apparaît dans aucun message d'erreur, mais elle est conservée en clair dans la file d'envoi (outbox), comme
> pour l'adresse d'une tâche externe du portail.

**Pièce jointe (issue #183, TODO §96).** `flowable:notificationAttachment="variable:generatedDocumentId"` joint à
l'e-mail le PDF produit par « Générer un document » ; `dossierField:<champ>` lit la référence dans un champ du
dossier. Le document doit être un **PDF produit et rattaché au dossier courant** : placer « Joindre le document »
**avant** « Notifier ». Une référence vers un autre dossier, une pièce déposée ou un fichier quelconque est refusée.
`flowable:notificationAttachmentName` (facultatif) fixe le nom vu par le destinataire (lettres, chiffres, espace,
`. _ - ( ) '`, 100 caractères au plus) ; à défaut, le libellé du document ; `.pdf` est ajouté s'il manque. Une seule
pièce jointe, 7 Mio au plus (`app.notification.email.attachment-max-bytes`). Les deux attributs ne sont lus que dans le
BPMN : une variable de process de même nom est ignorée. Avec une pièce jointe, `flowable:notificationTo` et
`flowable:notificationChannels` (avec `EMAIL`) doivent aussi être écrits dans le BPMN : le déploiement le refuse sinon, et
une variable `notificationTo` ne peut jamais rediriger le document. Document absent, vide, non
PDF ou trop gros : l'étape échoue comme pour un destinataire invalide (422 ou incident si asynchrone), le message
nomme la source. Un document supprimé entre l'émission et l'envoi fait échouer l'envoi (nouvelles tentatives, puis
abandon visible sur la fiche dossier). Le connecteur `smtp` n'envoie pas de pièce jointe.

```xml
<serviceTask id="mailDecision" name="E-mail de décision" flowable:async="true"
             flowable:delegateExpression="${notificationDispatchTask}"
             flowable:notificationChannels="EMAIL"
             flowable:notificationTo="dossierField:email"
             flowable:notificationSubject="Votre autorisation"
             flowable:notificationBody="Vous trouverez votre autorisation en pièce jointe."
             flowable:notificationAttachment="variable:generatedDocumentId"
             flowable:notificationAttachmentName="autorisation.pdf"/>
```

Le contenu littéral peut référencer les champs dynamiques exposés selon la méthode du §3
(`notificationBody` reste une simple variable process — construire la chaîne avec une expression
FEEL/UEL Flowable ou une tâche amont qui la compose). Pour un texte qui change sans redéployer le process, qui cite le
dossier ou une variable, ou qui existe en plusieurs langues, utiliser un **gabarit**.

#### Gabarit de notification (issue #19, TODO §123)

Un gabarit est géré dans **Admin → Gabarits de notification** (rôle `TENANT_ADMIN`), par l'API
`/api/notification-templates` ou, en brouillon, par les outils MCP `define_notification_template`,
`list_notification_templates` et `preview_notification_template`. Il porte une clé, une variante et une langue facultatives,
un objet, un corps texte et un corps HTML facultatif ; son cycle est `DRAFT → ACTIVE → ARCHIVED`, une seule version active
par combinaison. **L'activation est un geste humain** (jamais l'assistant). La brique le désigne par sa clé :

```xml
<serviceTask id="accuse" name="E-mail : accusé de réception" flowable:async="true"
             flowable:delegateExpression="${notificationDispatchTask}"
             flowable:notificationChannels="EMAIL"
             flowable:notificationTo="variable:mailFrom"
             flowable:notificationTemplate="accuse-reception-courrier"
             flowable:notificationLocale="variable:langue"/>
```

Règles, contrôlées **au déploiement** (le refus nomme l'attribut et l'activité) :

- `notificationTemplate`, `notificationVariant` et `notificationLocale` ne sont lus que dans l'**attribut** du BPMN
  déployé : une variable de process du même nom est ignorée (un utilisateur ne peut pas désigner un autre gabarit).
- `notificationTemplate` **exclut** `notificationSubject` et `notificationBody` (les deux à la fois sont refusés).
- Il exige `notificationChannels="EMAIL"`, **seul** (un autre canal en plus est refusé), et `notificationTo` écrit dans le
  BPMN. Le gabarit n'a pas d'effet sur `WEBHOOK` ni `BROWSER`.
- `notificationVariant` et `notificationLocale` exigent `notificationTemplate` ; leur valeur est une clé littérale
  (lettres minuscules, chiffres, tirets) ou `variable:<nom>`. Une variable absente ou vide vaut « pas de variante » ou « pas
  de langue » : la cascade de résolution `(clé, variante, langue)` s'applique, comme pour les templates de document.
- Le gabarit n'a **pas** besoin d'exister au déploiement (il peut arriver après, par la promotion de configuration) : sa
  clé n'est vérifiée qu'à l'envoi. Sans version active pour la combinaison, l'étape échoue en nommant la clé.
- Avec un gabarit au corps HTML, l'e-mail part en `multipart/alternative` (texte et HTML) ; sans corps HTML, en texte seul.
  Il porte toujours `Auto-Submitted: auto-generated`.

**Ce qu'un gabarit peut écrire.** L'objet et le corps texte utilisent l'inline échappé `[[${…}]]` ; le corps HTML utilise
`th:text="${…}"` et quelques attributs `th:`. Le contenu des accolades suit la grammaire des templates de document (D-40,
[08-templates-documents.md](../specs/08-templates-documents.md) « Grammaire des expressions ») : chemin de propriétés,
littéral, utilitaires `#strings`, `#dates`, `#numbers`, `#lists`. Le modèle offert au gabarit est celui des expressions
métier (§3sexies), sans `connectorResult` :

| Racine | Contenu |
|---|---|
| `dossier` | `reference`, `type`, `status`, `label`, `fields.<champ>` — champs **déclarés dans le type et non sensibles** seulement |
| `variables` | toutes les variables de process **scalaires** (texte, nombre, booléen, date en ISO, UUID) |

```text
Objet  : Accusé de réception : [[${variables.accuseObjet}]]
Corps  : Votre courrier est enregistré sous la référence [[${dossier.reference}]].
HTML   : <p th:if="${dossier.status} == 'OPEN'">Reçu le <span th:text="${variables.dateReception}">date</span></p>
```

- **La comparaison se place hors des accolades** : `th:if="${dossier.status} == 'OPEN'"`, pas `${dossier.status == 'OPEN'}`
  (refusé à l'activation, comme `and`, `or`, le ternaire, l'indexation et l'appel de méthode dans les accolades).
- **Attributs `th:` permis dans le corps HTML** : `th:text`, `th:if`, `th:unless`, `th:each`, `th:with`, `th:switch`,
  `th:case`, `th:remove` et l'élément `th:block`. Pas de `th:utext`, `th:href`, `th:attr`, `th:style`, `th:src`, `th:inline`,
  ni d'inline non échappé `[(…)]` : une variable ne fabrique ni une URL ni un attribut. Les liens restent littéraux
  (`http`, `https`, `mailto`).
- **Politique HTML** : balises de mise en forme seulement (pas d'image, de `<style>`, de `<script>` ni de formulaire), pas
  d'attribut `on*`, un attribut `style` sans `url(`, `expression(`, `@import` ni `javascript:`. Un corps hors politique est
  **refusé à l'activation** avec la règle violée, jamais nettoyé en silence ; le HTML rendu repasse encore par un nettoyeur
  avant l'envoi.
- **Rendu strict.** Une variable ou une propriété **absente ou nulle** fait échouer le rendu, comme pour un document : l'étape
  échoue (incident si la brique est asynchrone, refus 422 de la complétion ou du démarrage sinon) et **aucun e-mail n'est
  écrit** dans l'outbox. Le message nomme le gabarit et l'expression, jamais une valeur. Le rendu échoue aussi si l'objet
  rendu dépasse 255 caractères, est vide, ou si le message rendu dépasse 1 Mio.
- **Valeurs facultatives : les calculer en amont.** Comme pour la génération de documents (§5), poser avant « Notifier » une
  brique « Évaluer une expression » (§3sexies) qui donne une valeur par défaut à tout ce qui peut manquer, puis citer cette
  variable. De même pour borner une valeur longue : `$substring(dossier.fields.objet, 0, 200)` garde l'objet d'un courrier
  sous la limite de l'objet d'e-mail. Le gabarit de l'accusé de réception de la mail room
  (`exemples/digital-mail-room/config/gabarit-accuse-reception.json`) calcule ainsi `accuseObjet` et `dateReception`.

> **Attention aux données sensibles.** Un champ `sensitive` du type de dossier n'est jamais exposé : `${dossier.fields.<champ>}`
> échoue comme une propriété absente. Le gabarit voit en revanche **toutes** les variables de process scalaires. Une variable
> qui recopierait la valeur d'un champ sensible (réponse de formulaire de tâche, valeur posée au démarrage, résultat de
> connecteur copié dans une variable) est donc rendue dans l'e-mail, et l'e-mail part en clair. Ne pas nommer ni citer dans un
> gabarit une variable qui porte une donnée sensible, et ne jamais recopier un champ sensible dans une variable de process.
> Le contenu rendu, comme l'adresse du destinataire, est conservé en clair dans l'outbox jusqu'à son envoi.

---

## 7. Catalogue des `serviceTask` livrés

Référence rapide — `delegateExpression`, variables lues (entrée) et écrites (sortie) :

| `delegateExpression` | Rôle | Entrée | Sortie |
|---|---|---|---|
| `${updateDossierStatusTask}` | change `Dossier.status` | `dossierId` (réservée), `targetDossierStatus` (valeur de `DossierStatus`) | — |
| `${updateWorkflowLinkStatusTask}` | change le statut métier de l'exécution courante (`DossierWorkflowLink.status`, validé contre `WorkflowTypeConfig.statusDefinitions`) | `dossierWorkflowLinkId` (réservée), `targetWorkflowStatus` (code déclaré dans la config workflow) | — |
| `${documentGenerationTask}` | génère un PDF depuis un template | `documentType`, `documentVariant`?, `documentLocale`?, + toute variable utile au template | `generatedDocumentId` |
| `${attachGeneratedDocumentTask}` | rattache le PDF généré au dossier (`DossierElement`) | `dossierId` (réservée), `generatedDocumentId` | — |
| `${connectorServiceTask}` | invoque un connecteur en rôle SERVICE | `connectorInstanceId`, `connectorInput`? ou attribut littéral `connectorRequest` (expression qui produit l'entrée, prime sur `connectorInput` — §4) | `connectorResult`? |
| `${notificationDispatchTask}` | envoie une notification (outbox) ; avec `notificationTemplate`, l'objet et le corps (texte, HTML facultatif) sont rendus depuis un gabarit de notification | `notificationChannels` + champs selon canal ; attributs littéraux `notificationTemplate`, `notificationVariant`?, `notificationLocale`? (canal `EMAIL` seul, `notificationTo` dans le BPMN ; exclut `notificationSubject` et `notificationBody`) ; lit le dossier (champs non sensibles) et les variables scalaires (§6c) | — |
| `${signatureRequestTask}` | demande une signature électronique (§3quater) | `signatureDocument`, `signatureMode`, `signatureDeadlineDays`, `<flowable:signer>` (attributs du bloc) | `signatureRequestId` ; à la reprise : `signatureOutcome` |
| `${businessExpressionTask}` | évalue une expression métier bornée (§3sexies) | attributs littéraux `formula`, `formulaResult` ; lit le dossier (champs non sensibles), `connectorResult`, variables scalaires | la variable nommée par `formulaResult` |
| `${fieldMappingTask}` | enregistre des valeurs calculées dans des champs du dossier, en un patch (§3septies) | attribut littéral `fieldMapping` (lignes `champ = expression`) ; lit le dossier (champs non sensibles), `connectorResult` (+ `json`), variables | champs du dossier |
| `${dossierSearchTask}` | compte les dossiers en cours du même type qui partagent des valeurs avec le dossier courant (§3octies) | attributs littéraux `searchCriteria` (lignes `champ = dossierField:<champ>` ou `variable:<nom>`), `searchCountResult`, `searchFirstReferenceResult` ; `dossierId` (réservée) | les variables nommées par `searchCountResult` (nombre) et `searchFirstReferenceResult` (référence ou `null`) |
| `${aiPrecheckTask}` | pré-vérification IA du dossier, **asynchrone** (§3nonies) | attributs littéraux `precheckPieces`, `precheckFields`, `precheckQuestions`, `precheckRules`, `precheckResult` ; `dossierId` (réservée), variables posées avant (règles) | la variable nommée par `precheckResult` (comptes déterministes) ; exécution et constats dans `precheck_run` |
| `${computeDeadlineTask}` | calcule une échéance pour un minuteur (§« Échéance et minuteur ») | attributs littéraux `champEcheance` (champ date non sensible), `delaiJours`, `rappelJours`, `delaiOuvrable`?, `rappelOuvrable`? ; `dossierId` (réservée) | `echeance`, `echeanceRappel`, `echeanceDepassee` (réservées) |
| `${routeMailTask}` | route un courrier entrant d'après la table de routage (§3decies) | variables `routageAction`, `routageService`, `routageTypeDossier`, `routageProcessus`, `routageDelaiJours`?, `routageDelaiOuvrable`? ; attribut littéral `groupeTri` ; `dossierId`, `dossierWorkflowLinkId` (réservées) | `routageResultat` (`ROUTE` ou `A_TRIER`), `routageMotif`, `dossierMetierId`, `dossierMetierReference` ; champs `service` et `echeance` du courrier ; champ d'échéance du dossier métier (§115) |
| `${createChildDossierTask}` | crée un dossier enfant/suite et démarre son workflow | `dossierId` (réservée), `childDossierTypeCode`, `childDossierLabel`, `workflowProcessKey`, `relationType` (`PARENT_CHILD` ou `SUBSEQUENT`), `childDossierFields`?, `childWorkflowVariables`? | — |

Toutes ces classes vivent dans `src/main/java/be/myrmacta/infrastructure/flowable/` — en cas
de doute sur une variable, elles font foi avant ce document ou avant les `docs/specs/*`
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
de la variable. Une sortie ne peut donc pas porter un nom réservé (`dossierId`, `generatedDocumentId`…), pas plus que
l'id de la décision, sous lequel le moteur pose la liste d'un résultat multiple : la table est refusée (TODO §100). Pas de
`businessRuleTask` : cet élément BPMN standard est câblé sur un moteur
Drools absent de ce dépôt et reste explicitement rejeté au parsing (`FlowableBpmnParserAdapter`,
TODO §58).

### §7ter. Quand utiliser une table de décision DMN plutôt qu'une cascade de gateways

| Situation | Choix |
|---|---|
| 2-3 branches, une seule condition simple (`${statut == 'VALIDE'}`) | Passerelle exclusive (`exclusiveGateway`) — pas de table DMN, surcharge inutile |
| Plusieurs critères combinés (ex. type de demandeur **et** montant) produisant un ou plusieurs résultats, règles qui évoluent indépendamment du flux | Table de décision DMN (`serviceTask type="dmn"`) — la logique de décision est isolée, relisible en tableau, modifiable sans retoucher le diagramme de process |
| La règle change plus souvent que le processus lui-même, ou doit être lisible/validée par un non-développeur | Table de décision DMN — édition dans son propre éditeur (`/admin/dmn-editor`), déploiement indépendant du process qui l'invoque |
| Une seule sortie booléenne dérivée d'une seule variable | Passerelle exclusive — une table DMN à une ligne n'apporte rien |

**Version à générer — DMN 1.3 (TODO §130).** L'éditeur intégré (`dmn-js`) n'ouvre que le DMN 1.3.
Toute table produite à la main, via MCP (`deploy_decision_table`) ou via l'assistant doit porter :

```xml
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" …>
```

Ne pas utiliser le namespace DMN 1.1 (`http://www.omg.org/spec/DMN/20151101`). Pas de commentaire
XML avant `<definitions>` (le schéma Flowable le refuse en 1.3). Un fichier 1.1 importé ou déjà
déployé est converti automatiquement à l'ouverture et à l'écriture ; pour un nouveau document,
écrire directement en 1.3.

**Sécurité — ne jamais oublier en concevant une table :** Flowable évalue les cellules d'une table
de décision en **JUEL**, pas en FEEL malgré ce que le standard DMN laisse penser (voir
[05-workflow-engine.md](../specs/05-workflow-engine.md) §« Tables de décision — DMN »). Une
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
  réutiliser cette garde (protection SSRF, voir [06-connecteurs.md](../specs/06-connecteurs.md) et
  [09-notifications.md](../specs/09-notifications.md)).
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
  [08-templates-documents.md](../specs/08-templates-documents.md)), de même que les
  opérateurs (`==`, `and`, ternaire…), l'indexation et les appels de méthode à l'intérieur des
  accolades (§5). Une variable ou propriété absente ou nulle fait échouer la génération (§3) :
  calculer les valeurs facultatives en amont plutôt que d'espérer un rendu vide.
- **Concurrence** : ne jamais tenter de démarrer un second workflow sur un dossier qui en a déjà
  un actif en dehors des mécanismes documentés ici (§2) — la contrainte unique
  `uq_dossier_workflow_link_active` le refuse de toute façon, mais un `serviceTask` custom qui
  l'ignorerait ferait échouer le processus en pleine exécution plutôt qu'à la conception.

---

## 9. Checklist avant de déployer un nouveau processus

1. `process id` = `workflowKey` de la `WorkflowTypeConfig` déjà créée (ou à créer juste après un
   premier déploiement via upload — voir `docs/guides/GUIDE-UTILISATEUR.md` §3).
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
