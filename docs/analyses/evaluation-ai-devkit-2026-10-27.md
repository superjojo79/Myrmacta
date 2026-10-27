# Évaluation des skills AI DevKit — 27 octobre 2026

> **Suite (2026-10-27).** Plan exécuté et décision au registre :
> [§107](../decisions/107-allegement-des-skills-ai-devkit.md). Écarts avec le plan ci-dessous, à la demande de
> l'utilisateur : `document-code` est gardé (adapté), Cursor reste un outil de travail (mêmes skills que Claude
> Code, donc `simplify-implementation` est gardé pour lui), et la question d'une mémoire projet est tranchée par une
> section « Précédents » dans `myrmacta-conventions`. Résultat : 14 skills, pas 12.

Objet : mesurer ce que le framework AI DevKit (`codeaholicguy/ai-devkit`, déclaré dans `.ai-devkit.json`) coûte et
rapporte à Myrmacta, et proposer un plan d'amélioration. Prolonge l'inventaire P8 du
[plan de remédiation du 23 octobre](plan-remediation-2026-10-23.md), qui recommandait sans rien supprimer.

## Verdict

**Les idées valent plus que l'outil.** Quatre principes du framework rapportent nettement : clarifier avant de
coder, prouver avant d'annoncer (`verify`), écrire le test d'abord (`tdd`) et garder une mémoire de travail entre
sessions pour une feature en plusieurs lots. La machinerie qui les porte coûte plus qu'elle ne rapporte : CLI
`npx ai-devkit@latest` appelée à chaque phase, base mémoire et suivi de tâches jamais alimentés, 9 phases avec
validation explicite à chacune, 7 documents par feature, gabarits génériques en anglais. Ce n'est pas le framework
du projet, et elle recouvre largement ce que Claude Code fait déjà nativement.

Recommandation : **garder les principes dans des skills appartenant au projet (`.project-skills/`), en français,
alignés sur `docs/specs`, `docs/decisions` et les issues ; retirer le reste du registre.** Résultat attendu : 12
skills au lieu de 28, plus aucun appel `npx ai-devkit` pendant une feature, sans perte de garde-fou.

## Ce qui est installé

28 skills, copiés deux fois (`.claude/skills`, `.cursor/skills`, 1,6 Mo chacun) par
`scripts/vendor-ai-devkit-skills.sh` :

| Origine | Skills | Volume (mots, fichiers annexes compris) |
|---|---|---|
| AI DevKit, workflow | `dev-lifecycle`, `dev-requirements`, `dev-design`, `dev-planning`, `dev-implementation`, `dev-testing`, `dev-review`, `dev-worktree`, `dev-commit`, `dev-pr` | ~5 600 |
| AI DevKit, transverses | `verify`, `tdd`, `structured-debug`, `security-review`, `simplify-implementation`, `document-code` | ~4 400 |
| AI DevKit, CLI locale | `memory`, `task`, `agent-management`, `agent-communication` | ~2 100 |
| antfu (frontend) | `vue`, `vue-best-practices`, `vue-testing-best-practices`, `pinia`, `vite`, `vitest` | ~43 500 |
| `npx skills add` | `playwright-best-practices` | ~80 500 |
| Projet | `myrmacta-conventions` | ~1 500 |

## Coûts mesurés

**Coût fixe par session : faible.** Seules les descriptions entrent dans le contexte de chaque session : environ
8 000 caractères pour les 28 skills, soit de l'ordre de 2 000 jetons. Les fichiers de référence (antfu, Playwright)
ne sont lus qu'à la demande. Ce n'est pas là que le framework coûte.

**Coût variable par feature : élevé.** Une feature menée par `dev-lifecycle` charge à chaque phase l'orchestrateur
(~1 000 mots), le skill de phase, `verify`, `tdd` et `task`, lance plusieurs commandes `npx ai-devkit@latest`
(`skill list`, `lint`, `lint --feature`, `task list`) et relit les documents de la feature. Sur 9 phases, c'est
un surcoût de l'ordre de la centaine de milliers de jetons lus par feature (estimation, hors cache), plus 9
allers-retours de validation.

**Volume documentaire.** Sur l'historique, 90 505 lignes ajoutées dans `docs/ai/` contre 212 425 dans `src/` et
`frontend/src/` : 0,43 ligne de document de travail par ligne de code. La remédiation du 23 octobre en a retiré
473 fichiers (3,5 Mo). Depuis, le ratio a fortement baissé : la feature #211 a produit 892 lignes dans `docs/ai/`
pour 9 958 lignes de code (9 %), et `docs/ai/` a été vidé à la livraison. La règle « mémoire de travail jetable »
fonctionne ; elle vient du projet, pas du framework.

## Constats

### 1. Une partie du framework n'a jamais servi

- **`memory`** : `~/.ai-devkit/memory.db` contient 0 entrée. Pourtant `dev-requirements`, `dev-design`,
  `structured-debug`, `security-review`, `document-code`, `verify` et `tdd` demandent tous un `memory search` ou un
  `memory store`. Ces étapes sont mortes ou échouent en silence.
- **`task`** : aucune trace d'usage ; `dev-lifecycle` le sonde à chaque démarrage. Claude Code a sa propre liste de
  tâches.
- **`agent-management`, `agent-communication`** : pilotent des agents lancés par la CLI ai-devkit. Les sessions du
  projet sont des sessions Claude Code (cloud ou bureau) ou Cursor, qui ont leurs propres mécanismes.
- **Phases `deployment` et `monitoring`** : déclarées dans `.ai-devkit.json` et présentes sous `docs/ai/`, mais
  aucun skill ne les traite. Le déploiement est documenté dans `docs/guides/DEPLOIEMENT.md`.

### 2. Les adaptations au projet ne sont pas protégées (risque réel)

Le commit `2897f97` a ajouté dans les copies de `dev-testing`, `dev-review` et `verify` la règle « preuve Playwright
obligatoire quand le frontend change ». Ces trois skills viennent du registre : `scripts/vendor-ai-devkit-skills.sh`
les recopie depuis le cache amont et ne préserve que `dev-lifecycle/scripts/check-status.sh`. Le contrôle
`check-docs.sh skills` compare `.claude` à `.cursor` et les sources locales à leurs copies, mais pas les skills du
registre à l'amont.

Conséquence : la procédure que `AGENTS.md` impose après toute modification de skill (relancer le script de vendor)
**efface sans alerte la règle Playwright**, et les deux copies restent identiques, donc la CI reste verte.

### 3. Dépendance réseau non épinglée

`.ai-devkit.json` indique la version 0.21.1, mais les skills et le script appellent `npx ai-devkit@latest`. Chaque
phase dépend donc du réseau, du registre npm et de la dernière publication d'un paquet tiers, qui s'exécute avec les
droits de la session. Le plan de remédiation l'avait déjà relevé pour la CI. Sur ce poste, le cache
`~/.ai-devkit/skills` n'existe pas : le script de vendor doit tout réinstaller avant de pouvoir tourner.

### 4. Recouvrement avec Claude Code

| AI DevKit | Équivalent natif |
|---|---|
| `task`, partie suivi de `dev-planning` | liste de tâches de la session, mode plan |
| `dev-worktree` (`.worktrees/feature-<nom>`) | worktrees gérés par l'application et `EnterWorktree` |
| `memory` | mémoire automatique de Claude Code |
| `agent-*` | sessions parallèles, sous-agents |
| `dev-review` | `/code-review` (dont `ultra`, multi-agents) |
| `simplify-implementation` | `/simplify` |
| `dev-commit`, `dev-pr` | consignes git intégrées, `gh` |

Ces doublons ne coûtent pas seulement du volume : quand deux consignes décrivent le même geste différemment (nom de
branche `feature-<nom>` contre `fix/…` ou `ccr-…` réellement utilisés), l'agent choisit au hasard ou ignore les deux.

### 5. La revue de fin de cycle est une auto-revue

Après la clôture de #211 (lot D), trois commits de correction sont venus d'une « revue adversariale » séparée
(`e79fafd`, `b1295c7`, `3c5334a`) : délais, relances, bail, audit. La phase 9 `dev-review`, conduite dans le contexte
qui venait d'écrire le code, ne les avait pas trouvés. Ce qui a fonctionné, c'est la revue indépendante.

### 6. Des validations que personne n'applique

Chaque phase exige de « proposer un plan et attendre l'accord ». En octobre, 108 commits sur 224 sont signés
Claude, pour l'essentiel en sessions cloud autonomes. Soit la règle est contournée (et une règle ignorée affaiblit
les autres), soit elle impose 9 interruptions par feature. Dans les deux cas elle ne sert pas.

### 7. Gabarits génériques, en anglais, qui doublent le registre

Les sept `README.md` de `docs/ai/` sont des gabarits génériques (« Hosting platform (AWS, GCP, Azure, etc.) »), à
l'exception de `testing/` adapté au projet. La section « Design Decisions » du document de conception double
`docs/decisions/`, qui est la vraie place d'une décision. Les skills sont en anglais, le projet écrit en français.

## Ce qui rapporte, et qu'il faut garder

| Principe | Où il vit aujourd'hui | Valeur |
|---|---|---|
| Preuve fraîche avant toute annonce ; régression vérifiée en retirant le correctif | `verify` | Élevée : peu coûteux, évite les faux « c'est fait » |
| Test qui échoue d'abord | `tdd` | Élevée, à condition de ne pas exiger de supprimer du code déjà écrit dans tous les cas |
| Cadrage : une question à la fois, options, recommandation, rien d'ouvert avant de concevoir | `dev-requirements`, `dev-design` | Élevée sur les features comme #211 ou #184 |
| Document de travail qui survit d'une session à l'autre | `docs/ai/` | Élevée pour une feature en plusieurs lots et plusieurs PR |
| Débogage par hypothèses avant correction | `structured-debug` | Moyenne |
| Grille de sécurité | `security-review` et sa liste de contrôle | Moyenne : utile pour MCP, précheck IA, multi-tenant |
| Références Vue, Vitest, Playwright | antfu, `playwright-best-practices` | Moyenne, coût nul hors usage |

## Plan d'amélioration

Chaque étape se livre seule, dans l'ordre. L'adoption du plan est une décision : elle prend un point du registre
`docs/decisions/` avant l'étape 2.

### Étape 1 — Protéger la règle Playwright (immédiat, moins d'une heure)

Déplacer les ajouts de `2897f97` hors des skills du registre, dans `.project-skills/myrmacta-conventions/SKILL.md`
(section « Preuves attendues »), puis relancer le vendor. Ajouter à `check-docs.sh` un contrôle simple : aucune
mention de Myrmacta, `mvnw` ou `frontend/e2e` dans un skill qui vient du registre. Le risque du constat 2 disparaît.

### Étape 2 — Retirer ce qui n'a jamais servi (une heure)

Retirer de `.ai-devkit.json` : `memory`, `task`, `agent-management`, `agent-communication`, `document-code`,
`simplify-implementation`, `vue` (couvert par `vue-best-practices`). Retirer les phases `deployment` et
`monitoring` et leurs gabarits. L'inventaire P8 du plan de remédiation reste tel quel : ce document le prolonge.

### Étape 3 — Un skill de feature propre au projet (une demi-journée)

Créer `.project-skills/myrmacta-feature/SKILL.md`, en français, qui remplace `dev-lifecycle` et les six skills de
phase :

- **Quatre temps** au lieu de neuf phases : cadrer (questions une à une, options, recommandation ; les points
  tranchés vont au registre), concevoir (découpage en lots, cf. le point 031 du registre), livrer par lots (TDD,
  preuve), clore (spec du domaine à jour, `docs/ai/` vidé, revue indépendante).
- **Un seul document de travail** par feature, `docs/ai/<date>-<feature>.md`, avec les sections Cadrage, Questions
  ouvertes, Lots, Preuves. Pas de copie des décisions : un lien vers le point du registre.
- **Aucune CLI** : pas de `npx`, pas de `lint`, pas de base mémoire.
- **Validation explicite** seulement là où elle compte : à la fin du cadrage et avant une décision irréversible
  (migration Liquibase, contrat d'API publique), pas à chaque phase.

Reprendre en copie possédée `verify` et `tdd` (francisés, sans les étapes mémoire et tâche), puis retirer
`dev-lifecycle`, `dev-requirements`, `dev-design`, `dev-planning`, `dev-implementation`, `dev-testing`,
`dev-review`, `dev-worktree`, `dev-commit` et `dev-pr` du registre.

### Étape 4 — Revue indépendante en clôture (une heure)

Remplacer la phase 9 par une revue dans un contexte neuf : sous-agent ou `/code-review` à effort élevé sur la
branche, `security-review` quand la feature touche l'authentification, le multi-tenant, MCP ou un appel à un
modèle. C'est ce qui a réellement trouvé les défauts de #211.

### Étape 5 — Cursor : trancher

Six commits Cursor en octobre, le dernier le 24. Si Cursor n'est plus un outil de travail, retirer `.cursor/skills`
du vendor (−1,6 Mo, une copie de moins à contrôler). Sinon, garder les deux copies, déjà contrôlées par la CI.

### Étape 6 — Mesurer après deux features

| Indicateur | Avant (#211) | Cible |
|---|---|---|
| Lignes `docs/ai/` / lignes de code | 9 % | ≤ 10 % |
| Commits de correction après la clôture d'une feature | 3 | 0 ou 1, trouvés avant la fusion |
| Appels `npx ai-devkit` par feature | ≥ 9 | 0 |
| Skills installés | 28 | 12 |
| Skills du registre modifiés localement | 3 | 0 |

Si, après deux features, les corrections tardives n'ont pas baissé ou si le document unique s'est révélé trop
pauvre pour reprendre une feature en cours, revoir l'étape 3 plutôt que réinstaller le framework.

## Ce que ce plan ne change pas

- `scripts/vendor-ai-devkit-skills.sh` reste le seul moyen de produire les copies ; il sert encore aux skills antfu,
  à Playwright et aux skills du projet.
- La règle « `docs/ai/` = feature en cours, vidé à la livraison » reste, simplifiée à un fichier.
- Aucune entrée du registre ni identifiant `D-…` n'est renuméroté.
