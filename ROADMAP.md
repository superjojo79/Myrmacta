# FlowForge — Roadmap Fonctionnelle

> **Document de référence** — Dernière vérification code : 14 septembre 2026 (`main` @ PR #124)  
> **Statut** : statuts **recoupés sur le code**, pas sur l’analyse d’origine du 12/09  
> **Sources de vérité produit :** [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md), [TODO.md](TODO.md)  
> **Vague B :** `connecteurs-cible` (Kafka + import batch + ERP) — **en cours de développement** (consigne session). Aucun de ces trois types n’est encore dans le catalogue de `main`.

---

## Contexte (vérifié)

FlowForge est un SaaS multi-tenant à architecture hexagonale. Stack **réelle** (pas celle de la v1.0 du 12/09) :

| Couche | Code actuel |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8.0.x Community — BPMN 2.0 **et** DMN (TODO §61, PR #123) |
| Auth | OIDC BFF (Keycloak) — pas de JWT dans le browser |
| Frontend | **Vue 3 + TypeScript + Vite + PrimeVue + Pinia**, embarqué dans `static/` |
| MCP | **23 tools** sur `POST /api/m2m/mcp` (Streamable HTTP, JWT M2M `API_SERVICE`) — pas 25+, pas SSE comme transport MCP |
| Connecteurs livrés | `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback` |
| Isolation | 1 instance Spring Boot = 1 tenant (`app.tenant.id`) |

La v1.0 de ce fichier (12/09) décrivait Spring Boot 3 / Java 17 / Flowable 6 / React / MCP-SSE : **faux** par rapport au dépôt.

---

## Synthèse — que faut-il réellement couvrir ?

Trois filtres appliqués à chaque item :

1. **Déjà dans le code** → ne plus le traiter comme un gap.
2. **Vague B en cours** → déjà pris en charge par le cycle `connecteurs-cible` ; ne pas relancer en parallèle.
3. **Décision V1 contraire** (`FILE-ATTENTE.md` § *Ne pas lancer*) → hors roadmap active.

### À couvrir maintenant (Vague B — un seul cycle)

| ID | Item | Pourquoi |
|---|---|---|
| **B1a** | Import batch (CSV / fichier) | Lot B1. Pas de `CreateDossierBatchUseCase` ni de type catalogue. |
| **B1b** | Connecteur Kafka | Lot B1. Aucun type `kafka` dans `ConnectorRegistry`. |
| **B1c** | Connecteur ERP | Lot B1 **absent de la v1.0** de ce fichier, pourtant nommé dans `FILE-ATTENTE.md`. Même couture catalogue / `ExecuteConnectorRequest`. |

Ne **pas** découper B1 en 3 PRs (conflits catalogue). Ne **pas** lancer un 4ᵉ connecteur (signature, ClamAV, etc.) dans le même cycle.

### Déjà couvert par le code — retirer de la liste des gaps

| Item v1.0 | Preuve |
|---|---|
| **4.1 Éditeur DMN tableur** | `/admin/dmn-editor` + `dmn-js` Modeler (TODO §61). Tables + sous-vues natives. |
| **4.2 Visualiseur DRD** | Même Modeler : sous-vue DRD (`viewer.created` / `getActiveViewer`). |
| **Évaluation DMN runtime** | `serviceTask flowable:type="dmn"` + `DmnDecisionService` (IT réelles). Ce n’est **pas** un tool MCP autonome. |
| **Reporting SLA humain** | `/rapports`, `GET /api/reports/sla`, inbox `slaRemainingHours`, CSV SLA. |
| **Export CSV dossiers** | `GET /api/dossiers/export.csv` (champs sensibles masqués). |
| **Tests PostgreSQL ciblés** | Testcontainers : `TenantSchemaIsolationIT`, `TenantRoleIsolationIT`, `SequentialReferenceMultiPodIT`. Le reste des IT reste H2 **volontairement**. |
| **Provisioning tenant** | Lot A1 livré : `Dockerfile` + `scripts/provision-tenant.sh` + `context-path` hors `local` (PR #119). |
| **Antivirus — port** | `AttachmentScanPort` + `NoOpAttachmentScanAdapter` (D-AV). Seul l’adaptateur réel manque (Vague C). |

### Hors scope actif (ne pas couvrir tant que V1 n’est pas rouvert)

| Item v1.0 | Motif |
|---|---|
| **6.1 SSE multi-pod / Redis** | `FILE-ATTENTE.md` : *Ne pas lancer* (pas de Redis dans la stack figée). |
| **1.4 Tools MCP composites** | Doctrine MCP : tools atomiques qui réutilisent les use cases REST. Un composite dupliquerait `CreateDossierUseCase` / `StartWorkflowUseCase`. |
| Validation FEEL temps réel | Flowable 8 évalue le DMN en **JUEL**, pas FEEL. Allowlist déjà au deploy (`FlowableDmnParserAdapter`). |
| Simulation DMN depuis l’éditeur | Non-goal explicite de `introduction-dmn` (même discipline que l’éditeur BPMN). |
| Chart.js SLA, Cockpit Flowable, codegen OpenAPI, OTel/Grafana | Décisions V1 déjà tranchées. |

### Gaps réels **hors Vague B** (backlog, pas le cycle en cours)

À ne démarrer **qu’après** B1 (et sans empiéter sur Compose si ClamAV).

| Priorité file | Item | Statut code | Notes |
|---|---|---|---|
| Vague C | **2.3 Scan pièces jointes (ClamAV)** | Port no-op | **Pas** un connecteur CALLBACK. Adaptateur + Compose, après A1 (déjà livré). |
| Optionnel A4 | Headers HTTP globaux (CSP/HSTS) | CSP preview HTML seulement | Reliquat S-9. Pas dans la v1.0. |
| Produit, pas cadré | **3.1 Portail externe tokenisé** | Aucune route `/portal` | Vrai trou « guichet » ; **aucune analyse lifecycle**. |
| Produit, pas cadré | **2.4 Signature électronique** | Aucune trace DocuSign/Yousign/eIDAS | Hors file actuelle. |
| Produit, pas cadré | **3.2 Inbox notifications persistée** | SSE JVM-local uniquement | Outbox notifications ≠ historique utilisateur. |
| Produit, pas cadré | **5.2 Alerting SLA proactif** | Reporting a posteriori seulement | `/rapports` + `AT_RISK` existent ; pas de job d’alerte/escalade. |
| **Implémenté** (TODO §66) | **1.1 `evaluate_decision` MCP** | 27 tools | Phase 5 `outillage-dmn` 2026-09-23 — MCP + REST + Tester UI. |
| **Implémenté** (TODO §66) | **1.2 / 1.3 / 5.1** tests + historique/rollback DMN | versions Flowable listées | Même cycle ; JSON et CSV ; redéploiement version N. |
| Produit, pas cadré | **3.3 Dashboard opérationnel widgets** | `/rapports` + inbox tâches | Incrément UX, pas une page vide. |
| Produit, pas cadré | **5.3 BI JDBC / Power BI / SFTP** | CSV dossiers + CSV SLA | Agrégations configurables / JDBC = nouveau produit. |

---

## Catégorie 1 : MCP & IA

Catalogue MCP **réel** (27) : `define_dossier_type`, `deploy_workflow`, `get_dossier`, `get_dossier_type`, `list_document_templates`, `list_dossier_types`, `list_workflow_types`, `query_sla_summary`, `search_dossiers`, `list_connector_definitions`, `list_connector_instances`, `get_connector_instance`, `define_connector_instance`, `list_task_question_schemas`, `get_task_question_schema`, `define_task_question_schema`, `create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document`, `deploy_decision_table`, `list_decision_tables`, `get_decision_table`, `evaluate_decision`, `test_decision`, `list_decision_table_versions`, `redeploy_decision_table`.

### 1.1 EvaluateDecisionTool

**Statut vérifié :** ✅ Implémenté (Phase 5, 2026-09-23) — tool `evaluate_decision`, `POST /api/dmn-decision-deployments/evaluate`, bouton Tester.  
**Priorité réelle :** moyenne (confort agent), **pas** critique.  
**Cadrage :** `outillage-dmn` (TODO §66) — D-DMN-EVAL-SURFACE = MCP + REST + bouton Tester ; D-DMN-EVAL-VERSION = version optionnelle.

Un agent peut déjà : déployer une table, déployer un process `serviceTask type=dmn`, démarrer le workflow. Il n’existe pas d’appel MCP *ad hoc* `decisionKey` + `inputs` → résultat. `DmnJuelSecuritySmokeIT` / `DmnServiceTaskEndToEndSmokeIT` prouvent `DmnDecisionService` côté serveur.

### 1.2 TestDecisionTool

**Statut vérifié :** ✅ Implémenté — tool `test_decision`, `POST /api/dmn-decision-deployments/test` (JSON et CSV).  
**Cadrage :** même cycle `outillage-dmn` — D-DMN-TEST-FORMAT = JSON et CSV.

### 1.3 GetDecisionHistoryTool + RollbackDecisionTool

**Statut vérifié :** ✅ Implémenté — `list_decision_table_versions` et `redeploy_decision_table` (pas de suppression, confirmation uniquement dans l'éditeur).  
`list_decision_tables` / `get_decision_table` exposent toujours la **dernière** version. Pas de table `decision_deployment_history` applicative — l'historique est celui du versioning Flowable.  
**Cadrage :** même cycle — D-DMN-ROLLBACK-UX = « Redéployer la version N » ; D-DMN-HISTORY-RETENTION = pas de purge.

### 1.4 CompositeScenarioTool

**Statut vérifié :** ❌ Non implémenté — **et à ne pas couvrir** dans la doctrine actuelle.  
A3 a livré 4 tools d’écriture atomiques. Un `CreateDossierAndStartWorkflowTool` recouperait `CreateDossierUseCase` (couture Vague B).

---

## Catégorie 2 : Connecteurs & Intégrations

Catalogue `main` : `http-rest` (TRIGGER+SERVICE), `flowable-signal` (TRIGGER+SERVICE), `scheduler` (TRIGGER), `smtp` (SERVICE), `document-storage` (SERVICE), `flowable-callback` (CALLBACK).

### 2.1 BatchImportConnector (SFTP / CSV / Excel)

**Statut vérifié :** ❌ Absent de `main` — **en cours (Vague B)**.  
À couvrir dans B1. `CreateDossierBatchUseCase` n’existe pas encore (seulement mentionné comme couture future dans `FILE-ATTENTE.md`).

### 2.2 KafkaConnector

**Statut vérifié :** ❌ Absent de `main` — **en cours (Vague B)**.  
À couvrir dans B1. Pas de dépendance Kafka, pas de type catalogue.

### 2.3 AttachmentScan (ClamAV)

**Statut vérifié :** ⚠️ Port livré, adaptateur no-op.  
**Pas un connecteur.** `NoOpAttachmentScanAdapter` est invoqué à l’upload (`DossierElementUploadController`), pas en CALLBACK Flowable. **Vague C**, après B, propriétaire Compose.

### 2.4 ElectronicSignatureConnector

**Statut vérifié :** ❌ Non implémenté.  
Hors file. Aucun symbole DocuSign / Yousign / eIDAS.

### 2.5 Connecteur ERP *(ajout — manquant en v1.0)*

**Statut vérifié :** ❌ Absent de `main` — **en cours (Vague B, B1c)**.  
Troisième jambe de `connecteurs-cible`. Même cycle que 2.1 / 2.2.

---

## Catégorie 3 : Portail externe & UX

### 3.1 Portail externe tokenisé

**Statut vérifié :** ❌ Non implémenté.  
Aucune route publique `/portal/{token}`. Toute UI passe par OIDC BFF. Gap produit réel, **non cadré** (pas d’analyse `dev-lifecycle`).

### 3.2 Notification Inbox persistée

**Statut vérifié :** ❌ Non implémenté.  
SSE : `NotificationSseController` + `SseNotificationAdapter` (map JVM). Canal email / webhook sortant existants. Pas de table `notification` utilisateur UNREAD/READ.

### 3.3 Operational Dashboard

**Statut vérifié :** ⚠️ **Partiellement livré** (`reporting-sla`, PR #80).  
`/rapports` : KPI, conformité, export CSV, filtres période/type. Inbox tâches + `slaRemainingHours`. Manque : widgets files bloquées / instances en erreur configurables. Incrément, pas une page à créer from scratch.

---

## Catégorie 4 : Édition & visualisation DMN

### 4.1 DMN Table Editor

**Statut vérifié :** ✅ **Livré** (TODO §61, `/admin/dmn-editor`).  
`dmn-js` Modeler, brouillon persisté, déploiement à chaud, MCP `deploy_decision_table`.  
Hors livré (non-goals du cycle) : simulation dans l’éditeur, validation FEEL (le moteur est JUEL).

### 4.2 DRD Visualizer

**Statut vérifié :** ✅ **Livré** (sous-vue DRD de `dmn-js`).  
Pas un visualiseur maison à reconstruire.

---

## Catégorie 5 : Sécurité, conformité & observabilité

### 5.1 Decision Versioning & Rollback UI

**Statut vérifié :** ❌ Non implémenté (UI / tools d’historique).  
Le moteur versionne ; l’UI n’expose que la version courante.

### 5.2 SLA Alerting

**Statut vérifié :** ❌ Non implémenté.  
Seuils `slaTargetHours` + statuts `ON_TRACK` / `AT_RISK` / `BREACHED` : **oui**. Job d’alerte email/escalade : **non**.

### 5.3 Export BI personnalisable

**Statut vérifié :** ⚠️ CSV basique livré ; BI JDBC / planification SFTP **non**.  
`GET /api/dossiers/export.csv` + export CSV des rapports SLA.

---

## Catégorie 6 : Performance & qualité interne

### 6.1 Multi-pod SSE

**Statut vérifié :** ⚠️ Hors V1, stack sans Redis.  
**Ne pas couvrir.** `FILE-ATTENTE.md` § *Ne pas lancer*.

### 6.2 PostgreSQL Real Tests

**Statut vérifié :** ⚠️ **Déjà partiel** (`fiabilisation-tests-plateforme`, PR #66).  
Testcontainers PostgreSQL 16 pour isolation `search_path`, GRANT par rôle, séquence multi-pods. Décision D-TESTCONTAINERS = adoption **ciblée**, pas un basculement de toute la suite H2. Item v1.0 « tout passer sur PG » = **ne pas relancer**.

---

## Priorisation corrigée (après recoupement code)

### Cycle en cours — Vague B uniquement

1. Import batch  
2. Kafka  
3. ERP  

### Ensuite, si on reprend la file officielle

4. Vague C — adaptateur ClamAV (`AttachmentScanPort`)  
5. A4 optionnel — headers HTTP globaux  

### Ensuite seulement — nouveaux lots (analyse `dev-lifecycle` obligatoire)

6. Portail externe tokenisé (si le besoin « guichet » est confirmé)  
7. Alerting SLA (s’appuie sur le reporting déjà là)  
8. `evaluate_decision` MCP (confort, pas un prérequis DMN)  
9. Historique / rollback DMN (UI + tools, versioning Flowable)  
10. Inbox notifications persistée  
11. Signature électronique  

---

## Historique des versions

| Version | Date | Auteur | Changements |
|---|---|---|---|
| 1.0 | 12/09/2026 | Assistant IA (analyse code) | Création initiale — **plusieurs statuts et la stack étaient faux** (DMN editor, MCP 25+, React, FEEL, PG tests, provisioning). |
| 1.1 | 14/09/2026 | Vérification `main` + Vague B | Statuts recoupés sur le code ; Vague B marquée en cours ; liste réelle de ce qu’il reste à couvrir. |
