# Fournisseurs de signature Yousign et DocuSign — document de travail

Issue : #191 · Spec du domaine : [docs/specs/30-signature-electronique.md](../specs/30-signature-electronique.md) · Points du registre : [§72](../decisions/072-signature-electronique-socle-generique-et-stub.md), [§109](../decisions/109-fournisseurs-de-signature-yousign-et-docusign.md)

## Cadrage

- Problème et utilisateurs : le socle de signature (port, portail, stub) est livré, sans valeur juridique. Un intégrateur doit pouvoir brancher un compte Yousign ou un compte DocuSign. La cérémonie, le niveau eIDAS et le prix par signature restent chez le fournisseur. Le signataire entre par le lien magique du portail ou par « Mes signatures », puis signe sur le site du fournisseur.
- Périmètre / hors-périmètre :
  - Dans l'issue : deux adaptateurs, `yousign` puis `docusign`, sélectionnés par `app.signature.provider`. Un seul est actif par instance. Chacun crée la demande, donne une URL de signature par signataire, reçoit le webhook déjà authentifié par le connecteur `signature`, et récupère le PDF signé et la preuve.
  - Le lot 1 branche aussi la redirection. Aujourd'hui `SignaturePort.SignatureSubmission.signingUrls` est ignoré par `RequestSignatureUseCaseImpl` : le portail termine la demande par `POST /api/portal/signature/sign`, ce qui ne vaut que pour le stub.
  - Hors issue : parcours eID et horodatage BOSA (autre piste, signatures peu fréquentes). Le texte de l'issue est prêt plus bas ; le dépôt automatique a été refusé (jeton sans droit d'ouvrir une issue). Pas de bascule automatique d'un fournisseur vers un autre. Pas d'Universign. Pas de choix de niveau eIDAS dans notre code. Pas de mise en production commerciale (contrat, go-live DocuSign, abonnement Yousign).
- Critères de succès (vérifiables) :
  1. `app.signature.provider=yousign`, contre un serveur HTTP fictif : un bloc signature crée la demande, le portail et « Mes signatures » envoient le signataire vers l'URL du fournisseur, un webhook `SIGNED` clôt la demande, le PDF signé et la preuve sont rangés dans le dossier, le process reprend. Même chaîne pour `DECLINED`, `EXPIRED` et `FAILED`.
  2. La même chaîne avec `app.signature.provider=docusign` et un second serveur fictif.
  3. `app.signature.provider=stub` : le signataire signe toujours dans le portail, sans redirection, et l'artefact reste marqué simulé.
  4. Une demande hébergée ne peut pas être close par le `POST /sign` du portail.
  5. Le jeton Myrmacta n'est jamais envoyé au fournisseur. Les secrets (clé d'API, secret de webhook) ne sont pas journalisés.
  6. `./mvnw verify` vert. Playwright vert sur le portail et « Mes signatures », puisque l'écran change. `scripts/check-docs.sh` vert.
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
  - Java, architecture hexagonale, une instance par tenant. Le domaine ne dépend pas du vocabulaire Yousign ou DocuSign. Pas de SDK éditeur : client HTTP écrit à la main, ou client généré depuis l'OpenAPI du fournisseur, la même technique pour les deux, limitée aux opérations appelées.
  - Les appels sortants vont vers les hôtes fixes du fournisseur choisi (bac à sable ou production), jamais vers une URL venue du BPMN.
  - Le retour passe par `/api/callbacks/{instanceId}` et le connecteur `signature` (HMAC ou jeton, corps borné). Pas de nouvelle route publique.
  - Les tools MCP de signature existants restent. Pas de nouvel outil : le contrat d'usage ne change pas.
  - À la livraison : section « Implémentation » de la spec 30, un point de registre (numéro suivant, aujourd'hui 109), identifiants `D-…` cités par le code, guides et `.env*.example` seulement si une variable d'environnement nouvelle apparaît. Les secrets suivent le connecteur, pas un fichier d'environnement partagé.
  - Les tests automatiques n'appellent ni Yousign ni DocuSign. Le bac à sable est un essai manuel, une fois un compte développeur disponible.
- Validé par l'utilisateur le : 2026-10-28

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|
| Niveau eIDAS dans notre code | Brancher le parcours selon le niveau, ou le laisser au compte fournisseur | Le port transporte `assuranceLevels` ; le parcours ne change pas | Tranchée dans ce cadrage (échange du 2026-10-28) |
| Piste eID + TSA BOSA | Dans #191, ou piste séparée pour des signatures rares | Piste séparée, sans conception dans #191 | Tranchée : option C |
| Fournisseurs du premier vrai branchement | Yousign seul, DocuSign seul, ou les deux | Les deux, dans #191 | Tranchée : option C |
| Ordre des lots | Yousign puis DocuSign, ou l'inverse | Yousign d'abord : clé d'API et lien de signature, plus proches du port. DocuSign ajoute JWT, consentement et URI de compte | Tranchée le 2026-10-28 |
| Bibliothèque cliente | SDK éditeur, client HTTP écrit à la main, ou client généré depuis l'OpenAPI du fournisseur | HTTP du JDK, pas de génération | Tranchée → [§109](../decisions/109-fournisseurs-de-signature-yousign-et-docusign.md) D-SIGNATURE-HTTP |
| Emplacement de la signature sur le PDF | Coordonnées dans le BPMN, ou repère textuel dans le modèle de document | Repère `{{sN|signature|85|37}}`, lu par les deux API | Tranchée → §109 D-SIGNATURE-ANCHOR |
| Entrée du signataire | Courriel du fournisseur, ou lien Myrmacta puis redirection | Lien Myrmacta, URL obtenue à l'ouverture, redirection | Tranchée → §109 D-SIGNATURE-LINK |

## Conception

Le détail est au [§109](../decisions/109-fournisseurs-de-signature-yousign-et-docusign.md). Ici, seulement ce qui guide les lots.

- Faits vérifiés dans le code ou les sources (fichier, méthode) :
  - `RequestSignatureUseCaseImpl.request` ignore `signingUrls`. Un signataire interne est créé sans adresse (`resolveSigner`). `KnownUser.email` existe et peut être vide.
  - `CallbackWebhookController` refuse un corps sans `Idempotency-Key`. `HmacWebhookAuthenticator` ne couvre pas le HMAC Yousign ni celui de DocuSign.
  - `SignatureOutcomeHandler.attachArtifacts` télécharge le PDF dans la transaction du webhook. Yousign exige une réponse 2xx en moins d'une seconde.
  - Yousign, docs publiques : `delivery_mode=none`, ancre `{{s1|signature|85|37}}` avec `parse_anchors=true`, `signature_link` lu par GET (48 h, renouvelable ; nul avant le tour en mode séquentiel), webhook `signature_request.done` signé `x-yousign-signature-256`.
  - DocuSign, docs publiques : `clientUserId` puis `createRecipientView` (URL à usage unique, 5 minutes, à ne pas stocker), `anchorString`.
  - Pas de SDK Java Yousign v3. Le SDK Java DocuSign 6.9.0 embarque Jersey et Jackson 2, à côté du Jackson 3 du projet.
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) :
  - Port : une opération « URL de signature maintenant », appelée par le portail et « Mes signatures ». `submit` ne renvoie plus une URL à conserver.
  - Colonne nullable : référence du signataire chez le fournisseur.
  - Adaptateur HTTP Yousign (lot 1), puis DocuSign (lot 2). Hôtes et URL de redirection sur liste fixe.
  - Authentifiant de webhook du connecteur `signature`, un mode par fournisseur.
  - Répartiteur : télécharge les artefacts puis reprend le process, pour une demande hébergée.
  - Portail et « Mes signatures » : redirection si le fournisseur héberge ; écran d'attente si le lien n'est pas encore là ; `POST /sign` refusé dans ce cas. Le stub ne change pas.
  - MCP : aucun outil nouveau.
- Non couvert, à lire en tête de lot avant le mapping : noms exacts des webhooks « refusé » et « expiré ». `signature_request.done` est vérifié.
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) :
  1. Serveur fictif Yousign : création, ancre, activation, GET du lien, redirection, webhook `SIGNED` avec le HMAC Yousign et `event_id` rejoué (une seule clôture), PDF et preuve dans le dossier, process repris. Même chaîne `DECLINED`, `EXPIRED`, `FAILED`.
  2. Lien refusé si l'hôte n'est pas celui de Yousign. Jeton Myrmacta absent de la requête sortante.
  3. Signataire interne sans `KnownUser.email` : demande `FAILED` / `NO_ADDRESS`.
  4. `POST /sign` sur une demande hébergée : refus. Le stub signe encore dans le portail.
  5. Chaîne HTTP réelle : webhook signé accepté, webhook mal signé rejeté, corps trop gros rejeté, sans nouvelle route.
  6. Playwright : le portail et « Mes signatures » partent vers l'URL du fournisseur ; l'écran d'attente s'affiche quand le lien est nul.
  7. Le lot 2 rejoue 1, 2, 4 et 5 contre un fictif DocuSign (HMAC Base64, URL de vue créée à l'ouverture).

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|
| 1 | Adaptateur HTTP Yousign, URL demandée à l'ouverture, redirection, HMAC `event_id`, artefacts via le répartiteur, adresse des internes, refus du `POST /sign` hébergé, stub inchangé | à faire | |
| 2 | Adaptateur HTTP DocuSign sur le même parcours (JWT, `createRecipientView`, HMAC Base64) | à faire | |

Prochaine action : lot 1 (Yousign), en TDD. L'issue eID reste à déposer avec le texte ci-dessous.

## Issue eID — texte prêt à déposer

Dépôt refusé le 2026-10-28 : `gh issue create` et `POST /repos/superjojo79/Myrmacta/issues` répondent `Resource not accessible by integration` (jeton sans droit d'ouvrir une issue).

- Titre : `Signature par carte eID et horodatage BOSA — à cadrer`
- Libellés : `type-feature`, `priorite-3`
- Corps :

```markdown
## Manque

L'issue #191 branche les plateformes hébergées (Yousign, puis DocuSign). Elle ne couvre pas la signature faite avec la carte eID belge, utile quand les signatures sont peu fréquentes et qu'aucun compte fournisseur n'est souhaité.

Une instance n'active qu'un adaptateur (`app.signature.provider`). Ce parcours n'est pas une bascule automatique si Yousign ou DocuSign échoue : c'est une autre piste, choisie par le tenant.

## Constats déjà vérifiés (pas une conception)

- La signature est qualifiée parce que la carte eID porte un certificat de signature de la Citizen CA (politique QCP-n-qscd) et que la puce est un dispositif qualifié de création de signature. La clé privée ne quitte pas la carte. Le certificat d'authentification de la même carte ne sert pas à signer.
- L'horodatage ne crée pas cette qualification. Il prouve la date et permet de valider la signature après l'expiration du certificat. La liste des prestataires qualifiés du SPF Économie (19 mai 2026) attribue au gouvernement fédéral (`eidpki.belgium.be`) un horodatage électronique qualifié. L'endpoint public est `http://tsa.belgium.be/connect` (RFC 3161).
- Les conditions de cette TSA (relues le 30 août 2019) réservent le service à un usage non commercial, sans engagement de disponibilité, avec un plafond de volume. Une exception se demande au SPF Stratégie et Appui.
- La Signing Box du SPF (`sign.fts.bosa.belgium.be`) est un site pour un humain, pas une API. Aucune API fédérale gratuite ne signe à la place de la carte.
- Le signataire a besoin d'un ordinateur, d'un lecteur, du logiciel eID (`eid-mw`) et d'un pont navigateur (Web eID sait piloter la carte belge). Le code PIN est saisi hors de la page. Le téléphone et une personne sans eID belge sortent de ce parcours.
- Le socle actuel termine une demande par un clic (`POST /api/portal/signature/sign`) sans preuve cryptographique. Ce clic ne suffit pas pour ce parcours.

## Statut

À cadrer plus tard, après les adaptateurs de #191. Pas de conception dans le document de travail de #191.

Spec du socle : `docs/specs/30-signature-electronique.md`. Décision du socle : TODO §72.
```


## Preuves

| Date | Commande | Résultat |
|---|---|---|
| | | |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| | | |
