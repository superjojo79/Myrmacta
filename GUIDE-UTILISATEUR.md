# Guide utilisateur — prise en main de l'UI

Ce guide décrit, écran par écran, comment créer un dossier, démarrer/suivre un workflow BPMN,
configurer un connecteur et configurer un template de document **depuis l'interface web**.
Pour installer et lancer l'environnement, voir [GETTING-STARTED.md](GETTING-STARTED.md).

> Certaines opérations décrites ci-dessous (le dépôt du diagramme BPMN lui-même, notamment) ne
> sont **pas encore** exposées dans l'UI à ce stade du projet — c'est signalé explicitement à
> chaque fois plutôt que passé sous silence.

------

## 0. Rôles et navigation

Le menu latéral affiche deux sections. La deuxième n'apparaît que si le rôle du compte connecté
le permet — l'affichage du menu est indicatif, le contrôle réel se fait côté API :

| Section | Écran | Route | Rôle backend requis |
|---|---|---|---|
| Pilotage | Supervision | `/supervision` | tout utilisateur authentifié |
| Pilotage | Tâches (mes tâches) | `/tasks` | tout utilisateur authentifié |
| Pilotage | Nouveau dossier | `/dossiers/new` | tout utilisateur authentifié |
| Admin | Types (de dossier) | `/admin/dossier-types` | `TENANT_ADMIN` |
| Admin | Templates | `/admin/templates` | `TENANT_ADMIN` |
| Admin | Connecteurs | `/admin/connectors` | `TENANT_ADMIN` |
| Admin | Workflows | `/admin/workflow-types` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |

Sans le rôle requis, l'entrée de menu correspondante n'apparaît simplement pas — pas besoin de la
chercher.

------

## 1. Préalable — configurer un type de dossier

**Un dossier ne peut être créé que si un type de dossier actif existe déjà.** C'est la première
chose à faire sur une instance neuve, depuis **Admin → Types** (`/admin/dossier-types`,
`TENANT_ADMIN`).

Formulaire de création :

| Champ | Rôle |
|---|---|
| `code` | identifiant technique du type (utilisé partout ensuite : création de dossier, config workflow…) |
| `libellé` | nom affiché à l'utilisateur |
| `workflowKey` | clé du workflow qui sera proposé par défaut à l'ouverture d'un dossier de ce type — doit correspondre à une config workflow existante (§3) |

> **Limite actuelle de l'écran :** ce formulaire ne permet pas de définir les **champs
> dynamiques** du type (signalétique métier propre au dossier) — le type est créé avec une liste
> de champs vide. L'ajout de champs dynamiques (texte, nombre, date, booléen, liste, fichier)
> n'est pas piloté depuis cet écran à ce jour ; s'adresser à l'équipe technique si un type a
> besoin de champs métier spécifiques dès sa création.

Une fois créé, le type apparaît dans le tableau avec un bouton **Basculer** pour l'activer/le
désactiver — seuls les types actifs sont proposés à la création d'un dossier.

La section du bas de l'écran gère les **droits d'accès (ACL)** par type de dossier : associer un
`userId` à une permission `VIEW` (lecture seule) ou `EDIT` (modification), boutons
**Accorder**/**Révoquer**.

------

## 2. Créer un dossier

Écran **Nouveau dossier** (`/dossiers/new`), accessible à tout utilisateur authentifié.

1. **Type** — liste déroulante des types de dossier actifs (voir §1). Le premier est
   présélectionné.
2. **Libellé** — texte libre, obligatoire.
3. **Champs dynamiques** — si le type sélectionné en définit, ils apparaissent regroupés par
   section ; un `*` marque les champs obligatoires.
4. Cliquer sur **Créer** (type et libellé sont les deux seuls champs strictement requis pour
   valider le formulaire).

Le dossier est créé puis la page bascule automatiquement sur sa fiche détail
(`/dossiers/{id}`).

> **Le workflow ne démarre pas automatiquement à la création du dossier.** C'est une action
> séparée et explicite, décrite au §3.

------

## 3. Démarrer et suivre un workflow BPMN

### Ce que l'UI ne fait pas (à savoir avant de commencer)

FlowForge ne propose **aucun écran pour dessiner ou déposer un fichier `.bpmn`** — ni designer
graphique intégré, ni bouton d'upload. Le diagramme est visualisable en lecture seule une fois
qu'une instance tourne, mais son **dépôt côté moteur Flowable est une opération technique**,
réalisée par l'équipe de développement/exploitation :

1. Le diagramme BPMN 2.0 est modélisé avec un outil externe (Flowable Modeler ou tout éditeur
   BPMN produisant un `.bpmn20.xml` valide).
2. Le fichier est déposé dans le dépôt backend (`src/main/resources/processes/`).
3. Au redémarrage de l'application, le processus est automatiquement déployé dans Flowable pour
   le tenant courant.

Si le workflow qu'il vous faut n'existe pas encore, il faut passer par cette étape technique
avant de pouvoir continuer ci-dessous — ce n'est pas une action disponible depuis le navigateur.

### Une fois le processus déployé côté moteur — associer une config workflow

Écran **Admin → Workflows** (`/admin/workflow-types`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`) :

| Champ | Rôle |
|---|---|
| `workflowKey` | doit être **identique** à la clé du processus BPMN déployé (`process id` dans le XML) |
| `libellé` | nom affiché |

Cet écran ne configure que la **métadonnée applicative** (statuts de dossier autorisés, statut
appliqué au démarrage du workflow, etc.) — pas le diagramme lui-même, qui est déjà déployé côté
moteur à ce stade. Une fois créée, la config peut être renommée depuis le tableau.

### Démarrer une instance sur un dossier

Depuis la fiche dossier (`/dossiers/{id}`), bouton **Démarrer workflow** :

- proposé par défaut : la config workflow associée au type du dossier (`workflowKey` du type) ;
- possibilité de choisir une autre config workflow existante dans la liste déroulante.

### Suivre l'exécution

Toujours sur la fiche dossier :

- **Diagramme BPMN** — vue en lecture seule (bpmn-js), avec surbrillance des étapes actives,
  terminées et en erreur. Affiché uniquement si une instance de workflow est démarrée sur ce
  dossier.
- **Tâches** — liste des tâches humaines en cours, bouton **Compléter** ; les rôles
  `TENANT_ADMIN`/`WORKFLOW_ADMIN` peuvent en plus réassigner une tâche à un autre utilisateur
  (identifiant saisi librement).
- **Historique** — frise chronologique des événements du dossier.

Un utilisateur peut aussi retrouver l'ensemble de ses tâches assignées, tous dossiers confondus,
depuis **Tâches** (`/tasks`) — pratique en mobilité.

------

## 4. Configurer un connecteur

Écran **Admin → Connecteurs** (`/admin/connectors`, `TENANT_ADMIN`).

### Catalogue des types disponibles

La partie haute de l'écran liste les **types de connecteurs** installés sur l'instance (pas les
connecteurs eux-mêmes — leurs instances configurées). Deux types sont fournis de base :

| Type | Rôle (`capability`) | Champs de config attendus |
|---|---|---|
| `http-rest` | trigger (déclenche un workflow sur appel HTTP entrant) | `webhookAuthType` (`HMAC_SHA256` ou `SHARED_SECRET_TOKEN`), `webhookSecret`, `processKey`, `dossierTypeCode` |
| `flowable-signal` | trigger **et** service (peut aussi être appelé depuis un workflow) | `signalName`, `processKey`, `dossierTypeCode` |

D'autres connecteurs custom peuvent être ajoutés au classpath backend sans modifier le core (voir
[analysis doc/06-connecteurs.md](analysis%20doc/06-connecteurs.md)) — ils apparaîtront alors dans
ce même catalogue.

### Créer une instance de connecteur

1. **Nom** — libellé libre de l'instance.
2. **Type** — sélectionné dans le catalogue ci-dessus.
3. **Config** — **zone de texte JSON brut** : saisir directement l'objet JSON avec les clés
   attendues par le type choisi (tableau ci-dessus). Il n'y a pas de formulaire champ par champ
   pour l'instant — un JSON mal formé bloque la création avec une erreur générique.

   Exemple pour un `http-rest` :
   ```json
   {
     "webhookAuthType": "SHARED_SECRET_TOKEN",
     "webhookSecret": "un-secret-long-et-aleatoire",
     "processKey": "signal-smoke",
     "dossierTypeCode": "SINISTRE"
   }
   ```
4. Valider — le connecteur est créé actif par défaut.

Dans la liste, chaque connecteur affiche son nom, son type, son statut et les **clés** des
secrets configurés (jamais leur valeur — tout champ se terminant par `password`/`secret` est
masqué). Boutons **Basculer** (activer/désactiver) et **Supprimer**.

### Appeler un connecteur `http-rest` (webhook entrant)

> **L'UI n'affiche l'URL du webhook nulle part.** Elle se construit manuellement :
> `POST /api/webhooks/{id-du-connecteur}`, où `{id-du-connecteur}` est l'identifiant renvoyé par
> l'API à la création (visible dans les outils réseau du navigateur, ou à demander à un
> administrateur technique).

L'appel doit porter un en-tête `Idempotency-Key` et l'authentification déclarée
(`webhookAuthType`) ; la taille du corps et le débit sont limités par la configuration serveur
(voir [GETTING-STARTED.md](GETTING-STARTED.md) pour les paramètres correspondants).

------

## 5. Configurer un template de document

Écran **Admin → Templates** (`/admin/templates`, `TENANT_ADMIN`).

### Créer un template

| Champ | Rôle |
|---|---|
| `templateKey` | identifiant technique, référencé ensuite depuis le workflow ou la génération de document |
| `libellé` | nom affiché |
| `Contenu HTML` | zone de texte — HTML/Thymeleaf saisi directement (pas d'éditeur riche) |

Un panneau d'**aperçu en direct** affiche le rendu du HTML au fur et à mesure de la saisie.

> **Limites actuelles de l'écran :** l'API supporte des variantes par langue/contexte
> (`variantKey`, `locale`) et un versionnement complet, mais le formulaire de création n'expose
> pas encore `variantKey`/`locale` — un template créé ici est la variante par défaut. De même,
> il n'y a pas de sélection explicite d'un type de dossier associé : l'association se fait par
> `templateKey`, référencé ailleurs (étape de génération de document dans un workflow).

### Cycle de vie d'un template

Dans le tableau des templates existants (`templateKey` — libellé — version `vN` — statut) :

| Action | Effet |
|---|---|
| **Renommer** | modifie le libellé (invite de saisie du navigateur) |
| **Nouvelle version** | crée un brouillon versionné à partir du template existant |
| **Activer** | rend une version active (utilisable en génération de document) |
| **Archiver** | retire une version de la circulation |

------

## 6. Suivre l'activité — Supervision et Mes tâches

- **Supervision** (`/supervision`) — tableau de bord de tous les dossiers, filtrable par statut,
  type et préfixe de référence (filtres reflétés dans l'URL, donc partageables en lien). Recherche
  rapide d'un dossier par référence ou identifiant. Lien direct vers **Nouveau dossier**.
- **Tâches** (`/tasks`) — vue personnelle des tâches actuellement assignées à l'utilisateur
  connecté, avec accès direct au dossier concerné et bouton **Compléter**.

Sur la fiche d'un dossier (`/dossiers/{id}`), en plus du suivi de workflow (§3), on trouve aussi :

- l'édition des champs dynamiques du dossier (verrouillée si le statut est `CLOTURE`/`ARCHIVE`) ;
- la liste de ses **éléments** (pièces jointes, documents produits, notes, informations) avec
  ajout via un dialogue d'upload de fichier ;
- ses **relations** vers d'autres dossiers (hiérarchie, subséquence, lien simple) via un
  dialogue de liaison dédié ;
- le changement manuel de statut du dossier.

------

## Pour aller plus loin

- Mise en place de l'environnement : [GETTING-STARTED.md](GETTING-STARTED.md)
- Architecture des connecteurs : [analysis doc/06-connecteurs.md](analysis%20doc/06-connecteurs.md)
- Architecture des templates/génération de documents : [analysis doc/07-generation-documents.md](analysis%20doc/07-generation-documents.md), [analysis doc/08-templates-documents.md](analysis%20doc/08-templates-documents.md)
- Visualisation des workflows : [analysis doc/10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md)
- Décisions ouvertes (dont les limites d'écran citées ci-dessus) : [TODO.md](TODO.md)
