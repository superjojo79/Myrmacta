# Plan de remédiation : suite de la revue du 22 octobre 2026

*Réponse à [revue-2026-10-22.md](revue-2026-10-22.md). Les constats ont été revérifiés sur le clone local de `main` (commit `e91aa7b`) le 23 octobre 2026. L'accent est mis sur la documentation (risques 1 et 2) ; les risques 3 à 5 sont traités plus brièvement en fin de document.*

## 1. Ce que la revue dit juste, ce qu'elle rate

### Confirmé

- **Volume :** 7,66 Mo de `.md` pour 2,85 Mo de Java de production.
- **Dérive du README :** `README.md` cite Flying Saucer 10.4.x et jsoup 1.23.1, alors que `pom.xml` fixe 10.6.0 et 1.23.2.
- **Mode dev :** `.env.example` définit bien `SPRING_PROFILES_ACTIVE=local`, `APP_ENVIRONMENT=local` et `APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop`.

### Manqué par la revue

- **`analysis doc/`** (49 fichiers, 1 Mo). C'est la vraie spécification fonctionnelle par domaine (`00-index.md` à `47-…`). Le nom contient un espace, d'où les liens `analysis%20doc/` dans le README et des chemins à mettre entre guillemets dans tous les scripts. 43 fichiers de code y renvoient.
- **Douze `.md` à la racine** (~850 Ko) : `TODO.md`, `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md`, `ROADMAP.md`, deux `REVUE-*.md`, deux guides, etc.
- **Les skills ne sont pas deux copies indépendantes.** `scripts/vendor-ai-devkit-skills.sh` les génère à partir de trois sources : `.project-skills/` (skills maison), le registre ai-devkit (`.ai-devkit.json`) et `.agents/skills/` (`skills-lock.json`). La source de vérité existe donc, mais rien ne la fait respecter : `.claude/skills/flowforge-conventions/SKILL.md` a été modifié à la main (la ligne « Persistence » mentionne zonky et `ChangelogChecksumStabilityIT`) et ne correspond plus ni à `.project-skills/` ni à `.cursor/skills/`.

### Recommandation de la revue qui casserait quelque chose

« Archiver les lots terminés de `TODO.md` » : **à ne pas faire tel quel.** `TODO.md` n'est pas une liste de tâches mais un **registre de décisions cité par le code** :

| Référence | Fichiers (code, tests, frontend) qui la citent |
|---|---|
| `TODO §N` (41 numéros distincts) | 623 |
| identifiants `D-XXX-YYY` de `docs/roadmap-goal/DECISIONS.md` | 318 |
| `analysis doc/` | 43 |
| `docs/ai/` | 4 |

Déplacer ou renuméroter ces sections couperait le lien entre le code et ses justifications. C'est le meilleur actif documentaire du projet ; il faut le stabiliser, pas l'archiver.

### Le vrai problème n'est pas le volume

Git gère 8 Mo sans difficulté. Ce qui coûte, ce sont trois choses :

1. **Une même information à cinq ou six endroits.** « Ce qui est livré / ce qui reste » vit à la fois dans `TODO.md`, `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md`, `ROADMAP.md`, `docs/roadmap-goal/AVANCEMENT.md` et `CHANGELOG.md`. Sur les 30 derniers jours : `CHANGELOG.md` a été modifié 47 fois, `TODO.md` 33 fois, `FILE-ATTENTE.md` 25 fois, `ROADMAP.md` 23 fois. Un merge a touché 35 fichiers `.md`. Chaque PR paie cette taxe, et chaque oubli crée une dérive.
2. **La mémoire de travail des agents est mélangée à la doc de référence.** `docs/ai/` est la sortie du workflow `dev-lifecycle` : 7 phases × ~75 fonctionnalités, presque toutes livrées. Un agent qui cherche « comment marche X » y trouve des documents de conception antérieurs au code, souvent périmés, avant la spec à jour.
3. **Aucun contrôle automatique.** Les liens tiennent par discipline (12 liens cassés sur 1 016, dont 10 dans `TODO.md` vers des fichiers `docs/ai/` déplacés), les versions du README ont déjà dérivé, et les skills aussi.

## 2. Principes cibles

1. **Une information, un seul endroit.** Les autres renvoient vers lui.
2. **Quatre types de documents, rangés séparément :**

   | Type | Rôle | Emplacement cible |
   |---|---|---|
   | Référence vivante | Décrit le système tel qu'il est | `README.md`, `docs/specs/`, `docs/guides/` |
   | Décision | Immuable, identifiant stable, citée par le code | `docs/decisions/` |
   | Suivi | Ce qui reste à faire | GitHub Issues (recommandé) ou un seul `BACKLOG.md` |
   | Mémoire de travail d'agent | Utile pendant une fonctionnalité, jetable ensuite | `docs/ai/`, vidé à la livraison |

3. **Git est l'archive.** On ne déplace pas les vieux documents dans un dossier `archive/` : on les supprime de `HEAD` derrière un tag.
4. **La CI contrôle tout ce qui peut dériver.** Si une règle n'est pas vérifiée, elle finira par être violée, surtout avec des agents.

## 3. Plan documentation, par lots

Les lots sont ordonnés pour qu'aucun ne casse le précédent. D0 et D1 sont rapides et rentables tout de suite ; D4 est le plus structurant.

### D0 : corrections immédiates (moins d'une heure)

- Corriger les 12 liens cassés (10 dans `TODO.md`, 1 dans `docs/ai/implementation/2026-08-22-feature-deploiement-bpmn.md`, 1 vers `GETTING-STARTED.md`).
- Retirer les versions de patch du `README.md` (« Flying Saucer 10.x, version exacte dans `pom.xml` »). Supprimer la donnée en double vaut mieux que la contrôler.
- Reporter la modification de `.claude/skills/flowforge-conventions/SKILL.md` dans `.project-skills/flowforge-conventions/SKILL.md`, puis relancer `scripts/vendor-ai-devkit-skills.sh`.

### D1 : garde-fous CI (environ une demi-journée)

Ajouter un script `scripts/check-docs.sh`, lancé par un job `docs` dans `.github/workflows/ci.yml`. Il vérifie quatre choses :

1. **Liens relatifs :** toute cible de lien Markdown existe (hors `.claude/`, `.cursor/`, `.agents/`).
2. **Skills générés :** `.claude/skills` et `.cursor/skills` sont identiques, et chaque skill de `.project-skills/` et `.agents/skills/` est identique à ses deux copies. Une modification faite directement dans une copie générée fait échouer la CI. On ne relance pas le script de vendor en CI : il appelle `npx ai-devkit@latest`, ce qui rendrait le contrôle dépendant du réseau et des publications en amont.
3. **Références du code :** chaque `TODO §N` trouvé dans `src/` et `frontend/src/` correspond à un titre existant du registre ; idem pour les identifiants `D-…` dans `DECISIONS.md`.
4. **Versions :** si le README garde des versions, leur majeure et leur mineure correspondent aux propriétés du `pom.xml`.

Ce lot rend tous les lots suivants sûrs : chaque déplacement de fichier sera validé par la CI.

### D2 : renommer `analysis doc/` en `docs/specs/` (environ une heure)

- `git mv "analysis doc" docs/specs`, puis réécriture des liens par script (Markdown et les 43 fichiers de code).
- Plus d'espace dans les chemins, plus de `%20`, et toute la documentation de référence sous `docs/`.

### D3 : vider la mémoire de travail des agents (environ une demi-journée)

- Poser le tag `archive/docs-ai-2026-10` sur `main`, puis supprimer de `HEAD` les fichiers `docs/ai/` des fonctionnalités livrées (≈ 3,5 Mo).
- Remplacer les liens qui y pointaient (10 dans `TODO.md`, 4 dans le code) par des permaliens GitHub vers le tag, ou mieux, vers la section correspondante de `docs/specs/`.
- **Nouvelle règle de fin de cycle,** à inscrire dans `.project-skills/flowforge-conventions/SKILL.md`. Dans la PR qui livre une fonctionnalité :
  - ce qui reste vrai remonte dans `docs/specs/NN-*.md` (section « Implémentation ») ;
  - les décisions vont dans le registre (lot D4) ;
  - les 7 fichiers `docs/ai/…-feature-x.md` sont supprimés.
- Le contrôle CI (D1) peut avertir quand `docs/ai/` contient des fichiers d'une fonctionnalité marquée livrée.

### D4 : stabiliser le registre de décisions (1 à 2 jours, le lot le plus structurant)

- **Éclater `TODO.md` en un fichier par section :** `docs/decisions/057-revue-adversariale-2026-09-07.md`, etc. Les numéros restent figés, et le statut (`ouvert`, `clos`, `remplacé par §N`) va dans l'en-tête de chaque fichier.
  - Les 623 références `TODO §N` du code restent valides sans y toucher : le contrôle D1 fait la correspondance entre §N et le fichier `NNN-*.md`.
  - Un fichier par décision supprime l'essentiel des conflits de merge sur ce document de 292 Ko.
  - Un `docs/decisions/README.md` généré par script sert d'index (numéro, titre, statut).
- **`docs/roadmap-goal/DECISIONS.md` (D-XXX) :** le mandat « roadmap complète » est clos, ce journal est donc figé. Le déplacer sous `docs/decisions/roadmap-2026.md` sans le modifier, avec une mention « figé » en en-tête.
- **`REVUE-PRE-IMPLEMENTATION.md` et `REVUE-ALIGNEMENT-SECURITE.md` :** les déplacer dans `docs/analyses/`.

### D5 : un seul registre de suivi (environ une journée, puis de l'entretien courant)

Aujourd'hui, `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md`, `ROADMAP.md` et `AVANCEMENT.md` se recouvrent largement.

- **Recommandé : GitHub Issues + un GitHub Project** pour le backlog : un ticket par lot, des libellés `vague-*` et `priorite-*`, des jalons. Les agents y accèdent par le MCP GitHub, il n'y a plus de conflit de merge, et un ticket se ferme avec la PR (`Closes #…`).
- **À défaut,** fusionner ces quatre fichiers en un seul `BACKLOG.md` qui ne contient **que** ce qui reste à faire. Ce qui est livré est déjà dans le `CHANGELOG`.
- `FILE-ATTENTE.md` fait 163 Ko alors que la file active tient en une dizaine de lignes : le reste est de l'historique, à supprimer de `HEAD`.
- **`CHANGELOG.md` :** arrêter les commits manuels `docs(changelog): entrée PR #…` et le générer à partir des titres de PR et des commits conventionnels (git-cliff ou release-please), au moment d'une release.

### D6 : point d'entrée pour les agents et ménage dans les skills (environ une demi-journée)

- **Créer un `CLAUDE.md`** (et un `AGENTS.md` pour Cursor ou Codex) d'une trentaine de lignes : où lire, où écrire, et ce qu'on ne fait jamais (modifier une copie de skill générée, ajouter un nouveau fichier de suivi, renuméroter une décision). Aujourd'hui, ce rôle est tenu par le skill `flowforge-conventions`, qui n'est chargé que s'il est déclenché ; un `CLAUDE.md` est lu à chaque session.
- **Faire l'inventaire des 28 skills.** Plusieurs supposent la CLI ai-devkit locale (`agent-communication`, `agent-management`, `memory`, `task`) et ne servent probablement pas en session cloud. Retirer ceux qui ne servent pas réduit le bruit dans le contexte des agents.
- **Si Cursor n'est plus utilisé,** supprimer `.cursor/skills` du vendor. Sinon, garder les deux copies : le contrôle D1 garantit qu'elles sont identiques.

### Structure cible

```
README.md  CHANGELOG.md  CLAUDE.md  AGENTS.md
docs/
  specs/        ← ex « analysis doc/ » (référence vivante, par domaine)
  guides/       ← GETTING-STARTED, DEPLOIEMENT, GUIDE-UTILISATEUR, GUIDE-CONCEPTION-BPMN
  decisions/    ← ex TODO.md, un fichier par §N + roadmap-2026.md (D-XXX, figé)
  analyses/     ← revues ponctuelles (REVUE-*, revue-2026-10-22, ce plan)
  ai/           ← mémoire de travail des fonctionnalités en cours uniquement
.project-skills/  ← source des skills maison ; .claude/ et .cursor/ générés, contrôlés par la CI
```

### Indicateurs de réussite

- `.md` hors skills : de ~6,4 Mo à moins de 2 Mo dans `HEAD`, sans rien perdre dans l'historique.
- Fichiers de suivi modifiés par une PR de fonctionnalité : de 4 à 6 aujourd'hui à 1 ou 2 (spec et décision).
- Zéro lien cassé, zéro dérive de skill ou de version, garanti par la CI et non plus par la discipline.

## 4. Risque 2 : le savoir dans la doc plutôt que dans le code

Le projet a déjà le bon réflexe : `ArchitectureRulesTest` et `ChangelogChecksumStabilityIT` transforment des règles écrites en tests. Il faut le généraliser :

- **Toute décision « ne jamais… » ou « toujours… » du registre doit avoir un test qui l'impose,** et ce test cite l'identifiant de la décision. Une décision sans test reste une simple intention.
- Revoir d'abord les quatre décisions marquées ⚠ en tête de `DECISIONS.md` (`D-EXPR-*`). Ce sont celles qui touchent les points fragiles cités au risque 4.
- Un registre de décisions stable (D4) et un `CLAUDE.md` (D6) réduisent aussi la dépendance au porteur unique : un nouvel intervenant, humain ou agent, sait où chercher.

## 5. Autres risques (plan court)

| Priorité | Risque | Action | Effort |
|---|---|---|---|
| **P1** | 5. Deux interrupteurs dev | Au démarrage, refuser le profil `local` quand `APP_ENVIRONMENT` ≠ `local` (dans le `EnvironmentPostProcessor` existant). Dans `.env.example`, ne plus activer `local` par défaut et fournir un `.env.local.example`. Avertissement au démarrage si l'antivirus est en `noop` hors local, ou refus en `production`. | ½ j |
| P2 | 4. Sandboxes fragiles | Tests de non-régression qui échouent si Thymeleaf ajoute un processeur non listé (on énumère les processeurs du dialecte standard et on compare à `DISABLED_PROCESSORS`), ce qui revient en pratique à une liste blanche. Test de compilation sur `AstInspector` (déjà prévu par D-EXPR-AST-BRIDGE). Un ticket par surcharge CVE du `pom.xml`, avec la version de Spring Boot qui permettra de la retirer. | 1 j |
| P2 | 3. Exploitation par tenant | Un `docker-compose.tenant.yml` paramétré, ou un chart Helm, piloté par `scripts/provision-tenant.sh`. Realm Keycloak par import déclaratif. Configuration du reverse-proxy générée. Un IT qui démarre deux tenants et vérifie qu'ils ne voient pas les données l'un de l'autre. | 3 à 5 j |
| P3 | Classes volumineuses | Découper à la prochaine modification fonctionnelle de chaque classe, pas en chantier dédié. Exception : `frontend/e2e/pilotage.spec.ts` (194 Ko), à découper par écran dès maintenant, car il ralentit le diagnostic des échecs Playwright. | au fil de l'eau |

Le risque 5 passe en P1 devant la documentation : c'est le seul où une erreur de copier-coller désactive des contrôles de sécurité en production.

## 6. Séquencement proposé

1. **Semaine 1 :** risque 5 (P1), puis documentation D0 et D1.
2. **Semaine 2 :** D2, D3 et D4 (une PR par lot, chacune validée par le contrôle D1).
3. **Semaine 3 :** D5 et D6, puis le risque 4.
4. **Ensuite :** le risque 3 avant le premier client, et les classes volumineuses au fil de l'eau.

## 7. Suivi

*Tenu par le goal [`docs/goals/remediation-revue-2026-10.md`](../goals/remediation-revue-2026-10.md). Décisions prises en cours de route : `TODO §95`.*

| Phase | Statut | Commit | Preuves | Écarts au plan |
|---|---|---|---|---|
| P0 état initial | Livré | (ce commit) | voir « État initial » ci-dessous | Le code cite 217 identifiants de décision distincts, pas seulement les 38 de `DECISIONS.md` (D-REMED-ID-INVENTORY) |
| P1 risque 5, mode dev | Livré | `01b2975` | `./mvnw verify` vert, 3 471 tests (0 échec, 5 ignorés comme en P0) ; tests du profil `embedded-db` (6) et `SignatureBrowserE2eTest` (3) verts ; `DevModeEnvironmentPostProcessorTest`, `DevModeStartupRefusalTest`, `EnvironmentPostProcessorRegistrationTest` | Défaut trouvé et corrigé : les post-processeurs des secrets et des en-têtes n'étaient jamais chargés (D-REMED-EPP-REGISTRATION ⚠). La source du skill `flowforge-conventions` est réalignée sur sa copie dans ce commit (prévu en P2) |
| P2 D0, corrections immédiates | Livré | `4113669` | `scripts/check-docs.sh links versions decision-ids skills` : 0 faute (contre 11 liens, 2 versions, 9 identifiants et 3 écarts de skill en P0) | Les 9 identifiants sans définition sont reconstitués depuis le code dans `TODO §95`. Le contrôle des liens ignore les blocs de code : le 12ᵉ « lien cassé » du plan était un motif dans un bloc de code |
| P3 D1, `check-docs` | Livré | `fd12110` | `scripts/check-docs.sh` : 5 contrôles verts en moins d'une seconde ; `--self-test` : chaque contrôle passe sur un dépôt sain et détecte la faute injectée ; job `docs` ajouté à `ci.yml` | Contrôles en Python lancé avec `-I` derrière un lanceur bash. Le registre des numéros accepte déjà la structure cible (`docs/decisions/NNN-*.md`) et les lignes de tableau (§80 à §90) ; l'index `docs/decisions/identifiants.md` remplacera la recherche dans toute la doc dès qu'il existera (P5) |
| P4 D2, `docs/specs/` | Livré | `2dcf890` + `6f4c1bd` | 49 fichiers déplacés par `git mv` ; 379 liens Markdown recalculés et 290 mentions de chemin remplacées par script ; `check-docs` vert ; dans `src/` et `frontend/`, seules des lignes de commentaire changent | Les textes qui décrivent l'état antérieur (ce plan, le goal, `TODO §95`) gardent volontairement l'ancien nom |
| P5 D3, `docs/ai/` | Livré | `0d50afc` | 473 documents retirés (`docs/ai/` : 3,5 Mo → 11 Ko, les 7 README de phase restent) ; `docs/decisions/identifiants.md` : 187 identifiants cités par le code, 0 sans définition ; 102 liens, 46 mentions et 7 commentaires deviennent des permaliens ; table fonctionnalité → spec en annexe de `docs/specs/00-index.md` ; `check-docs` vert (contrôle `decision-ids` désormais sur l'index) | Tag d'archive non poussé (refus 403 de la session) : permaliens au commit `e91aa7b` de `main` à la place (D-REMED-ARCHIVE-REF ⚠). Cinq specs complétées d'une section « Implémentation ». Le commentaire d'une ressource DMN déployée garde l'ancien chemin |
| P6 D4, `docs/decisions/` | Livré | `docs(remediation): P6` | `TODO.md` (3 660 lignes) → 95 fichiers `NNN-*.md` (93 clos, 2 ouverts : §71, §95) + 13 fichiers `section-*.md` ; index `docs/decisions/README.md` généré et contrôlé (`decisions-index`) ; 63 liens entrants réécrits (11 vers un point précis, 52 vers l'index) ; revues → `docs/analyses/`, `docs/roadmap-goal/` → `docs/decisions/roadmap-2026/`, guides → `docs/guides/` (199 liens recalculés) ; `check-docs` vert, auto-test compris | §80 à §90 n'avaient pas de section : chacun reçoit un fichier tiré de sa ligne du tableau de la Vague D. Les textes d'interface qui citent un guide par son nom ne changent pas |
| P7 D5, backlog | À faire | | | |
| P8 D6, `AGENTS.md` et skills | À faire | | | |
| P9 risque 4, sandboxes | À faire | | | |
| P10 risque 3, déploiement tenant | À faire | | | |
| P11 découpage `pilotage.spec.ts` | À faire | | | |
| P12 clôture | À faire | | | |

### État initial (P0, `main` à `e91aa7b` + PR #182)

- **Environnement d'exécution :** le bac à sable tourne en root et zonky refuse `initdb` en root. `./mvnw verify` est lancé sous l'utilisateur `ubuntu`, sur une copie de travail synchronisée, avec `-Dmaven.compiler.release=21` (seul un JDK 21 est installé ; la CI utilise le JDK 25). Playwright utilise le Chromium préinstallé (`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`), la version du navigateur attendue par `playwright-core` n'étant pas installée.
- **Backend :** `./mvnw verify` vert en 12 min 44 s (téléchargement des dépendances compris) ; 3 461 tests, 0 échec, 5 ignorés (`ClamAvAttachmentScanIT`, faute de Docker ; `SignatureBrowserE2eTest`, activé seulement par `-Dsignature.e2e=true`) ; seuils JaCoCo atteints.
- **Frontend :** `npm run lint` vert ; Vitest 739 tests (127 fichiers) ; Playwright 130 tests (2 fichiers, dont 127 dans `pilotage.spec.ts`).
- **Volume :** 5,04 Mo de `.md` suivis hors arbres de skills, 8,19 Mo au total.
- **Identifiants cités par le code** (`src/`, `frontend/src/`) :
  - `TODO §N` : 41 numéros distincts dans 650 fichiers. Tous ont une section dans `TODO.md` (titre `N.`, ou ligne du tableau §80 à §90).
  - `D-…` et `DD-…` : 217 identifiants distincts. 38 sont définis dans `DECISIONS.md`, 65 seulement dans `docs/ai/`, 101 dans d'autres `.md`, et 9 nulle part.
  - « décision n° N » : 5 numéros distincts dans le code ; même numérotation que `TODO §N`.
- **Fautes relevées par `scripts/check-docs.sh`** (écrit en P3, lancé sur cet état) : 11 liens cassés, 3 écarts entre une copie de skill et sa source, 2 versions du README incohérentes avec le `pom.xml`, 9 identifiants de décision sans définition.
