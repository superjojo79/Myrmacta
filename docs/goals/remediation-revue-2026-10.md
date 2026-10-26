# Goal : remédiation de la revue du 22 octobre 2026

> Usage : dans une session Claude Code ouverte à la racine du dépôt, lancer la commande donnée dans la section [Commande](#commande).
>
> Le prompt ci-dessous est la source de vérité. Le modifier dans ce fichier plutôt que dans le chat. Relire d'abord la section « Paramètres » : ce sont les deux choix laissés à l'utilisateur.

## Commande

```
/goal Exécute le goal décrit dans docs/goals/remediation-revue-2026-10.md (section « Prompt »), qui fait foi. TERMINÉ quand : chaque phase P0 à P12 est Livré, Bloqué (motif) ou Non traité (motif) dans la section « Suivi » de docs/analyses/plan-remediation-2026-10-23.md, aucune n'étant À faire ou En cours ; ET, sur le HEAD final, les sorties fraîches de `./mvnw verify`, de `cd frontend && npm run test:all` et de `scripts/check-docs.sh` sont affichées avec 0 échec ; ET les indicateurs de réussite du plan sont mesurés et reportés ; ET tout est poussé sur la branche de développement.
```

## Paramètres (à valider avant de lancer)

- **Backlog :** `issues`. Les éléments restant à faire sont migrés vers des GitHub Issues. Valeur alternative : `fichier` (un seul `BACKLOG.md` à la racine).
- **Skills :** `proposer`. L'inventaire des skills produit une recommandation, sans rien supprimer. Valeur alternative : `supprimer` (retrait des skills jugés inutiles de `.ai-devkit.json` et des deux copies).

## Prompt

Implémenter le plan de remédiation `docs/analyses/plan-remediation-2026-10-23.md`, qui répond à la revue `docs/analyses/revue-2026-10-22.md`. Le plan fait foi pour le contenu des lots ; ce prompt fixe l'ordre, les contraintes et les preuves attendues.

### Contexte (vérifié dans le dépôt le 23 octobre 2026)

- 7,66 Mo de `.md`, dont `docs/ai/` (481 fichiers, sortie du workflow ai-devkit `dev-lifecycle`), `analysis doc/` (49 fichiers, spec par domaine, nom avec espace), douze `.md` à la racine, et trois arbres de skills.
- **Le code cite la documentation par identifiants :**
  - `TODO §N` dans 623 fichiers (41 numéros distincts), qui renvoie aux titres `### [x] N.` de `TODO.md` ;
  - `D-XXX-YYY` dans 318 fichiers, qui renvoie à `docs/roadmap-goal/DECISIONS.md` ;
  - `analysis doc/` dans 43 fichiers et `docs/ai/` dans 4 ;
  - le README cite aussi des « décision n° N ».

  Ces identifiants sont le lien entre le code et ses justifications.
- **Liens :** 12 liens Markdown relatifs cassés sur 1 016, dont 10 dans `TODO.md` vers `docs/ai/`.
- **Skills :** `scripts/vendor-ai-devkit-skills.sh` génère `.claude/skills` et `.cursor/skills` à partir de `.ai-devkit.json` (registre, via `npx ai-devkit@latest`, donc avec accès réseau), de `.project-skills/` et de `.agents/skills/`. `.claude/skills/myrmacta-conventions/SKILL.md` a été modifié à la main et diffère de la source.
- **Mode dev :** `.env.example` fixe `SPRING_PROFILES_ACTIVE=local`, `APP_ENVIRONMENT=local` et `APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop`. `SecretsEnvironmentPostProcessor` et `SecurityHeadersEnvironmentPostProcessor` se relâchent sur le profil `local`, alors que la gouvernance MCP dépend d'`APP_ENVIRONMENT` (défaut `production`).
- **Tests :** tests sur PostgreSQL embarqué zonky (non-root requis, sinon `TEST_PG_URL` ; voir `GETTING-STARTED.md`). Docker n'est pas utilisable dans le bac à sable.

### Contraintes non négociables

- **Conventions :** respecter le skill `myrmacta-conventions` (hexagonal, ArchUnit vert). Tout changement de code Java ou TypeScript se fait en TDD. Lancer `verify` avant toute affirmation de succès.
- **Identifiants :** ne jamais renuméroter, fusionner ni supprimer un identifiant cité par le code ou la doc (`TODO §N`, `D-XXX`, « décision n° N », points 1 à 46 des revues). Avant P6, inventorier **tous** les schémas d'identifiants en usage ; s'il en existe un autre, il reçoit le même traitement.
- **Déplacements :** tout déplacement se fait par `git mv`, pour garder l'historique. Les liens sont réécrits par script, jamais à la main, et `scripts/check-docs.sh` doit être vert à la fin de chaque phase à partir de P3.
- **Suppressions :** aucune suppression de documentation de `HEAD` avant que le tag `archive/docs-pre-remediation-2026-10` soit posé sur le commit qui la précède et poussé (`git push origin <tag>`). Les liens vers un contenu supprimé deviennent des permaliens `https://github.com/superjojo79/FlowForge/blob/archive/docs-pre-remediation-2026-10/<chemin>`, ou pointent vers la section de spec qui le remplace.
- **Documents figés :** les documents marqués « figés » (journal `D-XXX`, mandat roadmap clos, revues) ne reçoivent que des réécritures mécaniques de liens.
- **Skills :** ne jamais modifier une copie dans `.claude/skills` ou `.cursor/skills`. On modifie la source (`.project-skills/` ou `.agents/skills/`), puis on relance `scripts/vendor-ai-devkit-skills.sh`. Si le script ne peut pas tourner (réseau), on reproduit son effet **uniquement** pour les skills locaux (`cp -a` de la source vers les deux copies) et on le note dans le Suivi.
- **Interdits :** ne jamais modifier un changeSet Liquibase existant ; ne jamais ignorer, désactiver ni mettre en quarantaine un test ; ne pas changer de comportement fonctionnel en dehors de P1, P9 et P10.
- **Décisions :** créer en P0 un point `TODO §95 Remédiation revue 2026-10-22`, qui devient `docs/decisions/095-*.md` en P6, et y consigner chaque décision au moment où elle est prise (options, choix, justification, réversibilité, ⚠ si elle mérite une relecture humaine). Pour trancher : d'abord la cohérence avec les décisions déjà posées, ensuite la simplicité d'entretien pour un mainteneur seul assisté d'agents.
- **Git :** un commit par phase, au format Conventional Commits, poussé à la fin de chaque phase sur la branche de développement courante. Aucune PR ni merge.
- **Suivi :** tenir à jour à chaque phase une section « Suivi » ajoutée à la fin de `docs/analyses/plan-remediation-2026-10-23.md` : un tableau phase / statut / commit / preuves / écarts au plan.
- **Blocage :** une phase impossible sans enfreindre une contrainte est marquée « Bloqué » avec son motif ; passer à la suivante.

### Phases (dans cet ordre)

**P0, état initial.**
- Relever : la durée et le résultat de `./mvnw verify` et de `cd frontend && npm run test:all` ; la taille des `.md` hors skills dans `HEAD` ; les liens cassés ; le nombre de fichiers qui citent chaque schéma d'identifiants.
- Ajouter la section Suivi et le point `TODO §95`.

**P1, risque 5 : un seul interrupteur dev.**
- Refus au démarrage si le profil `local` est actif alors que l'`APP_ENVIRONMENT` résolu n'est pas `local`, avec un message clair, dans le style de `SecretsEnvironmentPostProcessor`. Vérifier le cas du profil `test` et consigner la règle retenue.
- `.env.example` devient un modèle de production : `APP_ENVIRONMENT=production`, pas de profil `local`, secrets à remplir (le démarrage échoue tant qu'ils ne le sont pas). Créer `.env.local.example` pour le dev, et mettre à jour `GETTING-STARTED.md`, `DEPLOIEMENT.md` et toute référence à `cp .env.example .env`.
- Antivirus `noop` : avertissement au démarrage en `staging`, refus en `production` sauf propriété d'acquittement explicite (nom et défaut à consigner).
- Tests unitaires et d'intégration pour chaque cas.

**P2, D0.**
- Réparer les 12 liens cassés.
- Retirer du README les versions de patch, en renvoyant au `pom.xml`.
- Reporter dans `.project-skills/myrmacta-conventions/SKILL.md` la modification faite à la main dans `.claude/skills`, puis propager.

**P3, D1 : `scripts/check-docs.sh` et un job `docs` dans `.github/workflows/ci.yml`.**
- Contraintes du script : rapide (moins de 30 s), hors ligne, en bash (python3 lancé avec `-I` autorisé), avec la même sortie en local et en CI.
- Contrôles :
  1. tous les liens Markdown relatifs résolus (hors arbres de skills) ;
  2. `.claude/skills` et `.cursor/skills` identiques, et chaque skill local identique à sa source (ne **pas** relancer le vendor en CI) ;
  3. chaque `TODO §N` cité dans `src/` et `frontend/src/` résolu dans le registre, en prévoyant dès maintenant la structure cible de P6 ;
  4. chaque `D-XXX` cité résolu ;
  5. toute version restant dans le README cohérente avec le `pom.xml`.
- Prévoir une option `--self-test` qui prouve, sur des fixtures temporaires, que chaque contrôle échoue bien quand il le doit.

**P4, D2.**
- `git mv "analysis doc" docs/specs` et réécriture de toutes les références : Markdown, commentaires du code, skills, CI.
- Vérifier d'abord qu'aucun build, test ou packaging ne lit ce chemin.

**P5, D3.**
- Poser et pousser le tag d'archive.
- Établir une table de correspondance entre chaque fonctionnalité de `docs/ai/` et sa spec `docs/specs/NN-*.md`. Pour une fonctionnalité livrée sans spec qui la couvre, ajouter une section « Implémentation » courte (30 lignes au plus, une synthèse et non une copie) dans la spec la plus proche.
- Supprimer de `HEAD` les fichiers `docs/ai/` des fonctionnalités livrées. Garder l'arborescence (README par phase) si `dev-lifecycle` ou `check-status.sh` en a besoin.
- Inscrire la règle de fin de cycle dans `.project-skills/myrmacta-conventions/SKILL.md` : la PR de livraison remonte l'essentiel dans la spec et les décisions dans le registre, puis supprime ses fichiers `docs/ai/`.

**P6, D4.**
- Éclater `TODO.md` en un fichier par section numérotée, `docs/decisions/NNN-<slug>.md`, avec le statut en en-tête (`ouvert` / `clos` / `remplacé par §N`).
- Les sections non numérotées (bloquants, contradictions, « décisions actées », etc.) deviennent des fichiers nommés qui gardent leurs numéros internes.
- Générer `docs/decisions/README.md` par script : index, et convention « `TODO §N` = `docs/decisions/NNN-*.md` ». Supprimer `TODO.md`.
- Déplacer, figés :
  - `docs/roadmap-goal/` vers `docs/decisions/roadmap-2026/` ;
  - `REVUE-*.md` vers `docs/analyses/` ;
  - les guides (`GETTING-STARTED`, `DEPLOIEMENT`, `GUIDE-UTILISATEUR`, `GUIDE-CONCEPTION-BPMN`) vers `docs/guides/`, après avoir vérifié qu'aucun build, test, Dockerfile ni frontend ne les lit.
- Les références `TODO §N` du code restent inchangées. `check-docs` les résout.

**P7, D5 : selon le paramètre Backlog.**
- Commun aux deux modes :
  - recenser ce qui reste réellement à faire dans `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md`, `ROADMAP.md` et la section « Points ouverts » de l'ancien `TODO.md` ;
  - les points ouverts qui sont des **décisions à trancher** restent dans `docs/decisions/` ;
  - `ROADMAP.md` est réduit à une page de vision produit s'il a ce rôle, et supprimé sinon (décision consignée) ;
  - `FILE-ATTENTE.md` et `DEFERRED-FEATURES.md` sont supprimés de `HEAD`.
- Mode `issues` : chercher les doublons (`search_issues`), puis créer une issue par élément, avec libellés `vague-*`, `priorite-*` et `type-*`, un lien vers la spec et la décision, et des jalons si l'ordre l'exige. Reporter la table élément → issue dans le Suivi.
- Mode `fichier` : un seul `BACKLOG.md`, qui ne contient que ce qui reste à faire.
- CHANGELOG :
  - ajouter une configuration git-cliff (`cliff.toml`) qui produit le format actuel du CHANGELOG ;
  - garder intactes les entrées existantes ;
  - documenter la commande de release ;
  - retirer l'obligation d'une entrée manuelle par PR de `myrmacta-conventions` et de tout autre endroit qui l'impose.

**P8, D6.**
- Écrire `AGENTS.md` (40 lignes au plus) : carte de la documentation (où lire, où écrire), règles « jamais » (copie de skill générée, nouveau fichier de suivi, renumérotation d'un identifiant), commandes de vérification.
- `CLAUDE.md` contient `@AGENTS.md`.
- Mettre `myrmacta-conventions` à jour selon la nouvelle structure (source puis propagation).
- Inventorier les 28 skills (utilité en session cloud ou locale, recouvrements). Selon le paramètre Skills, reporter une recommandation dans le Suivi, ou supprimer.

**P9, risque 4.**
- Thymeleaf : test qui énumère tous les processeurs des dialectes enregistrés par `SafeHtmlTemplateEngineFactory`. Chacun doit figurer soit dans une liste blanche explicite, soit dans `DISABLED_PROCESSORS` ; tout processeur inconnu ajouté par une montée de version fait échouer le build. Passer à une vraie liste blanche si c'est possible sans changement de comportement (décision consignée).
- JSONata : tests de non-régression sur les formes d'AST qu'`AstInspector` exploite (voir D-EXPR-AST-BRIDGE).
- Surcharges CVE du `pom.xml` (Tomcat, Kotlin, Netty, swagger-ui) : un commentaire par surcharge (CVE, condition de retrait), et une issue ou une entrée de backlog par surcharge.

**P10, risque 3 : déploiement d'un tenant en code.**
- `docker/tenant/` : un compose paramétré par un fichier d'environnement de tenant. Étendre `scripts/provision-tenant.sh` pour générer ce fichier, la configuration Keycloak déclarative (client et mappers `tenant_id`, par import ou par script `kcadm` idempotent) et la configuration du reverse-proxy déjà documenté dans `DEPLOIEMENT.md`.
- Validation statique : `shellcheck` ; `docker compose config` si la CLI est disponible, sinon le noter.
- Test d'intégration : deux contextes applicatifs (tenants A et B) sur le même PostgreSQL zonky, provisionnés par la même source SQL que la production. Vérifier que le rôle de A ne lit pas le schéma de B, et qu'un jeton `tenant_id=B` est refusé par l'instance A. Étendre `TenantSchemaIsolationIT` et `TenantRoleIsolationIT` plutôt que de dupliquer.
- Mettre `DEPLOIEMENT.md` à jour.

**P11, découpage de `frontend/e2e/pilotage.spec.ts` par écran.**
- `npx playwright test --list` doit donner les mêmes titres de tests, en même nombre, avant et après (sorties à l'appui).

**P12, clôture.**
- Mesurer les indicateurs de réussite du plan : taille des `.md` hors skills, fichiers de suivi touchés par une PR type, zéro lien cassé, zéro dérive.
- Compléter le Suivi, lister les décisions ⚠, et lancer les vérifications finales.

### Critères d'acceptation (sorties de commandes à l'appui)

1. `./mvnw verify` et `cd frontend && npm run test:all` verts sur le HEAD final ; ArchUnit et JaCoCo inchangés ou meilleurs.
2. `scripts/check-docs.sh` vert, et `scripts/check-docs.sh --self-test` prouve que chaque contrôle détecte sa faute.
3. Chaque `TODO §N`, `D-XXX` et « décision n° N » cité avant la remédiation est toujours résolu (comparer avec l'inventaire de P0).
4. `HEAD` ne contient plus `analysis doc/`, `TODO.md`, `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md`, ni les fichiers `docs/ai/` des fonctionnalités livrées ; le tag d'archive est poussé et les permaliens fonctionnent.
5. Démarrer avec le profil `local` et `APP_ENVIRONMENT=production` échoue ; démarrer en `production` avec l'antivirus `noop` sans acquittement échoue (tests automatisés).
6. Un processeur Thymeleaf inconnu fait échouer un test (démontré, puis retiré). Le test à deux tenants est vert.
7. Les titres et le nombre de tests Playwright sont identiques avant et après le découpage.
8. La section Suivi du plan est complète, avec les indicateurs mesurés et les décisions ⚠ listées.

### Hors périmètre

- Découpage des grosses classes Java (au fil de l'eau, comme le prévoit le plan).
- Chart Helm.
- Suppression de `.cursor/skills`.
- Nouvelles fonctionnalités produit.
- Toute modification de la base de production ou du modèle multi-tenant.
