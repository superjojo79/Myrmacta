# Exemple — Digital Mail Room (courrier entrant)

Le courrier entrant d'une organisation (e-mail aujourd'hui ; PDF numérisés et eBox ensuite) est **enregistré** comme un dossier
numéroté, puis **trié** et rattaché au bon dossier métier. Issue [#217](https://github.com/superjojo79/Myrmacta/issues/217),
décisions [§113](../../docs/decisions/113-digital-mail-room-courrier-dossier-et-fils.md) et
[§114](../../docs/decisions/114-groupes-d-utilisateurs-et-assignation-par-groupe.md).

Un courrier est un **dossier de type `COURRIER`** (D-MAILROOM-MODEL), pas une entité à part. Il bénéficie de tout ce qu'a un dossier :
- numéro d'ordre ;
- pièces passées à l'antivirus ;
- rétention ;
- droits par type ;
- historique.

Le **registre du courrier** est la liste des dossiers filtrée sur ce type (Dossiers → filtre « Type : Courrier entrant »).

État : **lots 1 et 2b** de la feature.

- **Ce que fait déjà l'exemple :**
  - un e-mail devient un courrier, avec ses pièces ;
  - la **table de routage** (`routage-courrier.dmn`) décide du sort du courrier :
    - une facture d'un fournisseur connu ouvre un **dossier « facture » lié**, dont la tâche va au groupe COMPTABILITE, avec
      une échéance à 30 jours ;
    - le reste part à la **file de tri** du groupe COURRIER ;
  - les en-têtes de fil (`In-Reply-To`, `References`, `conversationId`) parviennent au processus.
- **Ce qui viendra :**
  - la réaffectation d'une tâche vers un autre groupe, avec sa trace (lot 2c) ;
  - le rattachement des réponses à leur fil (lot 3) ;
  - les rappels et l'escalade des échéances (lot 4) ;
  - les PDF déposés (lot 5) ;
  - les outils MCP (lot 6).

## Contenu

| Fichier | Rôle |
|---|---|
| `config/groupes.json` | Groupes : COURRIER (file de tri), COMPTABILITE, RH, JURIDIQUE |
| `config/type-dossier.json` | Type `COURRIER` : canal, expéditeur, objet, texte, service destinataire, échéance |
| `config/workflow-config.json` | Statuts du courrier : reçu, à trier, routé, rattaché, traité, classé |
| `routage-courrier.dmn` | Table de routage `routageCourrier` (entrées `canal`, `domaineExpediteur`) |
| `tri-courrier.bpmn20.xml` | Processus `triCourrier` : ouvrir le courrier, préparer les entrées, table, brique « Router le courrier », puis routé ou « Trier le courrier » |
| `config/type-facture.json`, `config/workflow-config-facture.json`, `traiter-facture.bpmn20.xml` | Dossier métier « facture » ouvert par le routage, et son process `traiterFacture` |
| `config/connecteur-imap.exemple.json` | Connecteur `imap-inbound` vers une boîte générique (**non créé**) |

## Installer

1. Créer les groupes de `config/groupes.json` (Admin → Groupes), puis y ajouter leurs membres.
2. Créer les types de dossier `config/type-dossier.json` et `config/type-facture.json` (Admin → Types de dossiers, ou
   `POST /api/dossier-types`). Donner l'ACL `EDIT` de chaque type aux membres des groupes concernés.
3. Créer les configurations de workflow `config/workflow-config.json` et `config/workflow-config-facture.json`. La forme du
   fichier est celle de l'outil MCP : avec l'API REST, renommer `processDefinitionKey` en `workflowKey`.
4. Déployer la table `routage-courrier.dmn` (Admin → Tables de décision), puis `traiter-facture.bpmn20.xml` et
   `tri-courrier.bpmn20.xml` (Admin → Workflows).
5. Créer le connecteur depuis `config/connecteur-imap.exemple.json` (Admin → Connecteurs) : hôte, compte, secret, et
   `trustedAuthservId` de votre serveur de réception.

Adapter la table à son organisation se fait sans code : une ligne par règle (canal, domaine de l'expéditeur), avec le
service, le type de dossier, le process et le délai.

## Points d'attention

- **Statut au démarrage :** un dossier créé par un connecteur naît au statut `DRAFT`. La configuration du workflow l'autorise donc au
  démarrage. Depuis `DRAFT`, on ne peut passer qu'à `OUVERT` : le passage « en cours » automatique du démarrage ne s'applique pas. Le
  tri ouvre donc le courrier, le met en cours, puis le clôture une fois routé ou classé.
- **Message sans objet :** il est rangé dans `Myrmacta-rejected`, puisque l'objet est mappé et qu'un champ mappé ne peut pas être vide. Remettez-le
  dans la boîte après lui avoir donné un objet.
- **Canal :** il vaut `EMAIL` par défaut. Les autres canaux le poseront eux-mêmes.

Test de référence : `MailRoomExampleIT`. Une facture d'un fournisseur connu est routée vers la comptabilité, dans un dossier « facture » lié ; un courrier inconnu va à la file de tri.
