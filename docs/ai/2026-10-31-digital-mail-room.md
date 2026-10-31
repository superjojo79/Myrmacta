# Digital Mail Room — document de travail

Issue : #217 (sous-issues #190, #186, #185) · Spec du domaine : à créer, `docs/specs/49-digital-mail-room.md` ; s'appuie sur
[04](../specs/04-dossiers.md), [25](../specs/25-introduction-dmn.md), [34](../specs/34-integrations-entreprise.md),
[48](../specs/48-preverification-ia.md) · Points du registre : [§87](../decisions/087-d3c-d3d-e-mail-entrant-bus-azure.md),
[§92](../decisions/092-assignation-des-taches-m-assigner-et-annuaire.md), [§106](../decisions/106-preverification-ia-et-types-de-pieces.md) ;
nouveaux points à partir de §112

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
- Validé par l'utilisateur le :

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|
| Représentation du courrier | Dossier d'un type dédié ; entité `mailroom` dédiée ; pas d'objet courrier | Dossier dédié | Tranchée par l'utilisateur le 2026-10-31 (dossier dédié + fils e-mail) ; registre à la conception |
| Réponse dans un fil connu | Nouveau courrier rattaché ; ajout au courrier existant ; reroutage | Nouveau courrier rattaché | Tranchée le 2026-10-31, avec un statut lisible et sans complexifier les BPMN |
| Réaction du workflow à une réponse | Plateforme seule ; sous-process modélisé ; workflow séparé | Plateforme + brique optionnelle | Tranchée le 2026-10-31 : les trois, au choix et combinables |
| Canaux de la première livraison | E-mail + PDF sans OCR ; e-mail seul ; tout, OCR compris | E-mail + PDF sans OCR | Tranchée le 2026-10-31 : OCR dans un lot ultérieur, eBox en feature séparée |
| Groupes | Locaux ; claim IdP ; les deux | Locaux | Tranchée le 2026-10-31 : les deux |
| Échéances | Plateforme ; minuteurs BPMN (#186) ; plus tard | Plateforme | Tranchée le 2026-10-31 : les deux au choix |
| MCP | Lecture ; lecture + actions ; rien | Lecture | Tranchée le 2026-10-31 : lecture + actions, avec `security-review` |
| Fils initiés par un e-mail sortant | Enregistrer le Message-ID des envois de « Notifier » ; ne suivre que les fils entrants | Enregistrer | Hypothèse : `SmtpEmailAdapter` ne garde aujourd'hui aucun Message-ID |
| Corrélation du message vers les process | Tous les process du dossier qui l'attendent ; seulement le principal | Tous ceux qui l'attendent | Hypothèse, à vérifier contre l'API Flowable 8 à la conception |
| Rôle de l'opérateur de tri | Rôle existant (`DOSSIER_MANAGER` sur le type « courrier ») ; nouveau rôle | Rôle existant + ACL du type | Hypothèse |
| Dépôt SFTP de PDF | Nouveau connecteur TRIGGER « dossier surveillé » ; étendre `batch-import` | Nouveau connecteur | Hypothèse, à vérifier contre `batch-import` |

## Conception

Sans objet tant que le cadrage n'est pas validé.

## Lots

Découpage provisoire, à confirmer à la conception :

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|
| 1 | Type « courrier », numéro d'ordre, canal e-mail vers le courrier, registre (vue) | à faire | |
| 2 | Groupes locaux + claim (#190), table DMN de routage, process de tri modèle, file de tri, réaffectation, trace | à faire | |
| 3 | Fils de discussion : détection, rattachement, trois modes de réaction, brique « Attendre un courrier », statuts | à faire | |
| 4 | Échéances plateforme (rappel, escalade) + minuteurs à expression (#186) | à faire | |
| 5 | Canal PDF (dépôt manuel et SFTP surveillé) | à faire | |
| 6 | MCP lecture + actions, `security-review` | à faire | |
| 7 | Démo reproductible, spec 49, guides | à faire | |
| plus tard | OCR intégré ; gabarits de notification (#185) ; classification IA | à faire | |

Prochaine action : validation du cadrage par l'utilisateur, puis conception (faits vérifiés, points du registre).

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
