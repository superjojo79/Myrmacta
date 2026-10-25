# Portail externe tokenisé — accès tiers hors BFF

> **Statut : analyse initiale, Phases 1–3 `dev-lifecycle` closes** (TODO §68, 2026-09-23). Les
> décisions produit et d'architecture sont tranchées dans
> [docs/ai/requirements/2026-09-23-feature-portail-externe.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-23-feature-portail-externe.md),
> pas ici. Origine : `ROADMAP.md` v1.1 (14/09/2026), item **3.1** (Portail externe tokenisé),
> classé « Produit, pas cadré ».
>
> **Scission 2026-09-23 (demande utilisateur).** Ce document couvrait à l'origine le portail **et**
> la signature électronique (item 2.4), sous le nom `26-portail-externe-signature.md`. La
> signature est désormais un lot distinct :
> [30-signature-electronique.md](30-signature-electronique.md). Le portail reste son **prérequis**
> (un signataire final n'a pas de compte Keycloak), mais a une valeur propre — consultation, dépôt
> de pièce, réponse à une tâche — et ne doit pas attendre le choix d'un fournisseur de signature.

---

## 1. Constat vérifié (pas supposé)

| Élément recherché | Résultat | Preuve |
|---|---|---|
| Route publique `/portal/**` ou équivalent | **Absente** | Grep `portal`/`PublicToken` sur `src/main/java` et `frontend/src` : 0 résultat (revérifié 2026-09-23) |
| Mécanisme d'accès anonyme/tokenisé à un dossier | **Absent** | Toute UI passe par la chaîne Spring Security OIDC BFF `@Order(4)` (`SecurityConfig.bffSecurityFilterChain`, session serveur, cookie httpOnly) |
| Service des fichiers statiques du SPA | **Authentifié** | `SpaFallbackConfig` sert `classpath:/static/**` et replie toute route non-API sur `index.html`, mais la chaîne BFF se termine par `anyRequest().authenticated()` : même `index.html` et les bundles JS redirigent vers Keycloak. Un tiers anonyme ne peut aujourd'hui charger **aucune** page du SPA |
| Précédent d'accès externe non authentifié par compte | **Existe, mais serveur-à-serveur** | Chaîne `@Order(3)` `webhookSecurityFilterChain` : `/api/webhooks/**` et `/api/callbacks/**`, sans session, sans CSRF, `permitAll`, limite de taille du corps avant bufférisation (`WebhookBodySizeLimitFilter`), auth métier HMAC dans les contrôleurs, rate-limit pré-auth (`WebhookPreAuthRateLimiter`) — TODO §51/§53/§54 |
| Précédent d'endpoint anonyme navigateur | **Existe** | Chaîne `@Order(2)` `cspReportSecurityFilterChain` (collecte de rapports CSP, rate-limitée, TODO §64) |
| Complétion d'une tâche `userTask` | **Liée à l'assignee** | `CompleteTaskUseCaseImpl.complete(...)` exige un `AccessContext.User` et applique `TaskAssignmentGuard.assertActorMayAct` (TODO §57 H2) : aucun acteur « externe » n'existe dans le modèle |
| Formulaires de tâche réutilisables | **Existent** | `flowable:formKey` `dossier-fields` / `document-generation` (TODO §52) et `user-question:<schemaKey>` (TODO §55, `TaskQuestionFormKeys`, réponses tracées dans `TaskQuestionAnswer`) |
| Dépôt de pièce jointe | **Existe (authentifié)** | `POST /api/dossiers/{id}/elements/attachment` (`DossierElementUploadController`) : magic bytes + `AttachmentScanPort` (ClamAV opt-in, PR #131) |
| Envoi d'e-mail | **Existe** | Canal `NotificationChannel.EMAIL` via outbox + connecteur `smtp` (PR #99) |
| Rate-limit applicatif | **Existe, JVM-local** | `SlidingWindowRateLimiter` et ses dérivés (webhook, callback, génération de documents, CSP) — pas de Redis (stack figée, S-7 assumé) |

---

## 2. Ce qu'un portail implique (périmètre posé en Phase 1)

- **Nature du jeton** : lien à durée de vie courte (à la `flowable-callback`) vs compte partenaire
  léger réutilisable. Impacte directement le modèle de menace (rejeu, énumération, transfert du
  lien).
- **Surface exposée** : lecture d'un dossier, dépôt de pièce jointe (réutiliser
  `AttachmentScanPort` + magic bytes), réponse à une tâche `userTask` via un `formKey` existant.
- **Acteur externe** : `TaskAssignmentGuard` et `AccessContext` ne connaissent que des utilisateurs
  OIDC ; la traçabilité (`TaskQuestionAnswer`, notifications `TASK_COMPLETED_BY_OTHER`, audit) doit
  pouvoir nommer un tiers externe sans lui donner de rôle V1.
- **Émission du lien** : depuis le process BPMN (cohérent avec le moteur), à la main par un
  instructeur, ou les deux ; destinataire identifié par e-mail ou lien remis hors FlowForge.
- **Authn/Authz** : nouvelle chaîne `@Order` Spring Security hors OIDC, sur le modèle de
  `/api/callbacks/**` — mais orientée navigateur (CSRF, cookies, `Referer`, service des fichiers
  statiques hors authentification).
- **Anti-énumération / rate-limit** : le jeton devient la seule barrière — leçons de
  `connecteur-callback` directement réutilisables (F5 DoS mémoire non authentifié, F6 rejeu, F7
  longueur minimale de secret, F8 journalisation des échecs, TODO §54).

## 3. Risques identifiés (à creuser en Phase 1/3)

- **Entropie, stockage et rejeu du jeton** : même classe de risque que `connecteur-callback` — ne
  pas repartir d'une hypothèse « littéral simple », s'appuyer sur les correctifs déjà prouvés
  (F5–F8). Un jeton stocké en clair en base est un identifiant réutilisable par quiconque lit la
  base.
- **Fuite du lien** : historique navigateur, en-tête `Referer`, logs du reverse-proxy, transfert de
  l'e-mail. Le jeton ne doit pas finir dans les logs ECS (MDC) ni dans les journaux d'accès.
- **Surface d'attaque nouvelle** : un humain non authentifié interagit avec une UI ; la discipline
  BFF actuelle (décision n° 27, même origine, pas de CORS applicatif) ne s'applique pas telle
  quelle. La CSP enforce (A4, PR #128) doit couvrir les pages portail.
- **Fuite de données du dossier** : champs sensibles (`sensitive`, TODO §23/§30), pièces jointes
  d'autres intervenants, historique workflow — tout ce qui n'est pas explicitement exposé doit
  rester invisible.
- **Rate-limit JVM-local** : suffisant en mono-réplique (décision n° 13), à documenter comme limite.

## 4. Points ouverts — tranchés en Phase 1

Voir [docs/ai/requirements/2026-09-23-feature-portail-externe.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-23-feature-portail-externe.md)
§ *Décisions*. Questions d'origine :

| ID | Question |
|---|---|
| D-PORTAL-SCOPE | Quel sous-ensemble de `Dossier`/tâches le portail expose-t-il au lancement ? |
| D-PORTAL-AUTH | Nature du jeton (lien à durée de vie courte vs compte partenaire) et durée de vie par défaut ? |
| D-PORTAL-CSRF-SESSION | Modèle sans session (comme les callbacks) ou session propre au tiers ? |
| D-PORTAL-ISSUE | Qui émet le lien (process BPMN, instructeur, les deux) et comment arrive-t-il au tiers ? |
| D-PORTAL-ACTIVATION | Activation opt-in (défaut désactivé) ou active par défaut ? |

Les questions `D-SIGNATURE-*` ont été déplacées dans
[30-signature-electronique.md](30-signature-electronique.md). `D-SIGNATURE-SCOPE` est **tranchée**
par la scission : lot séparé, après le portail.

## 5. Authentification forte du tiers (TODO §103)

Livrée par l'issue #210 ([§103](../decisions/103-authentification-belge-fas-et-itsme.md)). Une tâche externe qui porte
`flowable:portalIdentityField="dossierField:<champ>"` exige que le tiers s'identifie par FAS ou par itsme.

- **Client OIDC direct** (D-BEAUTH-PORTAL-CLIENT) : l'instance parle au fournisseur sans Keycloak ni compte pour le tiers ;
  code d'autorisation avec PKCE, `private_key_jwt` signé par la clé de l'instance (JWKS publiée sur
  `/api/portal/identity/jwks`) ou `client_secret_post` si le fournisseur a émis un secret ; ID token éventuellement
  chiffré, vérifié sur la JWKS du fournisseur (signature, émetteur de la découverte, audience, expiration, `nonce`).
- **Routes** (chaîne portail, sans session, limite par IP) : `POST /api/portal/identity/start` (jeton du lien, fournisseur
  choisi → URL d'autorisation) ; `GET /api/portal/identity/callback` (redirige vers `/portal/#identity:<preuve>` ou
  `#identity-refused`) ; `GET /api/portal/identity/jwks`.
- **Preuve d'identité** (D-BEAUTH-PROOF) : 256 bits, hachée en base, valable 60 minutes (plafond 4 h) et pour ce lien
  seulement ; elle remplace ensuite le jeton (`X-Portal-Identity`). Aucun stockage navigateur, comme pour le jeton.
- **Avant l'identification** (D-BEAUTH-PREVIEW) : `GET /api/portal/task` renvoie le nom de la tâche, la référence et les
  fournisseurs (`identity.verified = false`), sans consignes, formulaire ni pièces ; dépôt et soumission refusés.
- **Correspondance** (D-BEAUTH-NRN-FIELD, D-BEAUTH-LOA) : niveau eIDAS substantiel ou élevé ; champ rempli, le NRN vérifié
  doit lui être égal (11 chiffres, séparateurs ignorés) ; champ vide, il y est écrit à la soumission par le use case de
  correction des champs, en contexte système et à version attendue. La correspondance est recalculée sur le dossier relu
  à la soumission. Tout écart donne le refus neutre.
- **Trace** : fournisseur, niveau, nom et résultat, conservés après la soumission dans `portal_link_identity` (NRN et
  preuve effacés) et montrés à l'instructeur avec le lien ; le nom est réservé à qui peut gérer le lien.
- **Émission** : un champ d'identité absent du type, non textuel ou non sensible donne un lien `NOT_SENT`
  (`INVALID_IDENTITY_FIELD`) et la notification des participants, jamais un incident.
- **Preuves** : `PortalIdentityUseCasesTest`, `PortalAccessResolverIdentityTest`, `OidcPortalIdentityProviderAdapterTest`,
  `PortalIdentityRepositoryIT`, `PortalStrongAuthApiTest` (bout en bout, NRN absent des journaux, des variables de
  process et des outbox), Vitest du portail, Playwright `E2E-PORTAL-6` et `7`.

