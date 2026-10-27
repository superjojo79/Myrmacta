# §107 — Allègement des skills AI DevKit et mémoire projet

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-10-27). Analyse : [evaluation-ai-devkit-2026-10-27.md](../analyses/evaluation-ai-devkit-2026-10-27.md).

**Constat.** Le framework AI DevKit apportait quatre principes utiles (cadrer avant de coder, prouver avant
d'annoncer, test d'abord, document de travail entre sessions) portés par une machinerie coûteuse : CLI
`npx ai-devkit@latest` à chaque phase, base mémoire jamais alimentée (0 entrée), suivi de tâches et pilotage d'agents
inutilisés, neuf phases avec validation à chacune, sept documents par feature. Les règles Playwright ajoutées à la
main dans trois skills du registre (`dev-testing`, `dev-review`, `verify`) étaient effacées sans alerte par
`scripts/vendor-ai-devkit-skills.sh`.

**Décisions tranchées par l'utilisateur (2026-10-27).**

- **Skills du projet plutôt que du registre.** `myrmacta-feature` remplace `dev-lifecycle` et ses neuf skills de
  phase (`dev-requirements`, `dev-design`, `dev-planning`, `dev-implementation`, `dev-testing`, `dev-review`,
  `dev-worktree`, `dev-commit`, `dev-pr`) : quatre temps (cadrer, concevoir, livrer par lots, clore), un seul
  document de travail, aucune CLI. `verify`, `tdd`, `structured-debug`, `security-review`, `document-code` et
  `simplify-implementation` deviennent des copies adaptées dans `.project-skills/` (Apache-2.0, origine citée),
  sans les étapes mémoire et tâche. `simplify-implementation`, que l'analyse proposait de retirer parce que Claude
  Code a `/simplify`, reste pour Cursor, qui n'a pas d'équivalent.
- **Retirés :** `memory`, `task`, `agent-management`, `agent-communication`, `vue` (couvert par
  `vue-best-practices`), les phases `deployment` et `monitoring` et leurs sept gabarits sous `docs/ai/`, le serveur
  MCP `@ai-devkit/memory` de Cursor et de `scripts/setup-mcp.sh`. Résultat : 14 skills au lieu de 28, dont 8
  propres au projet, 5 skills Vue d'antfu et `playwright-best-practices`.
- **`document-code` gardé** (demande de l'utilisateur), adapté : la connaissance durable va dans la spec du
  domaine, plus dans `docs/ai/implementation/knowledge-*.md`.
- **Cursor reste un outil de travail** : `.cursor/skills` reçoit exactement les mêmes skills que `.claude/skills`.
  Les consignes ne supposent donc aucune commande propre à Claude Code ; chacune donne l'équivalent Cursor.
- **Revue de clôture indépendante.** La phase 9 (auto-revue dans le contexte qui a écrit le code) disparaît ; la
  revue adversariale par un agent au contexte neuf, qui a trouvé les défauts tardifs de #211, devient la revue de
  clôture.
- **Vendor :** la version d'ai-devkit est épinglée sur `.ai-devkit.json` (`version`) au lieu de `@latest` ; le
  script retire les skills qui ne sont plus déclarés ; `--local` recopie les skills du projet sans réseau.
  `check-docs.sh skills` refuse un skill du registre qui mentionne le projet (adaptation qui serait perdue).

**Constaté pendant la mise en œuvre, reste à traiter.** Le registre `antfu/skills` ne fournit plus
`vue-best-practices` ni `vue-testing-best-practices`, désormais publiés par `vuejs-ai/skills` (voir leur `SYNC.md`).
L'installeur ai-devkit les retirait alors en silence de `.ai-devkit.json` ; le script de vendor s'arrête désormais
en erreur dans ce cas et laisse les copies intactes. Les copies actuelles (MIT, synchronisées le 2026-03-16) restent
en place ; un vendor avec réseau échouera tant que leur source n'est pas changée (par exemple
`npx skills add vuejs-ai/skills`, qui les range sous `.agents/skills/`).

**Mémoire projet.** Besoin examiné contre les documents existants :

| Besoin | Déjà couvert par |
|---|---|
| Comment marche le système | `docs/specs/` |
| Pourquoi c'est ainsi | `docs/decisions/` |
| Règles à respecter | `AGENTS.md`, `myrmacta-conventions` |
| Lancer, déployer, dépanner | `docs/guides/` (dont GETTING-STARTED § Dépannage) |
| Quoi faire ensuite | issues GitHub |
| État d'une feature en cours | `docs/ai/` |
| Faits propres à un poste | mémoire locale de l'outil (Claude Code, Cursor) |

Seul manque : les **précédents**, erreurs déjà payées par un cycle de correction, qui ne sont ni une règle
d'architecture ni un comportement du système, et que chaque session (cloud, bureau, Cursor) doit connaître. Ils
étaient dispersés (charte de la roadmap, plan de remédiation, commits). Décision : une section « Précédents » dans
`myrmacta-conventions`, versionnée et lue par tous les outils, une ligne par précédent avec sa source, quinze
lignes au plus. Un précédent qu'un test ou un contrôle peut empêcher devient ce test et sort de la liste. Écarté :
base mémoire ai-devkit (locale, non partagée, jamais alimentée), nouveau dossier `docs/memoire/` (un type de
document de plus à entretenir, risque de redevenir le `docs/ai/` d'avant la remédiation), mémoire automatique de
Claude Code seule (absente des sessions cloud et de Cursor).

**Impacte :** `.ai-devkit.json`, `.project-skills/`, `.claude/skills/`, `.cursor/skills/`, `.cursor/mcp.json`,
`scripts/vendor-ai-devkit-skills.sh`, `scripts/setup-mcp.sh`, `scripts/check_docs.py`, `AGENTS.md`, `docs/ai/`,
`docs/decisions/roadmap-2026/CHARTE.md` (§3).
