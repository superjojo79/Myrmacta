# Signature électronique — socle générique et stub, en préparation d'un provider

> **Statut : analyse initiale recalibrée (2026-09-29), non démarré.** Aucune décision technique
> tranchée au sens `dev-lifecycle` — Phase 1 (requirements) obligatoire avant tout code. Origine :
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
> **Arbitrages d'orientation posés le 2026-09-29** (réponses de l'utilisateur, à confirmer en
> Phase 1 et à consigner dans le TODO) : abstraction = **port + adaptateur exposé comme
> connecteur** ; comportement du stub = **simulation de bout en bout** ; parcours = **tâche externe
> du portail + statut dans le dossier** ; déclenchement = **nouveau delegate allowlisté**.

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
FlowForge sait exprimer, et laisse au provider la charge de mapper le reste.

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
- **Cohabitation à trancher** (D-SIGNATURE-TRIGGER-VS-CONNECTOR) : les deux arbitrages d'orientation
  ne se recouvrent qu'en apparence — le **connecteur** configure et reçoit, le **delegate**
  déclenche et attend. Reste à décider si le delegate passe *par* le connecteur (config lue à
  chaque appel) ou lit directement le port ; recommandation : le delegate appelle le port, le
  connecteur ne sert qu'à la configuration et au retour.

### 2.4 Parcours du signataire

Le signataire n'a pas de compte : il passe par le **portail tokenisé** livré (PR #138).

- **Écart à traiter** : le portail n'accepte aujourd'hui que des tâches `user-question:`
  (D-PORTAL-FORM). Pour le stub, deux voies : (a) réutiliser `user-question` avec une question de
  confirmation (« Je signe ce document ») — aucun changement du portail, mais UX pauvre ; (b)
  introduire un **type de tâche externe « signature »** — plus propre et porteur pour un provider
  réel (redirection vers l'URL de signature du fournisseur), mais rouvre le périmètre du portail.
  Décision de Phase 1 (D-SIGNATURE-PORTAL-KIND).
- **Instructeur** : statut de chaque demande (`PENDING`, `SIGNED`, `DECLINED`, `EXPIRED`,
  `FAILED`) visible dans le détail du dossier, avec renvoi et annulation.

### 2.5 Modèle de données

- **Demande de signature** (`signature_request`) : dossier, élément source, provider (code de
  l'adaptateur), signataires, statut normalisé, `simulated`, corrélation, échéance, horodatages.
- **Document signé et preuve** : rattachés au dossier. Choix à figer (D-SIGNATURE-ELEMENT) :
  `PRODUCED_DOC` avec un marqueur, ou nouvelle valeur d'`ElementType`. Une nouvelle valeur touche
  l'export CSV, l'affichage, la purge et le schéma — à mesurer avant.
- **Purge et rétention** : la purge de dossier doit supprimer les demandes, documents signés et
  preuves (même leçon que D-PORTAL-PURGE : le graphe de `ProcessPurgeOutboxUseCaseImpl` doit
  couvrir les nouvelles tables).

## 3. Ce que le lot ne fait pas

- Aucun fournisseur réel, aucune valeur juridique, aucun choix de niveau eIDAS **implémenté**.
- Pas de signature manuscrite, de certificat ni d'horodatage qualifié gérés par FlowForge.
- Pas d'aller-retour complexe multi-fournisseurs ni de bascule à chaud d'un provider vers un autre
  pour des demandes en cours.

## 4. Risques identifiés

| ID | Risque | Parade proposée |
|---|---|---|
| **R-SIM-EN-PROD** | Le stub actif en production produit des « documents signés » pris pour authentiques | Désactivé par défaut ; marquage indélébile (filigrane, `simulated`, bandeau) ; refus de démarrage ou avertissement fort si actif avec un profil de production — à trancher (D-SIGNATURE-STUB-GUARD) |
| **R-CONTRAT-TROP-ETROIT** | Le contrat figé sans provider réel se révèle inadapté au premier fournisseur | Capacités déclarées ; événement normalisé minimal ; **valider le contrat contre 2 ou 3 API publiques** (documentation Yousign, DocuSign, Universign) avant Phase 3, sans les intégrer |
| **R-CONTRAT-TROP-LARGE** | Sur-conception d'un contrat « universel » jamais exercé | Périmètre §2.1 restreint ; ce qui n'est pas utilisé par le stub ni par un provider cible ne sort pas |
| **R-PORTAIL** | Ajouter un type de tâche externe rouvre la sécurité du portail (routes publiques, CSP) | Préférer (a) tant qu'aucun provider n'impose (b) ; toute nouvelle route publique repasse par la revue du lot portail |
| **R-DELEGATE** | Un nouveau delegate modifie la surface d'exécution BPMN | Ajout à `AllowedBpmnDelegates` + tests de l'allowlist (§57/§69) ; aucune expression libre |
| **Secrets fournisseur** | Clés API en clair | Champs `PASSWORD` de connecteur (chiffrés au repos) ; jamais en propriété d'environnement partagée |
| **Webhook du fournisseur** | Rejeu, secret faible, DoS mémoire | Réutiliser les correctifs F5–F8 de `flowable-callback` |
| **SSRF** | Appel sortant vers le fournisseur | Mêmes garde-fous que `http-rest` SERVICE (TODO §44) |
| **CSP** | Un parcours embarqué (iframe) exigerait d'élargir `frame-src` | Redirection plutôt qu'iframe ; ne pas toucher à la CSP dans ce lot |
| **Conformité juridique** | Valeur probante, niveau eIDAS | **Hors lot** : arbitrage reporté au choix du premier provider réel (voir D-SIGNATURE-LEVEL) |

## 5. Points ouverts (Phase 1 `dev-lifecycle` — ne rien trancher silencieusement)

| ID | Question | Statut |
|---|---|---|
| ~~D-SIGNATURE-SCOPE~~ | Lot séparé, après `portail-externe` | **Tranchée 2026-09-23** |
| ~~D-SIGNATURE-PROVIDER~~ | Quel fournisseur ? | **Reportée** : aucun provider désigné ; le lot fournit le point d'extension et le stub |
| D-SIGNATURE-ABSTRACTION | Port + adaptateur exposé comme connecteur | **Orientation posée 2026-09-29**, à confirmer en Phase 1 |
| D-SIGNATURE-STUB-BEHAVIOR | Simulation de bout en bout, marquée non probante | **Orientation posée 2026-09-29**, à confirmer |
| D-SIGNATURE-UX | Tâche externe du portail + statut dans le dossier | **Orientation posée 2026-09-29**, à confirmer |
| D-SIGNATURE-TRIGGER | Nouveau delegate allowlisté | **Orientation posée 2026-09-29**, à confirmer |
| D-SIGNATURE-TRIGGER-VS-CONNECTOR | Le delegate passe-t-il par le connecteur ou lit-il directement le port ? | Ouvert — recommandation §2.3 |
| D-SIGNATURE-PORTAL-KIND | Réutiliser `user-question` (a) ou créer un type de tâche « signature » (b) ? | Ouvert — recommandation : (a) |
| D-SIGNATURE-ELEMENT | Document signé et preuve : `PRODUCED_DOC` marqué ou nouvelle valeur d'`ElementType` ? | Ouvert |
| D-SIGNATURE-STUB-GUARD | Comportement du stub avec un profil de production (refus de démarrer, avertissement, indisponible) | Ouvert |
| D-SIGNATURE-ACTIVATION | Propriété d'activation, nom et valeur par défaut (`off`) | Ouvert — patron `scan.type` |
| D-SIGNATURE-LEVEL | Niveau eIDAS visé (simple, avancé, qualifié) | **Reportée** au premier provider réel ; le contrat expose seulement un champ « niveau » informatif |
| D-SIGNATURE-CONTRACT-CHECK | Faut-il valider le contrat contre 2 ou 3 API publiques avant de le figer ? | Ouvert — recommandé (R-CONTRAT-TROP-ETROIT) |

## 6. Coutures et séquencement

- **Surfaces touchées :** nouveau port et adaptateur (`domain/…/port/out`, `infrastructure/…`),
  entrée `ConnectorCatalog`, `AllowedBpmnDelegates`, portail (selon D-SIGNATURE-PORTAL-KIND),
  détail du dossier (statut), purge, golden OpenAPI, tools MCP (exposition dans le même lot,
  principe P1 de l'[analyse 46](46-exposition-mcp-couche-low-code.md)).
- **Parallélisme (voir `FILE-ATTENTE.md`, Vague 1) :** disjoint de LC1 (frontend BPMN), D1
  (`FieldDefinition`) et D3a (`HttpRestConnector`). **Attention** : l'entrée `ConnectorCatalog` et
  l'enregistrement des tools MCP sont des coutures partagées avec D3c/D3d et tout lot `LC*` —
  pas de PR concurrente sur le catalogue.
- **Suite naturelle :** l'arrivée d'un provider réel devient un lot court (adaptateur, mapping
  d'événements, tests d'intégration contre le sandbox du fournisseur, arbitrage eIDAS), et le bloc
  « Demander une signature » de la palette LC1 s'appuie sur le delegate posé ici.

Aucune décision de priorité n'est prise par ce document.
