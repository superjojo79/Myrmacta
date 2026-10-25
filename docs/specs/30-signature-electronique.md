# Signature électronique — socle générique et stub, en préparation d'un provider

> **Statut : livré le 2026-09-29** ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), TODO §72 clos) — socle générique + stub, sans provider ; fournisseur réel et eIDAS à cadrer. La Phase 2 a élargi
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

- Aucun fournisseur réel, aucune valeur juridique, aucun choix de niveau eIDAS **implémenté**.
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
| **SSRF** | Appel sortant vers le fournisseur | Mêmes garde-fous que `http-rest` SERVICE (TODO §44) |
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
