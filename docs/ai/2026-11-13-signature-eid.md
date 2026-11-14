# Signature par carte eID — document de travail

Issue : #10 · Spec du domaine : docs/specs/30-signature-electronique.md · Points du registre : §72, §109, §138

## Cadrage

- Problème et utilisateurs : un tenant veut une signature qualifiée par carte eID belge, pour des signatures peu
  fréquentes, sans compte fournisseur et sans agent propre au produit sur le poste du signataire. Signataires :
  externes (portail) et internes (« Mes signatures »).
- Périmètre : fournisseur `eid` de `app.signature.provider`. Deux parcours, même vérification : (a) signature
  dans la page par pont navigateur, Web eID ou BeIDConnect (demande de l'utilisateur du 2026-11-14) ; (b) en
  repli, le signataire télécharge le PDF, le signe hors de Myrmacta et téléverse le PDF signé. Myrmacta vérifie,
  garde les constats, produit la preuve. Horodatage RFC 3161 facultatif (lot 4).
- Hors-périmètre : pilotage de la carte depuis le navigateur (D-EID-TRANSPORT), liste de confiance européenne,
  rapprochement automatique du nom du certificat avec le signataire attendu, signature par téléphone.
- Critères de succès (vérifiables) :
  1. Un PDF signé par un certificat qualifié d'une autorité configurée, qui prolonge le document remis, est accepté
     et clôt la décision du signataire ; la demande passe `SIGNED` quand tous les requis ont signé.
  2. Sont refusés, chacun avec son motif : fichier non PDF, document qui ne prolonge pas le document remis, version
     dépassée, aucune ou plusieurs signatures ajoutées, contenu modifié, signature invalide, autorité inconnue,
     certificat non qualifié, certificat révoqué, révocation injoignable, certificat déjà utilisé dans la demande.
  3. Le clic « Signer » est refusé pour le fournisseur `eid` (portail et « Mes signatures »).
  4. Le document signé final porte toutes les signatures ; la preuve reprend les constats de chacune.
  5. Sans fichier d'autorités de confiance, l'instance refuse de démarrer avec `provider=eid`.
  6. Aucun appel sortant vers une adresse venue d'un certificat dont la chaîne n'est pas vérifiée.
  7. Avec un pont détecté, la page signe sans quitter Myrmacta : certificat lu par le pont, condensé calculé par
     le serveur, signature de la carte, PDF assemblé puis vérifié comme un téléversement. Sans pont, la page
     propose le téléversement.
- Contraintes : stack figée ; un tenant par instance ; portail anonyme (jeton en en-tête, refus uniforme 404 pour
  l'accès) ; JPA seulement ; MCP : sans objet (acte du signataire, lecture existante inchangée) ; documentation :
  spec 30, guides DEPLOIEMENT / GETTING-STARTED / GUIDE-UTILISATEUR, `.env*.example`, identifiants.
- Validé par l'utilisateur le : mandat du 2026-11-13 (« une fois la meilleure piste trouvée, orchestre le
  développement »), décisions tracées au §138.

## Questions ouvertes

| Question | Options | Recommandation | Statut (ouverte, reportée, hypothèse, tranchée → §N) |
|---|---|---|---|
| Piste sans agent | WebAssembly + WebUSB ; Web Smart Card ; mTLS ; signature apportée | signature apportée | tranchée → §138 (D-EID-TRANSPORT, D-EID-UPLOAD) |
| Signer dans la page | pont navigateur ; rester au téléversement | pont, téléversement en repli | tranchée par l'utilisateur → §138 (D-EID-BRIDGE) |
| BeIDConnect accepte-t-il un site hors `belgium.be` / `fgov.be` ? | — | lire ses manifestes | tranchée : oui sous Chrome, Edge et Firefox ; non sous Safari (§138) |
| PIN saisi dans la page avec BeIDConnect sur lecteur ordinaire | accepter ; lecteur à clavier seulement ; exclure BeIDConnect | accepter, réglable par l'exploitant | tranchée → §138 (D-EID-PIN), à confirmer par l'utilisateur |
| Bibliothèque de validation | DSS ; BouncyCastle à la main | DSS 6.5.1 | tranchée → §138 (D-EID-VALIDATION) |
| Outils réels (Acrobat, service fédéral) : signent-ils par mise à jour incrémentale ? | — | recette avec une vraie carte | hypothèse : oui ; sinon le motif `DOCUMENT_MISMATCH` s'affiche |
| Horodatage injoignable après les reprises du répartiteur | bloquer ; livrer sans horodatage en le disant dans la preuve | à trancher au lot 4 | ouverte |
| Qualification par la liste de confiance européenne | plus tard | hors lot | reportée |

## Conception

### Contrat posé au lot 0 (ne pas le changer sans prévenir l'orchestrateur)

- `domain.signature.model` : `SignedUploadRejection` (motifs), `SignedUploadEvidence`, `SignedUploadVerdict`.
- `SignaturePort` : capacité `signedUpload`, `artifactsDeferred()`, `verifySignedUpload(SignedUploadCheck)`.
- `infrastructure.signature.eid.SignedPdfVerifier` : `verify(base, signé, empreintes déjà utilisées)`.

### API HTTP (contrat entre le backend et le frontend)

Portail (chaîne anonyme, jeton dans `X-Portal-Token`) :

- `GET /api/portal/signature` : la réponse gagne `signedUpload: boolean`.
- `GET /api/portal/signature/document` : sert le document de travail courant (source + signatures acceptées).
- `POST /api/portal/signature/signed-document` : `multipart/form-data`, partie `file`. 200 `{"status":"SIGNED"}`.

« Mes signatures » (chaîne BFF, CSRF) :

- `GET /api/signatures/mine` : chaque élément gagne `signedUpload: boolean`.
- `GET /api/signatures/mine/{signerId}/document` : document de travail courant.
- `POST /api/signatures/mine/{signerId}/signed-document` : même contrat que le portail.

Signature dans la page (D-EID-BRIDGE), mêmes chaînes de sécurité que ci-dessus, corps JSON, binaire en base64 :

- `GET /api/portal/signature` et chaque élément de `GET /api/signatures/mine` gagnent
  `cardBridges: string[]` : les ponts autorisés par l'exploitant, dans l'ordre à essayer, parmi `web-eid` et
  `beidconnect` ; tableau vide si la signature dans la page n'est pas offerte.

- `POST /api/portal/signature/card-signature/prepare` et `POST /api/signatures/mine/{signerId}/card-signature/prepare` :
  requête `{"certificate": "<DER base64>", "certificateChain": ["<DER base64>", …]}` (chaîne facultative) ;
  réponse 200 `{"digest": "<base64>", "hashFunction": "SHA-256" | "SHA-384", "signingTime": "<instant ISO-8601>",
  "documentDigest": "<SHA-256 hex du document courant>"}`.
- `POST …/card-signature/complete` : requête `{"certificate", "certificateChain", "signingTime", "documentDigest",
  "signature": "<base64>"}`, les quatre premiers repris tels quels de l'échange précédent ; réponse 200
  `{"status":"SIGNED"}`.
- Erreurs : 400 pour une entrée illisible (base64, certificat) ; 422 avec `code` comme pour le téléversement
  (`STALE_DOCUMENT` si `documentDigest` n'est plus celui du document courant) ; 422 avec
  `code` = `SIGNING_SESSION_EXPIRED` si `signingTime` date de plus de quinze minutes ou est dans le futur.
- Le PIN ne figure dans aucun de ces échanges (D-EID-PIN).

Erreurs du téléversement :

- 422 `application/problem+json` avec la propriété `code` = une valeur de `SignedUploadRejection`
  (`NOT_A_PDF`, `DOCUMENT_MISMATCH`, `STALE_DOCUMENT`, `NO_NEW_SIGNATURE`, `SEVERAL_SIGNATURES`,
  `CONTENT_MODIFIED`, `SIGNATURE_INVALID`, `CERTIFICATE_UNTRUSTED`, `CERTIFICATE_NOT_QUALIFIED`,
  `CERTIFICATE_REVOKED`, `REVOCATION_UNAVAILABLE`, `DUPLICATE_SIGNER`).
- 413 si le fichier dépasse la taille autorisée ; 404 uniforme pour tout refus d'accès (portail) ; 429 limite par IP.
- `POST …/sign` avec le fournisseur `eid` : 409, propriété `code` = `SIGNED_UPLOAD_REQUIRED`.

### Composants

- Domaine : `SubmitSignedDocumentUseCase` (portail par jeton, interne par identité), modèle et dépôt des
  téléversements acceptés (document de travail, constats), décision appliquée par la même progression que le clic.
- Infrastructure : `eid.DssSignedPdfVerifier` (DSS), `eid.EidSignatureAdapter`, entité et dépôt JPA, changeSet
  Liquibase suivant, contrôleurs, propriétés `app.signature.eid.*`, purge du dossier.
- Frontend : `frontend/portal/PortalSignature.vue`, `frontend/src/views/pilotage/MySignaturesView.vue`, clients
  d'API, catalogues i18n FR/EN, Vitest et Playwright.

### Scénarios de test

- Unitaires domaine : acceptation, chaque motif de refus, version dépassée, progression séquentielle et parallèle.
- Vérificateur : PDF signés fabriqués par les tests (hiérarchie de certificats de test), un test par motif.
- Chaîne HTTP et sécurité : téléversement par le portail (jeton, 404 uniforme, limite par IP, 413) et par
  « Mes signatures » (session, CSRF, signataire d'un autre utilisateur).
- Playwright : parcours télécharger → téléverser → confirmation, et affichage d'un refus.

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|
| 0 | Contrat : types du domaine, capacité du port, interface du vérificateur, §138 | livré | — |
| 1 | Vérificateur DSS (`DssSignedPdfVerifier`) et ses tests | en cours | — |
| 2 | Domaine, persistance, adaptateur `eid`, API, configuration, purge | en cours | — |
| 3 | Frontend : portail et « Mes signatures » | en cours | — |
| 1b | Assembleur PAdES en deux temps (`DssCardSignatureAssembler`) | en cours | — |
| 2b | Points d'API `card-signature/prepare` et `complete`, cas d'usage | à faire | — |
| 3b | Client des ponts Web eID et BeIDConnect, parcours dans la page | à faire | — |
| 4 | Horodatage RFC 3161 et extension LT/LTA | à faire | — |
| 5 | Clôture : revue indépendante, spec 30, guides, suppression de ce document | à faire | — |

Prochaine action : intégrer les lots 1 à 3, câbler le vérificateur réel dans la configuration, `./mvnw verify`.

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
