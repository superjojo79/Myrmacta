# §150 — Lecture de l'eBox Enterprise (canal de la Digital Mail Room)

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-20, issue [#12](https://github.com/superjojo79/Myrmacta/issues/12)) : se clôt avec la livraison du
lot 1b.

**Constat.** Une commune ou un CPAS reçoit dans son eBox Enterprise le courrier officiel des administrations. Myrmacta le
lit comme *Document Consumer* (API e-Box REST 3.8 et Document Provider Registry 1.2, OAuth v5 de la sécurité sociale) et
en fait un courrier de la Digital Mail Room (§113). Contraintes relevées dans les specs publiques : télécharger la pièce
principale d'un message le marque comme lu ; l'API filtre la date de réception au jour près ; plusieurs registres de
messages (fournisseurs) existent en production ; l'onboarding consommateur est irréversible et l'environnement
d'acceptation n'est pas accessible pendant le développement.

**Décisions.**

- **D-EBOX-PARTITIONS** (choix de l'utilisateur, 2026-11-20). Myrmacta ne lit que des partitions eBox **qui lui sont
  dédiées** : la liste des partitions est obligatoire dans la configuration de l'instance et chaque lecture est filtrée
  par `recipientPartition`. Le reste de l'eBox n'est jamais lu ni marqué comme lu. Écartés : tout lire (les agents qui
  suivent l'eBox sur le portail perdent le repère « non lu ») et ne lire que les métadonnées (une pièce expirée est perdue).
- **D-EBOX-JOURNAL.** Un message pris en charge est inscrit dans `ebox_message_journal`, dans la même transaction que la
  création du courrier. La clé est l'eBox lue (numéro BCE), le registre et le `messageId`, et non l'instance : deux
  instances qui lisent la même partition ne créent pas de doublon. Un échec de remise n'inscrit rien : le message est
  relu au passage suivant. Une pièce écartée par les contrôles ne retient pas le courrier (comme pour l'e-mail) : le
  process reçoit `inboundAttachmentsRejected`.
- **D-EBOX-WINDOW.** Chaque passage lit à partir de la veille du dernier jour lu entièrement (curseur `ebox_read_cursor`
  par instance, registre et partition). Le curseur n'avance que si aucune remise n'a échoué et que le plafond par
  passage n'a pas été atteint. Premier passage : `initialLookbackDays` (14 par défaut, 90 au plus). Écarté : ne lire que
  les non lus (`read=false`), qui casse dès qu'un agent ouvre un message sur le portail.
- **D-EBOX-REGISTRY-HOSTS.** Les registres sont découverts par le Document Provider Registry. Le jeton part vers chaque
  URL annoncée : elle doit être en `https`, passer `OutboundUrlGuard` (adresse figée, pas de redirection) et son hôte doit
  finir par un suffixe autorisé (`allowedHostSuffixes`, par défaut `socialsecurity.be`, `fgov.be`, `belgium.be`). Seuls
  les points d'accès en version 3.x, actifs à la date du jour et non désactivés, sont lus.
- **D-EBOX-AUTH.** OAuth v5 : `client_credentials` avec `client_assertion` signée par la clé du certificat X.509
  (`OAuth2TokenProvider`, mode `PRIVATE_KEY_JWT`, imposé par le connecteur). La forme exacte attendue par la sécurité
  sociale (en-têtes et revendications du JWT) reste à confirmer en acceptation.
- **D-EBOX-ACTION.** La brique « Marquer l'action eBox comme faite » (`eboxActionDoneTask`) confirme l'action qu'un
  expéditeur demande sur un message (`PATCH /ebox/messages/{id}/messageActions/{actionId}`, `isExecuted: true`, corps
  `application/merge-patch+json`). Le connecteur `ebox-inbound` devient `BOTH` : son unique opération de service,
  `markActionExecuted`, ne touche qu'un message **inscrit au journal** pour ce tenant et cette eBox, sur le point d'accès
  actif de son registre. Le connecteur (`connectorInstanceId`, de type `ebox-inbound`) et l'action (`eboxActionId`) sont
  des attributs littéraux du BPMN. Le message vient des variables `eboxRegistre` et `eboxMessageId` du courrier, sinon des
  champs de même nom du dossier courant : le routage ne passe aucune variable au process métier, mais il copie les champs
  non sensibles déclarés sur les deux types. Sans `eboxActionId`, la brique confirme la seule action encore ouverte
  (`GET .../messageActions`) ; plusieurs actions ouvertes font échouer l'étape, et une action déjà faite la laisse passer.
  Toute erreur de l'eBox remonte : l'étape échoue et son incident se rejoue. Écartés : « Appeler un connecteur » avec une
  requête construite (la cible viendrait de variables modifiables sans garde propre à l'eBox) et une table qui relie le
  dossier au message (changeset de plus pour une information que les champs portent déjà).
- **Specs non versionnées.** Les YAML de l'ONSS (licence « e-Box » sans clause de redistribution) ne sont pas copiés
  dans le dépôt : les tests utilisent des réponses écrites à la main, conformes aux schémas, et cette fiche garde les
  liens : [index des specs](https://dev.eboxenterprise.be/docs/spec/specifications).

**Hors périmètre.** Gestion des partitions et accusés des recommandés (lot 2), envoi (#13), rattachement des réponses
(#11), recette en acceptation.

**Impacte.** `docs/specs/49-digital-mail-room.md`, `docs/specs/06-connecteurs.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`,
`exemples/digital-mail-room/`.
