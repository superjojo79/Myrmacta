# §70 — Positionnement produit (D0) — intégrateurs + couche low-code

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §70 · [registre](README.md)

**Énoncé :** lot **D0** de `ROADMAP.md` (Vague D, PR #143), issu de l'analyse critique
fonctionnelle du 2026-09-28. Le `README.md` promet « modéliser sans développement » alors que
concevoir un process exige une expertise BPMN (`docs/guides/GUIDE-CONCEPTION-BPMN.md`) et que toute logique
hors des 7 delegates allowlistés (`AllowedBpmnDelegates`) impose du code Java. Constat vérifié :
le verrouillage est **serveur** (allowlist §57/§58/§69), mais le manque de no-code est **côté
UI** — le panneau de propriétés BPMN (`flowablePropertiesProvider.ts`) n'expose que des champs
texte libres (`delegateExpression`, `formKey`, `type`), les templates de documents se saisissent
en HTML Thymeleaf brut (`TemplatesAdminView.vue`), et il n'existe aucun langage d'expression
métier en dehors du JUEL restreint.

**Options présentées :**
- **A.** Outil de delivery pour intégrateurs, hébergement dédié par client ; le discours « sans
  développement » est abandonné.
- **B.** SaaS mutualisé en libre-service — impose de rouvrir la décision n° 13 (1 instance = 1
  tenant) et une UX de modélisation pour non-techniciens.
- **C.** Produit pour intégrateurs **avec une couche low-code / no-code** pour les utilisateurs
  avancés côté client.

**Décision (utilisateur, 2026-09-28) : D-POSITIONING = C.**
- La **décision n° 13 est maintenue** (1 instance Spring Boot = 1 tenant, hébergement dédié par
  client). L'option B n'est pas fermée, mais n'est plus un préalable.
- L'**intégrateur** conçoit les processus ; l'**utilisateur métier** ajuste formulaires,
  documents et règles, et peut s'appuyer sur un assistant de conception IA.
- **Aucun relâchement de l'allowlist serveur** : toute brique low-code produit le même XML BPMN
  que l'existant, ou passe par un évaluateur borné exécuté hors JUEL. Le `scriptTask` natif
  Flowable (JSR-223 Groovy/JS) reste **écarté** (exécution de code arbitraire, §57/§69).

**Découpage en sous-lots (Vague D, préfixe `LC`)** — détail et coutures dans `ROADMAP.md`
Catégorie 7. Aucun n'est cadré : `dev-lifecycle` Phase 1 obligatoire pour chacun. Analyses
initiales (2026-09-28) : [31](../specs/31-positionnement-produit.md) pour cette décision,
[32](../specs/32-modele-champs-enrichi.md)–[38](../specs/38-internationalisation-ui.md)
pour D1–D7, [39](../specs/39-palette-metier-bpmn.md)–[45](../specs/45-scripts-sandbox.md)
pour LC1–LC6.

| Sous-lot | Contenu | Taille | Dépend de |
|---|---|---|---|
| **LC1** `palette-metier-bpmn` | Briques métier dans l'éditeur BPMN (element templates), listes alimentées par l'API au lieu de champs texte | S–M | — |
| **LC2** `langage-expression-metier` | Langage d'expression borné (conditions, champs calculés, mappings) évalué hors JUEL | M | — |
| **LC3** `assistant-conception-ia` | Assistant de conception dans l'UI, s'appuyant sur les 27 tools MCP, résultats en brouillon | M | D6 |
| **LC4a** `editeur-formulaires` | Éditeur visuel des formulaires de tâche (`user-question`, `dossier-fields`, portail) | M | — |
| **LC4b** `editeur-templates-wysiwyg` | Éditeur WYSIWYG des templates de documents, variables insérées par menu | M | — |
| **LC5** `concepteur-parcours` | Vue par étapes compilée en BPMN, éditeur BPMN conservé pour les experts | L | LC1, LC2 |
| **LC6** `scripts-sandbox` | Scripts JS/TS réduits en bac à sable WASM — **conditionnel** : spike de qualification d'abord, lot seulement si LC2 ne suffit pas | M–L | LC2 |

**Non retenus :** règles « Quand / Si / Alors » hors BPMN (recouvrement avec les événements BPMN
et l'alerting SLA §65 — deux endroits pour la logique métier) ; GraalJS (bac à sable strict
réservé à Oracle GraalVM sous licence GFTC, à reconfirmer, contraire au principe « licences
libres » ; alternative de repli si le spike LC6 échoue). Complément hors lot : modèles n8n/Make
prêts à l'emploi (documentation), l'intégration no-code restant externalisée.

**Conséquences sur les autres lots de la Vague D :**
- **D4** (promotion de configuration entre environnements) **remonte** : indispensable à un
  intégrateur qui fait recette puis production.
- **D5** (verrou des jobs multi-pods) **reste à l'audit** : l'hébergement dédié ne l'impose pas.
- **D6** (garde-fous MCP) devient **prérequis de LC3**.
- **D1** : les champs calculés passent par LC2 ; les nouveaux types (montant, groupe répétable,
  référence externe) restent indépendants. **D3b** (mapping réponse → champs) consomme le
  langage de LC2.
- **D7** (i18n) dépend du marché visé, sans changement.

**Décisions encore ouvertes (à trancher dans le lot concerné, pas ici) :**
- LC2 : JSONata (recommandé — couvre conditions, calculs et mappings) vs CEL ; vérifier que le
  portage Java retenu borne temps et profondeur d'évaluation.
- LC5 : politique d'aller-retour vue simplifiée ↔ BPMN (verrouillage par mode, conversion à
  sens unique).
- LC6 : moteur (QuickJS en WASM via Chicory vs repli) après spike ; un moteur WASM n'enregistre
  pas de `ScriptEngine` JSR-223, mais tout ajout de moteur JS au classpath doit être revérifié
  contre la défense `language`/`scriptFormat` de §69.
- LC4b : éditeur (TipTap vs GrapesJS), limité au sous-ensemble CSS 2.1 que Flying Saucer rend.

**Impacte :** `ROADMAP.md` (Catégorie 7, priorisation), `FILE-ATTENTE.md` (note de décision, file
inchangée). À répercuter lors du premier lot livré : `README.md` (vision « sans développement »
à nuancer, § Statut obsolète).
