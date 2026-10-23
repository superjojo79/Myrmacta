# §68 — Portail externe tokenisé — tâche externe par lien magique

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §68 · [registre](README.md)

**Énoncé :** item **3.1** de `ROADMAP.md`, analyse
[26-portail-externe.md](../specs/26-portail-externe.md). Scindé le 2026-09-23 (demande
utilisateur) de la signature électronique, désormais lot distinct après celui-ci
([30-signature-electronique.md](../specs/30-signature-electronique.md)). Un tiers **sans
compte** répond à une `userTask` qui lui est destinée (formulaire `user-question` + dépôt de
pièces) via un lien reçu par e-mail. Aujourd'hui aucune route publique n'existe : même
`index.html` exige la session BFF.

**Numérotation :** §67 = `outillage-dmn` (PR #134, mergée et renumérotée) ; §69 = correctif de
sécurité UEL trouvé en Phase 3 de ce lot (PR #136, mergée).

**Statut :** **Phase 1 (requirements) close (2026-09-23)** — 10 décisions tranchées une par une
par l'utilisateur (`AskUserQuestion`) : D-PORTAL-SCOPE = tâche externe ; D-PORTAL-AUTH = lien
magique seul (jeton opaque ≥ 256 bits, haché, expirant, révocable) ; D-PORTAL-ISSUE = BPMN +
renvoi/révocation par l'instructeur ; D-PORTAL-TTL = 7 jours, surchargeable par tâche, plafond
tenant ; D-PORTAL-CSRF-SESSION = sans session, jeton dans le fragment d'URL puis en en-tête ;
D-PORTAL-DATA = contexte de la tâche seul ; D-PORTAL-FORM = `user-question` uniquement ;
D-PORTAL-ACTIVATION = opt-in, désactivé par défaut, tâche externe rejetée au déploiement si off ;
D-PORTAL-SCAN = **fail-open identique à l'interne** (risque R-SCAN-FAIL-OPEN accepté
explicitement, contre la recommandation fail-closed) ; D-PORTAL-EXPIRY = tâche reste ouverte +
notification inbox des participants. **Phase 2 (revue) close (2026-09-23)** : H1–H6 validées ; D-PORTAL-REUSE = lien réutilisable
jusqu'à soumission ; D-PORTAL-NO-ADDRESS = pas de lien + notification, process jamais en incident ;
D-PORTAL-PURGE = la purge de dossier supprime les liens portail **et** les `TaskQuestionAnswer` —
**écart préexistant trouvé en revue** (`task_question_answer.dossier_id` absent du graphe de
`ProcessPurgeOutboxUseCaseImpl`), corrigé dans ce lot. **Phase 3 (design) close (2026-09-23)** : D-PORTAL-UI-BUILD = build Vite séparé
`/portal/` (seul `/portal/**` public) ; D-PORTAL-BPMN-MARKER = `formKey` `user-question:` +
attributs `flowable:portalRecipient` / `flowable:portalLinkTtlDays` (conservés non évalués par
Flowable, vérifié dans les sources 8.0.0) ; D-PORTAL-INVITE-DELIVERY = jeton généré au moment de
l'envoi par un job dédié via `EmailPort`, jamais en clair en base (le canal EMAIL de l'outbox
stocke le corps du message) ; D-PORTAL-ACTOR = nouvelle variante scellée
`AccessContext.External`. Table `portal_link` (index unique partiel « un lien vivant par tâche »),
chaîne Spring Security `@Order(3)` conditionnelle, listener Flowable global `TASK_CREATED`.
**Constat de sécurité hors périmètre** : Flowable 8 évalue en UEL `formKey`, `name`,
`documentation`, `dueDate`, `priority`, `category`, `owner` d'une `userTask`, non couverts par
l'allowlist H1-bis — écart préexistant proposé comme tâche séparée ; ce lot impose seulement un
`formKey` littéral aux tâches externes. → **Corrigé le 2026-09-24 avant la Phase 4**, sur demande
utilisateur : voir §69. **Phase 4 (planning) close (2026-09-24)** : 13 milestones (M1 fondation →
M2 parser → M3/M4 émission et envoi en parallèle → M5 accès public → M6/M7 dépôt-soumission et
gestion instructeur → M8 purge → M9/M10 frontend portail et interne → M11 Playwright → M12 doc →
M13 vérification), 65 scénarios de test tracés (52 backend, 8 Vitest, 5 Playwright), estimation
~7 j. **Phase 5 (implémentation) livrée (2026-09-24)**, M1 → M13 enchaînés sur demande explicite
utilisateur, en TDD, 11 commits. **Décision prise en cours de route** : H6 contenait une erreur
factuelle (la réassignation exige TENANT_ADMIN/WORKFLOW_ADMIN, pas EDIT) — **tranchée par
l'utilisateur** : envoyer/renvoyer/révoquer = EDIT sur le type (admins compris), lecture = VIEW
(adresse masquée sans EDIT). **Écarts au design documentés** (design § Écarts) : envoi *puis*
activation du lien ; page portail en HTML natif (70 Ko, sans PrimeVue) ; `DynamicFieldInput.vue`
extrait (`DynamicFields` tirait le store d'auth via `SensitiveValue`) ; `UserQuestionAnswers`
extrait de `CompleteTaskUseCaseImpl` (comportement interne inchangé) ; limiteur par IP **borné**
(les limiteurs webhook/CSP existants ne purgent jamais leurs compteurs — même classe que F5 §54,
non corrigé hors périmètre) ; `OpenApiContractIT` démarré portail activé. **Preuves M13 :**
`./mvnw verify` → **2253 tests, 0 échec**, couverture Jacoco respectée, ArchUnit vert (dont la
nouvelle règle « seul `..workflow.portal..` construit `AccessContext.External` ») ; Vitest
**387/387** ; Playwright **86/86** (dont E2E-PORTAL-1…5) ; 66/66 scénarios du document de tests,
36/36 tâches du planning. Points notables prouvés par test : jeton absent de la base, de l'outbox
et des logs ; refus neutre identique octet pour octet ; soumission unique sous concurrence réelle ;
pas de lien orphelin après rollback moteur ; fail-open ClamAV réel ; rendu sous CSP enforce sans
violation ; portail désactivé = aucune route publique. **Phase 7 (contrôle d'implémentation)
close (2026-09-24)** : 3 défauts réels corrigés en TDD — `/portal` sans slash final (page blanche,
assets résolus vers le SPA interne), base d'API fausse si le chemin du tenant contient `/portal`,
erreur imprévue sortie de la chaîne portail (filet 500 générique) ; limitation SMTP sans backoff
documentée. **Phase 8 (tests) close** : tests des branches hors moteur (notificateur, e-mail,
courses compare-and-set perdues, schéma supprimé) ; `main` intégrée (PR #134, #136 — conflits de
documentation seulement). **Phase 9 (revue) close** : **F-PORTAL-1 (Moyenne) corrigé** — la limite
par IP et le contrôle du jeton s'exécutaient après la lecture du corps multipart (un anonyme
faisait ingérer jusqu'à 25 Mo par requête avant son 404/429) ; déplacés dans
`PortalPreAuthInterceptor` (`preHandle`). F-PORTAL-2 (Faible, multipart mal formé → 500) confié
à la revue adversariale. **Preuves finales (après merge de `main`)** : `./mvnw verify` → **2347
tests, 0 échec** ; Vitest **402/402** ; Playwright **92/92** ; couverture du code portail 96,5 %
lignes / 87,5 % branches, use cases 100 % lignes / 95,1 % branches (4 moitiés de conditions
défensives listées dans le document de tests). **Revue adversariale (subagent, serveur réel)
close (2026-09-24)** : aucun constat critique ni haut ; ni IDOR, ni escalade de l'acteur externe, ni
fuite du jeton, ni XSS. **Corrigés en TDD** (tests rouges d'abord sur Tomcat réel,
`PortalRealServerTest`) : **C1 (Moyenne)** — une erreur de routage sur une chaîne publique
(portail, webhooks, rejet du pare-feu HTTP) finissait en redirection Keycloak + session anonyme
`;jsessionid` → dispatch `ERROR` autorisé dans la chaîne BFF, sessions par cookie seulement ;
**C2 (Moyenne)** — limite par IP contournable par un en-tête `Forwarded` forgé →
`ClientIpResolver` (en-têtes transférés lus seulement depuis un proxy de confiance, `Forwarded`
ignoré), aussi pour le limiteur CSP ; docs/guides/DEPLOIEMENT.md : le proxy supprime `Forwarded` et
`X-Forwarded-Prefix` ; **C3 (Faible, = F-PORTAL-2)** — 500 + log ERROR sur requête mal formée →
statut 4xx propre ; **H1** — table du limiteur plafonnée même au sein d'une minute, IPv6 par /64.
**Preuves** : `./mvnw verify` → **2358 tests, 0 échec**. **Mergée sur `main`** le 2026-09-24 ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), squash `578a2db`), CI verte (`build` + `frontend`). Suite : lot `signature-electronique` (analyse 30), désormais démarrable.

**Impacte (réel, Phase 5) :** `SecurityConfig` (chaînes renumérotées) + `PortalSecurityConfig`,
`SpaFallbackConfig`, `AccessContext` (+ 5 politiques d'accès), `AddDossierElementUseCaseImpl`,
`DossierFieldRedactor`, `CompleteTaskUseCaseImpl` (extraction `UserQuestionAnswers`),
`DeployBpmnProcessUseCaseImpl`, `FlowableBpmnParserAdapter`, `ProcessPurgeOutboxUseCaseImpl`,
`TaskQuestionAnswerRepository`, migration `025-portal-link`, `application.yml` (`app.portal.*`),
`frontend/portal/**`, `vite.portal.config.ts`, `DynamicFields.vue`/`DynamicFieldInput.vue`,
`PortalLinksPanel.vue`, `flowablePropertiesProvider.ts`, `eslint.config.ts`, golden OpenAPI,
**`docs/guides/GUIDE-UTILISATEUR.md` (§0 tableau des rôles + §11), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3ter),
`docs/guides/GETTING-STARTED.md` (§14), `.env.example`, `docs/guides/DEPLOIEMENT.md` (§9)**, `CHANGELOG.md`.

**Impacte (prévisionnel, Phase 3 — conservé pour l'historique) :** `SecurityConfig` (nouvelle chaîne publique),
`SpaFallbackConfig` / packaging frontend, `FlowableBpmnParserAdapter` (allowlist), use cases
workflow (`CompleteTaskUseCaseImpl`, `TaskAssignmentGuard`), upload de pièce jointe, notifications
(EMAIL + BROWSER), Liquibase (table des liens), `application.yml`, `docs/guides/GUIDE-UTILISATEUR.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md`, `docs/guides/GETTING-STARTED.md`, `.env.example`, `docs/guides/DEPLOIEMENT.md`, golden OpenAPI.

**Détail :** [`docs/ai/requirements/2026-09-23-feature-portail-externe.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-23-feature-portail-externe.md),
[`docs/ai/design/2026-09-23-feature-portail-externe.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-23-feature-portail-externe.md),
[`docs/ai/testing/2026-09-23-feature-portail-externe.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-23-feature-portail-externe.md),
[`docs/ai/planning/2026-09-23-feature-portail-externe.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-23-feature-portail-externe.md),
[`docs/ai/implementation/2026-09-23-feature-portail-externe.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-23-feature-portail-externe.md).
