# §113 — Digital Mail Room : le courrier est un dossier, fils de discussion et réaction des workflows

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

En cours (issue [#217](https://github.com/superjojo79/Myrmacta/issues/217)). Cadrage validé par l'utilisateur le
2026-10-31. Les canaux de la première livraison sont l'e-mail et les PDF déjà numérisés. L'eBox est une feature
séparée (#213 à #216), et l'OCR viendra dans un lot ultérieur.

**D-MAILROOM-MODEL** (tranché par l'utilisateur, 2026-10-31) — un courrier entrant est un **dossier** d'un type dédié,
pas une entité nouvelle :

- Il reçoit un numéro d'ordre par `DossierReferenceGenerator`.
- Il réutilise les pièces, ClamAV, la rétention, l'ACL par type et l'audit (`DossierWorkflowLink`).
- Le registre du courrier est la liste des dossiers de ce type.
- La mail room est livrée comme un **paquet de configuration** (`exemples/digital-mail-room/`) : type « courrier »,
  table DMN de routage, process de tri, connecteurs d'exemple. La plateforme n'y ajoute que ce qu'une configuration ne
  sait pas faire : fils, groupes, échéances, dépôt de fichiers, outils MCP.
- Écarté : un domaine `mailroom` avec sa propre table, qui aurait refait pièces, antivirus, rétention, ACL et audit.
- Écarté : pas d'objet courrier du tout, puisqu'il n'y aurait alors ni registre ni file de tri.

**D-MAILROOM-ROUTE** (conception, 2026-10-31) — le process de tri appelle une table DMN (`type="dmn"`, déjà
autorisée). Il passe ensuite le résultat à une brique de routage, dont le délégué est écrit par la plateforme :

- La table renvoie le groupe destinataire, l'action (ouvrir un dossier, alimenter un dossier existant, trier à la main),
  le type de dossier et le process à démarrer, et une échéance facultative.
- Le délégué vérifie chaque valeur contre les catalogues : type de dossier actif, process déployé, groupe existant. Une
  valeur inconnue envoie le courrier dans la file de tri, sans faire échouer le process.
- Pour ouvrir un dossier métier, il reprend le chemin de `createChildDossierTask` : relation idempotente, puis démarrage
  par `ManualStartOrchestrator`. Le lien est `LINKED` du courrier vers le dossier métier.
- Pourquoi un délégué : `createChildDossierTask` ne lit que des paramètres littéraux, et une expression dans le BPMN est
  refusée (§57, §69). Une branche par service dans le BPMN ne tiendrait pas à l'échelle.

Précisions de conception du lot 2b (2026-10-31) :

- **Entrées de la table.** Une entrée de table DMN n'accepte qu'un nom de variable nu, et une cellule qu'une comparaison à
  un littéral (`FlowableDmnParserAdapter`). Le process de tri prépare donc ses entrées avant la table, par des briques
  « Calcul » (JSONata, §40) : canal, domaine de l'expéditeur, etc.
- **Sorties de la table**, lues par la brique `routeMailTask` :
  - `routageAction` : `OUVRIR` ou `TRIER` ;
  - `routageService` : code d'un groupe, §114 ;
  - `routageTypeDossier` et `routageProcessus` ;
  - `routageDelaiJours`, facultatif ; la brique l'écrit dans le champ `echeance`, que le lot 4 exploitera.
- **Paramètre littéral de la brique.** `groupeTri` désigne le groupe de la file de tri.
- **`OUVRIR` :**
  - le dossier métier est créé et lié au courrier (`LINKED`, clé d'idempotence `processInstanceId:activityId`) ;
  - son process est démarré par `ManualStartOrchestrator` ;
  - son lien reçoit le groupe `routageService` (D-GROUP-LINK) ;
  - le champ `service` du courrier est renseigné, et la brique pose `routageResultat=ROUTE`, `dossierMetierId` et
    `dossierMetierReference`.
- **`TRIER`, absent, ou valeur hors catalogue** (groupe, type actif ou process déployé inconnus) :
  - la brique pose `routageResultat=A_TRIER` et donne au lien du tri le groupe `groupeTri` ;
  - la tâche « Trier le courrier » qui suit va donc à la file de tri ;
  - une erreur de configuration n'arrête jamais le courrier.
- **Hors 2b.** Les pièces du courrier ne sont pas copiées dans le dossier métier, qui y accède par le lien. La copie viendra
  avec le rattachement des réponses (lot 3).

**D-MAILROOM-THREAD** (tranché par l'utilisateur, 2026-10-31) — chaque message reçu reste un courrier, avec son propre
numéro.

- Une réponse à un fil connu est liée au courrier d'origine. Elle ne repasse pas par le routage, et ses pièces vont dans
  le dossier métier du fil.
- Le fil se reconnaît, dans cet ordre :
  1. `In-Reply-To`, puis `References`, comparés aux Message-ID déjà reçus **et envoyés** ;
  2. `conversationId` côté Microsoft Graph ;
  3. la référence dans l'objet (`referencePattern`, §87), en secours.
- La règle d'expéditeur authentifié du §87 (D-INTEG-MAIL-SENDER) s'applique aussi aux en-têtes : une réponse non attestée
  devient un nouveau courrier, avec le signalement `mailThreadIgnored`, et ne touche pas au dossier du fil.
- Les Message-ID reçus et envoyés sont enregistrés dans une table d'index par tenant. Aujourd'hui `SmtpEmailAdapter` ne garde
  aucun Message-ID : les envois de « Notifier » en recevront un, généré par la plateforme.
- Statuts du courrier, lisibles dans le registre et sur le dossier métier : reçu, à trier, routé, rattaché, traité,
  classé. Ce sont des statuts de workflow (`workflow-config`), pas de nouveaux statuts de dossier.

Précisions de conception du lot 3a (2026-10-31) :

- **Index des identifiants (D-MAILROOM-INDEX).** La table `mail_message_index` associe un identifiant à un dossier, avec
  le sens et l'adresse de l'interlocuteur. Les identifiants sont des Message-ID, ou des `conversationId` Graph.
  - **Reçus (`IN`) :** les inscrit le relais du connecteur, quand un e-mail crée un courrier. Interlocuteur = expéditeur.
  - **Envoyés (`OUT`) :** les inscrit le relais de l'outbox, une fois l'e-mail de « Notifier » parti. Le Message-ID est
    celui que JavaMail a posé. Interlocuteur = destinataire.
- **Brique « Rattacher au fil » (`attachToThreadTask`).** Elle passe en tête du tri, avant la table de routage.
  - **Recherche du fil :**
    - elle cherche dans l'index `In-Reply-To`, puis les `References` de la plus récente à la plus ancienne, puis le
      `conversationId` ;
    - un identifiant reçu désigne le courrier d'origine ; s'il a été routé, c'est son dossier métier lié qui compte ;
    - un identifiant envoyé désigne le dossier qui a envoyé l'e-mail.
  - **Conditions du rattachement :**
    - l'expéditeur est attesté (`mailSenderAuthenticated`, D-INTEG-MAIL-SENDER) ;
    - son domaine est celui de l'interlocuteur inscrit ;
    - le dossier du fil n'est ni clôturé ni archivé.

    Sinon, le courrier suit le routage ordinaire, avec la raison dans `filMotif`.
  - **Effets du rattachement :**
    - un lien `LINKED` du courrier vers le dossier du fil ;
    - la copie de ses pièces, avec les contrôles des pièces reçues, antivirus compris ;
    - l'avertissement des personnes assignées aux tâches ouvertes du dossier (mode 1) ;
    - l'envoi du message `courrierRecu` à toutes les exécutions de ce dossier qui l'attendent (mode 2) ;
    - `filResultat=RATTACHE`, `filDossierId` et `filDossierReference`. Hors fil : `filResultat=NOUVEAU`.
- **Les variables de fil sont posées par la plateforme.** Les variables `mail*` posées par le connecteur ne sont plus
  remplaçables par un mapping de connecteur.
- **Reporté au lot 3b :** le workflow « réponse » déclaré par le type de dossier (mode 3).

Précisions du lot 7 (2026-11-01, tranché par l'utilisateur) :

- **Référence dans l'objet, en secours (3e moyen de D-MAILROOM-THREAD).** Les références de dossier
  (`DOS-<type>-<année>-<séquence>`) sont séquentielles, donc devinables. Une référence lue dans l'objet ne rattache donc
  que si le dossier a **déjà échangé un e-mail**, reçu ou envoyé (index), avec le domaine de l'expéditeur. Les autres
  conditions sont celles des deux premiers moyens : expéditeur attesté, dossier ouvert.
  - Bornes : 300 caractères d'objet lus, 3 références essayées, 50 échanges par dossier.
  - Écarté par l'utilisateur : l'expéditeur attesté seul (n'importe quel domaine), et l'abandon de ce moyen.
- **Signalement d'une réponse non rattachée.** Le signal est `filResultat=NOUVEAU` avec la raison dans `filMotif` (par
  exemple « expéditeur non attesté »), et non une variable `mailThreadIgnored` comme l'annonçait le cadrage.
**D-MAILROOM-REPLY-WORKFLOW** (tranché par l'utilisateur, lot 3b, 2026-10-31) — le mode 3 bute sur une règle existante : un
seul workflow actif par dossier (`AttachWorkflowUseCaseImpl.reserve`, index unique en base). Cette règle ne change pas. Le type
de dossier choisit l'un de deux modes :

- **`SUR_DOSSIER`** : le workflow « réponse » démarre sur le dossier du fil, seulement si aucun process n'y est actif (par
  exemple un dossier traité mais pas clôturé, qu'une réponse relance). Si un process tourne, les modes 1 et 2 suffisent.
- **`DOSSIER_ENFANT`** : chaque réponse rattachée crée un dossier enfant du dossier du fil (`PARENT_CHILD`), du type
  déclaré, où le workflow « réponse » démarre, en parallèle du process métier.

Commun aux deux modes :

- Le workflow reçoit `courrierId` et `courrierReference`. S'il déclare ses variables de démarrage (§111), il doit déclarer
  ces deux-là.
- **Vérifications** : process déployé, configuration de workflow qui accepte le statut du dossier, type enfant actif sans
  champ obligatoire inconnu. Elles ont lieu avant toute écriture. Si l'une échoue, le workflow n'est pas démarré : la raison
  va dans `filReponseMotif`, et le rattachement, lui, a bien eu lieu.
- **Stockage** : le réglage est rangé à part (table `dossier_type_reply_workflow`, API
  `/api/dossier-types/{code}/reply-workflow`), sans toucher au modèle `DossierType`, que 43 endroits construisent.
- **Limite connue** : la promotion de configuration entre environnements ne transporte pas encore ce réglage.

**D-MAILROOM-REACT** (tranché par l'utilisateur, 2026-10-31) — quand une réponse arrive sur un dossier dont un process
tourne déjà, trois réactions se choisissent et se combinent :

1. **Toujours :** la réponse est rangée, le courrier passe à « rattaché », le dossier affiche « nouveau courrier » et la
   personne assignée est prévenue (boîte de réception, §27).
2. **Si un process attend :** le message `courrierRecu` est corrélé à **toutes** les exécutions du dossier abonnées à ce
   message. Elles l'attendent soit par la brique « Attendre un courrier » (événement intermédiaire de message, avec un délai
   en bordure), soit par un sous-process événementiel modélisé. La requête est celle de `FlowableWorkflowEngineAdapter`
   (`messageEventSubscriptionName` et `processVariableValueEquals("dossierId", …)`, puis `messageEventReceived`). Personne
   n'attend : rien d'autre ne se passe, ce n'est pas une erreur.
3. **Si le type de dossier en déclare un :** un workflow « réponse » démarre sur le même dossier.

**D-MAILROOM-PDF-SFTP** (tranché par l'utilisateur, lot 5a, 2026-10-31) — les PDF déjà numérisés déposés par un
partenaire dans un dossier SFTP deviennent des courriers, par un connecteur d'entrée `sftp-pdf-inbound` :

- **Interrogation, remise avant rangement :** même mécanique que l'e-mail (`PollingTriggerConnector`, §87). Chaque PDF
  est remis au runtime avant d'être déplacé : dans `processedDirectory` s'il est pris en charge, dans `rejectedDirectory`
  sinon. Rien n'est supprimé.
- **Index du partenaire, au choix dans la configuration (`indexFormat`) :**
  - `JSON` : un fichier compagnon de même nom (`facture-117.pdf` et `facture-117.json`, 64 Ko au plus, un objet) ;
  - `CSV` : un index par lot (`csvPattern`, par défaut `*.csv`), une ligne par PDF, dont la colonne `csvFileColumn` (par
    défaut `fichier`) nomme le fichier. Le partenaire dépose l'index **après** ses PDF ; une ligne sans PDF est ignorée et
    journalisée, et l'index est rangé une fois lu ;
  - `NONE` : pas d'index.
- **Sans index** (`requireIndex`, vrai par défaut) : le PDF attend son index et reste en place. Avec `requireIndex=false`,
  il est traité aussitôt, sans métadonnées. Le partenaire doit alors déposer l'index avant le PDF, ou par renommage
  atomique.
- **Champs :**
  - le mapping (`mapping.fields`, `mapping.variables`) lit la charge `{"fichier": nom, "index": {…}}` avec les mêmes
    chemins `$.…` que les autres connecteurs ;
  - `defaultFields` donne des valeurs constantes, par exemple `{"canal": "PAPIER"}`, prises quand le mapping ne donne
    rien ;
  - un champ mappé vide, sans défaut, fait refuser le PDF, comme un e-mail.
- **Contrôles :**
  - la signature `%PDF-` est vérifiée ;
  - la taille est bornée par `app.inbound.max-attachment-bytes`, et le nombre par interrogation par
    `app.inbound.max-messages-per-poll` ;
  - le PDF suit le chemin des pièces jointes de l'e-mail, antivirus compris ;
  - le connecteur SFTP exige l'empreinte de la clé hôte, comme `batch-import`.
- **Limite connue, la même que l'e-mail :** une panne entre la création du courrier et le déplacement du PDF fait
  retraiter ce PDF au passage suivant.

**D-MAILROOM-DEPOSIT** (tranché par l'utilisateur, lot 5b, 2026-10-31 ; conçu au lot 5b) — un agent dépose à la main un ou
plusieurs PDF depuis un écran « Déposer ». Chaque PDF devient un courrier (canal `PAPIER`), qui suit le même tri que
l'e-mail :

- **Point de dépôt :** une instance active du connecteur `mail-deposit`. Elle porte le type de dossier, le process, les
  champs qui reçoivent l'expéditeur et l'objet saisis (`senderField`, `subjectField`) et des valeurs constantes
  (`defaultFields`). Le connecteur ne se déclenche jamais seul.
- **Droit :** un agent ne voit et n'utilise que les points dont il peut créer le type de dossier (ACL EDIT du type, comme
  pour créer un dossier).
- **Même chemin qu'un connecteur d'entrée :** `ConnectorTriggerRuntime.deliver` crée le dossier au nom du connecteur,
  contrôle le PDF comme une pièce jointe d'e-mail (types, signature, antivirus) et met le process en file. Le déposant est
  tracé dans la variable `courrierDeposePar`, et le nom du fichier dans `courrierFichier`.
- **Un fichier, une transaction :** un fichier refusé n'empêche pas les autres, et la réponse donne le sort de chacun
  (courrier créé ou raison du refus).
  - Refusés avant toute écriture : un fichier qui n'est pas un PDF, un fichier trop volumineux
    (`app.inbound.max-attachment-bytes`), un nom invalide.
  - Refusé ensuite : un champ obligatoire manquant.
  - 20 fichiers au plus par dépôt. L'objet vaut, à défaut, le nom du fichier.
- **API :** `GET /api/mail-deposit-points`, `POST /api/mail-deposit-points/{id}/deposits` (multipart `files`,
  `expediteur`, `objet`).

**D-MAILROOM-MCP** (tranché par l'utilisateur, lot 6, 2026-11-01) — la mail room s'expose au serveur MCP en lecture et en
actions. Les actions ont les **mêmes garde-fous que les outils d'écriture déjà livrés** (§60) : droits de l'utilisateur du
jeton, `dryRun` qui valide sans rien écrire, trace au nom de l'utilisateur. Il n'y a pas de proposition à valider par un
humain (option écartée par l'utilisateur).

- **Lecture :**
  - le registre reste `search_dossiers` avec `dossierTypeCode=COURRIER` ;
  - `list_my_tasks` : mes tâches et les tâches libres de mes groupes, dont la file de tri (D-GROUP-ASSIGN) ;
  - `get_mail_thread` : pour un dossier visible, les courriers liés (`LINKED`) et les e-mails reçus et envoyés de son fil
    (index, D-MAILROOM-INDEX).
- **Actions** (ACL EDIT du dossier, `dryRun`, trace) :
  - `reassign_task_to_group` : la réaffectation de D-GROUP-REASSIGN, avec sa note de trace ;
  - `attach_mail_to_dossier` : rattache à la main un courrier à un dossier. Les effets sont ceux du rattachement
    automatique, par le même code (`MailThreadAttacher`) : lien, pièces copiées, participants prévenus, `courrierRecu`
    remis aux exécutions qui l'attendent. Une note au nom de l'utilisateur est ajoutée. Il faut l'ACL EDIT du courrier et
    du dossier cible ; un dossier clôturé est refusé ;
  - `complete_task` : termine une tâche **sans formulaire** (`formKey` vide) **de la file d'un de mes groupes**, par
    exemple « Trier le courrier ». La tâche est prise si elle est libre, puis terminée, avec une note de trace. Une tâche
    à formulaire (question, document, signature) ou hors de mes groupes reste à l'interface.
- **Hors périmètre :** les ACL, la purge et le statut des liens de workflow restent exclus du MCP (§60, spec 15).
- **Revue de sécurité du lot 6** (2026-11-01, `security-review`) : aucun constat critique ni haut. Plan choisi par
  l'utilisateur :
  - **SR-1** (moyen, corrigé) : un nouvel essai de `attach_mail_to_dossier` recopiait les pièces, prévenait de nouveau et
    remettait `courrierRecu`. Désormais, un courrier déjà lié au dossier ne refait rien (`alreadyAttached`). Le lien
    existant est cherché **avant** l'insertion : la violation de `uq_dossier_relation` condamnerait la transaction
    PostgreSQL. Tests : `MailRoomMcpToolsTest`, `MailRoomExampleIT`.
  - **SR-4** (faible, corrigé par choix de l'utilisateur) : `complete_task` est restreint aux tâches candidates d'un
    groupe dont l'utilisateur est membre. Un client M2M générique (`api-service`, sans groupe) ne termine donc aucune
    tâche.
  - **SR-3** (faible, atténué) : `get_mail_thread` renvoie des chaînes venues des e-mails reçus (`messageKey`,
    `counterpart`), bornées à 250 et 320 caractères. La description de l'outil les signale comme données non fiables.
  - **SR-2** (moyen, préexistant, risque A3-M2 déjà accepté) : par le canal M2M, l'acteur est `API_SERVICE`, traité comme
    un administrateur par `DossierAccessPolicy`. `reassign_task_to_group` et `attach_mail_to_dossier` y agissent donc sans
    ACL de type, comme les autres outils d'écriture. Avant d'ouvrir `/api/m2m/mcp` à un second client, renseigner
    `trustedClientIds` (`M2mSecurityProperties`). Par l'assistant, ce sont les droits de l'utilisateur réel qui
    s'appliquent.

**Reste à trancher à la conception de leur lot :**

- OCR.

**Impacte :** nouvelle spec `docs/specs/49-digital-mail-room.md`, `docs/guides/GUIDE-UTILISATEUR.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md`, `openapi/openapi.yaml`, nouveaux changesets, `exemples/digital-mail-room/`.
