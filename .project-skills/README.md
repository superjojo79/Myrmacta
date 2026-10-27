# Project-local skills

Skills spécifiques à Myrmacta, hors registres AI DevKit (registre §107).

| Skill | Rôle |
|---|---|
| `myrmacta-conventions` | Conventions, workflow, commandes, précédents |
| `myrmacta-feature` | Cycle d'une feature : cadrer, concevoir, livrer par lots, clore |
| `verify`, `tdd` | Preuve avant annonce ; test d'abord |
| `structured-debug`, `security-review`, `document-code` | Débogage, revue de sécurité, documentation du code |

- Source de vérité : ce répertoire (`.project-skills/<name>/SKILL.md`)
- Distribution : `./scripts/vendor-ai-devkit-skills.sh` (ou `--local`, sans réseau) les copie vers `.cursor/skills/`
  et `.claude/skills/` ; les deux outils reçoivent les mêmes skills.

Pour ajouter un skill local : créer `.project-skills/<name>/SKILL.md`, puis relancer le script de vendor.

`verify`, `tdd`, `structured-debug`, `security-review` et `document-code` sont adaptés des skills d'AI DevKit
(<https://github.com/codeaholicguy/ai-devkit>, Apache License 2.0, <https://www.apache.org/licenses/LICENSE-2.0>).
Chacun le signale en tête et dit ce qui a été modifié.

Skills installés via `npx skills add …` vivent sous `.agents/skills/` (+ `skills-lock.json`) ; le même script de vendor
les propage aussi vers Cursor et Claude. Les skills du registre (`.ai-devkit.json`, aujourd'hui les skills Vue d'antfu)
sont recopiés tels quels : une adaptation au projet va dans un skill de ce répertoire, jamais dans leur copie
(`scripts/check-docs.sh skills` le contrôle).
