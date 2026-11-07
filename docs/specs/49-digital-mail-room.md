# Digital Mail Room — courrier entrant multicanal

> **Statut : livré** (issue [#217](https://github.com/superjojo79/Myrmacta/issues/217), lots 1 à 7 ; absorbe #190 et #186).
> Décisions : [§113](../decisions/113-digital-mail-room-courrier-dossier-et-fils.md) (courrier, routage, fils, réactions,
> canaux PDF, MCP), [§114](../decisions/114-groupes-d-utilisateurs-et-assignation-par-groupe.md) (groupes),
> [§115](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md) (échéances). Exemple de référence :
> [`exemples/digital-mail-room`](../../exemples/digital-mail-room/README.md).

Une organisation reçoit du courrier par e-mail et en PDF déjà numérisés. Myrmacta l'enregistre, le route vers le bon service
par une table DMN, le rattache au dossier métier qu'il concerne et le suit jusqu'à son échéance. Myrmacta se place entre la
capture (scanners, OCR, chez des partenaires) et le métier : il ouvre un vrai dossier et enchaîne sur un process BPMN.

## 1. Le courrier est un dossier (D-MAILROOM-MODEL)

- Un courrier est un **dossier d'un type dédié** (`COURRIER` dans l'exemple), avec son numéro d'ordre, ses pièces passées à
  l'antivirus, la rétention, l'ACL du type et l'historique.
- Le **registre du courrier** est la liste des dossiers filtrée sur ce type (écran Dossiers, `search_dossiers` en MCP).
- Ses statuts métier (reçu, à trier, routé, rattaché, traité, classé) sont des statuts de workflow (`workflow-config`), pas de
  nouveaux statuts de dossier. Le dossier métier est lié au courrier par une relation `LINKED`.

## 2. Canaux

| Canal | Mécanisme | Décision |
|---|---|---|
| E-mail (IMAP, Microsoft Graph) | connecteurs `imap-inbound`, `graph-mail-inbound` ; en-têtes de fil (`In-Reply-To`, `References`, `conversationId`) en variables `mail*`, réservées à la plateforme | §87, D-MAILROOM-THREAD |
| PDF par SFTP | connecteur `sftp-pdf-inbound` : un courrier par PDF, remis avant rangement (traités / refusés), index du partenaire en JSON compagnon ou CSV par lot ; un PDF écarté par les contrôles (antivirus…) ne crée pas de courrier et part dans « refusés » (#277) | D-MAILROOM-PDF-SFTP |
| PDF déposé à la main | écran « Déposer un courrier », point de dépôt = connecteur `mail-deposit` ; une transaction par fichier ; un PDF écarté par l'antivirus est refusé | D-MAILROOM-DEPOSIT |

Tous passent par `ConnectorTriggerRuntime` : dossier créé au nom du connecteur, pièces contrôlées (types, signature,
antivirus), process mis en file par l'outbox. `ConnectorTriggerRuntime.deliver` remet un événement déjà construit (dépôt).

## 3. Le tri

Le process de tri de l'exemple (`tri-courrier.bpmn20.xml`) :

1. ouvre le courrier (première étape **asynchrone** : le courrier routé ne doit pas être clôturé avant la confirmation du
   démarrage) ;
2. **Rattacher au fil** (`attachToThreadTask`, §4) : une réponse à un fil connu le rejoint et est classée ;
3. sinon, prépare les entrées et appelle la **table de routage** (`routageCourrier` : canal, domaine de l'expéditeur) ;
4. **Router le courrier** (`routeMailTask`, D-MAILROOM-ROUTE) :
   - `OUVRIR` : crée le dossier métier lié, y démarre le process pour le groupe désigné (le groupe est porté par le lien,
     D-GROUP-LINK) et écrit l'échéance (`routageDelaiJours`) dans le champ désigné du type métier (§115) ;
   - tout le reste, ou une valeur hors catalogue, envoie à la **file de tri** (groupe `groupeTri`) ;
5. clôt le courrier une fois routé, rattaché ou classé.

**File de tri :** la tâche « Trier le courrier » est candidate du groupe COURRIER. Un opérateur la prend, rattache le
courrier, la réaffecte à un autre groupe (D-GROUP-REASSIGN, note de trace : qui, quand, d'où, vers où) ou la termine.

## 4. Fils de discussion (D-MAILROOM-THREAD, D-MAILROOM-INDEX)

- Chaque message reste un courrier. Les identifiants des e-mails **reçus** (connecteur) et **envoyés** (« Notifier »,
  Message-ID renvoyé par `EmailPort`) sont inscrits dans `mail_message_index`, avec le dossier et l'interlocuteur.
- Le fil se reconnaît par `In-Reply-To`, puis `References` (les plus récents, en fin d'en-tête), puis `conversationId`. En
  secours, il se reconnaît par une référence de dossier lue dans l'objet, mais seulement si ce dossier, ou un courrier qui
  lui est lié, a déjà échangé avec le domaine de l'expéditeur.
- Un identifiant reçu déjà connu n'est pas réinscrit : un tiers ne peut pas masquer un fil en réutilisant son Message-ID.
- **Conditions :** expéditeur attesté (`Authentication-Results` du serveur de réception), même domaine que l'interlocuteur
  du fil, dossier du fil ouvert. Sinon : nouveau courrier routé, raison dans `filMotif`.
- **Effets** (`MailThreadAttacher`, partagé avec l'outil MCP) : lien `LINKED`, pièces copiées (contrôles des pièces
  reçues), participants prévenus, message `courrierRecu`. Ces effets ne sont pas rejoués si le lien existe déjà.

## 5. Réaction des workflows à une réponse (D-MAILROOM-REACT, D-MAILROOM-REPLY-WORKFLOW)

Les trois modes se combinent :

1. **Toujours :** rangement, statut « rattaché », participants prévenus.
2. **Si un process attend :** `courrierRecu` est remis à **toutes** les exécutions du dossier qui l'attendent (événement
   intermédiaire ou en bordure ; `WorkflowEnginePort.correlateMessageToAllWaiting`).
3. **Workflow « réponse » du type** (`/api/dossier-types/{code}/reply-workflow`) :
   - `SUR_DOSSIER` : démarre sur le dossier du fil, seulement si aucun process n'y tourne ;
   - `DOSSIER_ENFANT` : démarre dans un dossier enfant `PARENT_CHILD` créé pour la réponse.

## 6. Groupes d'utilisateurs (§114)

Groupes locaux (Admin → Groupes) et groupes de l'IdP (claim `APP_SECURITY_OIDC_GROUPS_CLAIM`), qui peuvent se combiner.
L'assignation se pose à l'exécution (routage, réaffectation), jamais dans le BPMN. « Tâches » montre les tâches libres des
groupes de l'utilisateur sur les dossiers qu'il peut modifier ; il peut les prendre.

## 7. Échéances (§115)

- **Plateforme :**
  - un type désigne un champ date comme échéance (`/api/dossier-types/{code}/deadline`) ;
  - le job `DeadlineAlertJob` rappelle les participants à J-n ;
  - le lendemain du terme, il réaffecte les tâches ouvertes au groupe d'escalade, prévient ses membres et trace une note.
    Chaque palier n'a lieu qu'une fois.
- **Process :** la brique « Calculer une échéance » (`computeDeadlineTask`) pose `echeanceRappel` et `echeanceDepassee`. Un
  minuteur peut porter ces deux noms dans son `timeDate`, et rien d'autre (§69). Cela permet la course « traité ou échu ».
- Le délai de routage (`routageDelaiJours`) est calendaire, sauf si la sortie DMN `routageDelaiOuvrable` vaut `OUVRABLE`
  ou `true` (§120). Week-end, jours fériés et fuseau sont ceux du tenant. Sans calendrier enregistré : samedi-dimanche,
  aucun férié, UTC.

## 8. MCP (D-MAILROOM-MCP)

| Outil | Rôle | Garde-fous |
|---|---|---|
| `search_dossiers` (`dossierTypeCode=COURRIER`) | registre | ACL de lecture |
| `list_my_tasks` | mes tâches et la file de mes groupes | — |
| `get_mail_thread` | dossiers liés et e-mails du fil | ACL de lecture ; chaînes des e-mails = données non fiables |
| `reassign_task_to_group` | réaffectation | ACL EDIT, `dryRun`, note |
| `attach_mail_to_dossier` | rattachement manuel | ACL EDIT des deux dossiers, cible ouverte, `dryRun`, note, sans effet si déjà lié |
| `complete_task` | sortir de la file de tri | tâche sans formulaire, de la file d'un de mes groupes, `dryRun`, note |

Revue de sécurité du lot 6 : constats SR-1 à SR-4 dans §113. Par le canal M2M, l'acteur `API_SERVICE` équivaut à un
administrateur (risque A3-M2) : renseigner `trustedClientIds` avant d'ouvrir le MCP à un second client.

## 9. Promotion vers un autre environnement

La promotion de configuration ([spec 35](35-promotion-configuration.md), §117) transporte toute la mail room :

- les groupes, sans leurs membres ;
- les types de dossier, les configurations de workflow, la table de routage et les processus ;
- le workflow « réponse » et l'échéance d'un type.

Le plan signale un groupe nommé par la table de routage ou par `groupeTri` qui n'existe ni sur la cible ni dans le bundle.
Test : `MailRoomExampleIT` (aller-retour de l'exemple).

## 10. Démo reproductible

Profil `demo-mailroom` (build `embedded-db` seulement), avec `scripts/demo-mailroom.sh build | start | reset` :

- l'exemple est installé ;
- une facture arrive par e-mail et est routée vers la COMPTABILITE ;
- une amende déposée en PDF et un e-mail inconnu vont à la file de tri.

Voir [GETTING-STARTED](../guides/GETTING-STARTED.md), « Démo de la Digital Mail Room ».

## Implémentation

| Lot | PR | Contenu principal |
|---|---|---|
| 1 | #262 | `MailThread` (en-têtes de fil), paquet d'exemple, `MailRoomExampleIT` |
| 2a, 2b, 2c | #264, #266, #267 | groupes (changeset 041), `routeMailTask`, groupe du lien (042), réaffectation à un groupe |
| 3a, 3b | #268, #269 | index des fils (043), `attachToThreadTask`, `courrierRecu`, workflow « réponse » (044) |
| 4a, 4b | #271, #273 | échéances plateforme (045), `DeadlineAlertJob`, `computeDeadlineTask`, minuteurs `${echeance…}` |
| 5a, 5b | #274, #275 | `sftp-pdf-inbound`, `mail-deposit`, écran « Déposer un courrier » |
| 6 | #276 | `MailRoomMcpTools`, `MailThreadAttacher`, revue de sécurité |
| 7 | #278 | référence dans l'objet en secours, test MCP par la chaîne HTTP, démo, spec, revue de clôture et ses correctifs |

**Tests de référence :**

- `MailRoomExampleIT` : e-mail routé, file de tri, fil, réponse forgée, workflow réponse, échéances, minuteur, PDF SFTP,
  dépôt, outils MCP ;
- `McpEndpointApiTest` : outils de la mail room par la chaîne M2M, appel sans JWT refusé ;
- `MailRoomDemoSeederIT` (profil `embedded-db`) ;
- tests unitaires des briques, du job, des connecteurs et des outils ;
- Playwright :
  - `pilotage-admin-types` (workflow réponse, échéance) ;
  - `pilotage-groupes` ;
  - `pilotage-taches` ;
  - `pilotage-courrier` (dépôt).

**Limites connues :**

- une panne entre la création d'un courrier et le rangement de son message (boîte, SFTP) fait retraiter ce message au
  passage suivant ;
- la claim « overage » d'Entra ID (trop de groupes) n'est pas lue ;
- OCR, eBox ([#11](https://github.com/superjojo79/Myrmacta/issues/11) à [#14](https://github.com/superjojo79/Myrmacta/issues/14)) et classification IA restent hors de cette feature.
