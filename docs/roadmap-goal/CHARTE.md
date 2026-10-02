# Charte d'exécution — objectif « roadmap complète »

> Référencée par le `/goal` de la Vague D. Contrat de travail de l'agent : périmètre, ordre, cycle par
> lot, règles de décision déléguées, preuves exigées. **Les décisions prises par l'agent sont
> consignées dans [DECISIONS.md](DECISIONS.md) ; l'état d'avancement dans [AVANCEMENT.md](AVANCEMENT.md).**

## 1. Mandat

L'utilisateur délègue à l'agent les **décisions ouvertes** des lots de la roadmap (`ROADMAP.md`
Catégorie 7, `FILE-ATTENTE.md` § *File d'attente — Vague D*). Cette délégation vaut pour les décisions
**ouvertes** uniquement, et elle est **tracée** (§4) : elle n'autorise jamais une décision silencieuse
(`flowforge-conventions` § Workflow, point 5). Elle vaut aussi approbation explicite de chaque phase
(`dev-lifecycle` § *Plan Before Execution*) : ne pas s'arrêter pour demander un feu vert de phase.

## 2. Périmètre et ordre

Un lot à la fois (une seule branche, pas de collision de coutures). Ordre (respecte les contraintes de
`FILE-ATTENTE.md` : LC2 avant D3b et champs calculés ; D1 avant D2, LC4a, D4 ; D6 avant LC3 et D4 ;
LC1 et LC2 avant LC5) :

| # | Lot | Remarque |
|---|---|---|
| 1 | LC1-suite — liste des tables DMN déployées dans la palette | reliquat de TODO §73 |
| 2 | **LC2** langage d'expression métier | choix JSONata / CEL en Phase 1 (recommandation roadmap : JSONata, à vérifier : bornes temps/profondeur) |
| 3 | **D1** types de champs (MONEY, groupe répétable, EXTERNAL_REF) | |
| 4 | **D3a** OAuth2 client credentials sur `http-rest` | |
| 5 | **D5** audit des jobs planifiés | lecture seule d'abord ; verrou advisory lock PG seulement si l'audit montre une exécution en double réelle |
| 6 | **D3b** mapping réponse → champs ; **D1** champs calculés / visibilité | après LC2 |
| 7 | **D2** recherche champs dynamiques | tests Testcontainers JSONB |
| 8 | **D6** socle MCP low-code | prérequis D4 et LC3 ; clôt A3-M2 |
| 9 | **D4** promotion de configuration | |
| 10 | **LC4b** éditeur WYSIWYG de templates, puis **LC4a** éditeur de formulaires | |
| 11 | **LC3** assistant de conception IA | après D6 |
| 12 | **D3c** e-mail entrant + **D3d** bus Azure | un seul cycle catalogue ; D3d à confirmer par l'agent (valeur ERP/D365) |
| 13 | **LC5** concepteur de parcours | après LC1, LC2 |
| 14 | **LC6** spike de qualification | lot complet **seulement si** LC2 s'avère insuffisant (preuve dans le journal) |
| 15 | **D7** internationalisation de l'UI | l'agent tranche l'opportunité et les langues |

**Hors périmètre** (ne pas faire, ne pas « contourner ») : SSE multi-pod / Redis, tools MCP composites,
Chart.js SLA, Cockpit Flowable, codegen OpenAPI, OTel/Grafana, FEEL temps réel, simulation DMN, export BI
JDBC/Power BI, **fournisseur réel de signature et eIDAS** (besoin d'un compte externe), vérifications
d'environnement (ClamAV sous Docker, smoke Keycloak réel) — à lister dans AVANCEMENT comme « non
traité, raison ».

## 3. Cycle par lot (obligatoire, sans saut de phase)

Suivre `dev-lifecycle` (Phases 1 à 9) puis **Phase 10 — revue adversariale**. Nommage des docs :
`docs/ai/{requirements,design,planning,implementation,testing}/AAAA-MM-JJ-feature-<lot>.md`, point
`TODO.md` numéroté (suite du §73), statuts `ROADMAP.md` / `FILE-ATTENTE.md` mis à jour.

1. **Phase 1–2** requirements + revue : périmètre, décisions ouvertes listées et **tranchées** (§4).
2. **Phase 3** design (vérifier les faits contre le code et les sources des bibliothèques — précédent :
   `createMessageCorrelation` inexistant) ; **Phase 4** planning.
3. **Phase 5–7** implémentation en **TDD** (`tdd` + `verify`), planning mis à jour après chaque tâche,
   check implémentation contre design.
4. **Phase 8** tests : `./mvnw verify` **complet** (ArchUnit, Jacoco) ; si le SPA ou un contrat API
   consommé par le SPA est touché : `cd frontend && npm run test` **et** `npm run test:e2e`. Aucun
   « hors scope » Playwright. Au moins un test traversant la **vraie chaîne HTTP/sécurité** pour tout
   nouvel endpoint ou exposition MCP (précédent callback : 1533 tests verts, feature inatteignable).
5. **Phase 9** revue de code (`dev-review`).
6. **Phase 10 — revue adversariale** par un **sous-agent indépendant** (contexte neuf, modèle le plus
   capable disponible) qui attaque le diff du lot : authz/ACL, isolation tenant, injections, SSRF, DoS
   mémoire/CPU, secrets, surface d'expression (allowlist §57/§69), ruptures de contrat, trous de preuve.
   Corriger **toute** faille Critique/Haute/Moyenne en TDD avec test de régression permanent ; Basses
   tracées. Si une faille Critique/Haute est trouvée : **seconde passe** indépendante après correction.
   Consigner dans `docs/ai/implementation/…` § « Revue adversariale » (constat, sévérité, correctif).
7. **Documentation de clôture** (conventions point 6–7) : `GUIDE-UTILISATEUR.md`, `GETTING-STARTED.md`,
   `.env.example`, `CHANGELOG.md` (une entrée synthétique 2–5 lignes), « Impacte » dans `TODO.md`.
8. **Exposition MCP dans le même lot** (principe P1, analyse 46) pour chaque lot `LC*` concerné.
9. Commit(s) sur la branche de développement désignée, **push** à la fin de chaque lot. Pas de PR, pas de
   merge, sauf demande explicite de l'utilisateur.

Si `npx ai-devkit` est indisponible, poursuivre et le consigner (pas de blocage). Aucun test ne peut être
désactivé, ignoré ou quarantainé pour obtenir du vert.

## 4. Règles de décision déléguées

**Critères, par ordre de priorité :**

1. **Cohérence avec l'existant posé** — décisions figées de `TODO.md` (n° 13, 14, 17, 27, §57, §69, §70,
   §71…), stack figée, architecture hexagonale, allowlist BPMN, D0 = C, patterns des lots livrés. Non
   négociable : une option qui contredit une décision figée est **écartée**, quelle que soit sa valeur.
2. **Valeur produit** pour l'intégrateur et l'utilisateur métier low-code : robustesse, sécurité,
   exactitude, ergonomie, testabilité, exploitabilité. On choisit l'option qui apporte le plus de valeur,
   **pas la plus rapide ni la plus simple** ; le coût ne sert qu'à départager deux options de valeur
   équivalente.
3. **Sobriété** : pas de fonctionnalité au-delà de ce que l'analyse du lot justifie (la valeur est
   mesurée, pas l'ampleur).
4. **Réversibilité** : à valeur proche, préférer l'option additive / rétro-compatible (migrations
   Liquibase additives, contrats API étendus plutôt que rompus).

**Jamais tranché par l'agent :** relâcher l'allowlist serveur, autoriser `scriptTask` natif, évaluer de
l'expression métier en JUEL, ajouter Redis / un second moteur de persistance, licence incompatible
(AGPL), casser l'isolation 1 instance = 1 tenant, secrets en clair. Si un lot ne peut avancer sans cela :
le marquer **Bloqué** dans AVANCEMENT avec le motif, passer au lot suivant.

**Ressource externe manquante** (compte fournisseur, clé LLM, service tiers) : implémenter derrière un
port avec adaptateur stub/opt-in, comme `signature-electronique` ; consigner.

**Journal obligatoire — `DECISIONS.md`**, une ligne par décision, rédigée au moment où elle est prise
(pas reconstituée) : `ID` (`D-<LOT>-<NOM>`) · lot/phase · question · options envisagées (≥ 2) · **choix**
· justification *valeur produit* · cohérence (références n°/§) · conséquences et réversibilité ·
**⚠ à revoir en priorité** (oui si rupture de contrat, migration non triviale, dépendance ajoutée,
choix à forte valeur mais coûteux, ou décision non recommandée par l'analyse). Chaque décision est aussi
reprise dans le point `TODO.md` du lot (format existant `D-XXX`).

## 5. Suivi et preuves — `AVANCEMENT.md`

Tableau : lot · statut (`À faire` / `En cours` / `Livré` / `Bloqué` / `Non traité`) · phase atteinte ·
SHA du dernier commit · tests (`./mvnw verify`, Vitest, Playwright : nombre, 0 échec) · revue adversariale
(n° de passes, constats corrigés) · décisions ⚠. Mis à jour **à chaque phase**. Permet la reprise après
interruption : relire AVANCEMENT puis `check-status.sh <feature>` et continuer au premier lot non livré.

## 6. Définition de « terminé »

Tous les lots du §2 sont `Livré`, `Bloqué` (motif et décision requise) ou `Non traité` (motif) ; AUCUN
`À faire` / `En cours`. Sur le HEAD final, sorties **fraîches** de `./mvnw verify` et de
`cd frontend && npm run test:all`, 0 échec. `ROADMAP.md`, `FILE-ATTENTE.md`, `README.md` (Statut),
`CHANGELOG.md` alignés. `DECISIONS.md` sans ligne incomplète, décisions ⚠ listées en tête pour la revue.
Tout est poussé sur la branche de développement.
