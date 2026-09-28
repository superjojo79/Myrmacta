# FlowForge — Roadmap Fonctionnelle

> **Document de référence** — Dernière vérification code : 28 septembre 2026 (`main` @ `f9638c7`, PR #137)  
> **Statut** : statuts **recoupés sur le code et sur l’API GitHub**, pas sur l’analyse d’origine du 12/09  
> **Sources de vérité produit :** [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md), [TODO.md](TODO.md)  
> **Vagues A, B, C et A4 : livrées.** `enrichissement-reporting` (PR #132), `inbox-notifications-persistee` (PR #135) et `outillage-dmn` ([PR #134](https://github.com/superjojo79/FlowForge/pull/134)) : livrés.
> `portail-externe` ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), TODO §68) : livré. **Prochain :** `signature-electronique`.
> **Vague D (nouvelle, 28/09) :** priorités issues de l'analyse critique fonctionnelle — adéquation
> aux cas d'usage et industrialisation. Aucun lot cadré ; voir [Catégorie 7](#catégorie-7--adéquation-métier--industrialisation-vague-d).

---

## Contexte (vérifié)

FlowForge est un SaaS multi-tenant à architecture hexagonale. Stack **réelle** (pas celle de la v1.0 du 12/09) :

| Couche | Code actuel |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8.0.x Community — BPMN 2.0 **et** DMN (TODO §61, PR #123) |
| Auth | OIDC BFF (Keycloak) — pas de JWT dans le browser |
| Frontend | **Vue 3 + TypeScript + Vite + PrimeVue + Pinia**, embarqué dans `static/` |
| MCP | **27 tools** sur `POST /api/m2m/mcp` (Streamable HTTP, JWT M2M `API_SERVICE`) — dont 4 ajoutés par `outillage-dmn` (PR #134) |
| Connecteurs livrés | `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback`, `batch-import`, `kafka` |
| Antivirus | `AttachmentScanPort` + adaptateur ClamAV opt-in (PR #131) |
| Isolation | 1 instance Spring Boot = 1 tenant (`app.tenant.id`) |

---

## Synthèse — ce qu’il reste réellement à couvrir

| Priorité | Item | Lot | Statut |
|---|---|---|---|
| Ensuite | **2.4 Signature électronique** | `signature-electronique` | Non démarré, **après le portail** — [30-signature-electronique.md](analysis%20doc/30-signature-electronique.md) |
| Décision | **D0** Positionnement produit (intégrateur / hébergement dédié vs SaaS mutualisé) | — | **À trancher** — conditionne D5 et le discours `README.md` |
| Vague D | **D1** Modèle de champs enrichi (montant/devise, groupes répétables, référence externe) | à nommer | Non cadré |
| Vague D | **D2** Recherche sur champs dynamiques (JSONB + plein texte) | à nommer | Non cadré — après D1 |
| Vague D | **D3** Intégrations entreprise (OAuth2 client credentials, mapping réponse → dossier, e-mail entrant) | à nommer | Non cadré |
| Vague D | **D4** Promotion de configuration entre environnements | à nommer | Non cadré |
| Vague D | **D5** Scalabilité intra-tenant — verrou des jobs planifiés | à nommer | Audit d'abord |
| Vague D | **D6** Garde-fous des déploiements MCP | à nommer | Non cadré |
| Vague D | **D7** Internationalisation de l'UI | à nommer | Non cadré — selon marché |
| Non planifié | **5.3 bis** Export BI JDBC / Power BI | — | Écarté du lot reporting (D-BI-MECHANISM) ; nouveau produit si besoin confirmé |

### Déjà couvert par le code — ne plus traiter comme un gap

| Item v1.0 | Preuve |
|---|---|
| **2.1 Import batch (SFTP / CSV / Excel)** | Connecteur `batch-import` (lot B1, PR #129). « ERP » = cas d’usage de `batch-import`. |
| **2.2 Kafka** | Connecteur `kafka` (lot B1, PR #129). |
| **2.3 Antivirus** | `ClamAvAttachmentScanAdapter` opt-in (lot C, PR #131). |
| **3.2 Inbox notifications persistée** | `user_notification`, `NotificationInboxController`, badge serveur (PR #135, TODO §66). |
| **3.3 Dashboard opérationnel** | `/rapports` + widgets dead-letter et tâches sans assigné (PR #132). |
| **4.1 Éditeur DMN tableur** | `/admin/dmn-editor` + `dmn-js` Modeler (TODO §61). |
| **4.2 Visualiseur DRD** | Sous-vue DRD du même Modeler. |
| **5.2 Alerting SLA** | Alertes EMAIL/WEBHOOK/BROWSER par palier (PR #132, TODO §65). |
| **5.3 Export BI** | Export CSV planifié vers `DocumentStoragePort` (PR #132). JDBC/Power BI non couverts. |
| **Tests PostgreSQL ciblés** | Testcontainers : isolation schéma, GRANT par rôle, séquence multi-pods (PR #66). |
| **Provisioning tenant** | `Dockerfile` + `scripts/provision-tenant.sh` (lot A1, PR #119). |
| **Headers HTTP** | CSP/HSTS/XFO (lot A4, PR #128/#130). |

### Hors scope actif (ne pas couvrir tant que V1 n’est pas rouvert)

| Item v1.0 | Motif |
|---|---|
| **6.1 SSE multi-pod / Redis** | `FILE-ATTENTE.md` : *Ne pas lancer* (pas de Redis dans la stack figée). |
| **1.4 Tools MCP composites** | Doctrine MCP : tools atomiques qui réutilisent les use cases REST. |
| Validation FEEL temps réel | Flowable 8 évalue le DMN en **JUEL**, pas FEEL. Allowlist au deploy. |
| Simulation DMN depuis l’éditeur | Non-goal de `introduction-dmn`. |
| Chart.js SLA, Cockpit Flowable, codegen OpenAPI, OTel/Grafana | Décisions V1 déjà tranchées. |

---

## Détail par catégorie

### Catégorie 1 : MCP & IA

| Item | Statut |
|---|---|
| 1.1 `evaluate_decision` | ✅ Livré (`outillage-dmn`, PR #134, TODO §67) |
| 1.2 `TestDecisionTool` | ✅ Livré (`test_decision`, JSON et CSV) — même PR |
| 1.3 Historique / rollback DMN | ✅ Livré — même PR (versioning Flowable, « Redéployer la version N ») |
| 1.4 Tools composites | ⛔ Ne pas couvrir (doctrine MCP atomique) |

### Catégorie 2 : Connecteurs & Intégrations

| Item | Statut |
|---|---|
| 2.1 Import batch | ✅ Livré (`batch-import`, PR #129) |
| 2.2 Kafka | ✅ Livré (`kafka`, PR #129) |
| 2.3 Antivirus ClamAV | ✅ Livré (PR #131) — `ClamAvAttachmentScanIT` à jouer avec Docker |
| 2.4 Signature électronique | ❌ Non démarré — lot `signature-electronique`, **après le portail** |
| 2.5 ERP | ✅ Couvert par `batch-import` (décision Phase 1 de B1) |

### Catégorie 3 : Portail externe & UX

| Item | Statut |
|---|---|
| 3.1 Portail externe tokenisé | ✅ Livré (PR #138, TODO §68) — opt-in `APP_PORTAL_ENABLED` |
| 3.2 Inbox notifications | ✅ Livré (PR #135) |
| 3.3 Dashboard opérationnel | ✅ Livré (PR #80 + widgets PR #132) |

### Catégorie 4 : Édition & visualisation DMN

| Item | Statut |
|---|---|
| 4.1 Éditeur de tables | ✅ Livré (TODO §61) |
| 4.2 Visualiseur DRD | ✅ Livré |

### Catégorie 5 : Sécurité, conformité & observabilité

| Item | Statut |
|---|---|
| 5.1 Versioning & rollback DMN (UI) | ✅ Livré (PR #134) |
| 5.2 Alerting SLA | ✅ Livré (PR #132) |
| 5.3 Export BI | ⚠️ CSV planifié livré (PR #132) ; JDBC / Power BI non planifié |

### Catégorie 6 : Performance & qualité interne

| Item | Statut |
|---|---|
| 6.1 SSE multi-pod | ⛔ Hors V1 (pas de Redis) |
| 6.2 Tests PostgreSQL réels | ⚠️ Partiel volontaire (D-TESTCONTAINERS, adoption ciblée) |

### Catégorie 7 : Adéquation métier & industrialisation (Vague D)

> **Origine :** analyse critique fonctionnelle du 28/09/2026 (fonctionnalités × cas d'usage du
> `README.md` : sinistres, subventions, onboarding, réclamations, et intégration ERP type
> D365 F&O). Le socle technique et la sécurité ne sont pas en cause ; les manques sont
> **fonctionnels** et de **positionnement**. Chaque gap ci-dessous est **recoupé sur le code**
> de `main` @ `f9638c7`. Aucun lot n'est cadré : `dev-lifecycle` Phase 1 obligatoire avant tout
> code, et les décisions ouvertes restent à trancher par l'utilisateur, pas en cours de route.

#### D0 — Décision de positionnement (préalable, pas un lot de code)

**Constat :** le `README.md` promet « modéliser sans développement », mais la conception d'un
process exige une expertise BPMN (`GUIDE-CONCEPTION-BPMN.md`, 33 Ko de conventions et de noms de
delegates) et toute logique hors des 7 delegates allowlistés impose du code Java. Le modèle
« 1 instance = 1 tenant » (décision n° 13) donne une isolation forte, mais une densité faible
(une JVM + un moteur Flowable + 13 jobs `@Scheduled` par tenant).

**Options à trancher :**
- **A. Outil de delivery pour intégrateurs**, hébergement dédié par client — cohérent avec la
  décision n° 13 ; le discours « sans développement » est abandonné.
- **B. SaaS mutualisé en libre-service** — impose de rouvrir la décision n° 13 et d'investir dans
  l'UX de modélisation pour des utilisateurs non techniques.

**Impacte :** `README.md` (vision, proposition de valeur, § Statut encore « développement à
démarrer »), priorité de D5.

#### D1 — Modèle de champs dynamiques enrichi

**Constat vérifié :** `FieldType` = `STRING, NUMBER, DATE, BOOLEAN, SELECT, FILE`
(`domain/dossier/model/FieldType.java`). Pas de montant avec devise, pas de groupe répétable,
pas de référence vers une entité externe, pas de champ calculé ni de règle de visibilité.

**Cas d'usage débloqués :** sinistres (garanties, postes de préjudice), subventions (lignes
budgétaires), onboarding fournisseur (référence au compte fournisseur ERP).

**Périmètre à poser en Phase 1 :** `MONEY` (montant + devise ISO 4217, précision décimale),
groupe répétable (lignes typées), `EXTERNAL_REF` (système + identifiant) ; champs calculés et
visibilité conditionnelle en option.

**Coutures :** `FieldDefinition` et sa validation, blocage 409 sur schéma incompatible (PR #87),
formulaires `dossier-fields` (TODO §52), variables des templates PDF, export CSV / BI, masquage
`sensitive`, tool MCP `define_dossier_type`. **Taille :** L.

#### D2 — Recherche sur les champs dynamiques

**Constat vérifié :** `DossierSpecifications` ne filtre que sur `status`, `dossierTypeCode` et un
**préfixe** de `reference`. Aucune recherche sur les champs JSONB, aucun plein texte. Bloquant
au-delà de quelques milliers de dossiers par tenant.

**Périmètre à poser :** filtres sur champs déclarés `searchable` (index GIN JSONB), recherche
plein texte (`tsvector`) sur la référence et les champs texte. Respecter le masquage `sensitive`
(un champ sensible n'est pas cherchable, ou seulement par égalité stricte).

**Coutures :** partage `FieldDefinition` avec D1 → **séquencer après D1**, pas en parallèle.
Syntaxe propre à PostgreSQL → tests Testcontainers (extension ciblée de D-TESTCONTAINERS, les IT
H2 ne couvrent pas les opérateurs JSONB). Tool MCP `search_dossiers`. **Taille :** M.

#### D3 — Intégrations entreprise

| Sous-lot | Constat vérifié | Débloque | Taille |
|---|---|---|---|
| **D3a** OAuth2 client credentials sur `http-rest` | Seul un `authHeaderValue` **statique** est supporté (`HttpRestConnector`). Aucun `client_credentials` dans le code. | Appels directs aux API protégées par Microsoft Entra ID (D365 F&O OData, Dataverse, Graph) dont les jetons expirent au bout d'une heure environ. | M |
| **D3b** Mapping réponse de connecteur → champs du dossier | `ConnectorServiceTask` pose la réponse dans la seule variable `connectorResult` ; aucun des 7 delegates allowlistés (`AllowedBpmnDelegates`) n'écrit de champ de dossier depuis un process. | Enrichissement automatique (score, données d'un référentiel, identifiant créé dans l'ERP). | M |
| **D3c** E-mail entrant (IMAP / Microsoft Graph), connecteur TRIGGER | `smtp` n'est qu'un SERVICE d'envoi ; aucun connecteur de réception. | Réclamations, demandes reçues par e-mail, pièces jointes rattachées au dossier (scan ClamAV existant). | M |
| **D3d** *(à confirmer)* Azure Service Bus / Event Grid, TRIGGER | Kafka existe (PR #129), pas les services Azure. | Business Events D365 F&O sans passer par un relais. | M |

**Coutures :** D3a et D3b touchent des surfaces disjointes (connecteur `http-rest` vs delegate
Flowable) → parallélisables. D3b ajoute un delegate : repasser par l'allowlist BPMN (TODO §57,
§69), pas d'expression libre. D3c/D3d ajoutent une entrée au catalogue : **un seul cycle** pour
les deux s'ils sont retenus (même leçon que B1 : pas plusieurs PR concurrentes sur le catalogue).
D3a peut démarrer en parallèle de D1 (aucune couture commune).

#### D4 — Promotion de configuration entre environnements

**Constat vérifié :** aucun mécanisme d'export/import de configuration. Types de dossier, BPMN,
DMN, templates, schémas de questions et instances de connecteurs se recréent à la main (ou via
MCP) sur chaque environnement.

**Périmètre à poser :** un bundle versionné (export / import / diff à blanc), **sans secrets**
(les secrets de connecteurs sont ressaisis ou injectés par variables sur la cible), idempotent,
journalisé. Indispensable pour un intégrateur qui fait recette puis production (option A de D0).
**Taille :** L.

#### D5 — Scalabilité intra-tenant : verrou des jobs planifiés

**Constat :** 13 méthodes `@Scheduled`, aucun ShedLock ni verrou distribué trouvé. Certains jobs
ont leur propre état persisté (`connector_schedule_state`) : **audit d'abord**, job par job, pour
savoir lesquels s'exécuteraient en double si un tenant passe à 2 pods.

**Périmètre possible :** verrou par advisory lock PostgreSQL — compatible avec la stack figée
(pas de Redis). Le SSE multi-pod et le rate-limit distribué restent **hors scope**
(`FILE-ATTENTE.md` § *Ne pas lancer*). Priorité liée à D0. **Taille :** S (audit) + M.

#### D6 — Garde-fous des déploiements MCP

**Constat :** `deploy_workflow`, `deploy_decision_table` et `redeploy_decision_table` agissent
directement sur l'instance, sans brouillon ni validation humaine. C'est puissant pour un agent,
mais risqué en production.

**Périmètre à poser :** mode « brouillon » par défaut pour les déploiements venant de MCP
(réutiliser les brouillons persistés des éditeurs BPMN et DMN) avec validation par un
`WORKFLOW_ADMIN`, et/ou propriété de démarrage interdisant les écritures de déploiement MCP hors
environnements hors production. **Taille :** S à M.

#### D7 — Internationalisation de l'UI

**Constat vérifié :** pas de `vue-i18n` ; l'UI est en une seule langue, alors que les templates
PDF gèrent déjà des variantes par langue. Priorité selon le marché visé (D0). **Taille :** M.

---

## Priorisation

1. `signature-electronique` — démarrable (portail livré, PR #138) ; fournisseur et niveau eIDAS à trancher en Phase 1.
2. **D0** — trancher le positionnement (décision produit, pas de code).
3. **D1** puis **D2** — modèle de champs enrichi, puis recherche (couture `FieldDefinition` commune).
   **D3a** et **D3b** parallélisables avec D1 (surfaces disjointes).
4. **D3c** (+ **D3d** si confirmé) — e-mail entrant et bus Azure, un seul cycle catalogue.
5. **D4** — promotion de configuration entre environnements.
6. **D6** — garde-fous des déploiements MCP.
7. **D5** — audit des jobs planifiés, puis verrou si un tenant doit tourner sur plusieurs pods.
8. **D7** — i18n de l'UI, selon le marché.
9. Vérifications d’environnement hors file : `ClamAvAttachmentScanIT` sous Docker, smoke Keycloak réel.

Correctif documentaire sans cycle : le § *Statut* du `README.md` annonce encore « développement à
démarrer ».


---

## Historique des versions

| Version | Date | Auteur | Changements |
|---|---|---|---|
| 1.0 | 12/09/2026 | Assistant IA (analyse code) | Création initiale — **plusieurs statuts et la stack étaient faux** (DMN editor, MCP 25+, React, FEEL, PG tests, provisioning). |
| 1.1 | 14/09/2026 | Vérification `main` + Vague B | Statuts recoupés sur le code ; Vague B marquée en cours ; liste réelle de ce qu’il reste à couvrir. |
| 1.2 | 23/09/2026 | Rattrapage post-merges #131/#132/#135 | Vagues A/B/C/A4, reporting et inbox passés en livré ; catalogue connecteurs à 8 ; portail et signature scindés en deux lots ; DMN et portail en cours. |
| 1.3 | 28/09/2026 | Analyse critique fonctionnelle | Ajout de la Vague D (Catégorie 7) : décision de positionnement D0 et lots D1–D7 recoupés sur `main` @ `f9638c7` ; MCP à 27 tools ; priorisation étendue après `signature-electronique`. |
