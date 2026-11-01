# Exemple — Digital Mail Room (courrier entrant)

Le courrier entrant d'une organisation (e-mail et PDF numérisés aujourd'hui ; eBox ensuite) est **enregistré** comme un dossier
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

État : **lots 1 à 5a** de la feature.

- **Ce que fait déjà l'exemple :**
  - un e-mail devient un courrier, avec ses pièces ;
  - un PDF numérisé déposé par le partenaire sur SFTP, avec son index JSON, devient un courrier « papier » qui suit le
    même tri (`config/connecteur-sftp-pdf.exemple.json`) ;
  - la **table de routage** (`routage-courrier.dmn`) décide du sort du courrier :
    - une facture d'un fournisseur connu ouvre un **dossier « facture » lié**, dont la tâche va au groupe COMPTABILITE, avec
      une échéance à 30 jours ;
    - le reste part à la **file de tri** du groupe COURRIER ;
  - une tâche se réaffecte à un autre groupe depuis la fiche du dossier, avec une note de trace ;
  - une **réponse** à un e-mail connu (reçu ou envoyé par « Notifier ») rejoint le dossier de son fil :
    - lien vers ce dossier, pièces copiées, participants prévenus ;
    - pendant le traitement d'une facture, la tâche « Prendre connaissance du courrier » s'ouvre ;
    - un expéditeur non attesté, ou d'un autre domaine, n'entre pas dans le fil : son courrier est routé comme un nouveau ;
  - le type « facture » déclare un **workflow réponse** (`config/reponse-facture.json`, mode `DOSSIER_ENFANT`) : chaque
    réponse rattachée ouvre un dossier enfant « réponse à la facture », où le process `traiterReponse` démarre, sans
    toucher au process de la facture. Ce process fait la course « traitée ou échue » : la brique « Calculer une
    échéance » pose l'échéance à 10 jours, et un minuteur `${echeanceDepassee}` en bordure de la tâche clôture la réponse au
    statut « échue » si personne ne l'a traitée ;
  - le type « facture » déclare une **échéance** (`config/echeance-facture.json`) : le routage y écrit le délai de la
    table (30 jours) ; les personnes qui ont la tâche sont rappelées 5 jours avant ; le lendemain du terme, la tâche passe
    au groupe DIRECTION_FINANCIERE, avec une note dans le dossier.
- **Ce qui viendra :**
  - le dépôt manuel de PDF depuis l'écran « Déposer » (lot 5b) ;
  - les outils MCP (lot 6).

## Contenu

| Fichier | Rôle |
|---|---|
| `config/groupes.json` | Groupes : COURRIER (file de tri), COMPTABILITE, RH, JURIDIQUE, DIRECTION_FINANCIERE (escalade) |
| `config/type-dossier.json` | Type `COURRIER` : canal, expéditeur, objet, texte, service destinataire, échéance |
| `config/workflow-config.json` | Statuts du courrier : reçu, à trier, routé, rattaché, traité, classé |
| `routage-courrier.dmn` | Table de routage `routageCourrier` (entrées `canal`, `domaineExpediteur`) |
| `tri-courrier.bpmn20.xml` | Processus `triCourrier` : ouvrir le courrier, « Rattacher au fil », sinon préparer les entrées, table, « Router le courrier », puis routé ou « Trier le courrier » |
| `config/type-facture.json`, `config/workflow-config-facture.json`, `traiter-facture.bpmn20.xml` | Dossier métier « facture » ouvert par le routage, et son process `traiterFacture` (attend `courrierRecu` pendant le traitement) |
| `config/type-reponse-facture.json`, `config/workflow-config-reponse.json`, `traiter-reponse.bpmn20.xml` | Dossier enfant « réponse à la facture » et son process `traiterReponse` (échéance à 10 jours, tâche, minuteur « échue », clôture) |
| `config/reponse-facture.json` | Workflow réponse du type FACTURE (corps de `PUT /api/dossier-types/FACTURE/reply-workflow`, sans `_commentaire`) |
| `config/echeance-facture.json` | Échéance du type FACTURE : champ `echeance`, rappel J-5, escalade à DIRECTION_FINANCIERE (corps de `PUT /api/dossier-types/FACTURE/deadline`, sans `_commentaire`) |
| `config/connecteur-imap.exemple.json` | Connecteur `imap-inbound` vers une boîte générique (**non créé**) |
| `config/connecteur-sftp-pdf.exemple.json` | Connecteur `sftp-pdf-inbound` vers le dossier SFTP du partenaire de numérisation, index JSON compagnon (**non créé**) |

## Installer

1. Créer les groupes de `config/groupes.json` (Admin → Groupes), puis y ajouter leurs membres.
2. Créer les types de dossier `config/type-dossier.json`, `config/type-facture.json` et `config/type-reponse-facture.json`
   (Admin → Types de dossiers, ou `POST /api/dossier-types`). Donner l'ACL `EDIT` de chaque type aux membres des groupes concernés.
3. Créer les configurations de workflow `config/workflow-config.json`, `config/workflow-config-facture.json` et
   `config/workflow-config-reponse.json`. La forme du fichier est celle de l'outil MCP : avec l'API REST, renommer `processDefinitionKey` en `workflowKey`.
4. Déployer la table `routage-courrier.dmn` (Admin → Tables de décision), puis `traiter-facture.bpmn20.xml`,
   `traiter-reponse.bpmn20.xml` et `tri-courrier.bpmn20.xml` (Admin → Workflows).
5. Déclarer le workflow réponse du type FACTURE (Admin → Types → Modifier les champs → « Réponse à un courrier »), selon
   `config/reponse-facture.json`, puis son échéance (même panneau, section « Échéance »), selon `config/echeance-facture.json`.
6. Créer le connecteur depuis `config/connecteur-imap.exemple.json` (Admin → Connecteurs) : hôte, compte, secret, et
   `trustedAuthservId` de votre serveur de réception.
7. Pour les PDF numérisés, créer le connecteur depuis `config/connecteur-sftp-pdf.exemple.json` : serveur, compte, secret et
   empreinte de la clé hôte du partenaire.

Adapter la table à son organisation se fait sans code : une ligne par règle (canal, domaine de l'expéditeur), avec le
service, le type de dossier, le process et le délai.

## Points d'attention

- **Statut au démarrage :** un dossier créé par un connecteur naît au statut `DRAFT`. La configuration du workflow l'autorise donc au
  démarrage. Depuis `DRAFT`, on ne peut passer qu'à `OUVERT` : le passage « en cours » automatique du démarrage ne s'applique pas. Le
  tri ouvre donc le courrier, le met en cours, puis le clôture une fois routé ou classé.
- **Message sans objet :** il est rangé dans `Myrmacta-rejected`, puisque l'objet est mappé et qu'un champ mappé ne peut pas être vide. Remettez-le
  dans la boîte après lui avoir donné un objet.
- **Canal :** il vaut `EMAIL` par défaut. Le connecteur SFTP le pose à `PAPIER` (`defaultFields`). La table de routage de
  l'exemple n'a de règle que pour l'e-mail : un courrier papier part donc à la file de tri.

Test de référence : `MailRoomExampleIT`. Une facture d'un fournisseur connu est routée vers la comptabilité, dans un dossier « facture » lié ; un courrier inconnu va à la file de tri ; la réponse du fournisseur rejoint le dossier « facture » et ouvre un dossier enfant « réponse » où `traiterReponse` démarre ; celle d'un autre domaine non. Le rappel puis l'escalade de l'échéance de la facture sont joués en avançant l'horloge du job ; le minuteur de la réponse est déclenché, et la réponse est clôturée « échue ». Un PDF numérisé et son index deviennent un courrier PAPIER, rangé dans « traités », qui part à la file de tri.
