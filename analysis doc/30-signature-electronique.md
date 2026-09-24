# Signature électronique — pont vers un tiers signataire externe

> **Statut : analyse initiale, non démarré.** Aucune décision technique tranchée — à affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code. Origine : `ROADMAP.md` v1.1
> (14/09/2026), item **2.4**, classé « Produit, pas cadré ».
>
> **Issu de la scission du 2026-09-23** (demande utilisateur) de l'analyse
> `26-portail-externe-signature.md`, désormais [26-portail-externe.md](26-portail-externe.md).
> **Prérequis : lot `portail-externe` livré** — un signataire final n'a pas de compte Keycloak,
> la signature présuppose donc un point d'accès externe tokenisé (ou la délégation complète du
> parcours de signature au fournisseur, voir D-SIGNATURE-UX).

---

## 1. Constat vérifié (pas supposé)

| Élément recherché | Résultat | Preuve |
|---|---|---|
| Symboles signature électronique (DocuSign/Yousign/eIDAS) | **Absents** | Grep `DocuSign\|Yousign\|eIDAS\|signature` (insensible à la casse) : seuls des faux positifs (`HmacWebhookAuthenticator`, `AttachmentContentSniffer` — « signature » = HMAC / magic bytes, sens différent) |
| Appel sortant vers un service tiers | **Existe** | Connecteur `http-rest` en rôle SERVICE (PR #95), protections SSRF (TODO §44, M3/M4) |
| Retour asynchrone d'un tiers vers un process en attente | **Existe** | Connecteur `flowable-callback` (capability CALLBACK, PR #102, corrélation par clé métier, HMAC + idempotence, TODO §51/§53/§54) |
| Document à signer | **Existe** | `GenerateDocumentUseCase` + `DocumentStoragePort` (PDF Flying Saucer/OpenPDF) |

## 2. Ce que la signature ajoute par-dessus le portail

- **Fournisseur** : aucun choisi (DocuSign, Yousign, eIDAS qualifié...) — décision produit
  (budget SaaS, conformité juridique locale), pas une question technique tranchable ici.
- **Intégration côté connecteurs** : le catalogue supporte déjà des connecteurs SERVICE externes —
  un connecteur signature suivrait le même patron, pas d'architecture nouvelle pour l'appel sortant.
- **Retour de signature** : le fournisseur notifie de façon asynchrone (webhook) qu'un document est
  signé — rôle exact du connecteur `flowable-callback`. Pas de nouveau mécanisme d'attente process.
- **Document signé** : à rapatrier et rattacher au dossier (`PRODUCED_DOC` ou nouveau type
  d'élément), avec sa preuve de signature (fichier de preuve / audit trail du fournisseur).
- **Parcours du signataire** : soit dans le portail FlowForge (lien tokenisé du lot
  `portail-externe`, redirection ou iframe vers le fournisseur), soit entièrement chez le
  fournisseur (e-mail envoyé par lui) — ce choix conditionne la dépendance réelle au portail.

## 3. Risques identifiés

- **Conformité juridique** (valeur probante, niveau eIDAS simple/avancé/qualifié) : arbitrage
  produit explicite requis avant tout choix de fournisseur.
- **Secrets fournisseur** : clés API à chiffrer comme les secrets de connecteurs existants.
- **Webhook du fournisseur** : même classe de risques que `flowable-callback` (rejeu, secret
  faible, DoS mémoire) — réutiliser les correctifs F5–F8.
- **SSRF** : l'appel sortant doit passer par les mêmes garde-fous que `http-rest` SERVICE.
- **CSP** : un parcours embarqué (iframe fournisseur) exigerait d'élargir `frame-src` de la CSP
  enforce (A4) — à éviter si une redirection suffit.

## 4. Points ouverts (Phase 1 `dev-lifecycle` — ne rien trancher silencieusement)

| ID | Question |
|---|---|
| ~~D-SIGNATURE-SCOPE~~ | **Tranchée 2026-09-23** : lot séparé, démarré après la livraison de `portail-externe` |
| D-SIGNATURE-PROVIDER | Quel fournisseur (ou abstraction multi-fournisseur dès le départ) ? |
| D-SIGNATURE-LEVEL | Niveau eIDAS visé (simple, avancé, qualifié) ? |
| D-SIGNATURE-TRIGGER | Déclenchement depuis un `serviceTask` BPMN (cohérent avec le moteur) ou par un appel MCP/REST direct ? |
| D-SIGNATURE-UX | Parcours du signataire dans le portail FlowForge ou entièrement chez le fournisseur ? |

Aucune décision de priorité n'est prise par ce document.
