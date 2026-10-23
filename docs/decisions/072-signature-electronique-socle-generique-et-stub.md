# §72 — Signature électronique — socle générique et stub (livré)

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §72 · [registre](README.md)

**Livré (2026-09-29).** Phases 1 à 9 closes, analyse adversariale par sous-agent comprise. **Mergée sur `main`** le 2026-09-29 ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), squash `1c839ff`), CI verte (`build` + `frontend`). Preuves : `./mvnw verify` → **2571 tests, 0 échec** ; Vitest 424 ; Playwright 98 ; test de bout en bout réel navigateur + backend (`SignatureBrowserE2eTest`). Seul fournisseur livré : `stub` (simulation, sans valeur juridique) ; un fournisseur réel et eIDAS restent à cadrer. Restent ouverts (voir [`docs/ai/implementation/2026-09-29-feature-signature-electronique.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-29-feature-signature-electronique.md)) : S-EMIT-10 sur PostgreSQL réel, vérifications manuelles T14.4, tests de concurrence à deux transactions réelles, parcours internes en e2e réel (session OIDC).

**Énoncé :** item **2.4** de `ROADMAP.md`, analyse
[30-signature-electronique.md](../specs/30-signature-electronique.md), recalibrée le
2026-09-29 : aucun fournisseur désigné, le lot pose le contrat, le modèle, le parcours et un
adaptateur **stub** qui simule le cycle complet, pour qu'un provider réel se branche ensuite par un
adaptateur et une configuration. Prérequis livré : portail externe (§68, PR #138).

**Statut :** **Phase 1 close (2026-09-29)** — décisions tranchées une par une par l'utilisateur
(`AskUserQuestion`) : D-SIGNATURE-ABSTRACTION = port `SignaturePort` + adaptateur exposé comme
connecteur mince ; D-SIGNATURE-STUB-BEHAVIOR = simulation de bout en bout marquée « SIMULATION —
sans valeur juridique » ; D-SIGNATURE-UX = tâche externe du portail + statut dans le dossier ;
D-SIGNATURE-TRIGGER = nouveau delegate allowlisté ; D-SIGNATURE-TRIGGER-VS-CONNECTOR = delegate →
port direct (le connecteur ne porte que configuration, secrets et retour `CALLBACK`) ;
D-SIGNATURE-PORTAL-KIND = **nouveau type de tâche externe « signature »** (contre la
recommandation de réutiliser `user-question` : rouvre le périmètre du portail, risque R-PORTAIL
assumé, revue de sécurité du portail à rejouer sur ce type) ; D-SIGNATURE-ELEMENT = `PRODUCED_DOC`
marqué, aucune nouvelle valeur d'`ElementType` ; D-SIGNATURE-STUB-GUARD = **refus de démarrer en
profil production, activable par un second drapeau explicite** (contre la recommandation de refus
strict : risque R-SIM-EN-PROD assumé, avertissement et marquage inchangés) ;
D-SIGNATURE-CONTRACT-CHECK = revue documentaire de 2 à 3 API publiques, sans intégration.
Reportés au premier provider réel : D-SIGNATURE-PROVIDER, D-SIGNATURE-LEVEL (eIDAS).

**Phase 2 (revue) close (2026-09-29)** — 11 décisions supplémentaires tranchées une par une par
l'utilisateur : D-SIGNATURE-ORDER = **modes parallèle et séquentiel** au choix du BPMN (contre la
recommandation parallèle seul) ; D-SIGNATURE-REQUIRED = **signataires requis ou facultatifs**
(SIGNED = tous les requis) ; D-SIGNATURE-SEQ-OPTIONAL = un facultatif qui refuse fait passer au
suivant ; D-SIGNATURE-OUTCOME = variable de process `signatureOutcome` ; D-SIGNATURE-DOCUMENT = un
seul `PRODUCED_DOC` désigné par variable ; D-SIGNATURE-DEADLINE = échéance de demande unique, pas
de relance automatique ; D-SIGNATURE-SIGNER-KIND = **internes et externes dès ce lot** (contre la
recommandation externes seuls) ; D-SIGNATURE-INTERNAL-UX = vue « Mes signatures » dans la SPA ;
D-SIGNATURE-SIGNER-SOURCE = utilisateur désigné par champ / variable ; D-SIGNATURE-PROOF = PDF de
preuve filigrané ; D-SIGNATURE-DELIVERY = un lot XL à jalons internes. Hypothèses A1–A5 acceptées
(voir requirements). Documents SDD : requirements `reviewed`, design / planning / testing `draft`
([requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-29-feature-signature-electronique.md)).

**Phase 3 (design) close (2026-09-29)** — 4 décisions tranchées par l'utilisateur :
D-SIGNATURE-DATA-MODEL = **tables dédiées** (le portail exige une `userTask` ouverte, F1) ;
D-SIGNATURE-BPMN-MARKER = attributs `flowable:*` + éléments `flowable:signer` validés au
déploiement ; D-SIGNATURE-GUARD-CRITERION = **liste positive** de profils (`local`/`test` sans
drapeau, sinon `app.signature.stub.allow-outside-local`) ; D-SIGNATURE-MCP = lecture + annulation
avec `dryRun`. Plus 11 décisions par alignement sur l'existant (dont D-SIGNATURE-DISPATCH,
répartiteur avec reprise). Trois précisions apportées aux requirements (doublons, référence du
document par `storageRef`, expiration propre à la demande). Design `reviewed`. Reste : **M0** fige la
forme finale de `SignaturePort` ; **Phase 4** (planning).

**Phase 4 (planning) close (2026-09-29)** : 19 milestones, environ 80 tâches, 94 scénarios de test
identifiés (`S-<groupe>-<n>`) et tous couverts (contrôle croisé automatisé). Trois jalons de
livraison internes dans une PR (J1 externes / parallèle / requis, J2 séquentiel et facultatifs, J3
internes), contrat et tables posés en entier dès M1. Chemin critique : M0 (revue documentaire, gel de
`SignaturePort`) → M1 → M3 → M4 → M5 → M6 → M8b. Risque technique à lever d'abord en M3 : le spike
F7 (API OpenPDF via Flying Saucer). Prochaine étape : **Phase 5 (implémentation)**, à démarrer par M0
(avec l'utilisateur pour le choix des API de référence).

**Ancienne liste « à figer », levée par la Phase 3 :** propriété d'activation et second drapeau (noms, défauts, jamais dans
les gabarits de configuration), forme du marqueur `PRODUCED_DOC`, marqueur BPMN de la tâche
« signature », critère de « profil de production ».

**Impacte :** `AllowedBpmnDelegates` (§57/§69), `ConnectorCatalog`, portail (`/portal/**`), purge de
dossier, golden OpenAPI, tools MCP (principe P1 de l'analyse 46), `docs/guides/GUIDE-CONCEPTION-BPMN.md`,
`docs/guides/DEPLOIEMENT.md` (propriétés d'activation), `CHANGELOG.md`.
