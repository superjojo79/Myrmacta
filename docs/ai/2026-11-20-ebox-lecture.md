# Lecture de l'eBox Enterprise — document de travail

Issue : #12 (parent #14) · Spec du domaine : docs/specs/49-digital-mail-room.md · Points du registre : §113 (mail room),
§87 (connecteurs d'entrée par interrogation)

## Cadrage

- Problème et utilisateurs : une commune ou un CPAS reçoit dans son eBox Enterprise le courrier officiel des
  administrations (ONSS, SPF Finances…), plus tard les réponses des citoyens et les accusés des recommandés. Aujourd'hui,
  quelqu'un le consulte à la main sur le portail. Myrmacta doit le lire par API et le faire entrer dans la Digital Mail
  Room comme un canal de plus : un courrier par message, avec le même tronc commun (routage DMN, file de tri, échéances,
  archivage, traçabilité). Utilisateurs : les agents du service courrier et des services métier du tenant.
- Périmètre du lot 1 :
  - connecteur d'entrée `ebox-inbound` (`PollingTriggerConnector`), interrogé par `InboundPollingConnectorTriggerJob` ;
  - authentification OAuth v5 de la sécurité sociale (JWT signé par la clé du certificat X.509), en réutilisant
    `OAuth2TokenProvider` (mode `PRIVATE_KEY_JWT`, `oauthPrivateKey`, `oauthCertThumbprint`) ;
  - découverte des registres de messages (document provider registry 1.2, `GET /messageRegistries`), puis
    `GET /ebox/messages` sur chacun, page par page ;
  - un message = un courrier : métadonnées natives (expéditeur BCE, application, type, objet, dates de réception et
    d'expiration, recommandé, action requise, `isReplyAuthorized`) en variables et champs, pièces jointes importées et
    passées à l'antivirus par `ConnectorTriggerRuntime` ;
  - lecture limitée aux partitions dédiées à Myrmacta (Q1) : liste de partitions **obligatoire** dans la configuration de
    l'instance, chaque appel filtré par `recipientPartition` ; le reste de l'eBox n'est jamais lu ni marqué comme lu ;
  - journal des messages traités par instance (identifiant de registre + `messageId`) : pas de double traitement ;
  - étape BPMN « Marquer l'action comme faite » (`PATCH /ebox/messages/{id}/messageActions/{actionId}`) ;
  - faux serveur eBox pour les tests et la démo de l'exemple `digital-mail-room`.
- Hors périmètre (lots suivants) : partitions (sync, `changePartition`), accusés des recommandés (lot 2) ; envoi (#13) ;
  rattachement des réponses au dossier d'origine (#11, en attente ONSS/BOSA) ; recette contre l'environnement
  d'acceptation (onboarding consommateur irréversible) ; service d'événements (l'API 4.0 ne publie pas les nouveaux
  messages : la lecture se fait par interrogation).
- Critères de succès (vérifiables) :
  1. Contre le faux serveur, un message de l'eBox devient un dossier `COURRIER` avec ses pièces, routé par
     `routageCourrier` comme un e-mail.
  2. Deux registres configurés : les messages des deux sont lus.
  3. Une deuxième interrogation ne recrée aucun courrier ; un échec de remise ne marque pas le message comme traité et
     il est relu au tour suivant.
  4. Une pièce refusée par l'antivirus est comptée et tracée, comme pour SFTP (#277).
  5. Le jeton OAuth est demandé par `client_assertion` signée ; aucun secret n'apparaît dans les journaux.
  6. L'étape « Marquer l'action comme faite » appelle le PATCH ; une erreur de l'eBox crée un incident rejouable.
  7. Isolation : l'instance d'un tenant ne lit qu'avec les identifiants de ce tenant.
  9. Plusieurs instances `ebox-inbound` par tenant sont possibles (une par numéro BCE ou par groupe de partitions) ; deux
     instances actives qui couvrent la même partition du même numéro BCE sont refusées à l'enregistrement (sinon le journal,
     tenu par instance, laisserait passer des doublons).
  8. Une instance sans partition configurée est refusée à l'enregistrement ; un message hors des partitions configurées
     n'est jamais demandé au serveur (vérifié sur le faux serveur).
- Contraintes : stack figée (aucune nouvelle dépendance d'exécution ; WireMock en test seulement si besoin) ; un schéma par
  tenant ; persistance JPA (§122) ; secrets chiffrés au repos (`ConnectorSecretCipher`) ; anti-SSRF des URL de registre
  (`PinnedHttpClient`) ; exposition MCP : aucune nouvelle, le courrier se cherche déjà par `search_dossiers` ;
  documentation : spec 49 (canal eBox), spec 06 (catalogue), guide de l'exemple, `.env*.example` si une propriété apparaît.
- Validé par l'utilisateur le :

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Q1. Télécharger la pièce principale marque le message comme lu dans l'eBox (spec 3.8, `isRead`). Les agents qui suivent encore l'eBox sur le portail perdent ce repère. | A. Accepter : Myrmacta devient le point d'entrée, filtre facultatif sur des partitions. B. Ne lire que les métadonnées et télécharger à la demande (risque de perte à l'expiration). C. Ne lire que des partitions dédiées à Myrmacta. | A, avec un filtre facultatif `recipientPartition` dans la configuration (qui couvre C) | tranchée par Benoit le 2026-11-20 : **C**, partitions dédiées obligatoires (point du registre à créer à la conception) |
| Q2. Licence des specs (« e-Box », digitalbelgium.be, sans clause de redistribution) | A. Committer les YAML. B. Garder les liens versionnés, écrire des stubs JSON à la main, et un test de contrat facultatif qui télécharge les YAML. | B | hypothèse |
| Q3. Fenêtre d'interrogation : l'API filtre `receivedAfter` au jour près | A. Relire depuis J-1 de la dernière lecture réussie, dédoublonner par le journal. B. Ne lire que les non lus (`read=false`). | A (B casse dès qu'un agent ouvre un message sur le portail) | hypothèse |
| Q4. Faux serveur de démo | A. Conteneur WireMock dans le compose de l'exemple, avec des fichiers de mappings. B. Contrôleur dans l'application sous un profil `demo`. | A (rien dans l'application livrée) | hypothèse |
| Q5. Journal anti-doublon | A. Table dédiée `ebox_message_journal` (instance, registre, messageId, dossier, date). B. Réutiliser `mail_message_index`. | A (`mail_message_index` porte des Message-ID e-mail et la logique de fil) | hypothèse |

## Conception

- Faits vérifiés dans le code ou les sources :
  - `PollingTriggerConnector.poll(instance, delivery)` : remise avant acquittement à la source (§87).
  - `InboundPollingConnectorTriggerJob.TYPES` liste les types interrogés ; `ebox-inbound` s'y ajoute.
  - `OAuth2TokenProvider` gère `client_credentials` + `client_assertion` (`PRIVATE_KEY_JWT`) ; champs dans
    `OAuth2ConfigSupport`.
  - `ConfigFieldType.SECRET_TEXTAREA` pour la clé PEM.
  - Spec 3.8 : `GET /ebox/messages` (pagination `page`/`pageSize` ≤ 100, `receivedAfter` en date, `recipientPartition`),
    `GET /ebox/messages/{id}/attachments/{attachmentId}/content`, `PATCH /ebox/messages/{id}/messageActions/{actionId}`
    (`isExecuted`), en-tête `EnterpriseNumberHeader`. Registry 1.2 : `GET /messageRegistries`.
- Composants touchés : à détailler à la conception.
- Scénarios de test : à dériver des critères 1 à 7.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 0 | Cadrage (ce document) | en cours | |
| 1 | Lecture de bout en bout contre le faux serveur | à faire | |
| 2 | Partitions et accusés des recommandés | à faire | |

Prochaine action : validation du cadrage par Benoit, puis conception du lot 1. Le lot 2 (partition management 3.0)
pourra créer la partition dédiée et ses règles de routage depuis Myrmacta ; en lot 1, l'administrateur la crée sur le
portail eBox.

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
