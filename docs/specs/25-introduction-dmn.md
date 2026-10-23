# Introduction de DMN — lever l'exclusion V1

> **Livré (2026-09-11).** Cycle `dev-lifecycle` complet (requirements → design → planning →
> implémentation, M1-M12, TODO §61) — détail dans
> [TODO.md](../TODO.md) §61. Le reste de ce document (état « non tranché ») est conservé tel
> quel comme trace de l'analyse initiale, y compris ce qui a depuis été corrigé (blocs
> « Corrigé »/« Confirmé » ci-dessous) et ce que l'implémentation a confirmé ou infirmé.
>
> **Statut : besoin métier confirmé (2026-09-11), périmètre et sécurité non tranchés.** Origine :
> demande utilisateur (2026-09-10) — DMN (Decision Model & Notation) est aujourd'hui explicitement
> exclu du V1 ; ce document vérifie contre le code réel ce que lever cette exclusion impliquerait.
> À affiner en phase `dev-lifecycle` (requirements → design) avant tout code — voir « Questions
> ouvertes » en fin de document.
>
> **Corrigé (2026-09-11), question utilisateur :** la version initiale sous-estimait l'état réel de
> l'édition BPMN (« modélisé en externe puis déployé ») — `edition-bpmn` (PR #71, TODO §39) a depuis
> ajouté un éditeur graphique intégré (`bpmn-js` Modeler). §3.2/§4.2 corrigés : `dmn-js`, toolkit
> sœur de `bpmn-js` (même éditeur bpmn.io, même licence vérifiée), est l'équivalent DMN direct de ce
> mécanisme — ajoute la question **D-DMN-EDITOR-SCOPE** (§5).
>
> **Confirmé (2026-09-11), question utilisateur :** DMN répond à un besoin métier identifié — gérer
> des cas/règles métier (barèmes, seuils, éligibilité) séparément du flux — et non à l'introduction
> d'une techno pour elle-même. **D-DMN-SCOPE (§5) n'est donc plus une question d'opportunité**, seul
> son périmètre concret (quel `DossierType`/process, quelle forme de règle) reste à préciser en
> Phase 1 — voir §2.
>
> **Corrigé (2026-09-11), Phase 3 design — mécanisme d'invocation DMN.** Investigation du code
> source Flowable 8 : `businessRuleTask` (H1-bis) est câblé sur **Drools**, sans rapport avec DMN —
> son rejet reste justifié, inchangé. Le vrai mécanisme est `<serviceTask flowable:type="dmn">`,
> bloqué aujourd'hui par une règle différente et plus large
> (`assertSafeServiceTask` rejette tout type intégré). §1.2/§3.1 corrigés en conséquence.

---

## 1. Rappel de l'état actuel (vérifié dans le code, pas seulement la doc)

DMN a toujours fait partie du standard porté par le moteur retenu, mais a été **explicitement
désactivé** au lancement du projet, et **re-bloqué une seconde fois, plus tard, pour une raison de
sécurité** — deux décisions distinctes, à connaître avant de rouvrir le sujet.

### 1.1 Exclusion d'origine (choix technique, décision n° 5 / `analysis doc/05`)

- `pom.xml` L23/L62-64 : une **seule** dépendance, `flowable-spring-boot-starter`
  (`flowable.version = 8.0.0`). Ce starter embarque déjà les classes du moteur DMN sur le
  classpath — il n'y a pas de dépendance Maven séparée à ajouter pour l'activer.
- `application.yml` L1-27 : `spring.autoconfigure.exclude` désactive explicitement, parmi
  d'autres modules non retenus (App Engine, IDM, Event Registry, CMMN, REST API Flowable, LDAP),
  **`DmnEngineAutoConfiguration`** et **`DmnEngineServicesAutoConfiguration`** (L16-17). Le
  commentaire en tête de fichier (L1-2) le documente : *« Flowable V1 = moteur BPMN uniquement »*.
- `README.md` / `analysis doc/00-index.md` / `analysis doc/05-workflow-engine.md` documentent tous
  les trois ce choix de façon cohérente : *« Le moteur DMN Flowable n'est pas activé en V1
  (auto-config exclue) »*.
- `TODO.md` §45 (`derive-documentaire`, clos) note explicitement, en 2026-08-25 : *« Ne pas
  élargir le V1 (DMN, connecteurs SERVICE) »* — **precedent utile** : `connecteurs SERVICE` était
  listé dans la même phrase comme hors-V1, et a pourtant été livré depuis (`http-rest` en rôle
  SERVICE, PR #95, 2026-08-31). L'exclusion DMN n'est donc pas un mur définitif, seulement une
  frontière V1 jamais rouverte à ce jour.

### 1.2 Blocage de sécurité ajouté depuis (TODO §58, finding H1-bis, PR #115)

Plus significatif pour le cadrage de cette feature : `FlowableBpmnParserAdapter.java` L77-79
**rejette explicitement tout `businessRuleTask`** au moment du parsing/déploiement d'un diagramme
BPMN :

```java
if (element instanceof BusinessRuleTask) {
    throw new InvalidBpmnException("BPMN businessRuleTask is not allowed: " + element.getId());
}
```

Ce n'est pas un oubli — c'est le fix retenu lors de la contre-revue adversariale post-merge PR
#113 (TODO §58), motivé noir sur blanc dans le commentaire de conception du fichier : *« aucun
delegate de l'allowlist n'en a besoin aujourd'hui »* / *« pas de moteur DMN dans la stack »*. Le
même fichier documente, pour les autres éléments dangereux qu'il neutralise (`assignee`
UEL, `conditionExpression`), la leçon apprise pendant cette revue : une première tentative de
restriction « littéral simple » (`${nomSimple}`) s'est révélée contournable, car UEL retombe sur
la résolution d'un bean Spring par nom dès qu'aucune variable de process ne porte ce nom —
`${connectorServiceTask}` est syntaxiquement indiscernable d'une variable métier légitime et
invoque pourtant un bean vivant du contexte applicatif. **Cette même classe de risque existe côté
DMN — et pas « comme si c'était du UEL », c'est littéralement le même moteur.** Correction
(2026-09-11, Phase 5 implémentation, vérifié dans le code source Flowable) : **Flowable
n'implémente pas FEEL du tout**. Son moteur DMN évalue les cellules d'entrée/sortie via **JUEL**,
le même `ExpressionManager`/EL que celui déjà durci côté BPMN — pas un langage FEEL séparé à
inventer. Le pré-parseur DMN de Flowable (`ELInputEntryExpressionPreParser`) transforme une cellule
brute (`== BRONZE`, `>= 18`, ou une valeur nue interprétée en égalité implicite) en une expression
`#{...}` évaluée par ce même moteur — donc une cellule contenant un identifiant nu non quoté
(`connectorServiceTask`) expose *exactement* la vulnérabilité H1-bis. La grammaire FEEL
(intervalles `[a..b]`, listes `"a","b"`) évoquée plus haut dans ce document **n'est pas supportée
par l'implémentation Flowable** — seules les formes « opérateur + valeur » ou « valeur nue = égalité
implicite » le sont ; une grille combinant plusieurs valeurs doit s'exprimer en plusieurs règles,
pas en une cellule à liste. Lever un blocage BPMN qui protège l'invocation de DMN, sans réappliquer
une discipline JUEL équivalente à `assertSafeConditionExpression`, rouvrirait la même surface que
H1/H1-bis avaient fermée.

**Correction (2026-09-11, Phase 3 design) — `businessRuleTask` n'est PAS le mécanisme d'invocation
DMN de Flowable.** Investigation du code source Flowable 8 et de sa documentation officielle,
faite en Phase 3 plutôt que supposée : l'élément BPMN `<businessRuleTask>` est câblé par Flowable
sur **Drools** (`BusinessRuleTaskActivityBehavior`, moteur de règles indépendant, absent du
classpath FlowForge — seul `flowable-spring-boot-starter` est déclaré, `pom.xml` L62-64). Son
rejet actuel (H1-bis) reste donc justifié tel quel — Drools n'a jamais eu de garde-fou côté
FlowForge et n'a aucun rapport avec DMN. **Le vrai mécanisme d'invocation DMN de Flowable est un
`<serviceTask flowable:type="dmn">`** portant un champ `decisionTableReferenceKey` (exemple
officiel, `flowable-examples/flowable-intro/.../dmn.bpmn20.xml`) :

```xml
<serviceTask id="dmnTask" flowable:type="dmn">
    <extensionElements>
        <flowable:field name="decisionTableReferenceKey">
            <flowable:string><![CDATA[intro]]></flowable:string>
        </flowable:field>
    </extensionElements>
</serviceTask>
```

Cette forme est **déjà bloquée aujourd'hui**, mais par une règle différente et plus large que le
rejet `businessRuleTask` : `FlowableBpmnParserAdapter.assertSafeServiceTask` (L91-109) rejette tout
`serviceTask` dont `getType()` est non-vide — c'est-à-dire **tous** les types intégrés Flowable
(`mail`, `camel`, `http`, `shell`, `mule`, `dmn`...), pas seulement DMN, pour ne garder que
`delegateExpression` vers l'allowlist `AllowedBpmnDelegates`. C'est le **vrai** gate à ouvrir, de
façon étroite (une exception `type="dmn"` explicitement validée, pas une levée générale des types
intégrés) — voir design §Architecture pour le mécanisme retenu.

### 1.3 Mécanisme de décision actuellement disponible (l'alternative existante à DMN)

Le seul mécanisme de branchement conditionnel du dépôt aujourd'hui est le **gateway exclusif
BPMN** avec `conditionExpression` — restreint par le même fichier (L144-195) à une grammaire sûre
(identifiants, littéraux, opérateurs de comparaison/logique, ni point ni parenthèse ni crochet ni
`@`/`#`, au moins un opérateur). Le commentaire de code confirme que c'est bien l'usage réel du
dépôt : *« `${decision == 'approve'}`, seul usage réel du dépôt, grep vérifié »*. Toute règle de
décision aujourd'hui modélisée dans FlowForge l'est donc comme une cascade de gateways dans le
diagramme BPMN lui-même — pas dans un artefact séparé, éditable indépendamment du process.

---

## 2. Ce que DMN apporterait — besoin confirmé (2026-09-11)

**Confirmé par l'utilisateur, ne pas rouvrir sans raison explicite :** DMN répond à un besoin
métier identifié — **gérer les cas/règles métier** (barèmes, seuils, grilles d'éligibilité,
matrices de tarification) qui se posent en dehors du seul enchaînement de tâches — pas une
introduction technologique pour elle-même. Ce n'est pas « une feature pour de la feature » : c'est
la même doctrine que chaque feature V1.x du dépôt (partir d'un écart ou d'un besoin constaté), donc
cadrable en `dev-lifecycle` sans revalider la légitimité de fond à chaque relecture. Ce qui reste à
préciser en phase requirements (Phase 1), ce n'est **pas** l'opportunité de DMN mais son **usage
concret** : quel(s) `DossierType`/process porte(nt) la première règle de décision candidate, sous
quelle forme (barème numérique, matrice d'éligibilité à conditions multiples, autre), avec quel
volume de règles — c'est ce détail qui alimentera D-DMN-SCOPE (§5) sans redemander « pourquoi DMN
du tout ».

Bénéfices attendus, confirmés pertinents pour ce besoin :

- **Séparer la règle de décision du flux.** Une matrice de règles devient un artefact `.dmn`
  autonome, versionné indépendamment du diagramme BPMN qui l'invoque — alors qu'aujourd'hui la même
  logique, si elle dépasse quelques conditions, se traduirait par une cascade de gateways exclusifs
  imbriqués dans le process (seul mécanisme disponible aujourd'hui, §1.3).
- **Lisibilité pour un profil non technique.** Une table de décision (lignes = règles, colonnes =
  conditions/conclusions) est plus proche d'une grille métier (Excel, cahier de règles) qu'un
  graphe de gateways — permet à un métier de faire évoluer des seuils sans repasser par un
  diagramme BPMN complet, cohérent avec l'objectif « gérer les cas métier » plutôt que le déléguer
  systématiquement à une réécriture de process.
- **Réutilisation.** Une même table de décision peut être invoquée depuis plusieurs
  `businessRuleTask` / plusieurs process, alors qu'une cascade de gateways est dupliquée à chaque
  process qui en a besoin — pertinent dès que plusieurs types de dossier partagent une même règle
  (ex. un même barème d'éligibilité appliqué par deux workflows différents).

---

## 3. Périmètre technique si la feature est lancée (architecture hexagonale)

### 3.1 Activation moteur

- Retirer les 2 lignes `DmnEngineAutoConfiguration` / `DmnEngineServicesAutoConfiguration` de
  `application.yml`. Le moteur DMN Flowable gère ses propres tables (`ACT_DMN_*`, comme
  `ACT_RU_*`/`ACT_HI_*` pour BPMN) — pas de changelog Liquibase applicatif à écrire, cohérent avec
  le mode `databaseSchemaUpdate` déjà en place pour le moteur BPMN.
- **`businessRuleTask` reste rejeté, inchangé** (§1.2 — Drools, hors sujet DMN). Le gate à ouvrir
  est `assertSafeServiceTask` : une exception étroite pour `flowable:type="dmn"` (littéral,
  `decisionTableReferenceKey` en `flowable:string` uniquement, jamais `flowable:expression`, clé
  vérifiée contre le catalogue des tables déployées) — **seulement** après avoir posé l'équivalent
  DMN de l'allowlist FEEL (voir §4.1). Sans ce garde-fou, ouvrir cette exception rouvrirait
  exactement la classe de risque fermée par H1/H1-bis.

### 3.2 Déploiement des tables de décision

**Correction par rapport à une première version de ce document (§4.2 initial) :** ce paragraphe
citait uniquement `deploiement-bpmn` (TODO §35, upload minimal) comme précédent, en présentant
l'absence d'éditeur intégré comme la doctrine du dépôt. C'est incomplet — `deploiement-bpmn` a
depuis été **remplacé en pratique** par un mécanisme plus riche, `edition-bpmn` (PR #71, TODO §39,
[14-edition-bpmn.md](14-edition-bpmn.md)) : FlowForge embarque aujourd'hui un **véritable éditeur
graphique** à `/admin/bpmn-editor` (`bpmn-js` Modeler + `bpmn-js-properties-panel` avec un provider
custom pour les attributs `flowable:*`), pas seulement un upload de fichier modélisé ailleurs. Les
trois mécanismes coexistent et sont tous les trois réutilisables par analogie pour DMN :

- Scan classpath au démarrage (`classpath*:/dmn/**/*.dmn` — miroir de `TenantProcessDeployment`).
- Upload admin à chaud avec prévisualisation (mécanisme historique `deploiement-bpmn`, TODO §35),
  sur le même modèle que `BpmnUploadPreview.vue` / `WorkflowBpmnDeploymentController` — nouveau
  port `DmnParserPort`/`DmnDeploymentPort` côté domaine, nouvel adaptateur infrastructure, nouveau
  contrôleur `TENANT_ADMIN`/`WORKFLOW_ADMIN`.
- **Éditeur intégré** (mécanisme actuel `edition-bpmn`, TODO §39) — voir §4.2 : `dmn-js` est le
  toolkit sœur de `bpmn-js`, même éditeur bpmn.io, et fournit un composant `Modeler` dédié aux
  tables de décision. C'est l'option directement analogue à ce qui a été livré côté BPMN, pas une
  extrapolation.

Un `businessRuleTask` référence une `decisionRef` (clé de table DMN) au même titre qu'un process
référence son `processDefinitionKey` — la validation « clé connue sinon rejet » déjà appliquée aux
process (`UnknownWorkflowKeyException` → 404) devrait avoir un équivalent DMN.

### 3.3 Multi-tenant

Aucun changement de modèle attendu : une instance Spring Boot = un tenant (décision n° 13), donc
un schéma PostgreSQL par tenant héberge déjà ses propres tables `ACT_*` — les tables `ACT_DMN_*`
suivraient la même règle sans travail supplémentaire.

### 3.4 MCP (cohérence avec `deploy_workflow`, TODO §56/§60)

Si DMN entre dans le catalogue de mécanismes qu'un agent peut opérer sans sortir du canal MCP
(même logique que l'analyse §60/24 sur les connecteurs et questions de tâche), il faudrait a minima
un tool `deploy_decision_table` miroir de `deploy_workflow`. À trancher en requirements si c'est
dans le même cycle ou différé (précédent : les connecteurs ont été traités dans un cycle MCP séparé
de leur livraison backend initiale).

### 3.5 Documentation à mettre à jour si la feature est retenue

`README.md` (retirer « DMN hors V1 » du schéma ASCII L105 et du texte L54), `analysis doc/05`,
`analysis doc/00-index.md` L57, `FILE-ATTENTE.md` § *Ne pas lancer* (retirer DMN de la liste),
`GUIDE-CONCEPTION-BPMN.md` (nouvelle section « quand utiliser un gateway vs une table DMN »).

---

## 4. Risques et points de vigilance

### 4.1 Sécurité — durcissement FEEL, pas une activation triviale

C'est le risque dominant de cette feature, pas un détail d'implémentation. L'historique du dépôt
montre que le même exercice côté BPMN (H1 → H1-bis, PR #113 puis #115) a nécessité **deux passes**
avant d'être réellement fermé, la première restriction envisagée s'étant révélée contournable à
l'exécution (pas seulement à la lecture de code). Toute table DMN acceptée doit donc, a minima,
avant de lever le blocage `businessRuleTask` :

- interdire toute expression FEEL non couverte par une allowlist explicite (littéraux, comparaisons
  simples sur variables de process connues) — même discipline que `assertSafeConditionExpression` ;
- vérifier, par un test d'exécution réel contre le vrai moteur Flowable (pas seulement une lecture
  statique de l'AST), qu'une expression conçue pour ressembler à une référence de variable ne peut
  pas résoudre un bean Spring — la leçon exacte de H1-bis ;
- probablement passer par une revue adversariale dédiée avant merge, comme les deux précédentes
  (§57/§58), plutôt que de considérer le sujet clos après l'implémentation initiale.

### 4.2 Éditeur DMN intégré — possible par le même mécanisme que l'éditeur BPMN, à confirmer

**Correction :** l'affirmation « FlowForge ne fait que déployer du BPMN modélisé ailleurs » n'est
plus exacte depuis `edition-bpmn` (PR #71, 2026-08-24, TODO §39) — un éditeur graphique est
**intégré** à `/admin/bpmn-editor` (`bpmn-js` Modeler + `bpmn-js-properties-panel`), en plus de
l'upload d'un fichier modélisé en externe. Ce que la décision `DEFERRED-FEATURES.md` §1.5 /
TODO §35 exclut réellement, c'est de déployer les **applications web propriétaires** de Flowable
(Modeler, Cockpit — option (d) écartée dans [14-edition-bpmn.md](14-edition-bpmn.md) §Options :
« app tierce à opérer/sécuriser par tenant ») — pas d'embarquer une **librairie JS** d'édition dans
le SPA FlowForge, ce qui est exactement le choix retenu pour BPMN (option (b) du même document).

**L'équivalent DMN existe et vient du même éditeur.** `dmn-js` ([bpmn.io/toolkit/dmn-js](https://bpmn.io/toolkit/dmn-js/),
[github.com/bpmn-io/dmn-js](https://github.com/bpmn-io/dmn-js)) est le toolkit sœur de `bpmn-js`
au sein de l'écosystème bpmn.io (même éditeur, maintenu par Camunda Services GmbH) : vue/édition de
diagrammes DRD (Decision Requirements Diagram), de **tables de décision** et d'expressions
littérales pour du DMN 1.3, packagé npm (`dmn-js`, avec un sous-module dédié
`dmn-js-decision-table`), avec un `Viewer` (lecture seule) et un `Modeler` (édition) — même
architecture bibliothèque-front-sans-service-à-opérer que `bpmn-js`. Le même schéma que
`edition-bpmn` s'applique donc directement : `DmnEditorCanvas.vue` (nouveau) embarquant
`dmn-js/lib/Modeler`, un écran admin dédié, un mécanisme de brouillon si le même choix D-EDIT-SAVE
est reconduit.

**Licence vérifiée (fichiers `LICENSE` des deux dépôts GitHub, pas seulement `package.json`)** :
`bpmn-js` et `dmn-js` portent exactement la **même** licence — MIT, avec une clause additionnelle
(« bpmn.io License ») : le filigrane bpmn.io affiché sur le canvas ne doit pas être retiré ni
masqué visuellement. Ce n'est **pas** un risque nouveau propre à DMN — la même clause s'applique
déjà à l'éditeur BPMN en production (`BpmnEditorCanvas.vue`), qui ne la contourne pas (grep
vérifié : aucun code de masquage du filigrane dans `frontend/src`). Un futur éditeur DMN
hériterait de la même contrainte, déjà acceptée implicitement pour BPMN — à documenter
explicitement plutôt qu'à redécouvrir, [14-edition-bpmn.md](14-edition-bpmn.md) ne mentionnant que
« MIT » sans la nuance du filigrane.

**Ce que ça ne change pas :** un éditeur graphique adresse la **saisie** d'une table de décision,
pas sa **sécurité d'exécution**. Le risque dominant de cette feature reste §4.1 — les expressions
FEEL d'une table de décision restent à durcir côté moteur avant de lever le rejet
`businessRuleTask`, qu'elles soient saisies via `dmn-js` ou via un outil externe puis uploadées.

### 4.3 Coexistence avec les gateways existants

Aucun diagramme livré avec ce dépôt n'utilise `businessRuleTask` (bloqué depuis toujours) — lever
le blocage est donc **sans régression** sur l'existant. Reste à documenter, dans
`GUIDE-CONCEPTION-BPMN.md`, quand préférer une table DMN à une cascade de gateways, pour éviter que
les deux mécanismes cohabitent sans règle claire dans les diagrammes livrés avec le produit.

### 4.4 Effort de test

Précédent direct : `FlowableBpmnAllowlistGapTest` / `FlowableBpmnParserAdapterTest` (22 cas) /
`FlowableBpmnParserAdapterShippedProcessesTest` (non-régression sur les 7 BPMN de production) ont
été nécessaires pour fermer H1-bis. Un exercice de même ampleur est à prévoir côté DMN, avec un
test d'intégration réel contre le moteur (miroir `UserQuestionFormSmokeIT`/`FormKeySmokeIT`) — pas
seulement des tests unitaires sur le parseur.

---

## 5. Questions ouvertes (à trancher en phase requirements/design)

1. **D-DMN-SCOPE — besoin confirmé (2026-09-11), périmètre à préciser.** L'opportunité n'est plus
   ouverte (§2) : DMN répond à un besoin identifié de gestion des cas/règles métier, pas à une
   introduction technologique pour elle-même. Reste à trancher en Phase 1 : le(s) `DossierType`/
   process concret(s) porteur(s) de la première règle de décision, la forme de la règle (barème,
   matrice d'éligibilité, autre) et son volume — sans quoi le design (§3/§4) ne peut pas dimensionner
   le durcissement FEEL (§4.1 : la grammaire sûre à autoriser dépend des opérateurs/types réellement
   nécessaires, comme `assertSafeConditionExpression` l'a fait pour les gateways à partir de l'usage
   réel du dépôt, pas d'une grammaire FEEL générique).
2. **D-DMN-SECURITY-OWNER** — le durcissement FEEL (§4.1) est-il fait dans le même cycle que
   l'activation du moteur, ou l'activation est-elle bloquée tant qu'une revue de sécurité dédiée
   n'a pas eu lieu (comme le split H1/H1-bis le suggère) ?
3. **D-DMN-DEPLOY** — scan classpath, upload admin, éditeur intégré (`dmn-js`, §4.2), ou plusieurs
   dès le premier cycle ? BPMN a introduit les trois progressivement (`deploiement-bpmn` TODO §35
   puis `edition-bpmn` TODO §39) — à trancher si DMN suit le même séquencement ou démarre
   directement avec l'éditeur intégré, l'upload seul étant alors un jalon intermédiaire inutile.
4. **D-DMN-EDITOR-SCOPE** — si l'éditeur intégré est retenu dès ce cycle : `dmn-js` Modeler seul
   (DRD + table de décision standard) suffit-il, ou faut-il un provider custom pour des extensions
   Flowable spécifiques (miroir `flowablePropertiesProvider.ts`) ? À vérifier en design si le DMN
   de Flowable 8 a des attributs `flowable:*` propres au-delà du DMN 1.3 standard — contrairement à
   BPMN, ce n'est pas confirmé dans ce document.
5. **D-DMN-MCP** — `deploy_decision_table` dans le même cycle que l'activation backend, ou différé
   (précédent : connecteurs traités en cycles séparés côté backend puis côté MCP) ?
6. **D-DMN-HITPOLICY** — quelles hit policies Flowable DMN autoriser au lancement (`UNIQUE`,
   `FIRST`, `PRIORITY`, `COLLECT`...) ? Certaines (agrégation `COLLECT`) élargissent sensiblement la
   surface de test par rapport à une simple table à résultat unique.
7. **D-DMN-RESULT** — le résultat d'une table de décision s'écrit en variable(s) de process, au
   même titre qu'une réponse `user-question` (TODO §55) ou `dossierFields` (PR #86) — confirmer
   qu'il transite par le même mécanisme de masquage des valeurs sensibles
   (`SendNotificationUseCaseImpl`, TODO §23) avant toute écriture outbox, pour ne pas rouvrir un
   canal de fuite déjà fermé pour les autres sources de variables de process.
