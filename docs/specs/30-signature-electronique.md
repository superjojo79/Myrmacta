# Signature électronique — socle générique et stub, en préparation d'un provider

> **Statut : livré le 2026-09-29** ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), TODO §72 clos) — socle générique + stub. La Phase 2 a élargi
> le périmètre (signataires internes, modes parallèle et séquentiel, requis / facultatifs, issue en
> variable de process, un seul document, échéance unique, preuve PDF) : les
> [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-29-feature-signature-electronique.md) font foi sur
> ce point.
>
> Statut d'origine de la Phase 1 : Les décisions
> ci-dessous sont tranchées par l'utilisateur ; restent la Phase 2 (revue des requirements), le
> design (Phase 3) et le reste du cycle `dev-lifecycle` avant tout code. Origine :
> `ROADMAP.md` item **2.4**, classé « Produit, pas cadré ».
>
> **Recalibrage (2026-09-29, demande utilisateur).** Il n'y a **aucun fournisseur désigné**. Le lot
> n'est donc plus « intégrer DocuSign/Yousign » mais : **poser le contrat, le modèle de données, le
> parcours et le point d'extension** d'une signature électronique, avec un **adaptateur stub
> générique** qui simule le cycle complet, pour qu'un provider réel se branche ensuite en ajoutant
> **un adaptateur et une configuration**, sans toucher au domaine, au BPMN, au portail ni au
> modèle. La version précédente de cette analyse (choix de fournisseur, niveau eIDAS, avant tout
> développement) est remplacée ; ses risques et constats vérifiés sont conservés.
>
> Issu de la scission du 2026-09-23 de `26-portail-externe-signature.md`, désormais
> [26-portail-externe.md](26-portail-externe.md). **Prérequis livré :** `portail-externe`
> ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), TODO §68).
>
> **Décisions tranchées le 2026-09-29** (`AskUserQuestion`, consignées TODO §72) :
> D-SIGNATURE-ABSTRACTION = **port + adaptateur exposé comme connecteur** ;
> D-SIGNATURE-STUB-BEHAVIOR = **simulation de bout en bout** ; D-SIGNATURE-UX = **tâche externe du
> portail + statut dans le dossier** ; D-SIGNATURE-TRIGGER = **nouveau delegate allowlisté** ;
> D-SIGNATURE-TRIGGER-VS-CONNECTOR = **delegate → port direct** ; D-SIGNATURE-PORTAL-KIND =
> **nouveau type de tâche externe « signature »** ; D-SIGNATURE-ELEMENT = **`PRODUCED_DOC` marqué** ;
> D-SIGNATURE-STUB-GUARD = **refus en profil production, activable par un second drapeau
> explicite** ; D-SIGNATURE-CONTRACT-CHECK = **oui, revue documentaire de 2 à 3 API publiques**.
>
> **Fournisseurs livrés le 2026-10-28** ([PR #247](https://github.com/superjojo79/Myrmacta/pull/247),
> TODO §109, issue #191) : Yousign et DocuSign, un seul actif par instance. Le niveau eIDAS reste
> celui du compte chez le fournisseur. Le fournisseur `eid` (signature par carte eID, TODO §138) n'en fait pas
> partie : il reste ouvert (TODO §138) ; son pont navigateur est le parcours principal, le téléversement un repli
> désactivé par défaut. Détail en fin de spec.

---

## 1. Constat vérifié (pas supposé)

| Élément recherché | Résultat | Preuve |
|---|---|---|
| Symboles signature électronique (DocuSign/Yousign/eIDAS) | **Absents** | Grep `DocuSign\|Yousign\|eIDAS\|signature` : seuls des faux positifs (`HmacWebhookAuthenticator`, `AttachmentContentSniffer` — « signature » = HMAC / magic bytes) |
| Patron « port + adaptateur no-op + adaptateur réel, choisi par propriété » | **Existe** | `AttachmentScanPort` ; `NoOpAttachmentScanAdapter` (défaut, `matchIfMissing`) et `ClamAvAttachmentScanAdapter`, sélectionnés par `app.dossier.attachment.scan.type` dans `DossierConfig` |
| Catalogue de connecteurs et capacités | **Existe** | `ConnectorCapability` = `TRIGGER`, `SERVICE`, `BOTH`, `CALLBACK` ; connecteurs livrés : `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback`, `batch-import`, `kafka` |
| Retour asynchrone d'un tiers vers un process en attente | **Existe** | `flowable-callback` (corrélation par clé métier, HMAC horodaté, idempotence, TODO §51/§53/§54) |
| Document à signer | **Existe** | `GenerateDocumentUseCase` + `DocumentStoragePort` (PDF Flying Saucer/OpenPDF) |
| Nature des éléments d'un dossier | **Existe** | `ElementType` = `PRODUCED_DOC`, `ATTACHMENT`, `INFORMATION`, `NOTE`, `LINK` — pas de type dédié au document signé |
| Allowlist des delegates BPMN | **Existe** | `AllowedBpmnDelegates.NAMES` (7 noms) : un nouveau delegate impose d'y être ajouté (TODO §57/§69) |
| Portail externe | **Livré** | tâche externe = `userTask` `formKey` `user-question:` uniquement (D-PORTAL-FORM), lien magique, opt-in `APP_PORTAL_ENABLED` |

## 2. Ce que le lot livre (périmètre proposé)

### 2.1 Le contrat : `SignaturePort`

Un **port sortant** de domaine, indépendant de tout fournisseur, qui fige ce que le reste du
produit attend d'une signature :

- **Demander** une signature : document source (élément du dossier), liste ordonnée de signataires
  (nom, adresse, rôle), message, échéance, référence de corrélation.
- **Interroger / annuler** une demande ; **récupérer** le document signé et la **preuve**.
- **Recevoir** l'issue : un événement normalisé `SIGNED`, `DECLINED`, `EXPIRED`, `FAILED`,
  indépendant du vocabulaire du fournisseur.
- **Déclarer ses capacités** (`SignatureProviderCapabilities`) : signature multi-signataires,
  ordre imposé, niveau eIDAS supporté, parcours hébergé chez le fournisseur ou dans le portail,
  preuve fournie ou non. Le domaine s'adapte aux capacités au lieu de supposer un fournisseur.

Le contrat est volontairement **plus étroit que la somme des fournisseurs** : il porte ce que
Myrmacta sait exprimer, et laisse au provider la charge de mapper le reste.

### 2.2 L'adaptateur stub générique

Adaptateur `stub`, **désactivé par défaut**, sélectionné par propriété sur le modèle de
`app.dossier.attachment.scan.type` (nom exact à figer en Phase 1). Il simule **le cycle complet** :

1. la demande est créée et persistée (statut `PENDING`) ;
2. chaque signataire reçoit une **tâche externe dans le portail** (voir §2.4) ;
3. la « signature » est simulée par la réponse du signataire, ou déclenchée par un endpoint de
   simulation réservé au profil dev/test ;
4. un **document « signé »** est produit (copie du document source marquée, non probante) avec un
   **fichier de preuve factice** ; les deux sont rattachés au dossier ;
5. l'événement normalisé est renvoyé vers le process en attente.

**Marquage anti-confusion obligatoire** : tout artefact du stub porte la mention explicite
« SIMULATION — sans valeur juridique » (filigrane sur le PDF, champ `simulated = true` sur la
demande et sur la preuve, bandeau dans l'UI). Un stub actif en production ne doit jamais pouvoir
être pris pour une vraie signature — voir risque R-SIM-EN-PROD.

### 2.3 Exposition comme connecteur, et déclenchement

- **Connecteur catalogue mince** : une entrée `ConnectorCatalog` qui porte la **configuration et
  les secrets** du provider actif (chiffrement existant des `PASSWORD`), le **choix de
  l'adaptateur** et la **réception du retour** (capacité `CALLBACK`, mêmes garde-fous que
  `flowable-callback` : HMAC horodaté, idempotence, taille bornée). C'est le point d'entrée admin
  d'un futur provider : on y saisit sa clé API, pas dans un fichier de configuration.
- **Delegate dédié** (ex. `signatureRequestTask`) **ajouté à `AllowedBpmnDelegates`** : il émet la
  demande via `SignaturePort` puis met le process en attente jusqu'au retour normalisé. C'est ce
  que la palette LC1 proposera comme bloc « Demander une signature ».
- **Cohabitation (D-SIGNATURE-TRIGGER-VS-CONNECTOR, tranchée)** : le **delegate appelle
  directement `SignaturePort`** ; le **connecteur** ne sert qu'à la configuration, aux secrets et à
  la réception du retour (`CALLBACK`). L'exécution reste indépendante du modèle de connecteur
  générique.

### 2.4 Parcours du signataire

Le signataire n'a pas de compte : il passe par le **portail tokenisé** livré (PR #138).

- **Nouveau type de tâche externe « signature » (D-SIGNATURE-PORTAL-KIND, tranchée)** : le portail
  n'accepte aujourd'hui que des tâches `user-question:` (D-PORTAL-FORM). Ce lot **rouvre
  volontairement ce périmètre** avec un second type de tâche, porteur pour un provider réel
  (redirection vers l'URL de signature du fournisseur) et propre pour le stub (écran dédié :
  document à consulter, confirmation, refus). Conséquences à cadrer en Phase 2-3 : nouvelle route
  publique sous `/portal/**`, revue de sécurité du lot portail rejouée sur ce type (jeton, CSRF,
  limitation par IP, purge), marqueur BPMN distinct de `user-question:` (D-PORTAL-BPMN-MARKER),
  mise à jour de la validation au déploiement, et **CSP inchangée** (redirection, pas d'iframe).
- **Instructeur** : statut de chaque demande (`PENDING`, `SIGNED`, `DECLINED`, `EXPIRED`,
  `FAILED`) visible dans le détail du dossier, avec renvoi et annulation.

### 2.5 Modèle de données

- **Demande de signature** (`signature_request`) : dossier, élément source, provider (code de
  l'adaptateur), signataires, statut normalisé, `simulated`, corrélation, échéance, horodatages.
- **Document signé et preuve (D-SIGNATURE-ELEMENT, tranchée)** : éléments `PRODUCED_DOC` **marqués**
  (métadonnées : identifiant de la demande, rôle `SIGNED_DOCUMENT` ou `SIGNATURE_PROOF`,
  `simulated`). **Aucune nouvelle valeur d'`ElementType`** : pas d'impact sur l'export CSV, l'affichage
  ni le schéma. Le marqueur doit être filtrable côté API pour que l'UI distingue document source,
  document signé et preuve.
- **Purge et rétention** : la purge de dossier doit supprimer les demandes, documents signés et
  preuves (même leçon que D-PORTAL-PURGE : le graphe de `ProcessPurgeOutboxUseCaseImpl` doit
  couvrir les nouvelles tables).

## 3. Ce que le lot ne fait pas

- Le socle (§72) n'implémente ni fournisseur réel, ni valeur juridique, ni choix de niveau eIDAS.
  Yousign et DocuSign sont livrés ensuite (TODO §109, section Implémentation) : le niveau reste
  celui du compte fournisseur, pas un choix de Myrmacta.
- Pas de signature manuscrite, de certificat ni d'horodatage qualifié gérés par Myrmacta.
- Pas d'aller-retour complexe multi-fournisseurs ni de bascule à chaud d'un provider vers un autre
  pour des demandes en cours.

## 4. Risques identifiés

| ID | Risque | Parade proposée |
|---|---|---|
| **R-SIM-EN-PROD** | Le stub actif en production produit des « documents signés » pris pour authentiques | Désactivé par défaut ; marquage indélébile (filigrane, `simulated`, bandeau) ; **refus de démarrage en profil production**, sauf second drapeau explicite (D-SIGNATURE-STUB-GUARD) qui n'autorise qu'avec avertissement (log WARN au démarrage, bandeau UI, marquage inchangé). Le drapeau ne doit jamais figurer dans les gabarits de configuration ni `.env.example` |
| **R-CONTRAT-TROP-ETROIT** | Le contrat figé sans provider réel se révèle inadapté au premier fournisseur | Capacités déclarées ; événement normalisé minimal ; **revue documentaire de 2 à 3 API publiques** (D-SIGNATURE-CONTRACT-CHECK, tranchée : par ex. Yousign, DocuSign, Universign), tableau de correspondance livré en Phase 2-3, sans intégration |
| **R-CONTRAT-TROP-LARGE** | Sur-conception d'un contrat « universel » jamais exercé | Périmètre §2.1 restreint ; ce qui n'est pas utilisé par le stub ni par un provider cible ne sort pas |
| **R-PORTAIL** | Le type de tâche « signature » rouvre la sécurité du portail (routes publiques, CSRF, limitation, purge) — **risque assumé** (D-SIGNATURE-PORTAL-KIND) | Réutiliser les composants du portail (jeton haché, limite par IP avant lecture du corps, `PortalPreAuthInterceptor`) ; rejouer la revue adversariale du portail sur le nouveau type ; aucune route hors `/portal/**` ; CSP inchangée |
| **R-DELEGATE** | Un nouveau delegate modifie la surface d'exécution BPMN | Ajout à `AllowedBpmnDelegates` + tests de l'allowlist (§57/§69) ; aucune expression libre |
| **Secrets fournisseur** | Clés API en clair | Champs `PASSWORD` de connecteur (chiffrés au repos) ; jamais en propriété d'environnement partagée |
| **Webhook du fournisseur** | Rejeu, secret faible, DoS mémoire | Réutiliser les correctifs F5–F8 de `flowable-callback` |
| **SSRF** | Appel sortant vers le fournisseur | Hôtes fixés par le fournisseur et l'environnement (bac à sable ou production), jamais une URL venue du BPMN ; pas de redirection. Le lien de signature est contrôlé sur la même liste avant redirection (D-SIGNATURE-HTTP, TODO §109) |
| **CSP** | Un parcours embarqué (iframe) exigerait d'élargir `frame-src` | Redirection plutôt qu'iframe ; ne pas toucher à la CSP dans ce lot |
| **Conformité juridique** | Valeur probante, niveau eIDAS | **Hors lot** : arbitrage reporté au choix du premier provider réel (voir D-SIGNATURE-LEVEL) |

## 5. Décisions (Phase 1 close le 2026-09-29, TODO §72)

| ID | Décision | Statut |
|---|---|---|
| ~~D-SIGNATURE-SCOPE~~ | Lot séparé, après `portail-externe` | **Tranchée 2026-09-23** |
| ~~D-SIGNATURE-PROVIDER~~ | Aucun provider désigné : le lot fournit le point d'extension et le stub | **Reportée** au premier provider réel |
| ~~D-SIGNATURE-LEVEL~~ | Niveau eIDAS : le contrat n'expose qu'un champ « niveau » informatif | **Reportée** au premier provider réel |
| D-SIGNATURE-ABSTRACTION | **Port `SignaturePort` + adaptateur exposé comme connecteur** mince | **Tranchée 2026-09-29** |
| D-SIGNATURE-STUB-BEHAVIOR | **Simulation de bout en bout**, marquée non probante | **Tranchée 2026-09-29** |
| D-SIGNATURE-UX | **Tâche externe du portail + statut dans le dossier** | **Tranchée 2026-09-29** |
| D-SIGNATURE-TRIGGER | **Nouveau delegate allowlisté** (`signatureRequestTask`) | **Tranchée 2026-09-29** |
| D-SIGNATURE-TRIGGER-VS-CONNECTOR | **Delegate → port direct** ; connecteur = configuration, secrets, retour | **Tranchée 2026-09-29** |
| D-SIGNATURE-PORTAL-KIND | **Nouveau type de tâche externe « signature »** (rouvre le périmètre du portail) | **Tranchée 2026-09-29** |
| D-SIGNATURE-ELEMENT | **`PRODUCED_DOC` marqué**, pas de nouvelle valeur d'`ElementType` | **Tranchée 2026-09-29** |
| D-SIGNATURE-STUB-GUARD | **Refus de démarrer en profil production, activable par un second drapeau explicite** (avertissement) | **Tranchée 2026-09-29** |
| D-SIGNATURE-CONTRACT-CHECK | **Oui** : revue documentaire de 2 à 3 API publiques, sans intégration | **Tranchée 2026-09-29** |

**Restent à figer en Phase 2-3 (détail de conception) :** nom et valeur par défaut de la propriété
d'activation (patron `app.dossier.attachment.scan.type`, défaut désactivé), nom du second drapeau de
D-SIGNATURE-STUB-GUARD, forme exacte du marqueur D-SIGNATURE-ELEMENT, marqueur BPMN de la tâche
« signature » (D-PORTAL-BPMN-MARKER), critère de « profil de production » (profil Spring actif ou
propriété dédiée).

## 6. Coutures et séquencement

- **Surfaces touchées :** nouveau port et adaptateur (`domain/…/port/out`, `infrastructure/…`),
  entrée `ConnectorCatalog`, `AllowedBpmnDelegates`, portail (nouveau type de tâche « signature »),
  détail du dossier (statut), purge, golden OpenAPI, tools MCP (exposition dans le même lot,
  principe P1 de l'[analyse 46](46-exposition-mcp-couche-low-code.md)).
- **Parallélisme (voir [`FILE-ATTENTE.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md), Vague 1) :** disjoint de LC1 (frontend BPMN), D1
  (`FieldDefinition`) et D3a (`HttpRestConnector`). **Attention** : l'entrée `ConnectorCatalog` et
  l'enregistrement des tools MCP sont des coutures partagées avec D3c/D3d et tout lot `LC*` —
  pas de PR concurrente sur le catalogue.
- **Suite naturelle :** l'arrivée d'un provider réel devient un lot court (adaptateur, mapping
  d'événements, tests d'intégration contre le sandbox du fournisseur, arbitrage eIDAS), et le bloc
  « Demander une signature » de la palette LC1 s'appuie sur le delegate posé ici.

Aucune décision de priorité n'est prise par ce document.

## Implémentation — fournisseurs Yousign et DocuSign (TODO §109, PR #247)

Le socle du §72 reste en place. Les deux adaptateurs sont livrés ; un seul est actif
(`app.signature.provider` : `yousign` ou `docusign`). Le stub reste le troisième choix, refusé en
profil de production sans le second drapeau.

- **Ancre.** Le PDF porte `{{sN|signature|85|37}}` pour chaque rang N à partir de 1. Yousign ne
  renvoie que le nombre d'ancres reconnues : deux repères du même rang ne comptent pas pour deux
  signataires. La demande passe `FAILED` et le brouillon est annulé au mieux si un rang n'a pas le
  sien, ou si ce nombre est inférieur au nombre de signataires (D-SIGNATURE-ANCHOR). DocuSign pose
  un onglet SignHere sur la même chaîne et échoue de son côté si elle est absente.
- **Lien.** Il n'est pas conservé à la création. Le portail le demande à l'ouverture de la page.
  « Mes signatures » le demande au clic (`GET /api/signatures/mine/{signerId}/signing-url`), pas
  dans la liste. `signingUrl` est nul tant que le lien n'est pas prêt (tour séquentiel, attente) et
  le contrat OpenAPI l'accepte nul. L'hôte est contrôlé avant la redirection (D-SIGNATURE-LINK).
- **Webhook.** Réponse courte après enregistrement de l'issue. Le PDF signé et la preuve sont
  téléchargés par le répartiteur déjà en place, puis le process reprend. Un document produit par
  `signature` seul compte comme déjà archivé.
- **Appels sortants.** Client HTTP du JDK vers l'hôte du bac à sable ou de la production, sans
  redirection, corps borné, secret absent des erreurs (D-SIGNATURE-HTTP). Ce ne sont pas les
  garde-fous d'une URL libre de `http-rest`.
- **Premier essai sur le bac à sable Yousign (TODO §148, 2026-11-17).** La chaîne complète passe : création, dépôt
  du PDF, ancre reconnue, signataire, activation, redirection du portail, signature, webhook, téléchargement du PDF
  signé et de la preuve, reprise du processus. Deux écarts corrigés :
  - le nom du fichier déposé est en ASCII et finit par `.pdf` (Yousign refuse un fichier sans extension ou un nom
    hors ASCII simple ; le libellé d'un document généré n'a pas d'extension). La base est coupée avant l'extension
    et vaut `document` si elle est vide ;
  - un `signature_request.done` (ou `envelope-completed` de DocuSign) ne détaille pas les signataires et veut dire
    que tous ont signé : chaque signataire encore ouvert passe `SIGNED` (il restait affiché « révoqué »). La date de
    décision affichée est celle de la réception du retour.

  Contrainte du bac à sable : il refuse tout signataire hors de l'organisation du compte Yousign.
- **Hors lot.** Parcours eID et horodatage BOSA. Valeur juridique : celle du compte chez le
  fournisseur, pas un niveau choisi par Myrmacta.

## Implémentation — fournisseur eid (TODO §138)

Statut : §138 ouvert. Le fournisseur `eid` (`app.signature.provider=eid`) signe avec une carte eID belge, sans agent
propre à Myrmacta sur le poste du signataire. Le transport est tracé au §138 (D-EID-TRANSPORT).

- **Pont navigateur, parcours principal.** Dans la page, Web eID puis BeIDConnect signent, selon
  `app.signature.eid.bridges` (défaut `web-eid,beidconnect`). Le serveur calcule le condensé, la carte signe, et le
  serveur assemble lui-même le PDF à partir de la valeur de signature rendue (signature invisible, mise à jour
  incrémentale). Le PDF assemblé passe par la même vérification qu'un téléversement (D-EID-BRIDGE).
- **Téléversement, repli désactivé par défaut.** Le signataire signe hors de Myrmacta puis téléverse le PDF. Le
  parcours n'existe que si `app.signature.eid.upload-enabled` (`APP_SIGNATURE_EID_UPLOAD_ENABLED`) vaut `true`.
  Désactivé, les points `signed-document` répondent 409, `code` = `SIGNED_UPLOAD_DISABLED`, après le contrôle d'accès ;
  le champ `signedUpload` des lectures vaut `false`. Activé, un avertissement est journalisé au démarrage (D-EID-UPLOAD).
- **Contrôles du PDF téléversé.** Le fichier commence, octet pour octet, par le document remis ; il ajoute une seule
  signature, sur un champ neuf ; aucun objet du document remis n'est redéfini hors d'une liste fermée ; le parcours
  du document final part de sa racine ; une signature visible exige une zone vide. Le contrôle des objets ajoutés
  reste incomplet (voir Limites) (D-EID-BINDING).
- **Certificat et révocation.** Il doit remonter à une autorité de l'exploitant, être valide, porter la
  non-répudiation et se déclarer qualifié (D-EID-QUALIFIED) ; le niveau enregistré est `QES`. La révocation est
  obtenue en ligne seulement (OCSP, sinon CRL), signée par l'émetteur ou son répondeur délégué. Injoignable : refus
  (D-EID-REVOCATION).
- **Document de travail unique.** La demande garde une version : la source, puis les signatures acceptées. Un envoi
  fait sur une version dépassée est refusé (`STALE_DOCUMENT`). Le PDF final porte toutes les signatures (D-EID-CHAIN).
- **Constats et preuve.** Chaque signature acceptée garde le nom du signataire, l'émetteur, le numéro et l'empreinte
  du certificat, l'heure déclarée, l'heure de vérification, le format et la source de révocation. La preuve PDF
  les reprend (D-EID-EVIDENCE).
- **Horodatage facultatif.** Avec `app.signature.eid.timestamp.url`, le document final est étendu au niveau long
  terme quand les données de validation sont complètes, sinon il reçoit un horodatage de document. Une TSA
  injoignable est reprise par le répartiteur, puis le document est livré sans horodatage, dit dans la preuve
  (D-EID-TIMESTAMP).
- **Garde-fous.** Démarrage refusé sans fichier d'autorités (`app.signature.eid.trust-anchors-file`), et refusé si
  ni pont ni téléversement n'est actif. Aucun appel sortant vers une adresse venue d'un certificat dont la chaîne
  n'est pas vérifiée. Le PIN n'est jamais envoyé au serveur ni conservé (D-EID-PIN). Le numéro de registre national
  n'est ni extrait ni conservé à part (D-EID-NRN). Le clic « Signer » est refusé : 409, `SIGNED_UPLOAD_REQUIRED`.
  Une préparation datée de plus de quinze minutes, ou dans le futur au-delà d'une minute de décalage, est refusée :
  422, `SIGNING_SESSION_EXPIRED`.
- **Routes.** Portail : `GET /api/portal/signature` (champs `signedUpload`, `cardBridges`),
  `GET /api/portal/signature/document`, `POST /api/portal/signature/signed-document`,
  `POST /api/portal/signature/card-signature/prepare` et `…/complete`. « Mes signatures » : `GET /api/signatures/mine`
  (mêmes champs), `GET …/{signerId}/document`, `POST …/{signerId}/signed-document`, `POST …/{signerId}/card-signature/prepare`
  et `…/complete`. Prepare : requête `{certificate, certificateChain}` ; réponse `digest`, `hashFunction`,
  `signingTime`, `documentDigest`. Complete : `certificate`, `certificateChain`, `signingTime`, `documentDigest`,
  `signature`.
- **Motifs de refus d'un téléversement.** 422 `application/problem+json`, `code` = `NOT_A_PDF`, `DOCUMENT_MISMATCH`,
  `STALE_DOCUMENT`, `NO_NEW_SIGNATURE`, `SEVERAL_SIGNATURES`, `CONTENT_MODIFIED`, `SIGNATURE_INVALID`,
  `CERTIFICATE_UNTRUSTED`, `CERTIFICATE_NOT_QUALIFIED`, `CERTIFICATE_REVOKED`, `REVOCATION_UNAVAILABLE`,
  `DUPLICATE_SIGNER`. Accès refusé : 404 uniforme (portail).
- **Critères de succès.** Un PDF signé par un certificat qualifié d'une autorité configurée, qui prolonge le document
  remis, est accepté et clôt la décision ; la demande passe `SIGNED` quand tous les requis ont signé. Chaque motif
  ci-dessus refuse avec son motif. Le clic « Signer » est refusé. Le document final porte toutes les signatures, et
  la preuve reprend les constats. Sans fichier d'autorités, l'instance refuse de démarrer. Aucun appel sortant vers
  une adresse non vérifiée. Avec un pont détecté, la page signe sans quitter Myrmacta ; sans pont, elle ne propose le
  téléversement que si l'exploitant l'a activé.
- **Tests de référence.** `CardSignatureUploadDisabledRealApiTest`, `CardSignatureUploadEnabledRealApiTest`,
  `CardSignatureUseCaseImplTest`, `DssCardSignatureAssemblerTest`, `DssSignedPdfVerifierTest`,
  `DssSignedPdfVerifierReviewTest`, `DssSignedPdfVerifierCounterReviewTest`, `EidSignatureAdapterTest`,
  `EidTrustAnchorsTest`, `PinnedRevocationTransportTest`, `DssDocumentTimestamperTest`, `MySignaturesEidApiTest`,
  `SignaturePortalEidApiTest`, `SignaturePropertiesTest`.
- **Limites.** Aucun PDF signé avec une vraie carte n'a encore été vérifié, et aucun pont n'a été essayé avec une
  vraie extension : les tests fabriquent leur hiérarchie de certificats et simulent l'extension. Le profil exact des
  certificats eID n'a pas été relu sur les sites officiels. Le téléversement, désactivé par défaut, a deux
  contournements connus non corrigés et peut refuser à tort des PDF d'outils réels (voir §138, « Limites connues »).
- **Hors lot.** Pilotage de la carte depuis le navigateur (D-EID-TRANSPORT), liste de confiance européenne,
  rapprochement du nom du certificat avec le signataire attendu, signature par téléphone.
