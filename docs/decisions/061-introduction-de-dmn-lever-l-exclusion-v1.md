# §61 — Introduction de DMN — lever l'exclusion V1

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §61 · [registre](README.md)

**Énoncé :** DMN est explicitement hors V1 depuis l'origine (`application.yml` : auto-config
`DmnEngineAutoConfiguration`/`DmnEngineServicesAutoConfiguration` exclue) et le `businessRuleTask`
BPMN est en plus explicitement rejeté au parsing depuis la contre-revue adversariale post-merge PR
#113 (TODO §58, finding H1-bis) — pas de moteur DMN dans la stack à ce moment-là. Origine :
question utilisateur (2026-09-10), pas encore priorisé ni tranché en `dev-lifecycle`.

**Besoin confirmé (2026-09-11, utilisateur) :** DMN répond à un besoin métier identifié — gérer des
cas/règles métier (barèmes, seuils, éligibilité) séparément du flux BPMN — pas une introduction
technologique pour elle-même. **D-DMN-SCOPE n'est donc plus une question d'opportunité** ; reste à
préciser en Phase 1 le périmètre concret (quel `DossierType`/process, quelle forme de règle, quel
volume) — voir [25-introduction-dmn.md](../specs/25-introduction-dmn.md) §2/§5.

**Phase 1 (requirements) close (2026-09-11)** : cycle `dev-lifecycle` démarré, no-worktree
(branche de session), doc
[docs/ai/requirements/2026-09-11-feature-introduction-dmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-11-feature-introduction-dmn.md).
5 décisions actées via `AskUserQuestion` : **D-DMN-SCOPE** = cas illustratif générique (grille
d'éligibilité, pas de cas métier réel identifié) ; **D-DMN-SECURITY-OWNER** = durcissement FEEL
dans le même cycle que l'activation moteur (pas de split H1/H1-bis) ; **D-DMN-DEPLOY** = éditeur
intégré `dmn-js` dès ce cycle, pas de palier upload-seul intermédiaire ; **D-DMN-MCP** = tool MCP
dans le même cycle ; **D-DMN-HITPOLICY** = `UNIQUE`/`FIRST` + `COLLECT` dès ce cycle. Périmètre du
cycle donc large (activation moteur + durcissement sécurité + éditeur + MCP en une fois, plutôt que
le séquencement progressif suivi historiquement par BPMN) — assumé explicitement par l'utilisateur,
pas une extension silencieuse. Suite : Phase 3 (design).

**Phase 3 (design) — correction majeure du mécanisme (2026-09-11) :** investigation du code source
Flowable 8 (pas supposée) — `businessRuleTask` (H1-bis) est câblé sur **Drools**, sans rapport avec
DMN, son rejet reste inchangé. Le vrai mécanisme d'invocation DMN de Flowable est
`<serviceTask flowable:type="dmn">` + champ `decisionTableReferenceKey`, bloqué aujourd'hui par une
règle différente et plus large (`assertSafeServiceTask` rejette tout type intégré : mail/camel/
http/shell/dmn...). Décision actée avec l'utilisateur (`AskUserQuestion`) : ouvrir une exception
**native** et étroite (`type="dmn"` strictement littéral, `decisionTableReferenceKey` en
`flowable:string` uniquement, jamais `flowable:expression`, clé vérifiée contre le catalogue des
tables déployées) plutôt qu'un delegate custom réimplémentant la sémantique DMN à la main.
[25-introduction-dmn.md](../specs/25-introduction-dmn.md) §1.2/§3.1 corrigés en conséquence.

**Phase 3 (design) close (2026-09-11)** :
[docs/ai/design/2026-09-11-feature-introduction-dmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-11-feature-introduction-dmn.md).
Architecture : `DmnParserPort`/`DmnDeploymentPort`/`DmnDraftRepository` (domaine, miroir exact
`BpmnParserPort`/`WorkflowDeploymentPort`/`BpmnDraftRepository`), exception native `type="dmn"`
dans `assertSafeServiceTask` (D1), `dmn-js` Modeler **sans** provider custom — le `.dmn` est du DMN
1.3 standard, l'extension `flowable:*` est côté éditeur **BPMN** existant
(`flowablePropertiesProvider.ts`, à compléter) pas côté DMN (D2). Table `dmn_draft` (composite
`tenant_id`/`decision_key`, miroir `bpmn_draft`). Tools MCP :
`deploy_decision_table`/`list_decision_tables`/`get_decision_table` (D3). **Non résolu, reporté au
planning/un sous-cycle dédié** : grammaire FEEL exacte de l'allowlist de sécurité — risque
dominant de la feature, le FEEL des cellules DMN (intervalles, listes, `-`) n'est pas la même
grammaire que l'UEL des gateways, ne peut pas être calquée telle quelle sur
`assertSafeConditionExpression`. Suite : Phase 4 (planning).

**Phase 4 (planning) close (2026-09-11)** :
[docs/ai/planning/2026-09-11-feature-introduction-dmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-11-feature-introduction-dmn.md) +
[docs/ai/testing/2026-09-11-feature-introduction-dmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-11-feature-introduction-dmn.md).
12 milestones séquencés : **M1** grammaire FEEL (spike bloquant, prérequis de tout le reste) ;
M2 activation moteur + parsing/allowlist (TDD) ; M3 déploiement DMN ; M4 exception BPMN
`type="dmn"` + vérification clé au déploiement process ; M5 persistence brouillon ; M6 endpoints
admin REST ; M7 éditeur `dmn-js` (parallélisable à M5/M6, comme `edition-bpmn`) ; M8 complément de
l'éditeur BPMN existant pour référencer une table depuis un `serviceTask` ; M9 écran admin ; M10
tools MCP ; M11 fixture grille d'éligibilité ; M12 doc (`docs/guides/GUIDE-UTILISATEUR.md`, `CHANGELOG.md`,
retrait définitif « hors V1 ») + vérification globale. Tous les scénarios de test tracés vers au
moins une tâche (table de couverture exhaustive dans le doc planning). Risques identifiés :
grammaire FEEL potentiellement plus dure que prévu si le moteur DMN de Flowable n'offre aucun mode
restreint (validation texte exhaustive alors nécessaire, même risque de 1ère passe insuffisante que
H1→H1-bis) ; forme exacte de `DmnRepositoryService` non garantie symétrique à `RepositoryService`
BPMN ; source de la liste des tables déployées côté éditeur BPMN à trancher en implémentation.
Suite : Phase 5 (implémentation), démarrage par **M1**.

**Phase 5 (implémentation) — correction M1 (2026-09-11) :** investigation du code source
`flowable-dmn-engine` (`org.flowable.dmn.engine.impl.el`) — **Flowable n'implémente pas FEEL**, le
moteur réel est **JUEL**, le même déjà durci côté BPMN. `ELInputEntryExpressionPreParser`
transforme une cellule brute (fragment `[opérateur] valeur`, pas une expression complète) en
`#{...}` évalué par ce moteur — un identifiant nu en position de valeur expose exactement la
vulnérabilité H1-bis. M1 devient une **adaptation** de `assertSafeConditionExpression` à cette
grammaire plus étroite, pas une grammaire FEEL à inventer — risque réduit par rapport à l'estimation
du planning. Confirmé aussi : ni intervalles `[a..b]` ni listes `"a","b"` ne sont supportés par
l'implémentation Flowable (fixture M11 à base de règles multiples, pas de cellules à liste).
[25-introduction-dmn.md](../specs/25-introduction-dmn.md) §1.2,
[design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-11-feature-introduction-dmn.md) §Sécurité et
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-11-feature-introduction-dmn.md) corrigés en conséquence.

**Phase 5 (implémentation) — livré (2026-09-11), M1 à M12.** Backend (M1-M6, M10) : allowlist JUEL
(`FlowableDmnParserAdapter`, 16 tests unitaires + `DmnJuelSecuritySmokeIT` — bean Spring réel
résolu par un identifiant nu, preuve d'exécution sur le vrai moteur, même doctrine que H1-bis) ;
`DmnDeploymentPort`/`DeployDecisionTableUseCase` ; exception native `type="dmn"` dans
`FlowableBpmnParserAdapter` + vérification de la clé référencée au déploiement d'un process
(`UnknownDecisionKeyException`) ; persistence brouillon (`dmn_draft`, composite
`tenant_id`/`decision_key`) ; endpoints REST (`/api/dmn-decision-drafts`,
`/api/dmn-decision-deployments`) ; tools MCP (`deploy_decision_table`, `list_decision_tables`,
`get_decision_table`). Frontend (M7-M9) : éditeur `dmn-js` (`/admin/dmn-editor`), complément du
panel de propriétés BPMN existant (`decisionTableReferenceKey` sur `serviceTask type="dmn"`),
nouvel écran admin. **Deux bugs réels de sérialisation `moddle-xml`** trouvés et corrigés via un
round-trip E2E réel (pas un test mocké, même leçon que TODO §39) : le conteneur
`bpmn:ExtensionElements` doit être créé via `bpmnFactory.create` (type enregistré) et non
`moddle.createAny`, et les enfants d'un élément générique `createAny` doivent être assignés à la
propriété `$children` spécifiquement, toute autre propriété étant sérialisée en attribut plat
stringifié.

**M11 (fixture livrée)** : `grille-eligibilite-client.dmn`
(`src/main/resources/decisions/`, hit policy `FIRST`) + `dmn-grille-eligibilite-smoke.bpmn20.xml`
(`src/main/resources/processes/`), auto-déployés au démarrage par `TenantDmnDeployment` (nouveau,
miroir de `TenantProcessDeployment`) — mêmes conventions que les process `-smoke` déjà livrés,
preuve de bout en bout par `DmnGrilleEligibiliteSmokeIT` (3 branches de la grille, sans aucun
déploiement manuel dans le test).

**Bug réel trouvé en vérification finale (M12), pas seulement lu dans le code :** `./mvnw verify`
complet a révélé que le déploiement d'**une** table DMN, quelle qu'elle soit, faisait planter le
moteur Flowable 8.0.0 sur le JDK 25 de ce dépôt —
`DmnXMLException: StAXSource(XMLStreamReader) with XMLStreamReader not in
XMLStreamConstants.START_DOCUMENT or XMLStreamConstants.START_ELEMENT state`. Root-cause identifiée
par décompilation (`javap -p -c`) de `flowable-dmn-engine`/`flowable-dmn-xml-converter` 8.0.0, pas
supposée : `DmnParse.execute()` transmet `DmnEngineConfiguration.isEnableSafeDmnXml()` (vrai par
défaut, seul point d'appel de cette méthode dans tout le moteur) comme 3ᵉ argument de
`DmnXMLConverter.convertToDmnModel`, qui choisit alors de **réutiliser** un même
`XMLStreamReader` entre la détection du namespace et la validation de schéma — un
`javax.xml.transform.stax.StAXSource` construit sur ce reader lève l'exception sur ce JDK. Pas de
version Flowable 8.0.x plus récente disponible (8.0.0 = dernière sur Maven Central). **Contournement
sans régression de sécurité** — vérifié dans le même désassemblage : les réglages anti-XXE de
`convertToDmnModel` (DTD/entités externes désactivées) sont appliqués **inconditionnellement**,
indépendamment de ce flag, qui ne pilote que la réutilisation du reader. `WorkflowConfig` déclare
donc un `EngineConfigurationConfigurer<SpringDmnEngineConfiguration>` mettant
`enableSafeDmnXml=false`, documenté en détail sur place. Détecté uniquement parce que M11/M12 ont
exercé un vrai déploiement DMN de bout en bout au lieu de s'arrêter aux tests déjà verts de M1-M10
(qui, eux, n'appellent jamais ce chemin de code précis). `./mvnw verify` complet (1817 tests,
ArchUnit compris) + suite Playwright (71/71, dont 3 scénarios DMN réels) verts après correctif.
Documentation mise à jour : `README.md`, `docs/specs/05-workflow-engine.md` (nouvelle section
« Tables de décision — DMN »), `docs/specs/00-index.md` (entrée 25), `FILE-ATTENTE.md` (clôture
définitive), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§7bis/§7ter), `docs/guides/GUIDE-UTILISATEUR.md` (nouvel écran),
`CHANGELOG.md`.

**Analyse écrite :** [25-introduction-dmn.md](../specs/25-introduction-dmn.md) — état actuel
vérifié dans le code (activation moteur = 2 lignes de config + lever le rejet
`businessRuleTask`), risque dominant identifié = durcissement des expressions FEEL avant de lever
ce rejet (même classe de risque que H1/H1-bis, qui a nécessité deux passes avant d'être fermé),
questions ouvertes D-DMN-SECURITY-OWNER à D-DMN-RESULT (propriétaire de la revue de sécurité,
mécanisme de déploiement, périmètre éditeur intégré, tool MCP, hit policies, masquage du résultat).

**Corrigé (2026-09-11, question utilisateur) :** la version initiale sous-estimait l'état réel de
l'édition BPMN dans FlowForge (« modélisé en externe puis déployé/uploadé ») — `edition-bpmn`
(PR #71, TODO §39) a depuis ajouté un éditeur graphique **intégré** (`bpmn-js` Modeler +
`bpmn-js-properties-panel`, `/admin/bpmn-editor`), au-delà du simple upload de `deploiement-bpmn`
(TODO §35). `dmn-js`, toolkit sœur de `bpmn-js` dans l'écosystème bpmn.io (même éditeur, licence
vérifiée identique — MIT + clause filigrane, déjà acceptée implicitement côté BPMN en prod),
fournit un `Modeler` dédié aux tables de décision : c'est l'équivalent DMN direct de ce qui a déjà
été livré, pas une extrapolation. Nouvelle question ouverte **D-DMN-EDITOR-SCOPE** (§5).

**Impacté :** `application.yml`, `FlowableBpmnParserAdapter`, nouveaux ports/adaptateurs de
parsing/déploiement DMN, écran admin `dmn-editor`, `infrastructure.mcp`,
`README.md`/`docs/specs/05`/`docs/specs/00-index.md`/`FILE-ATTENTE.md` (retrait de la mention
« hors V1 »), `docs/guides/GUIDE-CONCEPTION-BPMN.md`, `docs/guides/GUIDE-UTILISATEUR.md`, `CHANGELOG.md` — voir détail
Phase 5 ci-dessus.

**Revue adversariale post-Phase 9, corrigé (2026-09-11) :** un subagent indépendant a trouvé un
contournement réel de l'allowlist JUEL — `FlowableDmnParserAdapter.assertSafeDecision` ne validait
que les cellules de règle (`inputEntry`/`outputEntry`), jamais le texte de
`<input><inputExpression>`. Or `ELInputEntryExpressionPreParser` injecte ce texte **tel quel**
comme opérande gauche de l'expression enveloppée (`#{ <inputExpressionText> <op> <valeur> }`) —
même surface JUEL qu'une cellule, jamais gardée. Preuve empirique sur le vrai moteur (pas
seulement lecture statique, même doctrine H1/H1-bis) : une table dont toutes les cellules de règle
étaient sûres mais dont l'`inputExpression` valait `connectorServiceTask.getClass().getName()`
passait `extractDecisionKey` sans rejet et, à l'exécution réelle, invoquait effectivement
`getClass().getName()` sur le bean Spring `connectorServiceTask` vivant — exécution de méthode
arbitraire, au-delà de la simple résolution de bean déjà connue. Atteignable par un `TENANT_ADMIN`/
`WORKFLOW_ADMIN` via `POST /api/dmn-decision-deployments` **et** par tout appelant M2M via le tool
MCP `deploy_decision_table`. **Corrigé** : `assertSafeDecision` valide désormais chaque
`InputClause.getInputExpression().getText()` contre un identifiant nu strict
(`[A-Za-z_][A-Za-z0-9_]*`, sans `.`/`(`/`)`/`[`/`]`/`#`/`$`/espace) — même restriction déjà
appliquée aux identifiants dans `assertSafeConditionExpression` côté BPMN (TODO §57 H1). 6 tests
ajoutés (`FlowableDmnParserAdapterTest`), `./mvnw verify` vert (1881 tests), fixture livrée
(`grille-eligibilite-client.dmn`, déjà en identifiants nus) et `DmnJuelSecuritySmokeIT` toujours
verts, non-régression confirmée. **Résidu accepté, pas nouveau** : un identifiant nu légitime
(nom de variable de process) qui ne correspond à aucune variable au runtime continue de résoudre
contre un bean du même nom (même risque déjà accepté pour les identifiants de
`conditionExpression` BPMN) — le correctif ferme l'escalade prouvée vers l'exécution de méthode
via navigation de propriété/chaîne d'appel, pas ce résidu structurel de UEL/JUEL.

**2 constats supplémentaires de la revue, non corrigés, signalés plutôt que tranchés
silencieusement** : (1) `DmnDraftController.save`/`SaveDmnDraftUseCaseImpl` n'a aucune limite de
taille sur le corps `xml` (contrairement à l'endpoint de déploiement multipart, qui réutilise
`spring.servlet.multipart.max-file-size`) — gap déjà présent à l'identique côté `BpmnDraftController`,
pas introduit par ce cycle, sévérité faible (endpoint admin-gated) ; (2) le redéploiement d'une
nouvelle version de table DMN prend effet immédiatement pour les instances de process en cours qui
l'invoquent ensuite — même comportement que BPMN, jamais explicitement tranché pour DMN, purement
comportemental (pas un défaut de sécurité).
