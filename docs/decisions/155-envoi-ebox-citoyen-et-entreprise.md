# §155 — Envoi vers l'eBox citoyen et entreprise (rôle expéditeur)

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-20, issue [#13](https://github.com/superjojo79/Myrmacta/issues/13), sous [#14](https://github.com/superjojo79/Myrmacta/issues/14)).
Lot 1 livré sur la branche `feat/ebox-envoi`, lot 2 (type de connecteur et brique) sur `feat/ebox-envoi-lot2` ; suite et questions ouvertes : document de travail
`docs/ai/2026-11-20-ebox-envoi.md`. Lecture de l'eBox : [§150](150-lecture-ebox-enterprise.md).

**Constat.** Une commune publie depuis un dossier une décision ou un courrier dans l'eBox d'une entreprise (numéro BCE) ou
d'un citoyen (NISS). Deux mondes : l'eBox Enterprise (ONSS/Smals, specs publiques e-Box REST 3.8, Federation 1.4, Registered
Mail 1.1, OAuth v5 avec JWT signé par le certificat X.509) et l'eBox citoyen (BOSA, spec sous NDA, OAuth sans X.509). BOSA
valide l'audit trail avant tout démarrage ; l'onboarding est irréversible et aucun environnement réel n'est accessible pendant
le développement : tout est testé contre un faux serveur.

**Décisions** (cadrage validé par Benoit le 2026-11-20, sauf hypothèses nommées).

- **D-EBOX-SEND-MODEL.** Un modèle commun, calqué sur le format entreprise (`MessageToPublish` réduit à ce que Myrmacta
  produit : destinataire, `senderApplicationId`, `messageTypeId`, une langue, objet, corps facultatif, une pièce principale et
  des annexes, recommandé, réponse autorisée et échéance, expiration, corrélation), plus une **extension citoyen** (invitation à
  payer : montant, devise, IBAN et communication structurée vérifiés modulo 97, BIC, bénéficiaire, échéance), refusée pour une
  entreprise. Règles de la spec : une langue, une pièce principale, 20 pièces et 100 Mo au plus ; un recommandé n'a pas de
  corps et au plus neuf annexes. Le NISS n'apparaît jamais dans un `toString`. Une pièce est copiée une seule fois (à la construction) et son empreinte calculée une seule fois ; le corps multipart est écrit d'un bloc à sa taille exacte, sans tampon intermédiaire (revue : environ quatre fois la taille des pièces en mémoire auparavant).
- **D-EBOX-SEND-CHANNELS.** Un port `EboxChannel` par voie, une implémentation par voie : `HttpEboxEnterpriseChannel`
  (entreprise) et `StubEboxCitizenChannel` (citoyen, voir ci-dessous). Le compte expéditeur est une instance de connecteur
  de type `ebox-outbound` (URL, mandat `onBehalfOf`, identifiants OAuth chiffrés au repos) ; le domaine n'en voit que
  l'identifiant. Mêmes gardes qu'en lecture : https, suffixe d'hôte autorisé, `OutboundUrlGuard`, aucune redirection
  (D-EBOX-REGISTRY-HOSTS), OAuth v5 en `PRIVATE_KEY_JWT` (D-EBOX-AUTH), un jeton par scope ; `oauthClientId` borné à 200 caractères sans caractère de contrôle. Le JSON publié suit un ordre de clés déclaré (`LinkedHashMap`), jamais `Map.of`, dont l'ordre d'itération change d'une JVM à l'autre (échec du job PIT en JDK 25) ; les tests comparent des nœuds JSON.
- **D-EBOX-SEND-CONSENT.** Le consentement est constaté avant chaque envoi et gardé au journal avec lui. Son poids diffère :
  citoyen sans consentement (`citizenEboxConsents/search`) ou sans eBox → **voie de secours seule** ; entreprise : la spec
  Federation 1.4 dit qu'il n'y a pas de consentement à vérifier, seulement la préférence `exclusivelyEbox`
  (`eboxPreferences/search`) → eBox ; **hypothèse H1** : eBox **et** copie par la voie de secours tant que l'entreprise n'a
  pas choisi l'eBox comme canal exclusif ; entreprise absente → voie de secours seule. La voie de secours (`PAPER`, `EMAIL`)
  est choisie par le process ; Myrmacta la lui rend, il ne l'exécute pas. Un service de consentement muet ou une réponse autre que 200 de la fédération, **404 compris**, arrête l'envoi : un 404 n'est jamais lu comme « pas d'eBox ».
- **D-EBOX-SEND-IDEMPOTENCE.** La clé d'idempotence est fournie par l'appelant (stable par dossier, étape, document). Elle part
  en `Idempotency-Key` (409 = doublon, le premier envoi a abouti) et en `originalMessageId`. Une clé déjà traitée rend l'issue
  du journal sans rappeler l'eBox ; la même clé pour un autre destinataire est refusée. Une issue inconnue (5xx, délai,
  coupure après l'envoi, ou 2xx inexploitable : JSON illisible, `messageId` absent ou de plus de 200 caractères, preuve incomplète) est journalisée puis l'étape échoue pour être rejouée avec la même clé. Au rejeu, le consentement est constaté **de nouveau** et la voie redécidée : un consentement retiré entre-temps mène à la voie de secours, jamais à l'eBox.
- **D-EBOX-SEND-REGISTERED.** Un recommandé passe par Registered Mail 1.1 (`POST /registeredMails`), `customerReference` =
  clé d'idempotence ; la preuve de dépôt (`ProofOfDispatch`, signée) revient dans la réponse ; avant d'envoyer, une preuve déjà
  émise pour cette référence (`GET /proofsOfDispatch`) fait un doublon. Le journal garde l'empreinte SHA-256 de la signature,
  le JSON de la preuve est rendu à l'appelant pour le dossier ; au rejeu d'un recommandé publié, la preuve est relue par `GET /proofsOfDispatch/{messageId}`. Sans `registeredMailUrl`, un recommandé est refusé. Registered Mail 1.1 n'a pas d'`Idempotency-Key` : la lecture préalable suivie du `POST` ne protège pas seule d'un double envoi. Une **réservation** unique par (tenant, clé) (`ebox_send_reservation`, changeset 055) est prise avant l'appel et rendue sur un refus certain. Une clé déjà réservée (tentative concurrente ou incertaine) n'est jamais renvoyée : sa preuve est cherchée par `customerReference` ; trouvée, c'est un doublon ; absente, l'envoi échoue avec une erreur explicite à rejouer plus tard (`EboxRegisteredMailUnresolvedException`). **Limite** : une clé dont la preuve n'apparaît jamais reste bloquée ; la lever est une action humaine (vérification auprès de Smals, nouvelle clé).
- **D-EBOX-SEND-AUDIT.** Journal `ebox_send_audit` (changeset 054) **en ajout seul** : entité JPA `@Immutable`, dépôt sans
  méthode de modification, et trois déclencheurs PostgreSQL qui refusent `UPDATE`, `DELETE` et `TRUNCATE` (ATREQ01). **Limite** : le rôle applicatif possède la table (Liquibase tourne avec lui, `provision-tenant.sh` lui donne `CREATE` et `ALL`) ; il peut donc désactiver ou supprimer ces déclencheurs (`ALTER TABLE … DISABLE TRIGGER`). Les déclencheurs protègent d'une erreur de l'application, pas d'un rôle applicatif compromis. **Mesure cible** : un rôle de migration distinct propriétaire des tables, le rôle applicatif réduit à `INSERT` et `SELECT` sur `ebox_send_audit` ; elle touche le provisionnement de toutes les tables et reste ouverte. Chaque
  ligne est validée dans sa propre transaction. Une ligne pour une publication acceptée, un doublon, une issue incertaine ou une
  voie de secours seule ; un refus certain (4xx, adresse refusée, connexion impossible) n'en laisse aucune (ATREQ04). Champs
  retenus, inspirés d'ATREQ :

  | Champ | Contenu | Source ATREQ / motif |
  |---|---|---|
  | `recorded_at` | horodatage à la microseconde (UTC) | horodatage ms ou plus fin |
  | `token_hash` | SHA-256 du jeton d'accès, jamais le jeton | empreinte du jeton |
  | `client_id`, `on_behalf_of` | client OAuth, mandat (BCE représenté) | client, en-tête de mandat |
  | `request_method`, `request_url`, `response_code` | appel ; `599` pour une réponse jamais reçue | méthode, URL, code (générique sur délai) |
  | `documents` | taille, SHA-256, rang de chaque pièce | taille et empreinte des pièces |
  | `provider_message_id`, `proof_signature_sha256` | identifiant rendu, empreinte de la preuve de dépôt | identifiant rendu |
  | `recipient_ref`, `recipient_ref_kind` | BCE en clair ; citoyen : HMAC-SHA256 du NISS (`SSIN_HMAC_V1`) | ATREQ03 (identifiant protégé) |
  | `consent_level`, `consent_checked_at`, `consent_source`, `delivery`, `fallback_channel` | état constaté et voie retenue | ajout demandé par #14 |
  | `idempotency_key`, `dossier_id`, `correlation_id`, `sender_application_id`, `message_type_id`, `language`, `registered`, `outcome` | contexte de l'envoi | rejeu, rattachement au dossier |

  Ni objet, ni corps, ni contenu de pièce (ATREQ02, minimalité). La clé du HMAC est dérivée de
  `app.connector.crypto.master-key` et du tenant (pas de nouvelle variable d'environnement). Q8 : la clé d'idempotence d'un
  envoi à un citoyen dépend de ce HMAC (D-EBOX-SEND-BLOCK) ; ne changer la clé maîtresse des connecteurs que lorsqu'aucun
  incident d'envoi eBox n'est en attente, sinon le rejeu calcule une autre clé et peut envoyer une seconde fois.
- **D-EBOX-SEND-CITIZEN-STUB.** Faute d'API citoyen connue, la voie citoyen est une implémentation générique et
  configurable (`app.ebox.citizen-stub.consent`, défaut `NO_CONSENT`) : par défaut aucun citoyen n'a consenti, rien n'est
  « publié », le process suit la voie de secours. Réglée à `CONSENTED` (démo, tests), elle simule une publication dont la trace
  porte la méthode `STUB` et l'URL `stub://ebox-citizen/…`, reconnaissable au journal. Le vrai adaptateur BOSA la remplacera.
- **D-EBOX-SEND-BLOCK** (lot 2, corrigé après revue). Le compte expéditeur est un type de connecteur `ebox-outbound`
  (SERVICE) dont la configuration est contrôlée à l'enregistrement par les règles de `EboxOutboundSettings`. Le connecteur
  **n'envoie rien** : il refuse tout appel, y compris d'une étape « Appeler un connecteur » dont la requête est écrite dans le
  BPMN. Seule la brique **« Envoyer en eBox »** (`eboxSendTask`) envoie, en appelant elle-même `SendEboxMessageUseCase`. Au
  déploiement, la brique choisit son instance par la source de référence `eboxSenders` ; la source `connectorInstances`
  d'« Appeler un connecteur » n'offre pas ce type, qui y est donc refusé. La brique est **toujours asynchrone** et **jamais
  multi-instance elle-même** (exigé au déploiement, revérifié à l'exécution) ; ses paramètres sont des attributs littéraux
  préfixés `eboxEnvoi*` (distincts des variables `ebox*` d'un message reçu). Le document est un `PRODUCED_DOC` PDF du dossier
  de l'étape (même règle que la signature et la pièce jointe d'e-mail). Clé d'idempotence : `myr1-` + SHA-256 de (dossier,
  étape, document, référence du destinataire) ; la référence est le numéro BCE, ou le HMAC du NISS de D-EBOX-SEND-AUDIT,
  jamais le NISS en clair. Chaque destinataire d'une boucle a donc sa clé ; le tour de boucle n'y entre pas, si bien qu'une
  liste réordonnée ou complétée au rebouclage ne renvoie rien aux destinataires déjà servis. Plusieurs destinataires :
  sous-processus multi-instance qui contient la brique et sa passerelle ; les variables de la brique sont alors locales au
  tour. Variables posées, **réservées** (une complétion de tâche ne peut pas faire croire à un envoi et sauter la voie de
  secours) : `eboxIssue` (`ENVOYE`, `DEJA_ENVOYE`, `VOIE_SECOURS`), `eboxLivraison`, `eboxSecoursRequis`, `eboxVoieSecours`,
  `eboxEnvoiMessageId`, `eboxEnvoiRejoue`. Une issue incertaine ne pose rien : l'étape échoue (incident rejouable, même clé).
  La preuve de dépôt d'un recommandé est versée au dossier comme pièce JSON marquée par la clé ; un rejeu ne la verse que si
  aucune pièce du dossier ne porte cette clé (elle a pu reculer avec la tentative qui a déposé).
  **Défaut prudent (Q11, à confirmer)** : `allowedHostSuffixes` ne fait que restreindre un plafond fixé par la plateforme
  (`socialsecurity.be`, `fgov.be`, `belgium.be`) ; un administrateur ne peut plus envoyer le jeton vers un autre domaine.
- **Specs non versionnées** (licence « e-Box » sans clause de redistribution), comme au §150 : réponses du faux serveur écrites
  à la main, conformes aux schémas ; [index des specs](https://dev.eboxenterprise.be/docs/spec/specifications),
  [audit trail ATREQ](https://dev.eboxenterprise.be/docs/dp/audit_trail/).

**Points ouverts** (détail et recommandations dans le document de travail) : H1 (copie papier d'une entreprise non
exclusive) ; accès d'une commune à Registered Mail 1.1 (spec destinée aux institutions de sécurité sociale, scopes
`ksz-bcss`) et au recommandé « non qualifié » de e-Box REST 3.8 ; mandat (`On-Behalf-Of`) absent de la spec Registered Mail ;
`paymentData` présent aussi dans le format entreprise 3.8 ; voie citoyen Smals (le serveur `…/citizen/messageRegistry/v3` de
la spec 3.8) ou BOSA ; scope du jeton pour la fédération ; durée de conservation (10 ans à partir du 1er janvier suivant) et
purge d'une table que la base interdit de toucher ; séparation du rôle de migration et du rôle applicatif (limite du journal) ; usage du NISS (autorisation, #13).

**Impacte.** `docs/specs/06-connecteurs.md` (type `ebox-outbound`), `docs/specs/49-digital-mail-room.md` (canal sortant),
`docs/guides/GUIDE-CONCEPTION-BPMN.md` (brique « Envoyer en eBox »), `docs/guides/GUIDE-UTILISATEUR.md` (type de connecteur,
formulaire générique), `exemples/envoi-ebox/` (BPMN avec passerelle vers la voie de secours). Aucune variable d'environnement.
