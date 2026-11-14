# §138 — Signature par carte eID : pont navigateur et signature apportée, vérifiées par Myrmacta

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-13, issue [#10](https://github.com/superjojo79/Myrmacta/issues/10)). Livraison par lots,
document de travail dans `docs/ai/` jusqu'à la clôture. Le socle reste celui du
[§72](072-signature-electronique-socle-generique-et-stub.md) ; les fournisseurs hébergés restent ceux du
[§109](109-fournisseurs-de-signature-yousign-et-docusign.md).

**Mandat.** L'utilisateur a demandé le 2026-11-13 d'explorer les pistes sans agent sur le poste du
signataire (dont un module WebAssembly), de retenir la meilleure et de la développer. Les décisions
ci-dessous sont prises par l'agent sous ce mandat. Elles restent révisables tant que le point est ouvert.

**Décisions de l'utilisateur (2026-11-14).** Au vu de l'exploration : la bibliothèque DSS est acceptée
(D-EID-VALIDATION) ; la signature doit pouvoir se faire dans la page, et un pont navigateur (Web eID,
BeIDConnect) est une dépendance acceptable sur le poste (D-EID-BRIDGE).

## Pistes explorées

La carte eID est un jeton cryptographique : la clé de signature ne quitte pas la puce, on lui parle en
APDU à travers un lecteur USB de classe CCID. La question n'est donc pas la logique carte, qu'un module
WebAssembly porterait sans peine, mais le **transport** entre une page web et le lecteur.

| Piste | Verdict pour une page web ordinaire | Raison |
|---|---|---|
| WebAssembly + WebUSB vers le lecteur | non viable | Chromium range la classe USB « smart card » (0x0B) parmi les classes protégées qu'une page ne peut pas réclamer ; Firefox et Safari n'ont pas WebUSB ; le pilote CCID du système tient déjà le lecteur |
| Web Smart Card API (`navigator.smartCard`) | non viable | Réservée aux Isolated Web Apps, livrée sur ChromeOS seulement |
| WebHID, Web Serial, Web Bluetooth, Web NFC | non viable | Aucune n'échange d'APDU avec une carte à contact dans un lecteur ordinaire |
| WebAuthn / FIDO2 | non viable | La carte eID n'est pas un authentificateur FIDO |
| Certificat client TLS (mTLS) | non viable pour signer | Le navigateur signe la poignée de main TLS, pas un document, et ne propose que le certificat d'authentification |
| Pont natif (Web eID, BeIDConnect) | **retenu** par l'utilisateur | Seule voie pour signer dans la page ; dépendance jugée acceptable (D-EID-BRIDGE) |
| Signature à distance (itsme, fournisseur hébergé) | hors sujet | Demande un compte fournisseur : c'est le §109 |
| Authentification forte (§104) puis clic | écarté | Signature avancée au mieux, pas qualifiée |
| **Signature apportée** | **retenue**, en repli | Aucun composant propre à Myrmacta sur le poste (D-EID-UPLOAD) |

Sources de la recherche du 2026-11-13 : dépôts `WICG/webusb` et `WICG/web-smart-card`, test WPT
`webusb/protected-interface-classes`, fils blink-dev « WebUSB Interface Class Filtering » et « Unrestricted
WebUSB ». Plusieurs pages primaires n'étaient pas joignables depuis l'environnement de travail : les
lignes WebHID à mTLS du tableau sont déduites, pas lues dans une spécification.

## Décisions

- **D-EID-TRANSPORT.** Aucune page web ordinaire n'atteint la carte eID sans composant natif. La piste
  WebAssembly est abandonnée : elle résout la logique carte, pas le transport, et la saisie du PIN dans
  la page serait un recul de sécurité.
- **D-EID-BRIDGE.** Signature dans la page par pont navigateur. La page essaie Web eID puis BeIDConnect,
  parmi les ponts que l'exploitant autorise (`app.signature.eid.bridges`, les deux par défaut). Le serveur
  calcule le condensé à signer, le pont le fait signer par la carte, le serveur assemble le PAdES (signature
  invisible, mise à jour incrémentale). Le PDF assemblé n'est pas tenu pour valide : il passe par la même
  vérification qu'un téléversement (D-EID-BINDING à D-EID-REVOCATION). Les deux appels sont sans état : une
  valeur falsifiée entre les deux donne une signature invalide, donc un refus. Sans pont détecté, la page
  propose le téléversement. Aucune dépendance npm : la page parle le protocole de chaque extension, lu dans
  ses sources (la bibliothèque Web eID n'est pas publiée sur npmjs).
- **D-EID-PIN.** Le PIN n'est jamais envoyé au serveur ni conservé. Avec Web eID, il est saisi dans la fenêtre
  du logiciel eID, hors de la page. BeIDConnect n'ouvre pas de fenêtre : sur un lecteur à clavier, le PIN est
  saisi sur le lecteur ; sur un lecteur ordinaire, la page doit le demander et le passer à l'extension, comme
  le fait le site fédéral de signature. La page le garde en mémoire le temps de l'appel. L'exploitant qui
  refuse cette saisie dans la page retire `beidconnect` des ponts autorisés.
- **D-EID-UPLOAD.** Signature apportée, en repli du pont. Le signataire télécharge le PDF, le signe avec l'outil qu'il a déjà
  (Adobe Acrobat Reader et sa carte, ou le service fédéral de signature), puis téléverse le PDF signé.
  Myrmacta vérifie la signature. Aucun composant propre à Myrmacta n'est installé sur le poste. Fournisseur
  `eid` de `app.signature.provider`, un seul actif par instance comme au §109. Le clic « Signer » du socle
  est refusé pour ce fournisseur.
- **D-EID-VALIDATION.** La vérification passe par la bibliothèque DSS de la Commission européenne
  (`eu.europa.ec.joinup.sd-dss`, LGPL-2.1, version 6.5.1), implémentation de référence de la validation
  eIDAS. Elle s'aligne sur les versions déjà présentes (PDFBox 3.0.8, HttpClient 5.6.5). Écarté : une
  validation écrite à la main sur BouncyCastle, trop de code de sécurité à maintenir.
- **D-EID-BINDING.** Le fichier téléversé doit commencer, octet pour octet, par le document remis au
  signataire. Il ajoute exactement une signature, qui couvre tout le fichier. Les objets ajoutés ne servent
  qu'à la signature : toute autre modification est refusée.
- **D-EID-CHAIN.** Une demande a un seul document de travail : la source, puis les signatures acceptées.
  Chaque signataire signe la version courante. Un téléversement fait sur une version dépassée est refusé,
  le signataire retélécharge. Le PDF final porte toutes les signatures.
- **D-EID-TRUST.** Myrmacta n'embarque aucun certificat. L'exploitant fournit les autorités de confiance
  (racines belges et autorités intermédiaires) dans un fichier PEM. Sans ce fichier, le fournisseur `eid`
  refuse de démarrer. Aucun téléchargement d'autorité intermédiaire depuis une URL du certificat.
- **D-EID-QUALIFIED.** Un certificat est accepté s'il remonte à une autorité configurée, s'il est valide,
  s'il porte l'usage de non-répudiation et s'il se déclare qualifié sur dispositif qualifié (QcCompliance,
  QcSSCD). Le niveau enregistré est `QES`. La liste de confiance européenne n'est pas consultée : la
  qualification repose sur le certificat et sur les autorités choisies par l'exploitant.
- **D-EID-REVOCATION.** L'état de révocation est obligatoire (OCSP, sinon CRL), demandé seulement pour un
  certificat dont la chaîne est déjà vérifiée. Appel sortant sans redirection, borné en durée et en taille,
  plages privées refusées. Révocation injoignable : téléversement refusé, à réessayer.
- **D-EID-EVIDENCE.** Chaque téléversement accepté garde ses constats : nom du signataire lu dans le
  certificat, émetteur, numéro de série et empreinte du certificat, heure déclarée, heure de vérification,
  format, source de révocation. La preuve de signature les reprend.
- **D-EID-NRN.** Le numéro de registre national que porte le certificat eID n'est ni extrait ni conservé à
  part. Il reste dans le PDF signé, qui contient le certificat : ce document suit la purge du dossier.
- **D-EID-TIMESTAMP.** L'horodatage (RFC 3161) est facultatif et configuré par l'exploitant. Absent par
  défaut. La TSA fédérale réserve son service à un usage non commercial, sans engagement de disponibilité
  (issue #10) : elle n'est pas un défaut.

## Limites connues

- Aucun PDF signé avec une vraie carte eID n'a été soumis à la vérification pendant le développement. Les
  tests fabriquent leur propre hiérarchie de certificats. Une recette avec une vraie carte est due avant
  usage réel.
- Le profil exact des certificats eID (identifiants de politique, hôtes OCSP) n'a pas pu être relu sur les
  sites officiels depuis l'environnement de travail. La conception n'en dépend pas : les autorités sont
  fournies par l'exploitant, les adresses de révocation viennent des certificats.
- Le signataire a toujours besoin d'un lecteur et d'un logiciel qui parle à la carte : un pont navigateur
  pour signer dans la page, ou son outil de signature pour le téléversement.
- Aucun des deux ponts n'a pu être essayé avec une vraie extension et une vraie carte pendant le
  développement : le code client suit leurs sources publiques et ses tests simulent l'extension.
- Web eID ne rend que le certificat du signataire : les autorités intermédiaires (Citizen CA) doivent alors
  figurer dans le fichier de l'exploitant (D-EID-TRUST). BeIDConnect rend la chaîne lue sur la carte.

## Ce que les sources des ponts établissent (lues le 2026-11-14)

| | Web eID | BeIDConnect |
|---|---|---|
| À installer sur le poste | extension, application native **et** logiciel eID belge (module PKCS#11) | extension et application native ; pas de logiciel eID |
| Sites acceptés | tous, en HTTPS réel | tous sous Chrome, Edge et Firefox ; domaines fédéraux seulement sous Safari |
| Saisie du PIN | fenêtre du logiciel eID | lecteur à clavier, sinon la page |
| Certificat rendu | le certificat seul | le certificat et la chaîne de la carte |
| Signature ECDSA rendue | brute (r puis s) | DER |

Versions lues : bibliothèque `web-eid.js` 2.1.0, extension Web eID 2.5.0, application Web eID 2.10.0,
BeIDConnect 2.12. La prise en charge de la carte belge par Web eID vient de `libelectronic-id`.

**Impacte.** `docs/specs/30-signature-electronique.md`, `docs/decisions/identifiants.md`,
`docs/guides/DEPLOIEMENT.md`, `docs/guides/GETTING-STARTED.md`, `docs/guides/GUIDE-UTILISATEUR.md`,
`.env.example`, `.env.local.example`, `pom.xml` (dépendance DSS), `NOTICE`.
