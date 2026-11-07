# Guide utilisateur — prise en main de l'UI

Ce guide décrit, écran par écran, comment créer un dossier, démarrer/suivre un workflow BPMN,
configurer un connecteur et configurer un template de document **depuis l'interface web**.
Pour installer et lancer l'environnement, voir [docs/guides/GETTING-STARTED.md](GETTING-STARTED.md).

> Un diagramme BPMN peut être construit ou modifié directement dans Myrmacta (éditeur intégré,
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
| Admin | Tables de décision | `/admin/dmn-editor` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| Admin | Questions de tâche | `/admin/task-question-schemas` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| Admin | Groupes (d'utilisateurs) | `/admin/user-groups` | `TENANT_ADMIN` |
| Admin | Webhooks (notification) | `/admin/notification-webhooks` | `TENANT_ADMIN` |
| Admin | File purge | `/admin/purge-outbox` | `TENANT_ADMIN` |
| Admin | Observabilité | `/admin/observabilite` | `TENANT_ADMIN` |
| Admin | Incidents (étapes en échec) | `/admin/incidents` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| Public | Portail tiers externe | `/portal/` | **aucun compte** — lien personnel reçu par e-mail, si le portail est activé (§11) |

Sans le rôle requis, l'entrée de menu correspondante n'apparaît simplement pas — pas besoin de la
chercher.

Ces rôles sont attribués côté Keycloak, pas dans Myrmacta — voir
[docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md#6-gérer-les-utilisateurs-et-leurs-rôles) pour créer un utilisateur
et lui attribuer un rôle.

**Se connecter avec l'eID ou itsme (TODO §104).** Si la commune a activé FAS ou itsme, la page de connexion propose
leurs boutons à côté du mot de passe, ou à sa place en mode « fournisseurs seuls ». À la **première** connexion par ce
moyen :

- **sans compte existant** : un compte est créé, mais il est **refusé** tant qu'un administrateur Keycloak ne lui a pas
  attribué le tenant et des rôles ([DEPLOIEMENT.md §6](DEPLOIEMENT.md#6-gérer-les-utilisateurs-et-leurs-rôles)) ;
- **avec un compte existant** (même adresse e-mail) : Keycloak propose de le relier ; vous confirmez avec votre mot de
  passe une seule fois et gardez vos droits, vos tâches et vos dossiers suivis.

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
| **Autoriser les champs inconnus** | case à cocher — si activée, un dossier de ce type peut porter des champs absents du schéma déclaré ci-dessous (compatibilité ascendante volontaire) |
| **Champs dynamiques** (`fieldDefinitions`) | éditeur de la liste des champs métier du type — voir ci-dessous |

**Éditeur de champs dynamiques.** Le bouton **Ajouter un champ** insère une ligne éditable :
`name` (clé technique, doit être unique dans le type), `label` (libellé affiché), `type`
(`STRING`, `NUMBER`, `DATE`, `BOOLEAN`, `SELECT`, `FILE` et les trois types structurés `MONEY`,
`EXTERNAL_REF`, `REPEATABLE_GROUP` — voir ci-dessous), `required`, `defaultValue`, `options`
(liste séparée par des virgules, pour `SELECT`, et pour `MONEY` / `EXTERNAL_REF` : devises / systèmes autorisés), `group` (regroupement visuel sur
l'écran dossier) et `sensible` (masquage à l'affichage/export pour tout acteur non
`TENANT_ADMIN`). Boutons ↑/↓ pour réordonner, **Supprimer** pour retirer une ligne. La
sauvegarde envoie la **liste complète** (pas un ajout unitaire) — même convention que l'outil MCP
interne `define_dossier_type`.

**Valeur unique (TODO §102).** Une case « Valeur unique » est proposée pour un champ texte, nombre ou
date qui n'est pas sensible, y compris un champ calculé. Elle est visible mais inactive pour les autres
types et pour un champ sensible ; la raison est affichée à côté de la case. Cochée, une même valeur ne
peut figurer que dans un seul dossier en cours de ce type (ni clôturé, ni archivé). Une valeur vide n'est
jamais un doublon. À la création ou à la correction, un doublon est refusé sous le champ : « Déjà utilisé
par le dossier {référence} ». Activer la case alors que des dossiers ouverts partagent déjà une valeur est
refusé : l'écran liste les valeurs et des références (20 au plus ; le total est indiqué). Clôturer le
dossier libère la valeur. L'exemple « autorisation riverain » l'utilise pour la clé calculée
`plaqueNormalisee` (pays et plaque normalisés) : une seconde demande ouverte pour le même véhicule est
refusée à la création, sans case à cocher par l'agent.

**Types structurés (TODO §76).**

| Type | Saisie | Valeur stockée | Règles |
|---|---|---|---|
| `MONEY` (montant) | un montant et une devise | `{"amount":"1234.50","currency":"EUR"}` | devise ISO 4217 en majuscules ; **options** = devises autorisées (vide = toutes ; une seule = préremplie) ; le montant est un décimal **exact** (jamais un nombre flottant), au plus autant de décimales que la devise (2 pour EUR, 0 pour JPY, 3 pour KWD) — **refusé, jamais arrondi en silence** ; le serveur le range à l'échelle de la devise (`12.5` → `12.50`) ; négatif permis (avoir) ; aucune valeur par défaut |
| `EXTERNAL_REF` (référence externe) | système, identifiant, libellé facultatif | `{"system":"d365fo","id":"V-0042","label":"Contoso"}` | **options** = systèmes autorisés (vide = tous) ; identifiant 200 caractères au plus, sans caractère de contrôle ; la résolution auprès du système distant n'est pas faite (la valeur est une référence) ; aucune valeur par défaut |
| `REPEATABLE_GROUP` (lignes) | tableau de lignes, boutons **Ajouter une ligne** / ✕ | liste d'objets, un par ligne | **Colonnes** = sous-champs définis dans l'éditeur (20 au plus, **un seul niveau** : ni groupe ni `FILE` dans un groupe) ; **lignes min./max.** (défaut 0 et 100, plafond 500) ; un champ obligatoire d'une ligne doit être renseigné ; la **sensibilité se pose sur le groupe entier**, pas sur une colonne ; aucune valeur par défaut |

Une erreur de saisie nomme le champ **et la ligne** (`lignes[1].poste`). Dans un export CSV, un montant se répartit en
deux colonnes (`montant.amount`, `montant.currency`), une référence en trois (`.system`, `.id`, `.label`) et un groupe en
**une** colonne portant les lignes en JSON compact. Un montant **négatif** y est préfixé d'une apostrophe comme tout
nombre négatif de l'export (protection contre l'injection de formule) : à retirer à l'import. Un changement de type d'un
champ existant (ex. `STRING` → `MONEY`) est refusé (409) tant que des dossiers ont une valeur incompatible.

**Règles de champs (champs calculés, visibilité, obligation conditionnelle).** Dans l'éditeur d'un type, chaque champ peut porter
trois expressions métier (langage JSONata borné, `dossier.fields.nomDuChamp`) : **formule** (le champ est calculé : jamais saisi,
toujours recalculé), **visible si** (faux ⇒ le champ n'est ni affiché, ni obligatoire, et sa valeur stockée est retirée) et
**obligatoire si**. Une règle ne lit que des champs déclarés, simples, non sensibles ; elle ne peut pas lire un champ calculé ni un
champ qui porte lui-même une règle « visible si ». Au plus 40 règles par type. La SPA affiche un aperçu calculé par le serveur ;
l'API et l'outil MCP `evaluate_dossier_rules` donnent le même résultat. Les règles ne sont pas disponibles dans les questions de tâche.
Les **questions de tâche** (formulaires utilisateur, portail) ne proposent pas encore ces trois types.

Une fois créé, le type apparaît dans le tableau avec un bouton **Basculer** pour l'activer/le
désactiver — seuls les types actifs sont proposés à la création d'un dossier. Le bouton
**Modifier les champs** ouvre le même éditeur, pré-rempli avec le schéma actuel, pour un type déjà
créé. **Si la modification proposée rend des dossiers déjà existants de ce type invalides** (ex.
un champ redevenu `required` mais absent, ou une valeur `SELECT` retirée des `options`), la
sauvegarde est **refusée** (409) avec le nombre de dossiers concernés — la modification n'est pas
appliquée ; il n'existe pas d'option de passage en force depuis cet écran.

> Pour éditer les **valeurs** des champs sur un dossier existant (pas la définition du schéma), voir
> l'écran détail dossier (§2).

**Templates de documents autorisés (TODO §52).** Dans le formulaire de création et dans le panneau
**Modifier les champs**, une nouvelle section liste les templates de documents actifs (§5) sous
forme de cases à cocher — cocher une clé l'ajoute aux templates que le formulaire de tâche
`document-generation` (§3bis du [docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md)) proposera
pour ce type de dossier. **Par défaut, aucun template n'est associé** : tant qu'aucune case n'est
cochée ici, un `userTask` avec `flowable:formKey=document-generation` sur un dossier de ce type
affiche un picker vide plutôt qu'un fallback « tous les templates » — associer explicitement les
templates pertinents avant de déployer un process qui les utilise.

**Types de pièces justificatives (TODO §106).** Section **Types de pièces justificatives** du formulaire de création et
du panneau **Modifier les champs** : une ligne par type (clé, libellé, case **Sensible**), 30 au plus. La clé commence par
une minuscule, puis lettres, chiffres ou `_` ; une clé invalide ou en double bloque l'enregistrement. Un type
**sensible** n'est jamais lu par une pré-vérification automatique. Retirer un type ne supprime aucune pièce : celles de ce
type redeviennent *Non classées*.

**Échéance (TODO §115).** La section **Échéance** du panneau **Modifier les champs** désigne le champ date qui porte
l'échéance des dossiers de ce type (la liste ne propose que les champs de type date) :

- **Rappel (jours avant)** : les personnes qui ont une tâche sur le dossier reçoivent une notification N jours avant le
  terme. 0 = pas de rappel.
- **Groupe d'escalade** : le lendemain du terme, si le dossier n'est pas clôturé, ses tâches ouvertes passent à ce
  groupe, dont les membres sont prévenus. Une note dans l'historique du dossier dit quelles tâches ont été réaffectées.

Chaque rappel et chaque escalade n'a lieu qu'une fois ; reporter l'échéance (corriger le champ) en relance de nouveaux.
**Décompte du rappel** : *Jours calendaires* (week-ends et fériés compris, escalade le lendemain) ou *Jours ouvrables*
(le rappel recule et l'escalade attend le prochain jour ouvrable). Un dossier en brouillon, clôturé ou archivé n'est
pas suivi. Ce réglage voyage avec la promotion de configuration (§ Promotion).

**Calendrier (TODO §120).** La page **Types** porte une section **Calendrier**, réservée à l'administrateur du tenant :

- **Fuseau** : jour courant et minuit des minuteurs d'échéance. Défaut `UTC`.
- **Week-end** : samedi et dimanche (défaut), vendredi et samedi, ou dimanche seul.
- **Jours fériés** : une date `AAAA-MM-JJ` par ligne, 400 au plus. Ils ne comptent que pour un délai marqué ouvrable.
- **Ajouter les fériés fédéraux belges** : ajoute les dix dates légales de l'année civile en cours et de la suivante.
  On peut les retirer. Rien ne se réécrit tout seul l'année suivante.

Le calendrier voyage avec la promotion de configuration.

**Workflow réponse (courrier entrant, TODO §113).** Le panneau **Modifier les champs** porte une section **Réponse à
un courrier**. Elle dit quel process lancer quand une réponse par e-mail rejoint un dossier de ce type (Digital Mail Room) :

- **Aucun workflow** (défaut) : la réponse est rangée dans le dossier, et ses participants sont prévenus ;
- **Sur le dossier (si aucun process actif)** : le process choisi démarre sur ce dossier, seulement si aucun process n'y tourne déjà ;
- **Dans un dossier enfant** : chaque réponse ouvre un dossier enfant du **type enfant** choisi, où le process démarre.

Le bouton **Enregistrer la réponse** valide le réglage. Un process sans configuration de workflow, ou un type
enfant inconnu, est refusé avec un message. Ce réglage voyage avec la promotion de configuration (§ Promotion).

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

**Pièces justificatives classées (TODO §106).** Sur la fiche, **Ajouter** → *Pièce jointe* propose un **Type de pièce**
quand le type de dossier en déclare (§1) ; *Non classée* reste possible. Dans la liste des éléments, un éditeur du
dossier change le type d'une pièce (ou la remet *Non classée*) avec la liste déroulante de la pièce ; un simple lecteur
voit le type. Un dossier clos ne se reclasse plus. Chaque changement est tracé (qui, quand).

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
   passerelles, événements) **et**, dans le groupe **« Briques métier »** (icônes marquées d'un point
   bleu), treize briques prêtes à l'emploi : générer / joindre un document, appeler un connecteur (avec, au besoin,
   la **requête** construite par une expression à partir du dossier et de la réponse précédente : guide de conception §4),
   notifier (e-mail avec, au besoin, le PDF produit en **pièce jointe** : champ « Pièce jointe (e-mail) » =
   `variable:generatedDocumentId`, après « Joindre le document » — guide de conception §6c), créer un dossier enfant, changer le statut du dossier ou du workflow, décision DMN,
   **évaluer une expression** (calcul ou condition sur les champs du dossier, voir le guide de conception §3sexies),
   **enregistrer des valeurs dans le dossier** (une ligne `champ = expression` par champ, typiquement à partir de la réponse d'un connecteur : `score = connectorResult.json.score` ; tout ou rien, voir §3septies),
   demander une signature, question utilisateur, tâche externe. Le **panel de propriétés** à droite
   affiche pour une brique un groupe dédié avec des **champs typés** (listes de templates, connecteurs,
   statuts, questions, **tables de décision DMN déployées** (clé et dernière version)… alimentées par le serveur) ; la section **« Avancé »**, repliée, garde les
   attributs Flowable bruts (`asynchrone`, `delegate expression`, `expression`, `scope`, `form key`…).
   Une tâche qui n'est pas une brique reste éditable comme avant. Un contour orange signale une brique
   à compléter (paramètre obligatoire vide, ressource supprimée) — l'avertissement n'empêche pas
   d'enregistrer, seul le déploiement refuse une référence inexistante. Si le catalogue n'est pas
   disponible, l'éditeur fonctionne avec ses seuls éléments BPMN standards.
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
> dynamiques ou les intervenants d'un dossier depuis le BPMN, voir le guide pratique
> [docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md).

### Déployer un diagramme BPMN (upload direct)

Une fois un fichier `.bpmn20.xml` obtenu (édité dans Myrmacta ou avec un outil externe), deux
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
- **Variables de démarrage**. Si le diagramme déployé déclare des variables (`flowable:startVariable`,
  voir le guide de conception), le dialogue les affiche comme un formulaire : le libellé, une étoile si la
  valeur est obligatoire, et un champ texte. Une obligatoire vide n'est pas envoyée. Si le diagramme n'en
  déclare aucune, une section repliée permet d'ajouter des lignes *nom / valeur* (texte), par exemple une
  adresse ou une priorité attendue par le processus. Le nom accepte lettres, chiffres et `_` ; les noms
  réservés de la plateforme (`dossierId`, `dossierWorkflowLinkId`, `tenantId`, `dossierFields`,
  `connectorResult`, `generatedDocumentId`) et les doublons sont refusés avant l'envoi. Un paramètre écrit dans
  le BPMN prime toujours sur une variable de même nom. Une adresse `notificationTo` doit être une seule
  adresse (`nom@domaine`) ; un `documentType` doit faire partie des templates autorisés du type de dossier.

### Suivre l'exécution

Toujours sur la fiche dossier :

- **Diagramme BPMN** — vue en lecture seule (bpmn-js), avec surbrillance des étapes actives,
  terminées et en erreur. Affiché uniquement si une instance de workflow est démarrée sur ce
  dossier.
- **Tâches** — liste des tâches humaines en cours, bouton **Compléter**. L'assigné s'affiche par
  son nom quand il est connu (« vous » pour soi-même).
  - **M'assigner** — tout utilisateur qui peut modifier le dossier (ACL **EDIT** du type, ou rôle
    `TENANT_ADMIN`/`WORKFLOW_ADMIN`) peut prendre une tâche **sans assigné** ; elle apparaît alors
    dans son menu **Tâches**. Une tâche déjà prise par quelqu'un d'autre ne se prend pas (message
    d'erreur) : elle se réassigne.
  - **Réassigner** (`TENANT_ADMIN`/`WORKFLOW_ADMIN`) — choisir le nouvel assigné dans une liste
    déroulante. La liste ne propose que les utilisateurs **déjà connectés au moins une fois** à
    l'application et qui **peuvent modifier ce dossier** (inutile d'assigner une tâche à quelqu'un
    qui ne pourrait pas la terminer). Un utilisateur qui n'apparaît pas doit d'abord se connecter.
  - **Réaffecter au groupe** (TODO §114) — tout utilisateur qui peut modifier le dossier choisit un autre groupe pour la tâche
    (le groupe actuel s'affiche sous la tâche). La tâche redevient libre et n'est plus destinée qu'à ce groupe. Les tâches
    suivantes du même workflow iront aussi à ce groupe. Une **note** s'ajoute aux éléments du dossier : qui, quand, de quel
    groupe (et de quel assigné) vers lequel. C'est le geste de la file de tri de la mail room : renvoyer un courrier au
    service compétent.
- **Historique** — frise chronologique des événements du dossier.
- **Étapes en échec** (TODO §94) — bandeau rouge au-dessus du diagramme quand une étape automatique
  (e-mail, connecteur, génération…) a échoué ; l'étape est aussi marquée en erreur dans le diagramme.
  Deux états : **nouvelle tentative prévue** (le moteur réessaiera seul, n tentatives restantes) et
  **incident** (plus de tentative automatique). Visible de quiconque voit les tâches de l'exécution ;
  le **message technique** n'est montré qu'aux `TENANT_ADMIN`/`WORKFLOW_ADMIN`. Une fois la cause
  corrigée (par exemple une adresse e-mail mal saisie dans le dossier), un administrateur clique sur
  **Rejouer** : l'étape repart et le workflow reprend. Le rejeu est journalisé. Juste après, l'étape peut
  apparaître quelques secondes en « nouvelle tentative prévue », le temps que le moteur la reprenne ; la fiche se
  met à jour seule. Un échec dans un sous-process appelé s'affiche sur l'étape d'appel (« Appel › étape »).

Tous les incidents du tenant se retrouvent dans **Admin → Incidents** (`/admin/incidents`,
`TENANT_ADMIN`/`WORKFLOW_ADMIN`) : dossier (lien vers la fiche), workflow, étape, état, date, message et
action **Rejouer**. La liste porte sur les 1 000 échecs les plus récents.

Un utilisateur peut aussi retrouver l'ensemble de ses tâches assignées, tous dossiers confondus,
depuis **Tâches** (`/tasks`) — pratique en mobilité.

### Construire une table de décision DMN (TODO §61)

Écran **Tables de décision** (`/admin/dmn-editor`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`) — même
principe que l'éditeur BPMN, avec l'éditeur `dmn-js` :

1. **Trois points d'entrée**, comme pour le BPMN : table vierge (à l'ouverture de l'écran), import
   d'un fichier `.dmn` local, ou édition d'une table déjà déployée pour une clé de décision donnée
   — contrairement au BPMN (accessible depuis le lien **Éditer** de l'écran **Admin → Workflows**),
   il n'existe pas d'écran listant les tables déployées : le champ **Charger une table existante**
   de la barre d'outils (clé de décision saisie à la main, puis bouton **Charger**) est le point
   d'entrée pour ce 3ᵉ cas.
2. L'éditeur `dmn-js` gère la table (colonnes d'entrée/sortie, règles, hit policy) — pas de
   panel de propriétés custom Myrmacta ici, le `.dmn` est du DMN 1.3 standard.
3. Bouton **Enregistrer le brouillon** — même mécanique qu'en BPMN (un brouillon par clé de
   décision, pas d'historique, pas d'autosave).
4. Bouton **Déployer** — revalidation côté serveur (schéma DMN + allowlist JUEL sur chaque
   cellule, voir ci-dessous) puis déploiement ; supprime le brouillon de cette clé.
5. Bouton **Tester** — zone JSON des entrées et sélecteur de version (défaut : dernière version
   déployée). Le résultat est une liste de lignes, vide si aucune règle ne correspond. Seules les
   tables déjà déployées sont évaluées, pas le brouillon.
6. Bouton **Jeu de tests** — envoi d'un fichier `.json` (`cases` avec `inputs` et `expectedRows`)
   ou `.csv` (colonnes `input.<nom>` et `expected.<nom>`, une ligne attendue par cas). Le rapport
   indique pour chaque cas s'il est réussi ou en échec. Cent cas maximum.
7. Bouton **Historique** — versions déployées, de la plus ancienne à la plus récente. **Redéployer
   la version N** demande une confirmation. Annuler n'envoie rien. Confirmer republie le XML de
   cette version comme nouvelle version courante, après la même allowlist JUEL. Ce n'est pas une
   suppression : les versions précédentes restent dans l'historique du moteur.

Une fois déployée, la table est invoquée depuis un diagramme BPMN via un `serviceTask
flowable:type="dmn"` — voir le champ **Clé de la table de décision** dans le panel de propriétés
de l'éditeur BPMN (§« Construire ou modifier un diagramme BPMN » ci-dessus), et le guide pratique
[docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md) §7bis/§7ter pour quand préférer une table de
décision à une cascade de passerelles.

> **Sécurité :** contrairement à ce que le standard DMN laisse penser, Flowable évalue les
> cellules d'une table de décision en **JUEL**, pas en FEEL — chaque cellule d'entrée/sortie doit
> rester un littéral simple (nombre, texte entre guillemets, booléen), un `-`, ou l'un de ces
> littéraux précédé d'un opérateur de comparaison. Toute expression plus riche est rejetée au
> déploiement (même logique que la validation déjà en place pour les expressions BPMN).

------

### Brouillons à publier — relire ce qu'un agent propose (TODO §82)

*Admin → Brouillons* (rôles TENANT_ADMIN ou WORKFLOW_ADMIN) liste les processus BPMN et tables de décision DMN **en attente de publication** :
type, clé, **origine** (Éditeur, Agent (MCP), Assistant), auteur, date, et l'**écart indicatif** avec la version en ligne (lignes ajoutées /
retirées). Un agent branché au MCP n'écrit **jamais** en ligne par défaut : `deploy_workflow`, `deploy_decision_table` et
`redeploy_decision_table` déposent un brouillon, avec — pour un processus — la configuration de workflow proposée.

- **Ouvrir** la clé dans l'éditeur BPMN / DMN pour relire ou corriger. Si vous enregistrez, le brouillon devient le vôtre (origine Éditeur) et
  la configuration de workflow proposée est conservée.
- **Publier** : le déploiement porte exactement sur ce que vous avez relu ; si le brouillon a été modifié entre-temps, la publication est refusée
  (rechargez et relisez). La configuration proposée est appliquée juste avant le diagramme, après validation.
- **Écarter** : supprime le brouillon (définitif).
- Un agent ne peut pas écraser le brouillon d'un autre auteur sans le demander explicitement.
- Les brouillons d'agent ou d'assistant non touchés depuis 30 jours sont purgés ; ceux de l'éditeur, jamais.
- *Dernières publications* : qui a rédigé, qui a publié, quand, quelle version, et si la publication était relue ou **directe**.

### Promotion de configuration — de la recette à la production (TODO §83, §117)

*Admin → Promotion* (rôle TENANT_ADMIN) exporte la configuration d'un environnement et l'importe sur un autre.

1. **Exporter** (sur la recette) : nommez l'environnement, puis téléchargez le bundle (ZIP). Ce sont des fichiers lisibles, que vous pouvez
   versionner dans Git. Il contient :
   - les groupes d'utilisateurs, **sans leurs membres** ;
   - les types de dossier, avec leur workflow « réponse » et leur échéance ;
   - les schémas de questions ;
   - la **version active** des templates de documents ;
   - les connecteurs, **sans leurs secrets** ;
   - les tables DMN et les processus BPMN **déployés**, et les configurations de workflow ;
   - la définition des parcours.

   Le bundle n'est pas signé : transportez-le par un canal de confiance. Ni les ACL ni les réglages du tenant (rétention, SLA, webhooks) n'y figurent.
2. **Planifier** (sur la production) : déposez le ZIP. Le plan **n'écrit rien** : pour chaque artefact, *Créer*, *Modifier*, *Inchangé*, *Conflit*
   (ex. un type dont le nouveau schéma casserait des dossiers existants — jamais forcé) ou *Bloqué* (secret requis, table DMN à publier d'abord,
   paramètre manquant, validation refusée), avec la raison.
   Le plan avertit aussi (`WARNING:` en tête du détail, l'artefact reste appliqué) quand une référence n'existe ni ici ni dans le bundle :
   - un groupe nommé par une table de routage, l'attribut `groupeTri` ou un `candidateGroups` ;
   - une clé de template d'un type sans version active.

   L'avertissement est repris dans le rapport d'application et dans l'historique des imports.

   Un workflow « réponse » ou une échéance dont le type, le process, le type enfant, le champ date ou le groupe d'escalade manque est
   *Bloqué*.
3. **Paramètres d'environnement** : une valeur de la forme `{{url_erp}}` est remplacée à l'import par le paramètre du même nom (JSON
   `{"url_erp": "https://…"}`) ; un paramètre absent bloque l'artefact. Cela vaut pour les fichiers de connecteur et pour le groupe de
   l'IdP d'un groupe d'utilisateurs, dont le nom diffère souvent entre recette et production.
4. **Appliquer** :
   - les types, schémas, groupes, workflows « réponse », échéances et parcours sont écrits ;
   - **les BPMN et DMN deviennent des brouillons** (*Admin → Brouillons*, origine « Import ») à relire et publier ;
   - **un template devient une nouvelle version brouillon**, à activer dans *Admin → Templates* ;
   - un connecteur est créé **désactivé** : relisez ses adresses, saisissez ses secrets si besoin, puis activez-le ;
   - un groupe est créé **sans membres**. S'il existe déjà, seul son libellé change : son groupe de l'IdP est conservé ;
   - un parcours est enregistré **sans être compilé** : le processus qui tourne est le BPMN du bundle.

   Les secrets requis à la création se saisissent dans la page (envoyés avec l'application, jamais conservés) ou plus tard dans *Connecteurs*.
5. **Rejouer est sans danger** : un second import converge (tout *Inchangé*). Si un artefact échoue, les autres sont appliqués et le rapport dit lequel ;
   corrigez et relancez. Plusieurs cas demandent un second passage :
   - un BPMN qui dépend d'une table DMN encore en brouillon : publiez le brouillon, puis relancez l'import ;
   - un BPMN qui dépend d'un template apporté par le bundle : relancez, puis activez le template avant de publier le processus ;
   - un workflow « réponse » dont la configuration arrive avec un brouillon BPMN : publiez le brouillon, puis relancez.

   Rien n'est jamais supprimé sur la cible.

*Derniers imports* : qui, quand, quel bundle, combien d'artefacts appliqués, inchangés, ignorés, en échec.

## 4. Configurer un connecteur

Écran **Admin → Connecteurs** (`/admin/connectors`, `TENANT_ADMIN`).

### Catalogue des types disponibles

La partie haute de l'écran liste les **types de connecteurs** installés sur l'instance (pas les
connecteurs eux-mêmes — leurs instances configurées). Types fournis de base :

| Type | Rôle (`capability`) | Champs de config (formulaire généré) |
|---|---|---|
| `http-rest` | trigger **et** service (`BOTH`) | `webhookEnabled` (case cochée par défaut ; décochée = instance « service seul », voir ci-dessous), `baseUrl` (optionnel, appels SERVICE), `webhookAuthType` (liste HMAC / token), `webhookSecret`, `processKey`, `dossierTypeCode` (ces quatre-là exigés seulement si le webhook est actif), `authHeaderValue` (optionnel, mot de passe — appels SERVICE), `authMode` et champs `oauth*` (authentification OAuth2, voir ci-dessous), `mapping` (optionnel) |
| `flowable-signal` | trigger **et** service | `signalName`, `processKey`, `dossierTypeCode`, `mapping` (optionnel) |
| `scheduler` | trigger | `cronExpression`, `processKey`, `dossierTypeCode` |
| `flowable-callback` | callback | `webhookAuthType`, `webhookSecret`, `messageName`, `correlationVariableName`, `correlationValuePath`, `mapping` (optionnel) |
| `smtp` | service | aucun champ d'instance (réutilise la config SMTP de l'application) |
| `document-storage` | service | aucun champ d'instance (réutilise le stockage documents déjà configuré) ; depuis un process, ne relit que ce qu'il a stocké pour le même dossier |
| `graph-mail-inbound` | trigger (e-mail entrant, Microsoft Graph) | `mailbox`, `graphBaseUrl`, `folder`, `processedFolder`, `rejectedFolder`, OAuth2 (`oauthTokenUrl`, `oauthClientId`, `oauthClientSecret` ou certificat, `oauthScope`), puis les mêmes réglages de routage que `imap-inbound` (`pollSeconds`, `maxAttachments`, `requireAuthenticatedSender`, `trustedAuthservId`, `referencePattern`, `processKey`, `dossierTypeCode`, `mapping`) |
| `imap-inbound` | trigger (e-mail entrant) | `host`, `port`, `security` (SSL / STARTTLS), `username`, `authMode` (mot de passe ou OAuth2), `password`, `folder`, `processedFolder`, `rejectedFolder`, `pollSeconds`, `referencePattern`, `processKey`, `dossierTypeCode`, `mapping` |
| `sftp-pdf-inbound` | trigger (PDF numérisés, SFTP) | `sftpHost`, `sftpPort`, `sftpUsername`, `sftpAuthType`, `sftpPassword` ou `sftpPrivateKey`, `sftpHostKeyFingerprint`, `remoteDirectory`, `processedDirectory`, `rejectedDirectory`, `indexFormat` (JSON / CSV / NONE), `csvPattern`, `csvFileColumn`, `requireIndex`, `pollSeconds`, `processKey`, `dossierTypeCode`, `mapping`, `defaultFields` |
| `mail-deposit` | trigger (écran « Déposer ») | `processKey`, `dossierTypeCode`, `senderField` (défaut `expediteur`), `subjectField` (défaut `objet`), `defaultFields` |
| `azure-service-bus` | trigger (bus Azure) | `namespaceHost`, `entityPath`, `authMode` (SAS ou OAuth2), `sasKeyName`, `sasKey`, `pollSeconds`, `processKey`, `dossierTypeCode`, `mapping` |

**Appeler une application depuis un processus : instance « service seul » et URL de base (TODO §112, issue #260).** Pour un
connecteur `http-rest` qui ne sert qu'à appeler une API (SIRH, IAM, ITSM, ERP…) :

- décocher **`webhookEnabled`** : le secret de webhook, `processKey` et `dossierTypeCode` ne sont plus demandés, aucune URL webhook
  n'est affichée et le webhook entrant de cette instance répond `404`, même si un ancien secret est resté ;
- renseigner **`baseUrl`** (`https://rh.exemple.com/api/v2`, https, adresses internes refusées, sans identifiants, `?` ni `#`) : le
  processus ne donne plus qu'un chemin relatif (`/employees/{id}`) ; une URL absolue est refusée, et l'en-tête d'authentification
  ne part donc jamais vers un autre hôte. Changer d'environnement (recette, production) revient à changer `baseUrl`, pas le BPMN.
  Depuis un processus, une instance avec `baseUrl` n'accepte que la requête écrite dans le BPMN (`connectorRequest`), jamais
  une variable saisie. **Sans `baseUrl`, l'en-tête d'authentification part vers l'URL donnée à chaque appel** : réservez ce
  mode aux appels dont l'URL est fixée par le concepteur.

Méthodes acceptées : `GET`, `POST`, `PUT`, `PATCH` et `DELETE` (sans corps pour `GET` et `DELETE` ; `PATCH` porte un corps,
comme `POST` et `PUT` — mises à jour partielles OData). Un exemple complet coordonne trois applications avec des validations
humaines : [exemples/onboarding-collaborateur](../../exemples/onboarding-collaborateur/README.md).

**Appeler une API protégée par OAuth2 (Microsoft Entra ID : D365 F&O, Dataverse, Graph).** Sur un connecteur `http-rest`, choisir
`authMode = OAUTH2_CLIENT_CREDENTIALS` : Myrmacta obtient lui-même le jeton d'accès, le garde en mémoire et le renouvelle avant son
échéance ; vous n'avez plus de jeton à recopier. Renseigner :

- `oauthTokenUrl` — `https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token` (https obligatoire, adresses internes refusées) ;
- `oauthClientId` et `oauthScope` (ex. `https://<org>.operations.dynamics.com/.default`) ;
- l'authentification de l'application (`oauthClientAuthMethod`) : `CLIENT_SECRET_POST` (défaut) ou `CLIENT_SECRET_BASIC` avec `oauthClientSecret`,
  ou **`PRIVATE_KEY_JWT`** (certificat) avec `oauthPrivateKey` (clé privée PEM PKCS#8 RSA) et `oauthCertThumbprint` (empreinte SHA-1 ou SHA-256 en
  hexadécimal, telle qu'affichée par le portail Entra) ;
- **`oauthAllowedHosts`** (obligatoire) — les noms d'hôtes qui peuvent recevoir le jeton, séparés par des virgules (`monorg.operations.dynamics.com,
  *.crm4.dynamics.com`). Un appel vers tout autre hôte est refusé **avant** toute demande de jeton : un process ne peut pas envoyer votre jeton ailleurs.

`oauthClientSecret` et `oauthPrivateKey` sont chiffrés au repos et n'apparaissent jamais ensuite (affichés `********`). Un éventuel `authHeaderValue` hérité est ignoré dans
ce mode (on peut donc migrer une instance existante). Les jokers d'`oauthAllowedHosts` couvrent **un seul niveau** (`*.crm4.dynamics.com`) et sont refusés sur les domaines
d'hébergement mutualisé (`*.azurewebsites.net`, `*.herokuapp.com`…). Si l'API répond `401`, le jeton est renouvelé (s'il a plus de 30 s) et l'appel rejoué **une seule fois**. Sans `authMode`, rien ne change (en-tête statique).

D'autres connecteurs custom peuvent être ajoutés au classpath backend sans modifier le core (voir
[docs/specs/06-connecteurs.md](../specs/06-connecteurs.md)) — ils apparaîtront alors dans
ce même catalogue, avec un formulaire généré depuis leur `configSchema`.

### Créer une instance de connecteur

1. **Nom** — libellé libre de l'instance.
2. **Type** — sélectionné dans le catalogue ci-dessus. Changer de type réinitialise les champs
   de configuration (les valeurs du type précédent ne sont pas réutilisées).
3. **Config** — **formulaire champ par champ** généré depuis le schéma du type : texte, mot de
   passe, liste, URL, entier, case à cocher. Un texte d'aide s'affiche sous le champ lorsqu'il
   est fourni par le schéma (ex. longueur minimale du secret HMAC). Les types sans configuration
   d'instance (`smtp`, `document-storage`) n'affichent aucun champ — valider suffit.

   La validation métier (secret trop court, cron invalide, etc.) reste faite **par le serveur** :
   le formulaire bloque seulement les champs requis vides. Les secrets ne sont jamais réaffichés
   dans la liste après création.
4. Valider — le connecteur est créé actif par défaut.

Dans la liste, chaque connecteur affiche son nom, son type, son statut et les **clés** des
secrets configurés (jamais leur valeur — tout champ se terminant par `password`/`secret` est
masqué). Boutons **Basculer** (activer/désactiver) et **Supprimer**.

Après création, l'écran affiche aussi l'URL d'entrée HTTP quand le type en a une :

| Type | URL affichée (à donner au partenaire) |
|---|---|
| `http-rest` | `POST https://{hôte}/{tenantId}/api/webhooks/{id}` — **URL webhook** |
| `flowable-callback` | `POST https://{hôte}/{tenantId}/api/callbacks/{id}` — **URL callback** |

Le préfixe `{tenantId}` est celui du reverse-proxy (chemin du tenant, décision n° 14) ; s'il
n'y en a pas, le chemin commence à `/api/…`. Une instance `http-rest` « service seul » (`webhookEnabled` décoché) n'affiche pas
d'URL webhook. Un bouton **Copier** recopie l'URL (sans le mot
`POST`). Les autres types (`smtp`, `document-storage`, `scheduler`, `flowable-signal`) n'ont
pas d'endpoint HTTP entrant.

### Recevoir des e-mails avec Microsoft Graph : le connecteur `graph-mail-inbound` (TODO §87)

Pour une boîte **Exchange Online**, `graph-mail-inbound` fait la même chose que `imap-inbound` (voir ci-dessous : dossier créé ou alimenté par la référence de l'objet, pièces
jointes contrôlées, réponses automatiques refusées, expéditeur attesté, rien jamais supprimé) en passant par l'API Microsoft Graph plutôt que par IMAP. Différences :

- **Authentification** : application Entra ID, OAuth2 `client_credentials` uniquement (`oauthScope = https://graph.microsoft.com/.default`), permission d'application `Mail.ReadWrite`.
  **Restreignez-la à la boîte voulue** (stratégie d'accès d'application Exchange Online), sinon l'application peut lire toutes les boîtes du tenant.
- **Boîte** : `mailbox` (adresse de la boîte, boîte partagée comprise). `folder` vaut `Inbox` ou le nom d'un dossier de premier niveau qui doit exister ; `processedFolder` et `rejectedFolder`
  sont des dossiers de premier niveau créés au besoin. `graphBaseUrl` n'accepte que les hôtes Microsoft Graph, en https.
- **Rangement et idempotence** : le message est déplacé (jamais supprimé) ; un message remis reçoit d'abord la catégorie Outlook `Myrmacta-delivered`, de sorte qu'un déplacement qui échoue ne provoque pas de seconde remise.
  La seule fenêtre résiduelle est l'échec simultané du marquage **et** du déplacement (panne de Graph entre les deux) : le message serait alors remis au tour suivant — comme en IMAP, il n'y a pas de détection de doublon par `Message-ID`.
  Il n'y a pas de curseur à conserver : un message pris en charge quitte le dossier lu. Pour rejouer un message déjà traité, remettez-le dans le dossier lu **et retirez-lui la catégorie** `Myrmacta-delivered`.
  Une catégorie posée avant le renommage des identifiants est encore reconnue ([docs/RENAMING.md](../RENAMING.md)).
  Un message qui ne peut pas être déplacé (verrou, conflit) est signalé dans les journaux et laissé en place, sans bloquer les suivants ; un message de plus de 4 Mo (corps et en-têtes) est refusé.
- **Pièces jointes** : seules les pièces *fichier* sont prises ; les images incrustées (logos de signature) sont ignorées, les messages joints et liens sont comptés dans `inboundAttachmentsSkipped`.
  Le total des pièces d'un message est borné (25 Mo par défaut), quelle que soit la taille annoncée.
- **`Authentication-Results`** : Graph expose les en-têtes du message ; `trustedAuthservId` est l'identifiant que **votre** serveur de réception écrit en premier (Exchange Online : celui qui figure
  dans les en-têtes d'un message reçu, souvent `spf.protection.outlook.com`). Myrmacta retient le **premier** en-tête `Authentication-Results` tel que Graph le restitue (ordre du message) :
  vérifiez-le avec un message de test dont l'expéditeur ajoute un faux en-tête, avant de compter sur le rattachement par référence.

### Recevoir des e-mails : le connecteur `imap-inbound` (TODO §87)

Un message reçu dans une boîte crée un **dossier** (ou alimente un dossier existant) et démarre le processus choisi. Myrmacta interroge la boîte
toutes les `pollSeconds` secondes (30 au minimum).

- **Connexion** : `host`, `port` (993), `security` — `SSL` (IMAPS) ou `STARTTLS` (jamais facultatif) ; le certificat du serveur est toujours vérifié.
  Le serveur doit répondre sur une adresse publique. **Authentification** : mot de passe, ou **OAuth2** (`authMode = OAUTH2_CLIENT_CREDENTIALS`, application
  Microsoft Entra ID, `oauthScope = https://outlook.office365.com/.default`) — Exchange Online n'accepte plus le mot de passe seul.
- **Rangement** : un message pris en charge est déplacé dans `processedFolder` (`Myrmacta-processed`), un message refusé dans `rejectedFolder`
  (`Myrmacta-rejected`) ; **rien n'est jamais supprimé**. Sont refusés : les réponses automatiques (absence, accusés), les listes de diffusion, les messages sans expéditeur,
  trop gros (25 Mo) ou dont un champ mappé est vide.
- **Corrélation** : `referencePattern` est une expression régulière à **un seul groupe** appliquée à l'objet, par exemple `\[(DOS-[0-9-]+)\]` : une réponse
  « RE: [DOS-2026-0042] … » est ajoutée à ce dossier ; une référence inconnue (faute de frappe) crée un nouveau dossier plutôt que de perdre le message.
- **Contenu** : `mapping` associe des champs du dossier ou des variables du processus à l'objet (`$.subject`), au texte (`$.body`, texte brut, 20 000 caractères),
  à l'expéditeur (`$.from`, `$.fromName`), à la date (`$.sentAt`). Les variables `mailMessageId`, `mailFrom` et `mailSubject` sont toujours posées.
- **Fil de discussion** (TODO §113) : les en-têtes `In-Reply-To` et `References`, et le `conversationId` de Microsoft Graph, sont lus et bornés : identifiants
  `<…>` seulement, 250 caractères chacun, les 10 références les plus récentes. Le processus voit `mailInReplyTo`, `mailReferences` (séparées par une espace) et
  `mailConversationId` quand ils existent. Ils sont aussi disponibles pour `mapping` (`$.inReplyTo`, `$.references`, `$.conversationId`), mais ne les mappez pas sur
  un champ du dossier : vides pour un nouveau message, ils feraient refuser celui-ci. Ils ne rattachent encore rien seuls.
- **Pièces jointes** : jusqu'à `maxAttachments` (5) par message, 10 Mo chacune ; elles passent les mêmes contrôles que les pièces ajoutées à la main (types autorisés,
  contenu conforme au type annoncé, antivirus). Une pièce refusée n'empêche pas les autres ni le dossier : le processus voit `inboundAttachmentsRejected` /
  `inboundAttachmentsSkipped`.
- **Expéditeur et rattachement.** L'adresse « De » d'un e-mail est falsifiable. Par défaut (`requireAuthenticatedSender = true`), une référence dans l'objet n'ajoute le
  message à un dossier existant **que si l'en-tête `Authentication-Results` écrit en premier par VOTRE serveur de réception atteste l'expéditeur** : son identifiant de serveur
  (`trustedAuthservId`, à relever dans les en-têtes d'un message reçu, avant le premier `;`) doit être celui que vous configurez, et il faut `dmarc=pass`, ou `dkim=pass` /
  `spf=pass` **aligné sur le domaine de l'adresse « De »**. Sinon un nouveau dossier est créé et le processus voit `mailReferenceIgnored` et `mailSenderAuthenticated = false`
  (toujours posée). `trustedAuthservId` est **obligatoire** dès qu'un `referencePattern` est défini (sauf `requireAuthenticatedSender = false`, qui revient à accepter que **quiconque
  devine une référence puisse alimenter ce dossier**). Cela suppose que votre serveur ajoute lui-même cet en-tête en tête et retire ceux qui prétendent venir de lui.
- **Idempotence du rangement.** Un message déjà remis dont le rangement échoue (quota, droits) est marqué du mot-clé IMAP `MyrmactaDelivered` : il n'est plus remis, seul le rangement est
  réessayé. Le mot-clé posé avant le renommage des identifiants est encore reconnu ([docs/RENAMING.md](../RENAMING.md)). Les messages marqués supprimés ne comptent pas dans la limite d'un lot.
- **Fiabilité.** Le message n'est rangé dans « traités » qu'**après** l'enregistrement du dossier. S'il ne peut pas être pris en charge (type de dossier inconnu, champ invalide, base
  indisponible), il est rangé dans **`Myrmacta-rejected`** (avec une alerte dans les journaux) : replacez-le dans la boîte après correction pour le rejouer. Un message illisible
  (encodage inconnu, MIME trop imbriqué) y va aussi, sans bloquer les autres. Une instance en échec (mot de passe expiré, hôte injoignable) est réessayée à son propre intervalle
  `pollSeconds`, jamais plus souvent. Il n'y a pas de détection des doublons par `Message-ID`. Les e-mails envoyés par Myrmacta portent `Auto-Submitted: auto-generated` : ils ne
  rebouclent pas.

### Recevoir des PDF numérisés : le connecteur `sftp-pdf-inbound` (TODO §113)

Le partenaire de numérisation dépose ses PDF dans un dossier SFTP. Chaque PDF y devient un dossier (un courrier) avec le PDF
en pièce jointe, puis suit le process indiqué (`processKey`), comme un e-mail.

- **Rangement :** un PDF pris en charge passe dans `processedDirectory`, un PDF refusé dans `rejectedDirectory`. Rien
  n'est supprimé. Sont refusés : un fichier qui n'est pas un PDF, un fichier trop volumineux, un index illisible, un champ
  mappé vide sans valeur par défaut et un PDF écarté par les contrôles des pièces (antivirus, contenu). Un PDF refusé ne
  crée pas de courrier.
- **Index du partenaire (`indexFormat`) :**
  - `JSON` : un fichier de même nom à côté du PDF (`facture-117.pdf` et `facture-117.json`) ;
  - `CSV` : un fichier par lot, une ligne par PDF, avec une colonne `fichier` (réglable : `csvFileColumn`) qui nomme le
    PDF. Déposez le CSV **après** les PDF : une ligne dont le PDF n'est pas encore là est ignorée ;
  - `NONE` : pas d'index.
- **Index obligatoire (`requireIndex`, coché par défaut) :** un PDF sans index attend le sien. Décoché, il est traité
  tout de suite, sans métadonnées.
- **Champs du dossier :**
  - `mapping` lit `{"fichier": nom du PDF, "index": {…}}`, par exemple `"expediteur": "$.index.expediteur"` ;
  - `defaultFields` donne des valeurs fixes, par exemple `{"canal": "PAPIER"}` ;
  - le nom du PDF est aussi dans la variable `courrierFichier`.
- **Sécurité :** l'empreinte de la clé hôte SFTP est obligatoire, et vérifiée à chaque connexion.
- **Exemple :** [`exemples/digital-mail-room/config/connecteur-sftp-pdf.exemple.json`](../../exemples/digital-mail-room/config/connecteur-sftp-pdf.exemple.json).

### Déposer un courrier papier : l'écran « Déposer un courrier » et le connecteur `mail-deposit` (TODO §113)

Un agent qui reçoit un courrier papier déjà numérisé le dépose depuis **Déposer un courrier** (menu principal) :

1. choisir le **point de dépôt** (choisi d'office s'il n'y en a qu'un) ;
2. joindre un ou plusieurs **PDF**, 20 au plus ;
3. saisir l'**expéditeur** et l'**objet**, tous deux facultatifs ; l'objet vaut, à défaut, le nom du fichier ;
4. **Déposer**.

Chaque PDF devient un courrier, avec le PDF en pièce jointe, et suit le même tri que l'e-mail. Le résultat donne, fichier
par fichier, la référence du courrier créé (avec un lien) ou la raison du refus : pas un PDF, trop volumineux, champ
obligatoire manquant. Un fichier refusé n'empêche pas les autres.

**Créer un point de dépôt (administrateur) :** créer un connecteur **Dépôt manuel de courrier** (`mail-deposit`) avec :

- le type de dossier et le process ;
- les champs qui reçoivent l'expéditeur et l'objet ;
- des valeurs fixes, par exemple `{"canal": "PAPIER"}`.

Puis donner l'ACL **EDIT** de ce type aux agents concernés : un agent ne voit que les points de dépôt dont il peut créer
le type.

Exemple : [`exemples/digital-mail-room/config/connecteur-depot.exemple.json`](../../exemples/digital-mail-room/config/connecteur-depot.exemple.json).

### Recevoir des événements du bus Azure : le connecteur `azure-service-bus` (TODO §87)

Pour les **Business Events de D365 F&O** (et tout message JSON publié sur Service Bus) sans relais. Renseigner l'espace de noms (`xxx.servicebus.windows.net`, domaines
Service Bus uniquement), la file ou l'abonnement (`file` ou `rubrique/subscriptions/abonnement`), puis l'authentification :

- **SAS** : nom et clé d'une stratégie d'accès partagé qui n'a que le droit *Écouter* ;
- **OAuth2** : application Entra ID avec le rôle *Azure Service Bus Data Receiver* (`oauthScope = https://servicebus.azure.net/.default`).

Le `mapping` est celui de Kafka : chemins JSON sur le corps du message (`fields`, `variables`, `processKeyPath`, `dossierTypeCodePath`,
`existingDossierReferencePath`). Un message est **retiré de la file après l'enregistrement du dossier** ; un message illisible, incomplet, ou que le runtime ne peut pas prendre
en charge est **rendu au courtier**, qui le place en file de lettres mortes après son `MaxDeliveryCount` : à surveiller côté Azure. Un incident passager de Myrmacta (base indisponible quelques minutes) consomme des livraisons : prévoyez un
`MaxDeliveryCount` confortable (10 ou plus). Aucun jeton ni clé n'est jamais affiché ni journalisé.

### Appeler un connecteur `http-rest` (webhook entrant)

L'URL webhook complète est visible dans la liste dès que l'instance existe. L'appel doit
porter un en-tête `Idempotency-Key` et l'authentification déclarée (`webhookAuthType`) ; la
taille du corps et le débit sont limités par la configuration serveur (voir
[docs/guides/GETTING-STARTED.md](GETTING-STARTED.md) pour les paramètres correspondants).

Pour `HMAC_SHA256`, la signature (`X-Signature-256`) couvre
`timestamp + "." + Idempotency-Key + "." + body` (`X-Webhook-Timestamp` = secondes epoch Unix,
fenêtre de 5 minutes). C'est une **rupture de contrat** par rapport à l'ancien schéma
`timestamp + "." + body` : les partenaires doivent inclure la clé d'idempotence dans le HMAC.
`SHARED_SECRET_TOKEN` reste un jeton nu ; un même corps rejoué dans la fenêtre de 5 minutes
est rejeté même avec une nouvelle `Idempotency-Key`.

------

## 5. Configurer un template de document

Écran **Admin → Templates** (`/admin/templates`, `TENANT_ADMIN`).

### Créer un template

| Champ | Rôle |
|---|---|
| `templateKey` | identifiant technique, référencé ensuite depuis le workflow ou la génération de document |
| `libellé` | nom affiché |
| `Contenu` | **éditeur visuel** (LC4b, TODO §84) ou source HTML/Thymeleaf — voir ci-dessous |

Un panneau d'**aperçu en direct** affiche le rendu du HTML au fur et à mesure de la saisie.

**Éditeur visuel.** Barre d'outils : gras, italique, souligné, titres, listes, alignement, tableaux, ligne horizontale,
**saut de page**. Quatre insertions guidées, alimentées par les *variables d'exemple* de l'aperçu : une **variable**
(`client.nom`), un **bloc conditionnel** (si / sauf / égal à une valeur), un **bloc répété** sur une liste, une **ligne de
tableau répétée**. Le HTML produit est du Thymeleaf standard, accepté par la liste blanche d'expressions inchangée. Le bouton
*Source HTML* donne accès au texte brut. **Un template écrit à la main que l'éditeur ne saurait pas réécrire sans rien
perdre** (balise ou attribut non pris en charge, `th:if` et `th:each` combinés, en-tête de tableau `thead`, expression en
ligne `[[…]]`…) **reste en mode source, intact**, avec la raison affichée. Les fragments (`th:replace`, `th:insert`,
`th:include`) et l'interpolation non échappée (`th:utext`, `[( … )]`) sont refusés à l'activation.

**Depuis un agent (MCP).** `define_document_template` écrit le HTML en **brouillon** (jamais en ligne : si la dernière
version est active, une nouvelle version brouillon est créée) et signale un éventuel problème de syntaxe ; `preview_document`
rend un HTML en direct avec des variables d'exemple. L'**activation reste un geste humain**.

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
  **Filtres sur les champs** (TODO §81) : une fois un type choisi, la liste propose un filtre pour chaque champ que
  l'administrateur a déclaré **Cherchable** (texte : « commence par » ; nombre et date : de / à ; choix et oui/non). Cinq
  filtres au plus ; un champ sensible n'est jamais cherchable. Les filtres figurent dans l'URL (`f.montant.gte=5000`).
- **Tâches** (`/tasks`) — vue personnelle des tâches actuellement assignées à l'utilisateur
  connecté, avec accès direct au dossier concerné, bouton **Compléter**, et un rappel SLA
  (`dans N h` / `dépassée`) lorsque le type de dossier a un objectif en heures. Depuis TODO §52,
  une tâche dont le diagramme BPMN déclare un formulaire (voir
  [docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md) §3bis) affiche ce formulaire à la place du
  bouton nu — édition des champs du dossier, ou choix d'un document à générer parmi ceux autorisés
  pour son type (§1). Une tâche sans formulaire déclaré garde le bouton **Compléter** habituel.
  **Tâches de groupe** (TODO §114) : une tâche sans assigné destinée à l'un de vos groupes (« Groupes d'utilisateurs » ci-dessous) y figure aussi, marquée
  « À prendre », si vous pouvez modifier son dossier. **Prendre** vous l'assigne : elle quitte alors la liste des autres
  membres. Une tâche sans assigné qui n'est destinée à aucun de vos groupes n'y figure pas : la prendre avec **M'assigner**
  depuis la fiche dossier (§3).
- **Rapports & SLA** (`/rapports`) — snapshot de conformité et de délais sur une période
  (défaut 30 jours). Visible pour `TENANT_ADMIN`, `WORKFLOW_ADMIN` et `DOSSIER_MANAGER`
  (ce dernier ne voit que les types pour lesquels il a VIEW). Export CSV manuel disponible.
  La page affiche aussi deux compteurs opérationnels (jobs Flowable en dead-letter, tâches
  ouvertes sans assigné) et la liste des fichiers d'export BI déjà produits. Un gestionnaire
  ne voit et ne télécharge que le CSV SLA et les CSV de types sur lesquels il a VIEW.
  L'objectif de conformité tenant (défaut 95 %), les alertes de franchissement et l'export
  BI se configurent dans **Admin → Types**.

### Groupes d'utilisateurs (Admin → Groupes, `TENANT_ADMIN`) — TODO §114

Un groupe représente un service (RH, comptabilité, juridique…). Ses membres voient ses tâches libres dans **Tâches**.

- **Créer** : un **code** (2 à 64 caractères : majuscules, chiffres, `_`, commençant par une lettre, par exemple
  `COMPTABILITE`), un **libellé**, et facultativement un **groupe d'IdP repris**.
- **Membres listés** : choisir un utilisateur de l'annuaire, c'est-à-dire un utilisateur déjà connecté au moins une fois.
  500 membres listés au plus par groupe.
- **Groupe d'IdP repris** : les utilisateurs dont le jeton porte ce groupe en sont aussi membres, sans les lister. La
  claim lue se configure avec `APP_SECURITY_OIDC_GROUPS_CLAIM`, `groups` par défaut : mapper « Group Membership » chez
  Keycloak, claim `groups` chez Microsoft Entra ID. L'appartenance est celle de la **dernière connexion** de l'utilisateur.
  Chez Entra, la claim porte les identifiants (GUID) des groupes : c'est cette valeur qu'il faut saisir.
- **Qui reçoit une tâche de groupe :**
  - le groupe que la table de routage de la mail room a choisi (TODO §113), posé à l'exécution sur le process qu'elle
    démarre ;
  - ou un groupe nommé par le diagramme (`candidateGroups` littéral, le code du groupe).
- **Prendre une tâche :** il faut toujours le droit de modifier le dossier (§3). Être membre du groupe rend la tâche
  visible, ça ne donne pas de droit sur le dossier.

### Notifications (cloche, tous rôles)

La cloche affichée en haut de chaque page (icône PrimeIcons, badge rouge = nombre de notifications
non lues) donne accès à un historique persistant — TODO §66. Contrairement au comportement
précédent (purement en mémoire de l'onglet, perdu au reload), les notifications reçues pendant
une déconnexion sont désormais retrouvées à la réouverture du tiroir : au montage, le tiroir
charge d'abord l'historique serveur (`GET /api/notifications`), puis reste à jour en direct via
le flux temps réel existant. Un clic sur une notification la marque lue (état conservé côté
serveur, identique sur tous les onglets/appareils de l'utilisateur) ; **Tout lu** marque
l'ensemble d'un coup, y compris les notifications non lues au-delà de celles déjà chargées dans le
tiroir. Le badge affiche un compte tenu à jour par le serveur (`GET /api/notifications/unread-count`),
pas une estimation limitée à ce qui est affiché. Un bouton **Charger plus** en bas du tiroir donne
accès à l'historique au-delà des 20 notifications les plus récentes. L'historique est conservé 30
jours puis purgé automatiquement.

Événements couverts : changement d'état d'un lien de workflow, franchissement de seuil SLA
(voir ci-dessous), réassignation d'une tâche, tâche terminée par un autre participant, document
généré sur un dossier suivi. Vue strictement séparée de l'inbox **Tâches** ci-dessus (« ce qui
s'est passé » vs « ce que j'ai à faire »).

### Alertes SLA et export BI (Admin → Types)

Le formulaire **Objectif SLA tenant** reste réservé au `TENANT_ADMIN` :

- **Alertes de franchissement** — désactivées par défaut. À l'activation, l'état courant des
  dossiers scorés est mémorisé sans envoi. Seul un franchissement ultérieur (`AT_RISK` à 80 %
  de l'objectif en heures, ou `BREACHED` au-delà) prévient une fois : e-mails saisis (20 maximum),
  webhooks déjà activés choisis dans la liste (20 maximum), et notification navigateur pour les
  participants du dossier. L'intervalle de contrôle se règle en minutes (15 à 10 080, défaut 60).
  Un enregistrement qui n'envoie qu'une partie des champs conserve le reste déjà enregistré.
  Seul un `false` ou une liste vide explicite coupe l'alerting, vide les destinataires ou arrête l'export.
- **Export BI planifié** — désactivé par défaut. Une fois activé, un CSV par type de dossier et
  un CSV SLA (période 30 jours) sont déposés après chaque intervalle (1 à 720 heures, défaut 24),
  compté depuis l'activation ou le dernier export réussi. Les clés de champs sensibles sont
  retirées de ces fichiers, y compris pour un administrateur ; l'export CSV manuel des dossiers
  reste en clair pour le `TENANT_ADMIN`. Les fichiers plus vieux que la rétention (1 à 365 jours,
  défaut 30) disparaissent de la liste, même si l'export est ensuite coupé.

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
événement métier (voir [docs/specs/09-notifications.md](../specs/09-notifications.md)),
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
| Migration Liquibase | Nombre de changesets Liquibase appliqués (niveau de schéma de cette instance) et identifiant du dernier changeset — permet de vérifier d'un coup d'œil que l'instance est bien à la version courante (§119) |

Chaque carte porte une sévérité (normal/alerte) calculée côté serveur selon des seuils fixes — cet
écran est destiné à un diagnostic rapide, pas à de l'historique ou du graphique dans le temps (pas
de série temporelle en V1 ; les mêmes métriques sont aussi exposées au format Prometheus sur le
port management, voir [docs/guides/GETTING-STARTED.md](GETTING-STARTED.md#7-démarrer-le-backend), pour qui
dispose déjà d'un outil de supervision externe).

------

## 10. Questions de tâche indépendantes du dossier (TODO §55)

Écran **Admin → Questions de tâche** (`/admin/task-question-schemas`, `TENANT_ADMIN` ou
`WORKFLOW_ADMIN`) — définit des mini-formulaires (« schémas ») qu'un `userTask` BPMN peut afficher
au porteur de tâche avant complétion, **indépendamment des données du dossier** (voir §3bis du
[docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md) pour la convention `formKey` côté BPMN).

- **Créer un schéma** : un `schemaKey` (identifiant stable, unique par tenant — c'est lui qui sera
  référencé dans le BPMN via `formKey = "user-question:<schemaKey>"`), un libellé, et un ou
  plusieurs champs (mêmes types que ceux d'un type de dossier : texte, nombre, date, booléen,
  choix — `FILE` n'est pas supporté ici, une tentative est rejetée avec un message explicite).
- **Concepteur de formulaire (LC4a, TODO §85)** — à droite des champs, un **aperçu en direct** montre le formulaire tel
  qu'il sera saisi (même moteur que les tâches, les dossiers et le portail externe ; décocher « Aperçu du formulaire » pour
  le masquer). Chaque champ propose : une **section** (le champ « Section » ; les sections déjà utilisées sont suggérées, les
  champs d'une même section sont regroupés), une **largeur** (*Pleine*, ou *Demi* : deux champs *Demi* se placent côte à côte,
  une seule colonne sur écran étroit) et une **aide** (texte brut, 500 caractères, affichée sous le champ). L'ordre se règle
  par glisser-déposer de la poignée ⋮⋮ ou avec les boutons ↑ ↓. Le même concepteur sert les champs d'un type de dossier
  (§1).
- Le nom de chaque champ devient le nom de la **variable de process** produite à la complétion de
  la tâche — un nom qui collisionne avec une variable déjà utilisée ailleurs dans le produit
  (`documentType`, `documentVariant`, `documentLocale`, ou une variable plateforme réservée) est
  refusé dès la sauvegarde du schéma, avant tout déploiement BPMN.
- **Modifier** un schéma s'applique immédiatement — pas de version figée ; une instance de process
  déjà démarrée référençant ce `schemaKey` verra le schéma tel qu'il est au moment où le porteur de
  tâche ouvre le formulaire.
- **Supprimer** un schéma encore référencé par un process en cours dégrade comme un `formKey`
  inconnu (bouton « Compléter » sans saisie), jamais une erreur bloquante.
- La réponse soumise n'est **jamais écrite dans le dossier** — elle part uniquement comme
  variable(s) de process, et reste consultable après coup (traçabilité interne, pas d'écran de
  consultation dédié dans cette itération).

## 11. Tiers externes — portail (TODO §68)

Quand un workflow contient une **tâche externe** (voir §3ter du
[docs/guides/GUIDE-CONCEPTION-BPMN.md](GUIDE-CONCEPTION-BPMN.md)), Myrmacta envoie automatiquement au tiers
concerné (citoyen, fournisseur, partenaire… **sans compte**) un e-mail contenant un **lien
personnel**. En l'ouvrant, le tiers voit la référence du dossier, l'intitulé et les consignes de la
tâche, remplit le formulaire, dépose des pièces et envoie sa réponse — la tâche se termine et le
workflow continue. Le tiers ne voit **rien d'autre** du dossier (ni ses champs, ni ses documents,
ni les pièces déposées par d'autres). Si la tâche propose des types de pièces (`flowable:portalPieceTypes`, TODO §106),
le tiers choisit le type de chaque pièce déposée ; sinon ses pièces arrivent *Non classées*, à classer depuis la fiche (§2).

Le portail est **désactivé par défaut** : il doit être activé par l'exploitant
(`APP_PORTAL_ENABLED`, voir [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md#9-portail-externe-tiers-sans-compte)).

**Panneau « Tiers externes »** sur le détail d'un dossier (visible dès qu'un lien existe) :

| Colonne | Contenu |
|---|---|
| Destinataire | Adresse e-mail du tiers — **masquée** (`a***@exemple.fr`) si vous n'avez que le droit VIEW sur le type |
| Statut | *Envoi en cours*, *En attente du tiers*, *Non envoyé* (avec la raison : aucune adresse, adresse invalide, formulaire introuvable, échec d'envoi), *Répondu*, *Expiré*, *Révoqué* |
| Envoyé / Expire / Dernier accès | Dates d'envoi, d'expiration du lien et de dernière ouverture par le tiers |

**Actions** (droit **EDIT** sur le type du dossier, ou `TENANT_ADMIN` / `WORKFLOW_ADMIN`) :

- **Envoyer / Renvoyer** : émet un nouveau lien (l'ancien devient immédiatement inutilisable).
  L'adresse est relue à chaque envoi — si le lien est *Non envoyé — aucune adresse*, corrigez le
  champ du dossier puis cliquez **Envoyer**. Si l'adresse reste invalide, le panneau l'indique.
- **Révoquer** : rend le lien inutilisable tout de suite (le tiers verra « Lien invalide ou expiré »).

Un lien est valable **7 jours** par défaut (durée ajustable par tâche dans le BPMN, plafonnée par
l'exploitant) et **réutilisable** jusqu'à l'envoi de la réponse. À son expiration, la tâche reste
ouverte et les participants du workflow sont prévenus.

**Notifications** (cloche) : *Réponse d'un tiers externe*, *Lien tiers expiré*, *Lien tiers non
envoyé*. Les réponses du tiers sont tracées (auteur `portal:<identifiant du lien>`), ses pièces
apparaissent dans les éléments du dossier.

**Authentification forte du tiers (TODO §104).** Une tâche peut exiger que le tiers s'identifie par FAS (eID, itsme,
eIDAS) ou par itsme. Le lien ne montre alors que le nom de la tâche et la référence du dossier, avec un bouton par moyen
d'identification ; une fois identifié, le tiers voit la tâche et le rappel « Identité vérifiée : <nom> ». Si le dossier
attend un autre numéro de registre national, il voit « Identification non reconnue » sans autre détail. L'identification
vaut une heure (réglage de l'exploitant) : au-delà, ou après un rechargement de la page, il rouvre le lien de l'e-mail.

Dans le panneau, le statut d'un tel lien porte la trace : moyen, niveau, nom vérifié (droit EDIT seulement) et résultat
(*conforme au dossier*, *NRN enregistré dans le dossier*, *sans numéro de registre national*). Le NRN n'est jamais
affiché ici : quand il est enregistré, il l'est dans le champ sensible du dossier, masqué comme les autres. Un lien
*Non envoyé — champ d'identité absent, non textuel ou non sensible* signale un type de dossier à corriger.

**Antivirus** : les pièces déposées par un tiers suivent la même politique que les dépôts
internes — si l'antivirus n'est pas activé ou ne répond pas, le fichier est **accepté** (risque
accepté, décision D-PORTAL-SCAN). L'exploitant doit activer ClamAV sur tout tenant qui active le
portail.

## 12. Signatures électroniques (TODO §72)

Quand un workflow contient un **bloc de signature** (GUIDE-CONCEPTION-BPMN §3quater), Myrmacta invite
les signataires à signer un document du dossier, suit leurs décisions et fait reprendre le workflow.
La fonctionnalité est **désactivée par défaut** (voir [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md#10-signature-électronique)).
Avec le fournisseur `stub`, les signatures sont une **simulation sans valeur juridique** : un bandeau
« SIMULATION » l'indique partout et les PDF produits le portent. Avec `yousign` ou `docusign`, le
signataire est envoyé sur le site du fournisseur. S'il n'est pas encore son tour, ou si le lien
n'est pas prêt, l'écran demande d'attendre. Dans « Mes signatures », ce lien est demandé au clic.
Le refus se fait dans Myrmacta. Un signataire interne doit avoir une adresse sur son compte.

**Signataire externe (sans compte).** Il reçoit un e-mail avec un lien personnel : il consulte le
document, puis **signe** ou **refuse** (motif facultatif). Un lien inconnu, expiré, déjà utilisé ou
d'une demande close affiche toujours le même message « Lien invalide ou expiré ». Il ne voit rien
d'autre du dossier.

**Signataire interne — « Mes signatures »** (menu du même nom). *À mon tour* liste les demandes à
signer maintenant ; *En attente* celles où un signataire précédent doit d'abord décider (mode
séquentiel). Vous pouvez consulter le document, **Signer** ou **Refuser**.

**Panneau « Signatures »** sur le détail d'un dossier (visible dès qu'une demande existe) :

| Colonne | Contenu |
|---|---|
| Signataire | Nom et, le cas échéant, *(facultatif)* |
| Contact | Adresse e-mail **masquée** (`a***@exemple.fr`) ou identifiant ; absent sans le droit EDIT |
| Statut | *En attente de son tour*, *À signer*, *Signé*, *Refusé*, *Expiré*, *Révoqué* |
| Décision | Date et motif éventuel de refus |

Droit **EDIT** sur le type du dossier (ou `TENANT_ADMIN` / `WORKFLOW_ADMIN`) : **Renvoyer** (nouveau
lien, l'ancien cesse de fonctionner) et **Annuler la demande** (le workflow reprend avec le résultat
*Annulée*). Une fois signée, le **document signé** et la **preuve** apparaissent dans les pièces du dossier.

## 13. Assistant de conception IA (TODO §86)

Écran **Admin → Assistant** (`/admin/assistant`, `TENANT_ADMIN` ou `WORKFLOW_ADMIN`). Si l'écran indique que l'assistant n'est pas activé, un
administrateur technique doit le configurer (`docs/guides/DEPLOIEMENT.md` §15).

- **Décrivez votre besoin** en français (« un type *Sinistre* avec numéro de contrat et montant, et un processus de validation à deux étapes »).
  L'assistant appelle les outils de conception (types de dossier, formulaires, processus, tables de décision, connecteurs, expressions) et
  vérifie son travail (essais à blanc, validation des expressions, test des tables) avant de répondre.
- **Tout est à relire.** Ce qu'il écrit pour un **processus** ou une **table de décision** est un **brouillon** : un lien vous mène à *Admin →
  Brouillons* pour relire l'écart et publier. L'assistant **ne publie jamais**.
- **Les autres écritures (type de dossier, schéma de questions, connecteur, modèle de document) sont des propositions.** L'assistant les valide
  à blanc ; elles apparaissent sous la conversation, dans « Écritures proposées », **avec les arguments exacts**. Rien n'est écrit tant que vous
  n'avez pas cliqué **Appliquer** (l'écriture se fait avec vos droits, une seule fois) ; **Ignorer** l'écarte. Un échec est conservé avec son motif.
- **Il n'a accès à aucune donnée de dossier** et **n'écrit jamais de secret** : un connecteur est créé sans mot de passe ni jeton ; saisissez-les vous-même
  dans *Admin → Connecteurs*.
- **Historique.** Vos conversations sont conservées 30 jours (configurable), visibles de vous seul ; vous pouvez en supprimer une.
- **Limites.** Nombre d'étapes par message, de tours et de jetons par conversation et par jour : un message vous prévient quand elles sont atteintes.
  L'assistant peut se tromper : relisez toujours.

## 14. Concepteur de parcours (TODO §88)

Écran **Admin → Parcours** (`TENANT_ADMIN` ou `WORKFLOW_ADMIN`). Un parcours est une **liste d'étapes** lisible par un métier ; le serveur la compile en processus BPMN et la dépose en **brouillon**
(*Admin → Brouillons*) pour relecture. Rien n'est publié par ce chemin.

- **Étapes disponibles** : question à un utilisateur, réponse d'un tiers (portail), génération de document (avec rattachement), notification, connecteur, table de décision DMN, calcul (expression),
  renseignement de champs, changement de statut (parcours ou dossier), attente (durée ISO 8601), et **aiguillage** : des conditions simples (variable, opérateur, valeur) vers une étape, plus un « sinon » obligatoire ;
  on peut revenir en arrière (boucle de correction) tant qu'une sortie existe. Sans « suite » explicite, une étape passe à la suivante de la liste.
- **Type de dossier** : en tête du parcours, choisissez le type de dossier sur lequel il porte. Ses champs sont alors proposés dans les formules et les correspondances ; l'écran
  signale si ce type démarre un autre workflow que ce parcours (clé de workflow du type ≠ clé du parcours). Le choix est enregistré avec le parcours mais n'entre pas dans le BPMN.
- **Formules** (étape « Calculer une valeur ») : la liste **Insérer…** ajoute au curseur un champ du dossier, une variable du parcours (réponses des formulaires, résultats de calcul),
  la réponse du dernier connecteur, un opérateur ou une fonction ; la formule est vérifiée pendant la frappe (mêmes règles qu'au déploiement). Les champs sensibles ne sont pas proposés :
  une expression ne les voit pas.
- **Correspondances** (étape « Renseigner des champs du dossier ») : un tableau « champ du dossier ← valeur », une ligne par champ ; le champ se choisit parmi ceux du type (hors champs
  calculés) et l'écran signale un champ non déclaré, renseigné deux fois ou sans valeur. La case **Éditer en texte** donne la forme brute (`champ = expression`, une ligne par champ).
- **Aiguillage** : le nom de variable propose les variables connues du parcours.
- **Valeurs de condition** : un nombre entier ou un texte court, sans point, parenthèse, guillemet, `&`, `<`, `>`, `;`. Les nombres négatifs ou décimaux ne s'écrivent pas dans une condition de BPMN ; utilisez un champ calculé.
- **Enregistrer** vérifie le parcours (étapes atteignables, sortie possible, statuts déclarés) et nomme l'étape fautive. **Aperçu du BPMN** dessine le diagramme généré (le XML reste consultable dessous) et dit si le déploiement le refuserait. **Valider** et
  **Compiler** travaillent sur la version enregistrée (les boutons se bloquent tant qu'il y a des modifications).
- **Sens unique** : on modifie le parcours ici, **jamais le BPMN généré** (une retouche manuelle ne revient pas dans le parcours et sera écrasée par la prochaine compilation).
- **MCP / assistant** : `list_parcours`, `get_parcours`, `define_parcours` (avec mode à blanc et `dossierTypeCode` facultatif), `compile_parcours`.

## 15. Langue de l'interface (TODO §90)

Le sélecteur de langue (français / anglais) est en haut de page, y compris sur la page publique des tiers. Votre choix est conservé dans ce navigateur et prime sur la langue par défaut de votre organisation.
Les dates, nombres et montants suivent la langue. Les libellés saisis par votre organisation (types de dossier, champs, statuts) ne sont pas traduits, ni le détail des erreurs renvoyées par le serveur.

## 16. Pré-vérification IA d'un dossier (TODO §106)

Quand votre organisation l'a activée (`docs/guides/DEPLOIEMENT.md` §20) et qu'un processus contient la brique
**« Pré-vérification IA »**, la fiche du dossier affiche un panneau **Pré-vérification** après les éléments. Il prépare
votre instruction ; **il ne décide rien**.

- **Ce que vous voyez** : le statut de l'exécution, les comptes (manquants, incohérences, à vérifier), une **synthèse**
  et la liste des **constats**. Chaque constat donne son type (*Manquant*, *Incohérence*, *À vérifier*, *Conforme*), sa
  source (champ, pièce, règle ou question) et son origine :
  - **Contrôle** : vérification déterministe (champ obligatoire vide, pièce obligatoire absente, règle du processus) ;
  - **IA** : proposé par un modèle de langage à partir des champs et pièces désignés, avec sa confiance. **À vérifier
    vous-même** : le modèle peut se tromper ou être trompé par le contenu d'une pièce.
- **Décider** (droit de modification, dossier non clos) : **Accepter** ou **Écarter** chaque constat ; **Annuler la
  décision** revient en arrière. Votre nom et l'heure sont enregistrés. Un lecteur voit le panneau sans boutons.
- **Relancer** après l'ajout d'une pièce : la pré-vérification repart en tâche de fond avec les mêmes contrôles ; le
  panneau se met à jour seul. L'exécution précédente reste consultable dans la liste en tête du panneau. Au plus 5
  relances par dossier sur 24 heures ; quand le plafond du jour est presque atteint, les relances sont suspendues
  pour laisser le reste aux workflows (le panneau affiche le motif).
- **Échec** : le panneau l'indique (plafond journalier atteint, fournisseur indisponible…) et garde les contrôles
  déterministes. Dans le workflow, l'échec apparaît aussi comme un **incident** rejouable par un administrateur.
- **Ce qui part chez le fournisseur** : seuls les champs et types de pièces listés dans la brique, jamais un champ ni une
  pièce **sensible**. Une pièce scannée (sans texte) n'est pas lue : elle est signalée « non lisible ».
- **Administrateur du tenant** : **Exporter le journal** télécharge le détail d'une exécution (modèle, version des
  consignes, empreinte de l'entrée, constats et décisions).

Pour placer la brique dans un processus, voir `docs/guides/GUIDE-CONCEPTION-BPMN.md` §3nonies ; pour l'outil MCP,
`run_dossier_precheck` (relancer) et `get_dossier_precheck` (lire).

## Pour aller plus loin

- Mise en place de l'environnement : [docs/guides/GETTING-STARTED.md](GETTING-STARTED.md)
- Déploiement, provisioning d'un tenant, gestion des utilisateurs/rôles Keycloak : [docs/guides/DEPLOIEMENT.md](DEPLOIEMENT.md)
- Architecture des connecteurs : [docs/specs/06-connecteurs.md](../specs/06-connecteurs.md)
- Architecture des templates/génération de documents : [docs/specs/07-generation-documents.md](../specs/07-generation-documents.md), [docs/specs/08-templates-documents.md](../specs/08-templates-documents.md)
- Visualisation des workflows : [docs/specs/10-visualisation-workflow.md](../specs/10-visualisation-workflow.md)
- Notifications (email, webhook, SSE) : [docs/specs/09-notifications.md](../specs/09-notifications.md)
- Autorisation et conformité (rétention, RGPD) : [docs/specs/12-autorisation-conformite.md](../specs/12-autorisation-conformite.md)
- Décisions ouvertes (dont les limites d'écran citées ci-dessus) : [registre des décisions](../decisions/README.md)
