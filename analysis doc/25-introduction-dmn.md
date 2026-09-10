# Introduction de DMN — lever l'exclusion V1

> **Statut : première analyse fonctionnelle et technique, aucune décision tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Questions ouvertes » en
> fin de document. Origine : demande utilisateur (2026-09-10) — DMN (Decision Model & Notation) est
> aujourd'hui explicitement exclu du V1 ; ce document vérifie contre le code réel ce que lever cette
> exclusion impliquerait, avant tout cadrage `dev-lifecycle`.

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
DMN** : les expressions FEEL d'une table de décision Flowable peuvent, selon la configuration du
moteur, résoudre des variables de process et des beans exactement comme UEL en BPMN. Lever le
blocage `businessRuleTask` sans réappliquer une discipline équivalente rouvrirait la même surface
que H1/H1-bis avaient fermée.

### 1.3 Mécanisme de décision actuellement disponible (l'alternative existante à DMN)

Le seul mécanisme de branchement conditionnel du dépôt aujourd'hui est le **gateway exclusif
BPMN** avec `conditionExpression` — restreint par le même fichier (L144-195) à une grammaire sûre
(identifiants, littéraux, opérateurs de comparaison/logique, ni point ni parenthèse ni crochet ni
`@`/`#`, au moins un opérateur). Le commentaire de code confirme que c'est bien l'usage réel du
dépôt : *« `${decision == 'approve'}`, seul usage réel du dépôt, grep vérifié »*. Toute règle de
décision aujourd'hui modélisée dans FlowForge l'est donc comme une cascade de gateways dans le
diagramme BPMN lui-même — pas dans un artefact séparé, éditable indépendamment du process.

---

## 2. Ce que DMN apporterait (à confirmer par un besoin métier concret en requirements)

- **Séparer la règle de décision du flux.** Une matrice de règles (barème, seuils, éligibilité,
  grille tarifaire) devient un artefact `.dmn` autonome, versionné indépendamment du diagramme
  BPMN qui l'invoque — alors qu'aujourd'hui la même logique, si elle dépasse quelques conditions,
  se traduirait par une cascade de gateways exclusifs imbriqués dans le process.
- **Lisibilité pour un profil non technique.** Une table de décision (lignes = règles, colonnes =
  conditions/conclusions) est plus proche d'une grille métier (Excel, cahier de règles) qu'un
  graphe de gateways — pertinent si l'intention est de permettre à un métier de faire évoluer des
  seuils sans repasser par un diagramme BPMN complet.
- **Réutilisation.** Une même table de décision peut être invoquée depuis plusieurs `businessRuleTask`
  / plusieurs process, alors qu'une cascade de gateways est dupliquée à chaque process qui en a
  besoin.

**Point de vigilance sur ce §2 :** ce sont des bénéfices génériques de DMN, pas un besoin métier
FlowForge identifié. Le cadrage `dev-lifecycle` (§5, D-DMN-SCOPE) doit s'appuyer sur un cas d'usage
réel plutôt que sur l'introduction de DMN pour elle-même — cohérent avec la doctrine du dépôt
(chaque feature V1.x part d'un écart ou d'un besoin constaté, pas d'une techno à cocher).

---

## 3. Périmètre technique si la feature est lancée (architecture hexagonale)

### 3.1 Activation moteur

- Retirer les 2 lignes `DmnEngineAutoConfiguration` / `DmnEngineServicesAutoConfiguration` de
  `application.yml`. Le moteur DMN Flowable gère ses propres tables (`ACT_DMN_*`, comme
  `ACT_RU_*`/`ACT_HI_*` pour BPMN) — pas de changelog Liquibase applicatif à écrire, cohérent avec
  le mode `databaseSchemaUpdate` déjà en place pour le moteur BPMN.
- Lever le rejet `businessRuleTask` dans `FlowableBpmnParserAdapter` — **seulement** après avoir
  posé l'équivalent DMN de `AllowedBpmnDelegates`/`assertSafeConditionExpression` (voir §4.1).
  Sans ce garde-fou, réactiver `businessRuleTask` rouvre exactement H1-bis.

### 3.2 Déploiement des tables de décision

Deux mécanismes déjà éprouvés côté BPMN, réutilisables par analogie (feature `deploiement-bpmn`,
TODO §35, décision (c) retenue à l'époque — **pas** de Modeler/Cockpit intégré) :

- Scan classpath au démarrage (`classpath*:/dmn/**/*.dmn` — miroir de `TenantProcessDeployment`).
- Upload admin à chaud avec prévisualisation, sur le même modèle que `BpmnUploadPreview.vue` /
  `WorkflowBpmnDeploymentController` — nouveau port `DmnParserPort`/`DmnDeploymentPort` côté
  domaine, nouvel adaptateur infrastructure, nouveau contrôleur `TENANT_ADMIN`/`WORKFLOW_ADMIN`.

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

### 4.2 Pas d'éditeur DMN intégré à prévoir

Cohérent avec la décision déjà actée sur Modeler/Cockpit (`DEFERRED-FEATURES.md` §1.5, TODO §35) :
si la feature est lancée, le mécanisme attendu est un **upload minimal** d'un `.dmn` validé, pas un
éditeur de table de décision dans FlowForge. Une table DMN se construit avec un outil externe
(Flowable Design, Camunda Modeler, ou XML à la main), comme un `.bpmn20.xml` aujourd'hui.

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

1. **D-DMN-SCOPE** — cas d'usage métier concret qui justifie la feature (voir §2), plutôt
   qu'introduire DMN pour compléter la couverture du standard. Sans cas d'usage porté par
   l'utilisateur, cette analyse reste un inventaire technique, pas un backlog priorisable.
2. **D-DMN-SECURITY-OWNER** — le durcissement FEEL (§4.1) est-il fait dans le même cycle que
   l'activation du moteur, ou l'activation est-elle bloquée tant qu'une revue de sécurité dédiée
   n'a pas eu lieu (comme le split H1/H1-bis le suggère) ?
3. **D-DMN-DEPLOY** — scan classpath seul, upload admin seul, ou les deux dès le premier cycle
   (mêmes deux mécanismes que BPMN aujourd'hui) ?
4. **D-DMN-MCP** — `deploy_decision_table` dans le même cycle que l'activation backend, ou différé
   (précédent : connecteurs traités en cycles séparés côté backend puis côté MCP) ?
5. **D-DMN-HITPOLICY** — quelles hit policies Flowable DMN autoriser au lancement (`UNIQUE`,
   `FIRST`, `PRIORITY`, `COLLECT`...) ? Certaines (agrégation `COLLECT`) élargissent sensiblement la
   surface de test par rapport à une simple table à résultat unique.
6. **D-DMN-RESULT** — le résultat d'une table de décision s'écrit en variable(s) de process, au
   même titre qu'une réponse `user-question` (TODO §55) ou `dossierFields` (PR #86) — confirmer
   qu'il transite par le même mécanisme de masquage des valeurs sensibles
   (`SendNotificationUseCaseImpl`, TODO §23) avant toute écriture outbox, pour ne pas rouvrir un
   canal de fuite déjà fermé pour les autres sources de variables de process.
