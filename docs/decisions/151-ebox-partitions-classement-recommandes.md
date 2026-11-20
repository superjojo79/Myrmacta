# §151 — eBox Enterprise : partitions vérifiées, classement des messages, recommandés

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-20, issue [#142](https://github.com/superjojo79/Myrmacta/issues/142)), suite de §150. Reste à trancher
par l'utilisateur : Myrmacta crée-t-il aussi la partition dédiée et ses règles de routage (API Partition Management 3.0) ?

**Constat.** La lecture (§150) ne lit que les partitions dédiées à Myrmacta, que l'administrateur crée sur le portail. Une
partition mal orthographiée dans la configuration n'était jamais signalée. Un message traité restait dans la partition
lue, sans que les agents qui suivent l'eBox sur le portail sachent qu'il est pris en charge. Les recommandés n'étaient
repérés que par un booléen, et `registeredMailType: notApplicable` le mettait à tort à vrai. L'API Event 4.0 ne publie
que des événements de documents (`newDocument`, `readDocument`…) : il n'y a aucun événement de partition à suivre.

**Décisions.**

- **D-EBOX-PARTITION-CHECK.** Avec `partitionApiUrl` (facultatif, même contrôle d'hôte que le registre), chaque passage lit
  les partitions de l'eBox (API Partition 1.3, `GET /ebox/{bce}/partitions`, lecture seule). Une partition configurée que
  l'eBox ne connaît pas est signalée dans le journal et n'est pas lue. Si la liste ne peut pas être lue, rien n'est retiré :
  la vérification ne bloque jamais la lecture.
- **D-EBOX-MOVE.** La brique « Classer le message eBox » (`eboxMoveTask`) déplace le message vers une partition cible
  (`eboxPartitionCible`, attribut littéral obligatoire) par `POST /ebox/messages/changePartition`
  (`targetRecipientPartitionId`). La cible ne peut pas être une partition lue par l'instance : le message serait relu et
  quitterait la vue des agents. Mêmes gardes que D-EBOX-ACTION : connecteur littéral de type `ebox-inbound`, message
  inscrit au journal de ce tenant et de cette eBox, opération servie à un process seulement, message lu dans les
  variables `eboxRegistre` et `eboxMessageId`, sinon dans les champs de même nom du dossier.
- **D-EBOX-REGISTERED.** `eboxRecommande` vaut vrai pour `isRegisteredMail` ou un `registeredMailType` autre que
  `notApplicable`. La variable `eboxTypeRecommande` reprend `nonQualifiedRegisteredMail` ou
  `qualifiedRegisteredMailNotification`, que la table de routage peut lire. La preuve de dépôt (API Registered Mail 1.1)
  concerne l'expéditeur : elle relève de l'envoi (#13).

**Hors périmètre.** Création des partitions et des règles de routage depuis Myrmacta (en attente de l'utilisateur),
envoi (#13).

**Impacte.** `docs/specs/06-connecteurs.md`, `docs/specs/49-digital-mail-room.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`.
