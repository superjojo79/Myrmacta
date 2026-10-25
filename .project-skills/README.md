# Project-local skills

Skills spécifiques à Myrmacta, hors registres AI DevKit. Le dossier du skill reste `flowforge-conventions` (nom de code).

- Source de vérité : ce répertoire (`.project-skills/<name>/SKILL.md`)
- Distribution : `./scripts/vendor-ai-devkit-skills.sh` les copie vers `.cursor/skills/` et `.claude/skills/`

Pour ajouter un skill local : créer `.project-skills/<name>/SKILL.md`, puis relancer le script de vendor.

Skills installés via `npx skills add …` vivent sous `.agents/skills/` (+ `skills-lock.json`) ; le même script de vendor les propage aussi vers Cursor et Claude.
