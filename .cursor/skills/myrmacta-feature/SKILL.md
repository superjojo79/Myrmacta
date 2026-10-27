---
name: myrmacta-feature
description: Myrmacta · Cycle d'une feature en quatre temps (cadrer, concevoir, livrer par lots, clore) avec un document de travail unique dans docs/ai et une revue indépendante. À utiliser pour démarrer, reprendre ou clore une feature ou une issue GitHub de ce dépôt.
---

# Myrmacta — cycle d'une feature

Complète `myrmacta-conventions`, à lire d'abord. Vaut pour Claude Code comme pour Cursor : aucune étape ne suppose
un outil propre à l'un des deux, et aucune ne passe par la CLI ai-devkit (décision : `docs/decisions/107-*.md`).

## Démarrer ou reprendre

- **Point de départ :** l'issue GitHub, la spec du domaine (`docs/specs/`) et les points ouverts liés
  (`docs/decisions/`).
- **Reprise :** lire `docs/ai/AAAA-MM-JJ-<feature>.md`. Sa section « Lots » dit où on en est et quelle est la
  prochaine action. Pas de document : le cadrage n'est pas fait.
- **Branche :** une par PR, jamais de travail direct sur `main`. Deux agents ne travaillent pas en même temps sur
  des lots qui écrivent dans les mêmes fichiers.

## Le document de travail

Un seul fichier par feature : `docs/ai/AAAA-MM-JJ-<feature>.md`, sur le gabarit de `docs/ai/README.md`. Il sert à
reprendre la feature dans une autre session ou un autre outil. Une décision n'y est pas recopiée : elle va au
registre, le document met un lien. Il est supprimé dans la PR qui clôt la feature.

## 1. Cadrer

1. Poser les questions **une à la fois** : pourquoi elle compte, deux ou trois options, une recommandation.
2. Couvrir : problème, utilisateurs, périmètre et hors-périmètre, critères de succès, contraintes (stack figée,
   multi-tenant, sécurité), exposition MCP, documentation à mettre à jour.
3. Ne pas passer à la conception tant qu'une question qui change le résultat reste ouverte. Elle peut être
   reportée explicitement ou devenir une hypothèse nommée.
4. Si un mandat délègue les décisions ouvertes (par exemple la charte `docs/decisions/roadmap-2026/CHARTE.md`),
   l'agent tranche, mais toujours en le traçant au registre, jamais en silence.
5. Restituer la compréhension commune, écrire le document de travail, puis **attendre la validation de
   l'utilisateur** (sauf mandat). C'est l'une des deux seules pauses obligatoires du cycle.

## 2. Concevoir

1. Vérifier chaque fait contre le code et les sources des bibliothèques : une API supposée doit exister.
2. Chaque objectif et critère de succès du cadrage a sa réponse ; signaler ce qui n'est pas couvert.
3. Découper en lots (registre §31) : chaque lot se livre seul, en une PR, tests verts.
4. Les décisions structurantes deviennent des points du registre (`NNN-*.md`, numéro suivant), puis
   `scripts/check-docs.sh --write-decisions-index`.
5. Dériver les scénarios de test des critères de succès : unitaires, intégration, Playwright si le SPA ou un
   contrat qu'il consomme change, et au moins un test qui traverse la vraie chaîne HTTP et sécurité pour tout
   nouvel endpoint ou outil MCP.

## 3. Livrer par lots

1. `tdd` pour chaque comportement, `verify` avant toute annonce.
2. Chercher l'existant avant d'écrire un utilitaire ou un composant.
3. **Pause obligatoire avant une décision irréversible** (sauf mandat) : migration qui transforme ou supprime des
   données, rupture d'un contrat d'API ou MCP publié, nouvelle dépendance d'exécution.
4. À la fin de chaque lot : sections « Lots » et « Preuves » du document à jour, preuves fraîches
   (`./mvnw verify` ; `cd frontend && npm run lint && npm run test:all` si le frontend change ;
   `scripts/check-docs.sh`), commit et PR. Le titre de PR suit Conventional Commits : il devient l'entrée du
   `CHANGELOG.md`.

## 4. Clore

1. **Revue indépendante**, dans un contexte qui n'a pas écrit le code :
   - Claude Code : un sous-agent (modèle le plus capable disponible) ou `/code-review` sur la branche ;
   - Cursor : un nouveau chat d'agent, sans l'historique, à qui l'on donne la grille et `git diff main...HEAD`.

   Grille : contrôle d'accès, isolation du tenant, injections, SSRF, déni de service mémoire ou CPU, secrets,
   surface d'expression (allowlist), ruptures de contrat, affirmations sans test, écart avec la spec et le
   registre. Ajouter `security-review` dès que la feature touche l'authentification, le multi-tenant, MCP, un appel
   à un modèle ou des fichiers déposés.
2. Corriger en TDD, avec un test de régression, tout constat critique, haut ou moyen ; ouvrir une issue pour les
   bas. Après un constat critique ou haut, seconde revue indépendante.
3. Documentation de clôture (`myrmacta-conventions`, workflow points 6 à 8) : section « Implémentation » de la
   spec du domaine, guides, `.env*.example`, `docs/decisions/identifiants.md`.
4. Supprimer le document de travail ; `scripts/check-docs.sh` vert. La PR ferme l'issue (`Closes #N`).
5. Si la revue ou un correctif a révélé une erreur qui a coûté un cycle et qu'aucun test ni contrôle ne peut
   empêcher, ajouter une ligne à « Précédents » dans `.project-skills/myrmacta-conventions/SKILL.md`.

## Ce que ce cycle ne demande pas

- Pas de validation à chaque étape : seulement à la fin du cadrage et avant une décision irréversible.
- Pas de document par phase, pas de base mémoire, pas de suivi de tâches hors de l'outil utilisé.
- Pas d'auto-revue tenue pour une revue : elle ne remplace pas l'étape 4.1.
