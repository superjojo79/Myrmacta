# Portail externe tokenisé & signature électronique — accès tiers hors BFF

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Origine : `ROADMAP.md` v1.1 (14/09/2026), items **3.1** (Portail externe tokenisé) et
> **2.4** (Signature électronique), tous deux classés « Produit, pas cadré » — aucune trace de code,
> aucune analyse lifecycle antérieure. Regroupés dans **un seul** document (pas deux features
> indépendantes) parce que la signature électronique présuppose quasi toujours un point d'accès
> externe tokenisé — un signataire final n'a pas de compte Keycloak — donc le portail est un
> prérequis architectural de la signature, pas une coïncidence de thème. Le portail seul reste
> toutefois une feature à valeur propre (consultation, dépôt de pièce, réponse à une tâche) sans
> attendre la signature.

---

## 1. Constat vérifié (pas supposé)

| Élément recherché | Résultat | Preuve |
|---|---|---|
| Route publique `/portal/**` ou équivalent | **Absente** | Grep `portal`/`PublicToken`/`external.*token` sur `src/main/java` : 0 résultat |
| Mécanisme d'accès anonyme/tokenisé à un dossier | **Absent** | Toute UI passe par la chaîne Spring Security OIDC BFF (session serveur, cookie httpOnly) — aucune deuxième chaîne `@Order` pour un accès public interactif |
| Symboles signature électronique (DocuSign/Yousign/eIDAS) | **Absents** | Grep `DocuSign\|Yousign\|eIDAS\|signature` (insensible à la casse) : seuls des faux positifs (`HmacWebhookAuthenticator`, `AttachmentContentSniffer` — « signature » = magic bytes/HMAC, sens différent) |
| Précédent d'accès externe non authentifié par compte | **Existe, mais async et inbound-only** | `flowable-callback` (capability CALLBACK, PR #102/#103/#104, TODO §51/§53/§54) : `POST /api/callbacks/{instanceId}` hors chaîne OIDC (`securityMatcher` dédié), auth HMAC + idempotence — mais c'est un webhook serveur-à-serveur, pas une UI consultée par un humain externe |

Le seul précédent architectural pertinent est donc `flowable-callback` : il prouve que FlowForge sait
déjà exposer une route hors BFF avec sa propre garde (HMAC, `securityMatcher` séparé, rate-limit,
audit des échecs — TODO §51/§53/§54). Un portail réutiliserait cette discipline, pas un mécanisme
neuf.

---

## 2. Ce qu'un portail impliquerait (périmètre à poser, pas à trancher ici)

- **Nature du token** : lien à durée de vie courte (à la `flowable-callback`) vs compte partenaire
  léger avec identifiant réutilisable. Impacte directement le modèle de menace (rejeu, énumération).
- **Surface exposée** : lecture seule d'un dossier ? Dépôt de pièce jointe (réutiliser
  `DossierElementUploadController` + `AttachmentScanPort`) ? Réponse à une tâche `userTask` via
  `flowable:formKey` — le mécanisme `dossier-fields`/`document-generation`/`user-question`
  (TODO §52/§55) existe déjà côté rendu de formulaire, il faudrait juste un point d'entrée non
  authentifié par compte qui le sert.
- **Authn/Authz** : nouvelle chaîne `@Order` Spring Security hors OIDC, sur le modèle de
  `/api/callbacks/**` — mais orientée UI (donc CSRF/session à repenser différemment d'un webhook
  serveur-à-serveur).
- **Anti-énumération / rate-limit** : le token devient la seule barrière — leçons directement
  réutilisables de `connecteur-callback` (F5 DoS mémoire non authentifié, F6 rejeu HMAC, F7 longueur
  minimale de secret, F8 journalisation des échecs, TODO §54).
- **Lien avec le mécanisme `user-question`** (§23, `TaskQuestionAnswer`) : une réponse externe
  pourrait suivre le même modèle « réponse tracée, indépendante du dossier » plutôt qu'un mécanisme
  ad hoc.

## 3. Ce que la signature ajouterait par-dessus le portail

- **Fournisseur** : aucun choisi (DocuSign, Yousign, eIDAS qualifié...) — décision produit
  (budget SaaS, conformité juridique locale), pas une question technique tranchable ici.
- **Intégration côté connecteurs** : le catalogue supporte déjà des connecteurs SERVICE externes
  (`http-rest` SERVICE, PR #95) — un connecteur signature suivrait le même patron, pas d'architecture
  nouvelle à inventer pour l'appel sortant.
- **Retour de signature** : le fournisseur notifie de façon asynchrone (webhook) qu'un document est
  signé — c'est exactement le rôle du connecteur `flowable-callback` déjà livré (capability
  CALLBACK, corrélation par clé métier). Pas de nouveau mécanisme d'attente process à concevoir.
- **Document à signer** : déjà généré par `GenerateDocumentUseCase`/`DocumentStoragePort` — la brique
  manquante est uniquement le pont vers un tiers signataire externe, pas la génération elle-même.

## 4. Risques identifiés (à creuser en Phase 1/3, pas résolus ici)

- **SSRF/entropie de token/anti-rejeu** : même classe de risque que `connecteur-callback` — ne pas
  repartir d'une hypothèse « littéral simple », s'appuyer sur les correctifs déjà prouvés (F5-F8).
- **Conformité juridique de la signature électronique** (valeur probante, eIDAS) : hors périmètre
  technique, nécessite un arbitrage produit explicite avant tout choix de fournisseur.
- **Surface d'attaque nouvelle** : un portail est par construction accessible sans compte —
  contrairement à `flowable-callback` (serveur-à-serveur), un humain non authentifié interagit avec
  l'UI ; la discipline BFF actuelle (décision n° 27, même origine, pas de CORS applicatif) ne
  s'applique pas telle quelle à une route volontairement publique.

## 5. Points ouverts (Phase 1 `dev-lifecycle` — ne rien trancher silencieusement)

| ID | Question |
|---|---|
| D-PORTAL-SCOPE | Quel sous-ensemble de `Dossier`/tâches le portail expose-t-il au lancement (lecture seule vs dépôt de pièce vs réponse de tâche) ? |
| D-PORTAL-AUTH | Nature du token (lien à durée de vie courte vs compte partenaire) et durée de vie par défaut ? |
| D-PORTAL-CSRF-SESSION | Le portail garde-t-il un modèle sans session (comme les callbacks) ou introduit-il une session propre au tiers externe ? |
| D-SIGNATURE-SCOPE | La signature est-elle lancée dans le même cycle que le portail, ou un lot séparé après confirmation du besoin portail ? |
| D-SIGNATURE-PROVIDER | Quel fournisseur (ou abstraction multi-fournisseur dès le départ) ? |
| D-SIGNATURE-TRIGGER | Le déclenchement de signature part-il d'un `serviceTask` BPMN (cohérent avec le reste du moteur) ou d'un appel MCP/REST direct ? |

Aucune décision de priorité n'est prise par ce document — il documente uniquement l'écart réel
constaté contre le code.
