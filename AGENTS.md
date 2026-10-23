# FlowForge — consignes pour les agents

Moteur de dossiers métier piloté par des workflows BPMN/DMN : Java 25, Spring Boot 4, Flowable 8, PostgreSQL
(un schéma par tenant), Vue 3 embarqué. Règles détaillées : skill `flowforge-conventions` (`.project-skills/`).

## Où lire

| Besoin | Fichier |
|---|---|
| Comment marche un domaine | `docs/specs/NN-*.md` (index : `docs/specs/00-index.md`) |
| Pourquoi c'est ainsi, points ouverts | `docs/decisions/` — « TODO §N » = `docs/decisions/NNN-*.md` ; identifiants `D-…` : `docs/decisions/identifiants.md` |
| Quoi faire ensuite | issues GitHub (libellés `priorite-*`, `type-*`, `vague-*`) ; vision : `ROADMAP.md` |
| Lancer, déployer, utiliser | `docs/guides/` (GETTING-STARTED, DEPLOIEMENT, GUIDE-UTILISATEUR, GUIDE-CONCEPTION-BPMN) |

## Où écrire

- Comportement livré : la spec du domaine (`docs/specs/`). Décision : un point du registre (`docs/decisions/`, numéro
  suivant), puis `scripts/check-docs.sh --write-decisions-index`. Identifiant `D-…` cité dans le code : sa ligne dans
  `docs/decisions/identifiants.md`.
- `docs/ai/` : mémoire de travail de la feature **en cours** uniquement, vidée dans la PR qui la livre.
- Pas d'entrée `CHANGELOG.md` à la main : le titre de la PR (Conventional Commits) devient l'entrée à la release.

## Jamais

- Modifier une copie de skill dans `.claude/skills` ou `.cursor/skills` : modifier la source (`.project-skills/` ou
  `.agents/skills/`), puis `scripts/vendor-ai-devkit-skills.sh`.
- Créer un nouveau fichier de suivi (file d'attente, backlog, avancement) : le backlog vit dans les issues.
- Renuméroter, fusionner ou supprimer un point du registre ou un identifiant cité par le code.
- Modifier un changeSet Liquibase existant ; ignorer ou désactiver un test ; trancher en silence un point ouvert.

## Vérifier avant d'annoncer un succès

- `./mvnw verify` — hors root (PostgreSQL embarqué) ; `-Dmaven.compiler.release=21` si seul un JDK 21 est installé.
- `cd frontend && npm run lint && npm run test:all`
- `scripts/check-docs.sh` — liens, skills, références « TODO §N », identifiants, index du registre.
