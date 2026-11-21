# §151 — eBox Enterprise : partitions vérifiées, classement des messages, recommandés

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-20, issue [#142](https://github.com/superjojo79/Myrmacta/issues/142)), suite de §150. Comportement :
[spec 06](../specs/06-connecteurs.md) et [spec 49](../specs/49-digital-mail-room.md) § « Canaux ». La question laissée
ouverte (Myrmacta crée-t-il la partition dédiée et ses règles de routage ?) est tranchée par **D-EBOX-NO-PARTITION-ADMIN**.

**Constat.** La lecture (§150) ne lit que les partitions dédiées à Myrmacta, que l'administrateur crée sur le portail. Une
partition mal orthographiée dans la configuration n'était jamais signalée. Un message traité restait dans la partition
lue, sans que les agents qui suivent l'eBox sur le portail sachent qu'il est pris en charge. Les recommandés n'étaient
repérés que par un booléen, et `registeredMailType: notApplicable` le mettait à tort à vrai. L'API Event 4.0 ne publie
que des événements de documents (`newDocument`, `readDocument`…) : il n'y a aucun événement de partition à suivre.

**Décisions.**

- **D-EBOX-NO-PARTITION-ADMIN** (choix de l'utilisateur, 2026-11-20). Le lot 2 se limite à « vérifier et classer » :
  Myrmacta ne crée ni partition ni règle de routage et n'appelle pas l'API Partition Management 3.0. L'administrateur crée
  sur le portail les partitions lues et la partition de classement ; Myrmacta vérifie les premières (D-EBOX-PARTITION-CHECK)
  et y range les messages traités (D-EBOX-MOVE). Écarté : créer la partition et ses règles depuis l'écran du connecteur
  (droit d'écriture sur l'organisation de l'eBox, pour une opération faite une fois).
- **D-EBOX-PARTITION-CHECK.** Avec `partitionApiUrl` (facultatif, même contrôle d'hôte que le registre), chaque passage lit
  les partitions de l'eBox (API Partition 1.3, `GET /ebox/{bce}/partitions`, lecture seule), page par page (`page`,
  `pageSize`, `totalItems`, comme l'API des messages ; dix pages de 100 au plus). Une partition configurée que l'eBox ne
  connaît pas est signalée dans le journal et n'est pas lue. Une liste « non vérifiable » ne retire rien et laisse un
  avertissement : erreur, réponse sans tableau `items`, liste vide, liste qui reste incomplète (le service ne pagine pas
  et rend moins que `totalItems`). La vérification ne bloque jamais la lecture.
- **D-EBOX-MOVE.** La brique « Classer le message eBox » (`eboxMoveTask`) déplace le message vers une partition cible
  (`eboxPartitionCible`, attribut littéral obligatoire) par `POST /ebox/messages/changePartition`
  (`targetRecipientPartitionId`). La cible a la forme d'une partition (`[A-Za-z0-9._:-]{1,100}`, contrôlée au déploiement)
  et n'est lue par aucune instance `ebox-inbound` active du tenant sur la même eBox : le message serait relu et quitterait
  la vue des agents. Le message doit avoir été lu dans une partition de l'instance appelée (partition inscrite au
  journal). Mêmes gardes que D-EBOX-ACTION : connecteur littéral de type `ebox-inbound`, message inscrit au journal de ce
  tenant et de cette eBox, opération servie à un process seulement, message lu dans les variables `eboxRegistre` et
  `eboxMessageId`, sinon dans les champs de même nom du dossier. **Rejeu** : sur un refus 400, 404 ou 409, le message est
  relu (`GET /ebox/messages/{id}`, `recipient.partition`) ; s'il est déjà dans la cible, l'étape passe avec
  `eboxClasse` et `eboxDejaClasse` à vrai. Sinon l'étape échoue avec un message qui dit la cause (404 : cible inconnue ou
  message disparu ; 410 : message expiré). Une erreur 5xx échoue sans relecture. `eboxClasse`, `eboxDejaClasse` et les
  variables de résultat de D-EBOX-ACTION sont réservées : un mapping ne peut pas les poser.
- **Contrat de `changePartition` (hypothèse).** Les specs de l'ONSS ne sont pas versionnées dans le dépôt (§150) et ni le
  faux serveur ni les tests ne fixent la réponse : le contrat n'est pas confirmable ici. Hypothèse retenue : 200, 202 et 204
  sont des succès ; un corps JSON qui signale l'échec du message (tableau ou objet non vide sous une clé `failed…`,
  `error…`, `rejected…`, `notMoved…`, `unprocessed…`, ou entrée de ce message avec `success` ou `moved` à faux, ou un
  `status` d'échec) fait échouer l'étape comme un refus 409, donc avec relecture. Un corps illisible ne compte pas comme
  un échec. 207 et les autres statuts font échouer l'étape. À confirmer en acceptation, comme D-EBOX-AUTH.
- **D-EBOX-REGISTERED.** `eboxRecommande` vaut vrai pour `isRegisteredMail` ou un `registeredMailType` autre que
  `notApplicable`. La variable `eboxTypeRecommande` reprend `nonQualifiedRegisteredMail` ou
  `qualifiedRegisteredMailNotification`, que la table de routage peut lire. La preuve de dépôt (API Registered Mail 1.1)
  concerne l'expéditeur : elle relève de l'envoi (#13).

**Hors périmètre.** Création des partitions et des règles de routage (D-EBOX-NO-PARTITION-ADMIN), envoi (#13). Suivis en
issues après la revue de clôture : vérifier l'existence de la cible avant le `POST`, montrer les partitions absentes dans
l'état de l'instance, test d'intégration complet de `eboxMoveTask`.

**Impacte.** `docs/specs/06-connecteurs.md`, `docs/specs/49-digital-mail-room.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`.
