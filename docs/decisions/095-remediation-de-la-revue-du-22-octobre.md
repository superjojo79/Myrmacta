# §95 — Remédiation de la revue du 22 octobre 2026

> **Statut :** ouvert · point de premier niveau · ancien `TODO.md` §95 · [registre](README.md)

[ ] en cours (branche `claude/eager-galileo-4i5w0g`, PR #182). Exécution du plan
[docs/analyses/plan-remediation-2026-10-23.md](../analyses/plan-remediation-2026-10-23.md) par le goal
[docs/goals/remediation-revue-2026-10.md](../goals/remediation-revue-2026-10.md). Le suivi phase par phase est tenu
dans la section « Suivi » du plan ; ce point consigne les décisions prises en cours de route.

**D-REMED-ID-INVENTORY** — L'inventaire de P0 élargit le périmètre des identifiants protégés. Le code cite
217 identifiants de décision distincts (`D-…` et `DD-…`), et pas seulement ceux de `docs/decisions/roadmap-2026/DECISIONS.md`
(38) : 65 ne sont définis que dans `docs/ai/`, 101 dans d'autres `.md` (`TODO.md`, `analysis doc/`, `FILE-ATTENTE.md`…)
et 13 ne sont définis nulle part. Supprimer `docs/ai/` (P5) ou `FILE-ATTENTE.md` (P7) sans précaution rendrait
orphelines des références du code. Choix : avant toute suppression, un index des identifiants
(`docs/decisions/identifiants.md`, une ligne par identifiant cité : énoncé court, source) est extrait des documents
existants, et `check-docs` exige que chaque identifiant cité par le code y figure. Réversible (simple fichier généré
puis relu).

**D-REMED-EPP-REGISTRATION** ⚠ — Constat de P1 : `SecretsEnvironmentPostProcessor` (D-43) et
`SecurityHeadersEnvironmentPostProcessor` (§64) n'étaient déclarés que dans
`META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor`, un fichier que Spring Boot ne lit
pas : depuis leur création (2026-09-03 et 2026-09-21), leurs refus au démarrage ne s'exécutaient jamais en
dehors des tests unitaires. Corrigé : déclaration dans `META-INF/spring.factories` (seul emplacement lu, comme
les trois post-processeurs qui fonctionnaient déjà), fichier mort supprimé, et
`EnvironmentPostProcessorRegistrationTest` fait échouer le build si un post-processeur de production n'est pas
chargé. Effet : une instance démarrée hors profils `local`/`test` avec un mot de passe de la denylist, sans
`APP_CONNECTOR_MASTER_KEY`, avec un HMAC M2M ou avec les en-têtes désactivés refuse désormais de démarrer, comme
le documentent `docs/guides/DEPLOIEMENT.md` §2 et le point 43. À relire : toute instance existante qui démarrait grâce à
cette absence échouera au prochain démarrage.

**D-REMED-DEVMODE** — `app.environment` (APP_ENVIRONMENT, défaut `production`) devient le seul interrupteur
« mode dev » (`DevModeEnvironmentPostProcessor`) : le profil `local` exige `APP_ENVIRONMENT=local` ; le profil
`test` est refusé seulement en `production`, parce que la suite de tests exerce aussi `staging`
(`McpDirectPublicationIntegrationTest`). Options écartées : refuser `test` hors `local` (casse un test légitime),
ou supprimer le relâchement par profil des deux autres post-processeurs (changement plus large, sans gain tant
que les profils sont verrouillés par `app.environment`). Réversible.

**D-REMED-NOOP-ACK** ⚠ — Antivirus `noop` (défaut) : refusé au démarrage quand `APP_ENVIRONMENT=production`,
sauf `app.dossier.attachment.scan.noop-acknowledged=true` (`APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED`,
défaut `false`) ; simple avertissement en `staging` ; silencieux en `local`. Un acquittement explicite plutôt
qu'un refus sans issue : un tenant sans portail ni pièce jointe externe peut assumer l'absence d'antivirus,
mais il doit l'écrire. À relire : une production sans ClamAV doit désormais poser cet acquittement.

**D-REMED-ENV-TEMPLATES** — `.env.example` devient le gabarit d'un environnement réel (`APP_ENVIRONMENT=production`,
profil `prod`, secrets vides donc démarrage refusé tant qu'ils ne sont pas renseignés, ClamAV) ; l'ancien contenu,
calibré pour le dev, devient `.env.local.example` (`cp .env.local.example .env`). Le nom historique désigne ainsi
le gabarit le moins dangereux à copier par erreur. Réversible.

**Identifiants cités par le code sans définition (reconstitués en P2 depuis les commentaires du code).** Ces
décisions avaient été prises sans être écrites ailleurs que dans le code ; elles sont consignées ici telles que le
code les décrit, sans changement de comportement.

- **D-PALETTE-FALLBACK** (§73) — catalogue des briques indisponible : l'éditeur BPMN retombe sur la palette et le
  panneau natifs de bpmn-js, sans briques métier.
- **D-PALETTE-NATIVE** (§73) — la palette native bpmn-js reste intégralement disponible ; les briques métier
  s'ajoutent dans un groupe « Briques métier ».
- **D-PALETTE-PANEL** (§73) — le groupe « Brique » du panneau de propriétés (champs typés du catalogue) s'insère
  après « General » ; le groupe « Flowable » devient « Avancé », replié pour une brique reconnue.
- **D-PALETTE-STATUSES** (§73) — les statuts de workflow proposés par les briques viennent de la configuration du
  process ouvert dans l'éditeur.
- **D-BI-SFTP-CONN-FAILURE** (connecteur `batch-import`, lot B1) — un échec de connexion ou d'authentification SFTP
  sort de `BatchImportConnector.onEvent` en `SftpConnectionException` ; le job de déclenchement décide de
  poursuivre avec les autres instances du même cycle.
- **D-CALLBACK-BRUTEFORCE** (revue adversariale) — secret partagé des webhooks et callbacks d'au moins
  20 caractères (`WebhookSecretPolicy.MIN_LENGTH`) : ces routes sont anonymes et exposées, un secret court se
  devine malgré la limite de débit.
- **D-CALLBACK-DOS** (revue adversariale) — corps des webhooks borné avant lecture : refus sur `Content-Length`
  déclaré trop grand, et flux d'entrée limité en octets pour un corps sans longueur (chunked) ou mensonger
  (`WebhookBodySizeLimitFilter`).
- **D-CSP-REPORTING** (§64) — collecte anonyme des rapports CSP : liste blanche de champs journalisés tronqués,
  jamais le corps brut, réponse toujours 204.
- **D-SIGNATURE-ACTIVATION** (§72) — sans fournisseur de signature (`app.signature.provider=none`), aucun use case,
  delegate, job ni route de signature n'est créé.

**D-REMED-ARCHIVE-REF** ⚠ — Le goal prévoyait un tag `archive/docs-pre-remediation-2026-10` poussé avant toute
suppression. Le push du tag est refusé par la politique de la session (HTTP 403 : seule la branche de développement
peut être poussée) ; il n'a pas été contourné. Choix : les contenus retirés pointent vers des permaliens au commit
`e91aa7b` de `main` (`https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/…`),
qui contient `docs/ai/`, `TODO.md`, `FILE-ATTENTE.md` et `DEFERRED-FEATURES.md` complets. Un commit de `main` reste
accessible quel que soit le mode de merge de la PR, ce qui est la garantie recherchée. La numérotation des lignes
est identique (P4 n'a modifié `docs/ai/` que ligne à ligne), si bien que les ancres `#Lnn` restent justes. À faire
par l'utilisateur, s'il veut aussi le tag : `git tag -a archive/docs-pre-remediation-2026-10 e91aa7b -m "…"` puis
`git push origin archive/docs-pre-remediation-2026-10`.

**D-REMED-DOCS-AI** — `docs/ai/` : les 473 documents de travail des 74 fonctionnalités livrées sont retirés
(aucun cycle en cours, d'après `FILE-ATTENTE.md`) ; les sept `README.md` de phase (modèles ai-devkit) restent, et
le goal `goal-outillage-podman-windows.md` rejoint `docs/goals/`. Avant suppression : index
`docs/decisions/identifiants.md` (187 identifiants cités par le code, chacun avec son énoncé et sa source),
correspondance fonctionnalité → spec, et section « Implémentation » ajoutée aux specs pour les cinq fonctionnalités
qu'aucune ne couvrait (en-têtes HTTP → `03`, correction des champs et antivirus → `04`, contrat OpenAPI et
pagination admin → `11`, tables DMN de la palette → `28`). Les liens et mentions vers ces documents deviennent des
permaliens (D-REMED-ARCHIVE-REF). Exception : le commentaire XML de
`src/main/resources/decisions/grille-eligibilite-client.dmn` garde l'ancien chemin, pour ne pas modifier une
ressource déployée dans Flowable (un contenu modifié crée une nouvelle version de déploiement).

**D-REMED-REGISTRY** — `TODO.md` (292 Ko, 3 660 lignes) devient `docs/decisions/` : un fichier par point
numéroté (95 fichiers, dont §80 à §90 reconstitués depuis le tableau de la Vague D), et un fichier `section-*.md`
par section de l'ancien registre, qui garde son texte d'introduction et la liste de ses points. Les numéros sont
inchangés : les références « TODO §N » et « décision n° N » du code et de la doc ne sont pas modifiées, et
`check-docs` (`todo-refs`) les résout vers `docs/decisions/NNN-*.md`. Les liens qui pointaient vers `TODO.md`
mènent au point précis quand le texte nomme son numéro, sinon à l'index `docs/decisions/README.md`, généré par
`scripts/check-docs.sh --write-decisions-index` et contrôlé (`decisions-index`). Pas de fichier de redirection
`TODO.md` : un point se cherche par son numéro. Le même lot range les documents figés : le journal du mandat
« roadmap complète » (`docs/roadmap-goal/`) dans `docs/decisions/roadmap-2026/`, les deux revues
(`REVUE-*.md`) dans `docs/analyses/`, et les quatre guides dans `docs/guides/`. Aucun build, test, Dockerfile ni
écran ne lit ces fichiers ; les textes de l'interface qui citent un guide par son nom (`frontend/src/i18n`) restent
inchangés. Réversible (`git mv`).

**D-REMED-BACKLOG** — Paramètre Backlog = `issues`. Ce qui restait réellement à faire dans `FILE-ATTENTE.md`,
`DEFERRED-FEATURES.md` et `ROADMAP.md` devient 12 issues GitHub (#183 à #194, libellés `vague-riverain`,
`priorite-2/3/4`, `type-feature/arbitrage/ops/hors-v1`), après recherche de doublons (le dépôt n'en avait aucune).
Une ligne par lot du backlog riverain ; les sujets écartés de la V1 sont regroupés dans une seule issue-inventaire
(#194) plutôt qu'en dix issues sans perspective. Les follow-ups déjà livrés ou rendus caducs par le code (activation
du `context-path`, tests sur vrai PostgreSQL, chaînes M2M…) ne sont pas repris. Les points ouverts qui sont des
décisions (§71) restent dans le registre. `FILE-ATTENTE.md` (163 Ko, dont une dizaine de lignes actives) et
`DEFERRED-FEATURES.md` sont retirés ; leurs liens deviennent des permaliens (D-REMED-ARCHIVE-REF).

**D-REMED-ROADMAP** — `ROADMAP.md` garde un rôle de vision produit (ce qui est livré, les prochaines étapes, le
hors V1) : réduit à une page qui renvoie aux issues ; la version détaillée reste accessible par permalien.

**D-REMED-CHANGELOG** — Plus d'entrée `CHANGELOG.md` écrite à la main dans chaque PR : à chaque release,
`scripts/changelog-release.sh <version>` génère la section avec git-cliff (`cliff.toml`, lancé par `npx`, sans
installation), une entrée par PR tirée de son titre (commit « Merge pull request #N » ou squash « (#N) »), la
catégorie venant du type Conventional Commits. Les entrées existantes, tenues à la main jusqu'à la PR #182, sont
conservées sous « Historique tenu à la main ». Option écartée : release-please, qui impose un flux de PR de release
et des versions sémantiques automatiques, disproportionné pour un projet sans release publiée. Réversible.

**D-REMED-AGENTS** — `AGENTS.md` (35 lignes : où lire, où écrire, interdits, vérifications) devient le point
d'entrée de tout agent ; `CLAUDE.md` se limite à `@AGENTS.md`, pour qu'une seule source serve Claude Code, Cursor
et Codex. Le skill `flowforge-conventions` garde le détail et renvoie à `AGENTS.md`. Inventaire des 28 skills
(paramètre Skills = `proposer` : recommandation seulement, rien n'est supprimé) dans la section Suivi du plan.
