# Digital Mail Room — document de travail

Issue : #217 (sous-issues #190, #186, #185) · Spec du domaine : à créer, `docs/specs/49-digital-mail-room.md` ; s'appuie sur
[04](../specs/04-dossiers.md), [25](../specs/25-introduction-dmn.md), [34](../specs/34-integrations-entreprise.md),
[48](../specs/48-preverification-ia.md) · Points du registre : [§87](../decisions/087-d3c-d3d-e-mail-entrant-bus-azure.md),
[§92](../decisions/092-assignation-des-taches-m-assigner-et-annuaire.md), [§106](../decisions/106-preverification-ia-et-types-de-pieces.md) ;
nouveaux points à partir de §113

## Cadrage

- Problème et utilisateurs :
  - Une organisation (commune, CPAS, entreprise avec un numéro BCE) reçoit du courrier par plusieurs canaux. Il faut
    l'enregistrer, le classer, le router vers le bon service et le rattacher à un dossier métier suivi jusqu'à son échéance.
  - Utilisateurs : l'opérateur de la mail room (tri, réaffectation), les agents des services destinataires (traitement),
    l'administrateur du tenant (groupes, table de routage, canaux), l'intégrateur (configuration, API, MCP).
  - Positionnement (commentaire de #217) : Myrmacta se place entre la capture et le métier. La capture et l'OCR restent chez
    des partenaires (NSI, Arco), qui sont des intégrateurs cibles. Myrmacta se distingue de Dynamic Flows et de COMAX : il ouvre
    un vrai dossier, route par DMN, suit les délais et enchaîne sur un process BPMN.
- Périmètre :
  - **Courrier entrant = dossier d'un type dédié** (« courrier »), avec son numéro d'ordre (`DossierReferenceGenerator`). Il
    réutilise les pièces, ClamAV, la rétention, l'ACL par type et l'audit. Le registre du courrier est une vue filtrée des
    dossiers de ce type. Le dossier métier est lié par une relation.
  - **Process de tri standard**, livré comme modèle : il appelle une table DMN de routage et exécute l'action prévue par la règle :
    - ouvrir un dossier métier (type et process) ;
    - alimenter un dossier existant ;
    - envoyer vers la file de tri manuel, avec réaffectation.
  - **Fils de discussion des e-mails** :
    - Chaque message reste un courrier, avec son propre numéro.
    - Une réponse à un fil connu est liée au fil. Elle ne repasse pas par le routage, et ses pièces vont dans le dossier
      métier du fil.
    - Le statut est lisible à la fois sur le courrier et sur le dossier.
  - **Réaction des workflows à une réponse** : trois modes, à choisir et à combiner selon le besoin métier :
    1. La plateforme range la réponse, passe le courrier au statut « rattaché », signale « nouveau courrier » sur le dossier
       et prévient la personne assignée. C'est toujours fait.
    2. La plateforme envoie un message standard à tout process du dossier qui l'attend. Il est reçu par la brique « Attendre
       un courrier » (avec délai) ou par un sous-process événementiel modélisé. Les deux reposent sur le même mécanisme.
    3. Un workflow « réponse » est lancé sur le même dossier, si le type de dossier en déclare un.
  - **Groupes (services)** : groupes locaux administrés dans Myrmacta, qu'on peut aussi alimenter depuis une claim du jeton
    OIDC. L'assignation à un groupe se pose au runtime (routage, réaffectation), jamais dans le BPMN (D-EDIT-SCOPE inchangée).
    Absorbe #190.
  - **Échéances**, deux mécanismes au choix :
    - portées par la plateforme : échéance du courrier venue de la règle DMN ou des métadonnées, rappel et escalade par un
      job planifié ;
    - minuteurs BPMN à expression, en bordure de tâche (#186).
  - **Canaux de la première livraison** :
    - e-mail, via les connecteurs IMAP et Microsoft Graph existants (§87) ;
    - PDF déjà numérisés, déposés à la main ou dans un dossier SFTP surveillé, avec l'index du partenaire s'il est fourni.
  - **MCP** : lecture du registre, de la file de tri et d'un fil, et actions gouvernées (réaffecter, rattacher à un dossier,
    sortir de la file de tri).
- Hors périmètre :
  - eBox (#213 à #216) : feature séparée, qui viendra s'y brancher comme un canal de plus.
  - OCR intégré : un lot ultérieur, après le tronc commun. Ce sera une nouvelle dépendance d'exécution, donc une pause et un
    point du registre.
  - Portail externe comme canal (#188 rangée dans #194).
  - Classification IA : option d'un lot ultérieur, quand les métadonnées manquent (#211).
  - Calendrier de jours ouvrables.
- Critères de succès (vérifiables) :
  1. **E-mail** : un e-mail reçu crée un courrier numéroté (canal, expéditeur, objet, date, pièces scannées). La table DMN le
     route vers un groupe et ouvre le dossier métier lié. Le courrier passe au statut « routé ».
  2. **Courrier non reconnu** : il tombe dans la file de tri. Un opérateur le réaffecte ou le rattache à un dossier existant,
     et la trace (qui, quand, d'où vers où) est consultable.
  3. **Réponse dans un fil** : une réponse (In-Reply-To ou References, conversationId côté Graph, référence dans l'objet en
     secours, expéditeur authentifié) crée un courrier lié au fil, sans routage. Ses pièces vont dans le dossier du fil.
     Ensuite :
     - le mode 1 s'applique toujours ;
     - un process en attente (brique ou sous-process) reprend ;
     - un workflow « réponse » déclaré démarre.
  4. **Réponse forgée** : un message qui imite un fil, mais dont l'expéditeur n'est pas authentifié, n'alimente pas le dossier
     du fil. Il est enregistré comme un nouveau courrier et signalé.
  5. **Groupes** : un groupe local et un groupe venu de la claim donnent accès à la même file. Un membre voit les tâches du
     groupe et peut les prendre ; un non-membre ne les voit pas.
  6. **Échéances** :
     - une échéance posée par la règle produit un rappel avant le terme et une escalade après, sans minuteur modélisé ;
     - un minuteur à expression en bordure de tâche fonctionne de son côté.
  7. **PDF** : un PDF déposé (à la main ou par SFTP) crée un courrier et suit le même routage.
  8. **MCP** :
     - les outils de lecture et d'action respectent l'ACL et sont tracés (provenance de l'agent) ;
     - un appel non autorisé est refusé ;
     - au moins un test traverse la vraie chaîne HTTP et sécurité.
  9. **Démo reproductible** : jeu de données, et un script qui le réinitialise. Une facture arrive par e-mail, une amende par
     PDF, et un courrier non reconnu tombe dans la file de tri.
  10. `./mvnw verify`, `npm run lint && npm run test:all` (Playwright compris) et `scripts/check-docs.sh` sont verts.
- Contraintes :
  - Stack figée. Architecture hexagonale (ArchUnit). Une instance par tenant.
  - Changesets Liquibase nouveaux uniquement.
  - Aucune expression libre hors du langage borné (§40, allowlist BPMN).
  - Secrets des canaux chiffrés.
  - Corps et pièces bornés (taille et nombre). Anti-rejeu par Message-ID et par fichier déposé.
  - Documentation à mettre à jour :
    - nouvelle spec 49 ;
    - guides utilisateur (registre, file de tri, groupes), conception BPMN (briques) et déploiement ;
    - `.env*.example` si une variable apparaît ;
    - `identifiants.md`, openapi.
- Validé par l'utilisateur le : 2026-10-31 (CI GitHub remplacée par les preuves locales tant que le runner est en panne)

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|
| Représentation du courrier | Dossier d'un type dédié ; entité `mailroom` dédiée ; pas d'objet courrier | Dossier dédié | Tranchée le 2026-10-31 (dossier dédié + fils e-mail) → §113 |
| Réponse dans un fil connu | Nouveau courrier rattaché ; ajout au courrier existant ; reroutage | Nouveau courrier rattaché | Tranchée le 2026-10-31, avec un statut lisible et sans complexifier les BPMN |
| Réaction du workflow à une réponse | Plateforme seule ; sous-process modélisé ; workflow séparé | Plateforme + brique optionnelle | Tranchée le 2026-10-31 : les trois, au choix et combinables |
| Canaux de la première livraison | E-mail + PDF sans OCR ; e-mail seul ; tout, OCR compris | E-mail + PDF sans OCR | Tranchée le 2026-10-31 : OCR dans un lot ultérieur, eBox en feature séparée |
| Groupes | Locaux ; claim IdP ; les deux | Locaux | Tranchée le 2026-10-31 : les deux → §114 |
| Échéances | Plateforme ; minuteurs BPMN (#186) ; plus tard | Plateforme | Tranchée le 2026-10-31 : les deux au choix |
| MCP | Lecture ; lecture + actions ; rien | Lecture | Tranchée le 2026-10-31 : lecture + actions, avec `security-review` |
| Fils initiés par un e-mail sortant | Enregistrer le Message-ID des envois de « Notifier » ; ne suivre que les fils entrants | Enregistrer | Tranchée à la conception → §113 D-MAILROOM-THREAD |
| Corrélation du message vers les process | Tous les process du dossier qui l'attendent ; seulement le principal | Tous ceux qui l'attendent | Tranchée à la conception → §113 D-MAILROOM-REACT (requête vérifiée dans `FlowableWorkflowEngineAdapter`) |
| Rôle de l'opérateur de tri | Rôle existant (`DOSSIER_MANAGER` sur le type « courrier ») ; nouveau rôle | Rôle existant + ACL du type | Hypothèse |
| Dépôt SFTP de PDF | Nouveau connecteur TRIGGER « dossier surveillé » ; étendre `batch-import` | Nouveau connecteur | Reportée à la conception du lot 5 |

## Conception

Décisions : [§113](../decisions/113-digital-mail-room-courrier-dossier-et-fils.md) (D-MAILROOM-MODEL, -ROUTE, -THREAD, -REACT),
[§114](../decisions/114-groupes-d-utilisateurs-et-assignation-par-groupe.md) (D-GROUP-SOURCE, -ASSIGN). Les points du registre
pour les échéances (lot 4), le dépôt de PDF (lot 5) et le MCP (lot 6) s'écrivent à la conception de leur lot.

- Faits vérifiés dans le code :
  - **Connecteurs et dossiers :**
    - `ConnectorTriggerRuntimeImpl.handle` crée le dossier, ou le retrouve par référence, puis rattache les pièces
      (`AttachInboundFilesUseCase`, ClamAV). Il démarre le process par l'outbox (`TriggerOutboxPayload`).
    - Un connecteur e-mail sait donc déjà créer un dossier « courrier » et démarrer le process de tri, à condition d'être
      configuré avec `dossierTypeCode` et `processKey` (`InboundMailRouting.fields`).
  - **En-têtes des e-mails :**
    - `ImapMailbox.Mail` ne porte ni `In-Reply-To` ni `References`. `AngusImapMailbox.header(message, name)` sait les lire.
    - Graph lit `internetMessageHeaders` (`HttpGraphMailbox.MESSAGE_SELECT`) mais pas `conversationId`.
  - **Envois :** `SmtpEmailAdapter` ne garde aucun Message-ID.
  - **Corrélation de messages :** `FlowableWorkflowEngineAdapter.correlate` interroge les exécutions par
    `messageEventSubscriptionName` et `processVariableValueEquals`, puis appelle `messageEventReceived`. Aujourd'hui il
    refuse plusieurs correspondances : la mail room en voudra plusieurs (D-MAILROOM-REACT).
  - **BPMN :**
    - `createChildDossierTask` lit des paramètres littéraux ; c'est le modèle de la brique de routage.
    - `BpmnExpressionSurfaceScanner` refuse tout `${…}` hors `delegateExpression` allowlisté et `conditionExpression`.
      Pas de minuteur à expression aujourd'hui.
    - Un `candidateGroups` littéral est accepté (`assertSafeUserTask`). Aucune assignation de groupe n'existe à l'exécution.
  - **Tâches :** « Tâches » ne liste que `taskAssignee` (`FlowableWorkflowTaskInboxAdapter`,
    `WorkflowTaskController.mine`). Les tâches libres sont visibles des éditeurs, dossier par dossier (§92).
  - **Annuaire :** `KnownUser` stocke les rôles du dernier jeton (`RecordUserLoginUseCaseImpl`), pas de groupes.
  - **Liste des dossiers :** elle filtre déjà par `dossierTypeCode` et par champ (`f.<champ>`) dans l'API et le SPA
    (`listDossiers`). Le registre peut s'y appuyer.
  - **Numérotation :** `DossierReferenceGenerator` préfixe par type.
  - **Jobs :** des jobs planifiés existent (`SlaAlertJob`, verrou de la spec 36). Ce sont les modèles du job d'échéances.
- Composants touchés :
  - **Lot 1 :**
    - `ImapMailbox.Mail` et Graph : `inReplyTo`, `references` (bornés), `conversationId`. Exposés au mapping et en variables.
    - Paquet `exemples/digital-mail-room/` : type `COURRIER` (canal, expéditeur, objet, reçu le, Message-ID, service,
      échéance), `workflow-config` (statuts du courrier), process de tri minimal (enregistrer, « Trier le courrier »),
      connecteur IMAP d'exemple.
  - **Lot 2a (#190) :**
    - Domaine `user` : groupes locaux, appartenances, claim de groupes au login.
    - Changesets. API d'administration des groupes. Écran d'administration.
    - « Tâches » étendu aux tâches des groupes du membre.
  - **Lot 2b :**
    - Délégué `routeMailTask` (allowlist et palette), table DMN d'exemple.
    - Assignation de groupe (`addCandidateGroup`). File de tri.
    - Réaffectation vers un groupe (API et SPA). Trace (statuts de workflow et historique).
  - **Lot 3 :**
    - Table d'index des Message-ID (reçus et envoyés). Message-ID généré pour « Notifier ».
    - Détection du fil dans `InboundMailRouting`, avant `referencePattern`.
    - Délégué de rattachement : pièces vers le dossier du fil, lien `LINKED`, avertissement de la personne assignée,
      corrélation `courrierRecu` vers toutes les exécutions abonnées.
    - Workflow « réponse » déclaré par type de dossier. Brique « Attendre un courrier ».
- Scénarios de test, par critère du cadrage :
  1. Intégration : un e-mail GreenMail avec deux pièces donne un dossier `COURRIER` numéroté, ses champs mappés, ses pièces
     scannées, et le process de tri démarré. Unitaire : `In-Reply-To` et `References` bornés en nombre et en longueur.
  2. Intégration : une règle DMN inconnue ou une valeur hors catalogue envoie le courrier à la file de tri. La réaffectation
     trace qui, quand, d'où et vers où. Chaîne HTTP : un non-membre ne réaffecte pas (403).
  3. Intégration : une réponse `In-Reply-To` attestée produit un courrier lié au fil, sans routage. Ses pièces vont dans le
     dossier du fil. Deux exécutions en attente reprennent toutes les deux. Un workflow « réponse » déclaré démarre.
  4. Intégration : la même réponse sans attestation donne un nouveau courrier, avec `mailThreadIgnored`, et le dossier du fil
     est intact.
  5. Chaîne HTTP et sécurité : un membre d'un groupe local et un membre venu de la claim voient la tâche du groupe et peuvent
     la prendre ; un non-membre ne la voit pas. Playwright : écran des groupes, et « Tâches » avec les tâches de groupe.
  6. Lot 4, à écrire avec son point du registre.
  7. Lot 5, à écrire avec son point du registre.
  8. Lot 6 : chaque outil MCP refuse sans ACL, trace la provenance, et traverse la chaîne HTTP.
  9. Lot 7 : le script de démo rejoue le scénario sur une base vide.

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|
| 1 | En-têtes de fil lus par IMAP et Graph ; paquet `exemples/digital-mail-room` (type `COURRIER`, statuts, process de tri minimal, connecteur d'exemple) ; registre = liste filtrée | livré | #262 |
| 2a | Groupes locaux + claim IdP, administration, « Tâches » par groupe (#190, §114) | livré | #264 |
| 2b | Brique de routage `routeMailTask` (DMN, dossier métier lié, groupe destinataire), groupe porté par le lien (D-GROUP-LINK), écouteur de tâches, table et process de tri de l'exemple | livré | #266 |
| 2c | Réaffectation d'une tâche vers un groupe (API, SPA), trace qui / quand / d'où / vers où | livré | à ouvrir |
| 3 | Fils : index des Message-ID reçus et envoyés, détection, rattachement, trois modes de réaction, brique « Attendre un courrier » | à faire | |
| 4 | Échéances plateforme (rappel, escalade) + minuteurs à expression (#186) ; point du registre à écrire | à faire | |
| 5 | Canal PDF : dépôt manuel + dossier SFTP surveillé ; point du registre à écrire | à faire | |
| 6 | MCP lecture + actions, `security-review` ; point du registre à écrire | à faire | |
| 7 | Démo reproductible, spec 49, guides, clôture | à faire | |
| plus tard | OCR intégré ; gabarits de notification (#185) ; classification IA | à faire | |

Fait dans le lot 1 :
- `MailThread` lit `In-Reply-To` et `References` (identifiants `<…>`, 250 caractères chacun, les 10 plus récents, en-tête lu sur 8 000
  caractères au plus) et le `conversationId` de Graph. Ces valeurs sont exposées au mapping et aux variables `mailInReplyTo`,
  `mailReferences` et `mailConversationId`.
- Paquet `exemples/digital-mail-room`.
- `MailRoomExampleIT` traverse le connecteur IMAP réel (boîte simulée), la création du dossier, la pièce, l'outbox et le démarrage du
  tri.

Appris pendant le lot :
- Un dossier créé par un connecteur naît `DRAFT` : la configuration du workflow doit l'autoriser au démarrage. Sinon l'outbox marque
  l'événement mort sans rien journaliser.
- Un champ mappé vide fait refuser le message : on ne mappe donc pas les en-têtes de fil sur un champ.
- Sans `trustedAuthservId`, aucun expéditeur n'est attesté.

Fait dans le lot 2a :
- Domaine `user` : `UserGroup` et `UserGroupsUseCase` (administration, annuaire, `groupsOf`). `KnownUser.idpGroups` est
  enregistré à la connexion depuis la claim `APP_SECURITY_OIDC_GROUPS_CLAIM`.
- Changeset 041 : `user_group`, `user_group_member`, `known_user.idp_groups`.
- API `/api/user-groups` (`TENANT_ADMIN`).
- « Tâches » : tâches assignées OU candidates pour les groupes de l'utilisateur, une tâche libre n'étant gardée que si
  l'utilisateur peut modifier le dossier.
- SPA : écran Admin → Groupes, et bouton « Prendre » dans « Mes tâches ».

Fait dans le lot 2b :
- Brique `routeMailTask` (« Router le courrier »), avec ses replis vers la file de tri.
- Groupe porté par le lien de workflow (table `workflow_link_group`, changeset 042) et écouteur `LinkGroupTaskAssigner`.
- `ManualStartOrchestrator.start(..., assignedGroup)`.
- Exemple : groupes, table `routageCourrier`, process de tri asynchrone qui ouvre le courrier, dossier « facture » et process
  `traiterFacture`.

Appris pendant le lot 2b :
- Un courrier routé traverse tout le tri pendant son démarrage : sans étape asynchrone, il serait clôturé avant que le
  démarrage ne soit confirmé.
- `DRAFT` ne mène qu'à `OUVERT` : le passage « en cours » du démarrage ne s'applique pas à un dossier créé par un
  connecteur, d'où les deux premières étapes du tri.

Fait dans le lot 2c (§114 D-GROUP-REASSIGN) :
- `ReassignTaskToGroupUseCase` et `POST .../tasks/{taskId}/reassign-group`, avec une note de trace dans le dossier.
- `GET /api/user-group-options` ; la liste des tâches d'un lien expose `candidateGroups`.
- Fiche dossier : groupe de la tâche, choix « Réaffecter au groupe… » pour les éditeurs.

Prochaine action : preuves complètes du lot 2c, PR, puis lot 3 (fils de discussion). Les preuves sont locales (`./mvnw verify` hors root, tests frontend, `check-docs`) tant que le
runner de CI est en panne (accord de l'utilisateur, 2026-10-31).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-10-31 | lot 1 — `./mvnw -Dmaven.compiler.release=21 verify` (frontend compris, worktree propre) | vert : 3 997 tests, 0 échec, 0 erreur, 5 ignorés, couverture atteinte |
| 2026-10-31 | lot 1 — `scripts/check-docs.sh` | vert |
| 2026-10-31 | lot 2a — tests ciblés (domaine, persistance, `UserGroupAdminApiTest`, `GroupTasksApiTest`) | vert : 33 unitaires, 9 intégration ; `GroupTasksApiTest` rouge sans le branchement des groupes |
| 2026-10-31 | lot 2a — `cd frontend && npm run lint && npm run test` | vert : 135 fichiers, 831 tests |
| 2026-10-31 | lot 2a — Playwright `pilotage-groupes` + `pilotage-taches` | vert : 13 tests |
| 2026-10-31 | lot 2a — `./mvnw -Dmaven.compiler.release=21 verify` (frontend compris) | vert : 4 021 tests, 0 échec, 0 erreur, 5 ignorés, couverture atteinte |
| 2026-10-31 | lot 2a — `cd frontend && npx playwright test` (suite complète) | vert : 152 tests |
| 2026-10-31 | lot 2b — `MailRoomExampleIT`, `RouteMailDelegateTest`, `ManualStartOrchestratorTest`, `BuildingBlockCatalogTest` | vert : 2 + 6 + 10 + 11 tests |
| 2026-10-31 | lot 2b — `./mvnw -Dmaven.compiler.release=21 verify` (frontend compris) | rouge au premier passage (ArchUnit : lecture brute des champs et de la valeur par défaut ; palette 15 → 16 briques), corrigé ; puis vert : 4 032 tests, 0 échec, 5 ignorés, couverture atteinte |
| 2026-10-31 | lot 2b — `npm run lint && npm run test` puis `npx playwright test` | vert : 831 Vitest, 152 Playwright |
| 2026-10-31 | lot 2c — `ReassignTaskToGroupUseCaseImplTest`, `GroupReassignApiTest` | vert : 4 + 1 tests ; 403 sans droit d'édition, 400 pour un groupe inconnu |
| 2026-10-31 | lot 2c — `./mvnw -Dmaven.compiler.release=21 verify` (frontend compris) | vert : 4 037 tests, 0 échec, 5 ignorés, couverture atteinte |
| 2026-10-31 | lot 2c — `npm run lint && npm run test` puis `npx playwright test` | vert : 836 Vitest ; Playwright 152/153 au premier passage (`E2E-BASE-2`, fermeture du contexte au-delà de 30 s, sans lien avec le lot ; 3/3 seul), puis 153/153 |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
