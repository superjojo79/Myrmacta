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

**Reste à trancher à la conception de leur lot :**

- échéances et minuteurs à expression (#186) : l'allowlist refuse tout `${…}` dans un minuteur (§69) ;
- outils MCP d'action ;
- dépôt SFTP de PDF ;
- OCR.

**Impacte :** nouvelle spec `docs/specs/49-digital-mail-room.md`, `docs/guides/GUIDE-UTILISATEUR.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md`, `openapi/openapi.yaml`, nouveaux changesets, `exemples/digital-mail-room/`.
