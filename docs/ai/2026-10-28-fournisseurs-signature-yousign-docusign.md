# Fournisseurs de signature Yousign et DocuSign — document de travail

Issue : #191 · Spec du domaine : [docs/specs/30-signature-electronique.md](../specs/30-signature-electronique.md) · Points du registre : [§72](../decisions/072-signature-electronique-socle-generique-et-stub.md)

## Cadrage

- Problème et utilisateurs : le socle de signature (port, portail, stub) est livré, sans valeur juridique. Un intégrateur doit pouvoir brancher un compte Yousign ou un compte DocuSign. La cérémonie, le niveau eIDAS et le prix par signature restent chez le fournisseur. Le signataire entre par le lien magique du portail ou par « Mes signatures », puis signe sur le site du fournisseur.
- Périmètre / hors-périmètre :
  - Dans l'issue : deux adaptateurs, `yousign` puis `docusign`, sélectionnés par `app.signature.provider`. Un seul est actif par instance. Chacun crée la demande, donne une URL de signature par signataire, reçoit le webhook déjà authentifié par le connecteur `signature`, et récupère le PDF signé et la preuve.
  - Le lot 1 branche aussi la redirection. Aujourd'hui `SignaturePort.SignatureSubmission.signingUrls` est ignoré par `RequestSignatureUseCaseImpl` : le portail termine la demande par `POST /api/portal/signature/sign`, ce qui ne vaut que pour le stub.
  - Hors issue : parcours eID et horodatage BOSA (autre piste, signatures peu fréquentes, issue à ouvrir après validation de ce cadrage, sans conception ici). Pas de bascule automatique d'un fournisseur vers un autre. Pas d'Universign. Pas de choix de niveau eIDAS dans notre code. Pas de mise en production commerciale (contrat, go-live DocuSign, abonnement Yousign).
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
- Validé par l'utilisateur le : en attente

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|
| Niveau eIDAS dans notre code | Brancher le parcours selon le niveau, ou le laisser au compte fournisseur | Le port transporte `assuranceLevels` ; le parcours ne change pas | Tranchée dans ce cadrage (échange du 2026-10-28) |
| Piste eID + TSA BOSA | Dans #191, ou piste séparée pour des signatures rares | Piste séparée, sans conception dans #191 | Tranchée : option C |
| Fournisseurs du premier vrai branchement | Yousign seul, DocuSign seul, ou les deux | Les deux, dans #191 | Tranchée : option C |
| Ordre des lots | Yousign puis DocuSign, ou l'inverse | Yousign d'abord : clé d'API et lien de signature, plus proches du port. DocuSign ajoute JWT, consentement et URI de compte | Hypothèse |
| Bibliothèque cliente | SDK éditeur, client HTTP écrit à la main, ou client généré depuis l'OpenAPI du fournisseur | Pas de SDK éditeur. Même technique pour les deux : HTTP écrit à la main, ou client généré. Le choix se fait en conception. Une génération est limitée aux opérations réellement appelées : la spec DocuSign complète couvre toute l'API eSignature | Tranchée le 2026-10-28 : HTTP ou généré, pour les deux |
| Emplacement de la signature sur le PDF | Coordonnées dans le BPMN, ou repère textuel dans le modèle de document | Repère textuel dans le PDF. Pas de nouveau paramètre de bloc tant que les deux API l'acceptent | Hypothèse, à vérifier en conception |
| Entrée du signataire | Courriel du fournisseur, ou lien Myrmacta puis redirection | Lien Myrmacta (portail ou « Mes signatures »), puis redirection. Le port le prévoit (`hostedSigningUrl`) ; l'URL n'est pas encore conservée | Hypothèse alignée sur D-SIGNATURE-UX / D-SIGNATURE-PORTAL-KIND (§72) |

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) :
  - `SignaturePort` (`domain/signature/port/out/SignaturePort.java`) : `submit`, `status`, `parseCallback`, `cancel`, `fetchArtifacts`, capacités `hostedSigningUrl`.
  - `RequestSignatureUseCaseImpl.request` conserve la référence fournisseur et le drapeau simulé, et ignore `signingUrls`.
  - `SignatureProperties` n'accepte que `none` et `stub`.
  - `SignaturePortalController.sign` clôt la demande sans preuve cryptographique.
  - `SignatureCallbackConnector` reçoit déjà le webhook authentifié.
  - Bacs à sable publics : Yousign `https://api-sandbox.yousign.app/v3` (essai, signatures non opposables, documents filigranés) ; DocuSign `https://demo.docusign.net/restapi` et `account-d.docusign.com` (compte développeur gratuit, signatures sans valeur de production).
  - Yousign v3 n'a pas de SDK Java officiel : le contrat est l'OpenAPI publique. Le SDK Java DocuSign (6.9.0) est généré depuis toute l'API eSignature et embarque Jersey, Jackson 2 et BouncyCastle, à côté du Jackson 3 de Spring Boot 4. Jusqu'à la 6.7.0 (2026-07-01), il acceptait tous les certificats TLS par défaut.
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) : à détailler après validation, avant le lot 1.
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) : à dériver des critères de succès après validation.

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|
| 1 | Adaptateur Yousign, conservation de l'URL de signature, redirection portail et « Mes signatures », refus du `POST /sign` pour une demande hébergée, webhook et récupération des artefacts, stub inchangé | à faire | |
| 2 | Adaptateur DocuSign sur le même parcours | à faire | |

Prochaine action : validation de ce cadrage. Pas de conception détaillée ni de code avant.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| | | |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| | | |
