# §35 — Modélisation et déploiement des diagrammes BPMN — écart vision / implémentation

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §35 · [registre](README.md)

**Constat :** [05-workflow-engine.md](../specs/05-workflow-engine.md) retient « Designer visuel :
Flowable UI (web, embarqué) » et « Monitoring instances : Flowable UI Cockpit » ; le `README.md`
reprend cette vision (« designer visuel intégré », « déployés sans redémarrage », « supervisés via le
cockpit Flowable »). En pratique, `pom.xml` ne dépend que de `flowable-spring-boot-starter` (le moteur
d'exécution) — aucune des applications web Flowable UI (Modeler, Cockpit, Task, IDM) n'est intégrée, et
`application.yml` exclut explicitement leurs autoconfigurations (REST API, sécurité, app engine).

Le volet **Cockpit** a déjà une décision tracée : `DEFERRED-FEATURES.md` le classe « Hors scope
produit », remplacé par le viewer lecture seule Vue/`bpmn-js` (`BpmnViewer.vue`, déjà livré). Le volet
**Modeler / déploiement** n'a en revanche **aucune décision équivalente** — c'est un écart de fait, pas
un choix assumé. Le déploiement réel des diagrammes se fait par scan classpath au démarrage
(`TenantProcessDeployment`, `classpath*:/processes/**/*.bpmn20.xml`) : pas de hot-deploy, pas d'écran
d'upload ou de design dans l'UI FlowForge — le `.bpmn20.xml` est modélisé en externe puis déposé dans le
dépôt backend par l'équipe technique (documenté comme limite actuelle dans
[docs/guides/GUIDE-UTILISATEUR.md](../guides/GUIDE-UTILISATEUR.md)).

**À décider :** trois options, non exclusives dans le temps (V1 minimaliste puis cible plus tard) :

- **(a) Acter l'état actuel comme cible V1.** Modélisation externe (Flowable Modeler autonome ou tout
  éditeur BPMN 2.0) + dépôt du fichier dans le repo backend + déploiement au build/redémarrage reste le
  mécanisme définitif. Impose de corriger `README.md` / `05-workflow-engine.md` (retirer « designer
  visuel intégré » et « sans redémarrage », qui décrivent une fonctionnalité non livrée).
- **(b) Intégrer Flowable Modeler** (webapp séparée) pour permettre le design et le déploiement en base
  depuis un navigateur, avec rechargement à chaud des déploiements Flowable
  (`RepositoryService.createDeployment()`) — récupère la vision initiale, mais ajoute une app tierce à
  opérer/sécuriser par tenant (probablement hors scope au vu de la décision Cockpit ci-dessus).
- **(c) Écran d'upload minimal dans FlowForge.** Upload d'un `.bpmn20.xml` validé (schéma BPMN 2.0,
  `processDefinitionKey` cohérent avec la config workflow) via l'API FlowForge existante, déploiement
  via `RepositoryService` — sans réintroduire toute la stack Flowable UI ni son coût opérationnel.

**Impacte :** [05-workflow-engine.md](../specs/05-workflow-engine.md), `README.md` (sections
Proposition de valeur et Fonctionnalités principales), [docs/guides/GUIDE-UTILISATEUR.md](../guides/GUIDE-UTILISATEUR.md),
`TenantProcessDeployment.java`.

**Résolu (2026-08-22, feature `deploiement-bpmn`) :** option **(c)** retenue — écran d'upload
minimal dans FlowForge, sans Flowable Modeler ni Cockpit (décision produit tranchée directement,
cette session) — plus prévisualisation obligatoire du diagramme avant tout import (réutilisation du
rendu `bpmn-js`, décision complémentaire non envisagée par les 3 options initiales).

- Backend : `DeployBpmnProcessUseCase`/`GetProcessDeploymentInfoUseCase` (domaine),
  `BpmnParserPort`/`FlowableBpmnParserAdapter` (parsing/validation BPMN 2.0, extraits en port pour
  respecter la règle ArchUnit domaine-sans-framework), `WorkflowDeploymentPort`/
  `FlowableWorkflowDeploymentAdapter` (`RepositoryService`/`RuntimeService`),
  `WorkflowBpmnDeploymentController` (`GET .../info`, `POST` multipart,
  `@PreAuthorize` `TENANT_ADMIN`/`WORKFLOW_ADMIN`). Clé de processus sans `WorkflowTypeConfig`
  correspondante → rejet strict (`UnknownWorkflowKeyException` → 404, symétrique
  `WorkflowTypeConfigNotFoundException`).
- Frontend : `BpmnCanvas.vue` (rendu bpmn-js bas niveau, extrait de `BpmnViewer.vue` qui le
  réutilise désormais en interne, contrat public inchangé) ; `BpmnUploadPreview.vue` (lecture
  locale, prévisualisation, info de redéploiement, confirmation explicite avant tout appel de
  déploiement) ; nouvelle section dans `/admin/workflow-types` (pas de nouvel écran dédié).
- Le scan classpath au démarrage (`TenantProcessDeployment`) n'est **pas remplacé** — l'upload
  admin est un mécanisme complémentaire de déploiement à chaud.
- Taille d'upload : pas de nouvelle propriété — réutilise le seuil global
  `spring.servlet.multipart.max-file-size` existant (convention « source de vérité unique » déjà
  établie pour les pièces jointes dossier, découverte en implémentation et signalée avant de
  trancher plutôt que silencieusement contournée).
- `README.md` et `05-workflow-engine.md` corrigés (retrait des mentions non livrées : designer
  visuel intégré, cockpit Flowable) ; `docs/guides/GUIDE-UTILISATEUR.md` §3 documente les deux mécanismes de
  déploiement qui coexistent.
