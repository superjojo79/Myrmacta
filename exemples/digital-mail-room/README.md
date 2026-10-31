# Exemple — Digital Mail Room (courrier entrant)

Le courrier entrant d'une organisation (e-mail aujourd'hui ; PDF numérisés et eBox ensuite) est **enregistré** comme un dossier
numéroté, puis **trié** et rattaché au bon dossier métier. Issue [#217](https://github.com/superjojo79/Myrmacta/issues/217),
décisions [§112](../../docs/decisions/112-digital-mail-room-courrier-dossier-et-fils.md) et
[§113](../../docs/decisions/113-groupes-d-utilisateurs-et-assignation-par-groupe.md).

Un courrier est un **dossier de type `COURRIER`** (D-MAILROOM-MODEL), pas une entité à part. Il bénéficie de tout ce qu'a un dossier :
- numéro d'ordre ;
- pièces passées à l'antivirus ;
- rétention ;
- droits par type ;
- historique.

Le **registre du courrier** est la liste des dossiers filtrée sur ce type (Dossiers → filtre « Type : Courrier entrant »).

État : **lot 1** de la feature.

- **Ce que fait déjà l'exemple :**
  - un e-mail devient un courrier, avec ses pièces ;
  - le tri démarre au statut « à trier », et un opérateur trie à la main ;
  - les en-têtes de fil (`In-Reply-To`, `References`, `conversationId`) parviennent au processus.
- **Ce qui viendra :**
  - le routage par table DMN vers un service, et la file de tri par groupe (lot 2) ;
  - le rattachement des réponses à leur fil (lot 3) ;
  - les échéances (lot 4) ;
  - les PDF déposés (lot 5) ;
  - les outils MCP (lot 6).

## Contenu

| Fichier | Rôle |
|---|---|
| `config/type-dossier.json` | Type `COURRIER` : canal, expéditeur, objet, texte, service destinataire, échéance |
| `config/workflow-config.json` | Statuts du courrier : reçu, à trier, routé, rattaché, traité, classé |
| `tri-courrier.bpmn20.xml` | Processus `triCourrier` : statut « à trier », tâche « Trier le courrier », classement |
| `config/connecteur-imap.exemple.json` | Connecteur `imap-inbound` vers une boîte générique (**non créé**) |

## Installer

1. Créer le type de dossier depuis `config/type-dossier.json` (Admin → Types de dossiers, ou `POST /api/dossier-types`). Ensuite, donner l'ACL
   `EDIT` du type aux opérateurs de tri.
2. Créer la configuration du workflow depuis `config/workflow-config.json`. La forme du fichier est celle de l'outil MCP : avec l'API REST,
   renommer `processDefinitionKey` en `workflowKey`.
3. Déployer `tri-courrier.bpmn20.xml` (Admin → Workflows, ou `POST /api/workflow-bpmn-deployments`).
4. Créer le connecteur depuis `config/connecteur-imap.exemple.json` (Admin → Connecteurs) : hôte, compte, secret, et `trustedAuthservId` de votre
   serveur de réception.

## Points d'attention

- **Statut au démarrage :** un dossier créé par un connecteur naît au statut `DRAFT`. C'est pourquoi la configuration du workflow l'autorise au
  démarrage. Le tri passe ensuite le courrier « en cours », puis « clôturé » au classement.
- **Message sans objet :** il est rangé dans `Myrmacta-rejected`, puisque l'objet est mappé et qu'un champ mappé ne peut pas être vide. Remettez-le
  dans la boîte après lui avoir donné un objet.
- **Canal :** il vaut `EMAIL` par défaut. Les autres canaux le poseront eux-mêmes.

Test de référence : `MailRoomExampleIT` (un e-mail avec une facture jointe devient un courrier à trier).
