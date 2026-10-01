# Intégrations entreprise (D3)

> **Statut : D3a livré (TODO §77, 2026-09-30)** — décisions D-INTEG-OAUTH-* dans `docs/roadmap-goal/DECISIONS.md`, conception dans
> `docs/ai/design/2026-09-30-feature-d3a-oauth2-client-credentials.md` ; D3b, D3c, D3d restent à faire. *(Ancien statut : première analyse
> du 2026-09-28.)* Lot **D3**
> `integrations-entreprise` de la Vague D (`ROADMAP.md` Catégorie 7), en quatre sous-lots
> (D3a à D3d). À affiner en `dev-lifecycle` Phase 1. Contexte connecteurs :
> [06-connecteurs.md](06-connecteurs.md), [18-connecteur-http-rest-service.md](18-connecteur-http-rest-service.md).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Catalogue | `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback`, `batch-import`, `kafka` | `infrastructure/connector/builtin/` |
| SPI | `ConnectorDefinition` + `TriggerConnector` / `ServiceConnector` ; schéma de configuration typé (`ConfigFieldType` : `STRING, PASSWORD, URL, INTEGER, BOOLEAN, SELECT, SECRET_TEXTAREA`) | `HttpRestConnector`, `ConfigFieldType` |
| Auth sortante `http-rest` | `authHeaderValue` **statique** envoyé tel quel ; aucun `client_credentials` dans le code | `HttpRestConnector` L157–164, L254–256 |
| Méthodes `http-rest` | `GET`, `POST`, `PUT` | `SUPPORTED_METHODS` |
| Retour d'un SERVICE | Réponse posée dans la seule variable `connectorResult` | `ConnectorServiceTask` L44 |
| Écriture de champs depuis un process | Aucun des 7 delegates allowlistés ne le fait (hors formulaire humain `dossier-fields`) | `AllowedBpmnDelegates` |
| E-mail entrant | Absent : `smtp` n'est qu'un SERVICE d'envoi | grep `imap` : 0 résultat |
| Bus Azure | Absent ; Kafka disponible | — |
| « ERP » | Couvert par `batch-import` (SFTP CSV / Excel), décision B1 | TODO §62 |

## 2. Sous-lots

| Sous-lot | Objet | Débloque | Taille |
|---|---|---|---|
| **D3a** | OAuth2 `client_credentials` sur `http-rest` (jeton obtenu, mis en cache, renouvelé ; secret client chiffré comme `PASSWORD`) | API protégées par Microsoft Entra ID : D365 F&O (OData), Dataverse, Graph | M |
| **D3b** | Mapping réponse de connecteur → champs du dossier (nouvelle brique allowlistée) | Enrichissement automatique : score, référentiel, identifiant créé dans l'ERP | M |
| **D3c** | E-mail entrant (IMAP ou Microsoft Graph), connecteur TRIGGER : un message crée un dossier ou alimente un dossier existant ; pièces jointes rattachées et scannées (ClamAV, PR #131) | Réclamations, demandes reçues par e-mail | M |
| **D3d** *(à confirmer)* | Azure Service Bus / Event Grid, TRIGGER | Business Events D365 F&O sans relais | M |

## 3. Coutures et dépendances

- **D3a** : configuration et exécution du connecteur `http-rest` seulement ; cache de jeton
  (par instance de connecteur, borné) ; garde SSRF inchangée sur l'URL du jeton (§44). Parallélisable
  avec D1 et LC1.
- **D3b** : ajoute un delegate → passage par l'allowlist BPMN (§57, §69), **sans** expression
  libre. Le langage de mapping est celui de **LC2** ([40](40-langage-expression-metier.md)) :
  D3b suit LC2. Respect de `DossierFieldValidator` et du masquage `sensitive`.
- **D3c / D3d** : nouvelles entrées du catalogue → **un seul cycle** pour les deux s'ils sont
  retenus (leçon de B1 : pas plusieurs PR concurrentes sur le catalogue). D3c : anti-rejeu,
  corrélation avec un dossier existant (référence dans l'objet ?), taille maximale, scan.
- **Sécurité** : secrets chiffrés au repos (`ConnectorSecretCipher`, DEK par tenant) ; aucun
  secret dans les logs.

## 4. Angle D365 F&O

Cas d'usage visés : onboarding fournisseur (création du fournisseur via OData après validation),
demandes de données de base, litiges clients aboutissant à un avoir, dérogations (grille DMN).
Conditions : D3a pour appeler OData en direct, et une entrée des Business Events (webhook
`http-rest` si le mode d'authentification du point de terminaison HTTPS de D365 le permet — à
valider —, Kafka, ou D3d).

## 5. Exposition MCP

`list_connector_definitions` expose déjà le `configSchema` : D3a, D3c et D3d y apparaissent sans
nouveau tool. `define_connector_instance` accepte les nouveaux champs, **sans secret via
l'assistant** (principe P9 de l'[analyse 46](46-exposition-mcp-couche-low-code.md)). D3b : la
brique de mapping entre dans le catalogue de LC1 ; `validate_expression` / `evaluate_expression`
(LC2) servent à la vérifier.

## 6. Décisions à trancher

1. **D-INTEG-OAUTH-SCOPE** — `client_credentials` seul, ou aussi certificat client (Entra ID).
2. **D-INTEG-MAPPING-TARGET** — le mapping écrit-il seulement des champs du dossier, ou aussi
   des variables de process.
3. **D-INTEG-MAIL-PROTOCOL** — IMAP, Graph, ou les deux ; corrélation avec un dossier existant.
4. **D-INTEG-AZURE** — D3d retenu ou non (selon les clients visés).

## 7. Documentation impactée

`GUIDE-UTILISATEUR.md` (Admin → Connecteurs), `GUIDE-CONCEPTION-BPMN.md` (brique de mapping),
`06-connecteurs.md`, `.env.example` et `GETTING-STARTED.md` si de nouvelles variables sont lues au
démarrage.
