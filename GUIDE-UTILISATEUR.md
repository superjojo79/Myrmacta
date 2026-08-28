# Guide utilisateur — prise en main de l'UI

Ce guide décrit, écran par écran, comment créer un dossier, démarrer/suivre un workflow BPMN,
configurer un connecteur et configurer un template de document **depuis l'interface web**.
Pour installer et lancer l'environnement, voir [GETTING-STARTED.md](GETTING-STARTED.md).

> Un diagramme BPMN peut être construit ou modifié directement dans FlowForge (éditeur intégré,
> §3) — sans dépendre d'un outil externe ni d'une application Flowable UI tierce (Modeler,
> Cockpit restent hors scope produit).

------

## 0. Rôles et navigation

Le menu latéral affiche deux sections. La deuxième n'apparaît que si le rôle du compte connecté
le permet — l'affichage du menu est indicatif, le contrôle réel se fait côté API :

| Section | Écran | Route | Rôle backend requis |
|---|---|---|---|
| Pilotage | Supervision | `/supervision` | tout utilisateur authentifié |
| Pilotage | Tâches (mes tâches) | `/tasks` | tout utilisateur authentifié |
| Pilotage | Rapports & SLA | `/rapports` | `TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER` |
| Pilotage | Nouveau dossier | `/dossiers/new` | tout utilisateur authentifié |
| Admin | Types (de dossier) | `/admin/dossier-types` | `TENANT_ADMIN` |
| Admin | Templates | `/admin/templates` | `TENANT_ADMIN` |
| Admin | Connecteurs | `/admin/connectors` | `TENANT_ADMIN` |
| Admin | Workflows | `/admin/workflow-types` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| Admin | Webhooks (notification) | `/admin/notification-webhooks` | `TENANT_ADMIN` |
| Admin | File purge | `/admin/purge-outbox` | `TENANT_ADMIN` |
| Admin | Observabilité | `/admin/observabilite` | `TENANT_ADMIN` |

Sans le rôle requis, l'entrée de menu correspondante n'apparaît simplement pas — pas besoin de la
chercher.

Ces rôles sont attribués côté Keycloak, pas dans FlowForge — voir
[DEPLOIEMENT.md](DEPLOIEMENT.md#6-gérer-les-utilisateurs-et-leurs-rôles) pour créer un utilisateur
et lui attribuer un rôle.

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
| `SLA heures` | objectif de traitement optionnel (heures). Vide = le type n'entre pas dans le % de conformité SLA |

> **Limite actuelle de l'écran :** ce formulaire ne permet pas de définir les **champs
> dynamiques** du type (signalétique métier propre au dossier) — le type est créé avec une liste
> de champs vide. L'ajout de champs dynamiques (texte, nombre, date, booléen, liste, fichier)
> n'est pas piloté depuis cet écran à ce jour ; s'adresser à l'équipe technique si un type a
> besoin de champs métier spécifiques dès sa création.

Une fois créé, le type apparaît dans le tableau avec un bouton **Basculer** pour l'activer/le
désactiver — seuls les types actifs sont proposés à la création d'un dossier.

### Rétention (conformité RGPD)

En haut du même écran **Admin → Types**, deux réglages distincts de la politique de rétention :

- **Rétention tenant** — une durée par défaut en jours, obligatoire pour que le job de rétention
  s'active (tant qu'elle n'est jamais enregistrée, l'API renvoie 404 et le job ne fait rien).
  S'applique à tout type de dossier qui n'a pas de réglage propre (mode **héritage**).
- **Rétention par type** — dans le formulaire de création (ou via les boutons **NEVER**/**Héritage**
  sur une ligne existante) : `héritage` (utilise le défaut tenant), `jours` (durée propre à ce
  type), ou `NEVER` (le type n'est jamais éligible à la purge). La colonne **Rétention** du tableau
  affiche le mode effectif de chaque type.

Le décompte démarre à la **clôture** du dossier (passage au statut `CLOTURE`), pas à sa création —
un dossier jamais clos n'expire jamais. À expiration, le dossier passe en `ARCHIVE` et une demande
de purge est déposée dans la file (§8) ; **aucune donnée n'est supprimée automatiquement à ce
stade**, la purge physique reste une étape distincte.

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

### Construire ou modifier un diagramme BPMN

Écran **Éditeur BPMN** (`/admin/bpmn-editor`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`), accessible
depuis le bouton **Éditer** en face de chaque config workflow sur l'écran **Admin → Workflows**
(`/admin/workflow-types`) :

1. **Trois points d'entrée** : diagramme vierge (aucune clé encore associée), import d'un fichier
   `.bpmn20.xml` local, ou édition d'un diagramme déjà déployé pour la clé de la config workflow
   choisie (le diagramme réellement en cours d'exécution est rechargé automatiquement).
2. **Palette d'édition** (`bpmn-js`) — glisser-déposer des éléments BPMN standard (tâches,
   passerelles, événements). Le **panel de propriétés** à droite permet d'éditer les propriétés
   BPMN standard, ainsi que les attributs Flowable spécifiques aux tâches de service
   (`asynchrone`, `delegate expression`, `expression`) et aux signaux (`scope`).
3. Bouton **Enregistrer le brouillon** — persiste l'état courant côté serveur, un brouillon par
   clé de processus (le dernier enregistrement écrase le précédent, pas d'historique). Rouvrir
   l'éditeur sur la même clé reprend ce brouillon.
4. Bouton **Déployer** — même flux de confirmation que l'upload ci-dessous (affichage de la
   version actuelle/instances actives si la clé existe déjà, confirmation explicite). Un
   déploiement réussi **supprime le brouillon** de cette clé (le diagramme réellement déployé
   redevient la référence à la prochaine ouverture de l'éditeur).

Un diagramme peut aussi toujours être modélisé avec un outil externe (ex. Camunda Modeler) puis
importé/déployé via cet écran ou via l'upload ci-dessous.

> Pour les conventions de nommage, les `serviceTask` disponibles (génération de document,
> connecteurs, notifications, chaînage de dossiers…) et la façon de référencer les champs
> dynamiques ou les intervenants d'un dossier depuis le BPMN, voir le guide technique
> [17-guide-conception-bpmn.md](analysis%20doc/17-guide-conception-bpmn.md).

### Déployer un diagramme BPMN (upload direct)

Une fois un fichier `.bpmn20.xml` obtenu (édité dans FlowForge ou avec un outil externe), deux
mécanismes de dépôt côté moteur Flowable coexistent :

**Au build/démarrage (référence CI/exploitation)** — le fichier est déposé dans le dépôt backend
(`src/main/resources/processes/`) ; au démarrage de l'application, tous les processus de ce
dossier sont automatiquement déployés dans Flowable pour le tenant courant
(`TenantProcessDeployment`). C'est le mécanisme à utiliser pour tout processus destiné à être
versionné avec le code.

**À chaud, depuis l'UI (complément)** — écran **Admin → Workflows**
(`/admin/workflow-types`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`), section **Déployer un diagramme
BPMN** en bas de page :

1. Sélectionner le fichier `.bpmn20.xml` — il est lu localement et son diagramme s'affiche en
   prévisualisation (rendu `bpmn-js`, aucun appel réseau de déploiement à ce stade).
2. Si la clé du processus (`process id` du XML) correspond à un déploiement déjà existant, la
   version actuelle et le nombre d'instances actives sont affichés — redéployer crée une nouvelle
   version sans affecter les instances déjà en cours (comportement natif Flowable).
3. Bouton **Confirmer le déploiement** — le fichier est revalidé côté serveur (BPMN 2.0 bien
   formé, clé de processus correspondant à une config workflow existante) puis déployé. Un
   déploiement est **rejeté** si la clé du processus ne correspond à **aucune** config workflow
   existante : contrairement au scan classpath, ce chemin exige que la config workflow (section
   suivante) soit créée **avant** l'upload.

Ce mécanisme ne remplace pas le scan classpath au démarrage — c'est un complément pour déployer
une nouvelle version sans redémarrer l'application.

### Associer une config workflow

Écran **Admin → Workflows** (`/admin/workflow-types`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`) :

| Champ | Rôle |
|---|---|
| `workflowKey` | doit être **identique** à la clé du processus BPMN (`process id` dans le XML) — que le processus soit déjà déployé (scan classpath) ou pas encore (upload UI, voir ci-dessus) |
| `libellé` | nom affiché |

Cet écran ne configure que la **métadonnée applicative** (statuts de dossier autorisés, statut
appliqué au démarrage du workflow, etc.) — pas le diagramme lui-même, qui se dépose séparément
(section précédente). Une fois créée, la config peut être renommée depuis le tableau.

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

> **Ce que cet écran ne permet pas :** configurer un appel sortant générique vers un service tiers
> depuis un workflow (rôle **service** d'un connecteur `http-rest`). Ce rôle existe dans le code
> mais n'est pas enregistré en V1 — seul `flowable-signal` peut être appelé en tant que service
> depuis un workflow aujourd'hui. Pour notifier un système externe en sortie, utiliser un webhook de
> notification (§7) plutôt qu'un connecteur.

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

## 6. Suivre l'activité — Supervision, Mes tâches et Rapports

- **Supervision** (`/supervision`) — tableau de bord de tous les dossiers, filtrable par statut,
  type et préfixe de référence (filtres reflétés dans l'URL, donc partageables en lien). Recherche
  rapide d'un dossier par référence ou identifiant. Lien direct vers **Nouveau dossier**.
- **Tâches** (`/tasks`) — vue personnelle des tâches actuellement assignées à l'utilisateur
  connecté, avec accès direct au dossier concerné, bouton **Compléter**, et un rappel SLA
  (`dans N h` / `dépassée`) lorsque le type de dossier a un objectif en heures.
- **Rapports & SLA** (`/rapports`) — snapshot de conformité et de délais sur une période
  (défaut 30 jours). Visible pour `TENANT_ADMIN`, `WORKFLOW_ADMIN` et `DOSSIER_MANAGER`
  (ce dernier ne voit que les types pour lesquels il a VIEW). Export CSV disponible ;
  la planification d'envoi n'est pas encore activée. L'objectif de conformité tenant
  (défaut 95 %) se configure dans **Admin → Types**.

Sur la fiche d'un dossier (`/dossiers/{id}`), en plus du suivi de workflow (§3), on trouve aussi :

- l'édition des champs dynamiques du dossier (verrouillée si le statut est `CLOTURE`/`ARCHIVE`) ;
- la liste de ses **éléments** (pièces jointes, documents produits, notes, informations) avec
  ajout via un dialogue d'upload de fichier ;
- ses **relations** vers d'autres dossiers (hiérarchie, subséquence, lien simple) via un
  dialogue de liaison dédié ;
- le changement manuel de statut du dossier ;
- pour `TENANT_ADMIN`, un bouton **Demander la purge** qui dépose une demande dans la file de
  purge (§8) indépendamment de l'échéance normale de rétention (§1).

------

## 7. Webhooks de notification (sortant)

Écran **Admin → Webhooks** (`/admin/notification-webhooks`, `TENANT_ADMIN`).

À ne pas confondre avec les connecteurs `http-rest` du §4, qui sont des webhooks **entrants**
(ils déclenchent un workflow). Ici, la plateforme **appelle** une URL externe pour notifier un
événement métier (voir [analysis doc/09-notifications.md](analysis%20doc/09-notifications.md)),
typiquement pour rebrancher un outil noCode (Make, n8n, Zapier).

| Champ | Rôle |
|---|---|
| `nom` | libellé libre de l'instance |
| `URL cible` | endpoint HTTPS qui recevra le payload JSON |
| `secret` | optionnel, champ écriture seule — utilisé pour signer le payload (HMAC) ; jamais réaffiché une fois enregistré, seul un badge « secret configuré » l'indique |

Une fois créé, le webhook est actif par défaut. Boutons **Basculer** (activer/désactiver) et
**Supprimer** dans la liste. L'URL cible passe systématiquement par une garde anti-SSRF (résolution
DNS, blocage des plages privées/loopback/métadonnées cloud) avant tout envoi — une URL qui pointe
vers une ressource interne sera refusée à l'appel, pas seulement à la création.

L'envoi est asynchrone (file d'attente interne avec retry/backoff) : un échec temporaire de la
cible ne bloque pas le workflow déclencheur et est rejoué automatiquement jusqu'à un nombre maximal
de tentatives, avant de passer en échec définitif (visible dans **Observabilité**, §9, comme
« Webhooks DEAD »).

------

## 8. File de purge (rétention / RGPD)

Écran **Admin → File purge** (`/admin/purge-outbox`, `TENANT_ADMIN`), en lecture seule.

Cette file recense les dossiers **candidats à la purge physique** — parce qu'ils ont expiré selon
la politique de rétention (§1) ou qu'une purge a été demandée manuellement depuis leur fiche (§6).
Deux blocs :

- **Compteurs** par statut : `PENDING` (en attente), `PROCESSING` (en cours), `FAILED` (échec —
  sera rejoué), `COMPLETED` (traité).
- **Liste des entrées** : dossier concerné, statut, origine de la demande (échéance de rétention ou
  demande manuelle), date de création, dernière erreur le cas échéant.

La purge elle-même (suppression des données PostgreSQL et des fichiers de stockage objet) est un
traitement de fond asynchrone — cet écran n'a pas de bouton pour la déclencher ou l'annuler ; il
sert à vérifier que la file avance et à repérer les échecs répétés.

------

## 9. Observabilité

Écran **Admin → Observabilité** (`/admin/observabilite`, `TENANT_ADMIN`) — tableau de bord
technique en lecture seule, bouton **Rafraîchir** pour recharger un instantané à la demande.

| Indicateur | Signification |
|---|---|
| Âge outbox déclenchement / notification / purge | Ancienneté du plus vieil enregistrement encore non traité dans chacune des trois files internes — une valeur qui grimpe signale un traitement de fond bloqué |
| Jobs Flowable dead-letter / en exception | Jobs du moteur de workflow qui ont épuisé leurs tentatives ou sont en erreur |
| Rendu PDF | Nombre de générations, durée moyenne et max |
| Webhooks DEAD | Webhooks de notification (§7) définitivement en échec après épuisement des tentatives |
| SSE actifs | Nombre de connexions de notification navigateur actuellement ouvertes |

Chaque carte porte une sévérité (normal/alerte) calculée côté serveur selon des seuils fixes — cet
écran est destiné à un diagnostic rapide, pas à de l'historique ou du graphique dans le temps (pas
de série temporelle en V1 ; les mêmes métriques sont aussi exposées au format Prometheus sur le
port management, voir [GETTING-STARTED.md](GETTING-STARTED.md#7-démarrer-le-backend), pour qui
dispose déjà d'un outil de supervision externe).

------

## Pour aller plus loin

- Mise en place de l'environnement : [GETTING-STARTED.md](GETTING-STARTED.md)
- Déploiement, provisioning d'un tenant, gestion des utilisateurs/rôles Keycloak : [DEPLOIEMENT.md](DEPLOIEMENT.md)
- Architecture des connecteurs : [analysis doc/06-connecteurs.md](analysis%20doc/06-connecteurs.md)
- Architecture des templates/génération de documents : [analysis doc/07-generation-documents.md](analysis%20doc/07-generation-documents.md), [analysis doc/08-templates-documents.md](analysis%20doc/08-templates-documents.md)
- Visualisation des workflows : [analysis doc/10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md)
- Notifications (email, webhook, SSE) : [analysis doc/09-notifications.md](analysis%20doc/09-notifications.md)
- Autorisation et conformité (rétention, RGPD) : [analysis doc/12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md)
- Décisions ouvertes (dont les limites d'écran citées ci-dessus) : [TODO.md](TODO.md)
