# FlowForge — Roadmap Fonctionnelle

> **Document de référence** — Dernière vérification : 22 octobre 2026 (`main` @ `b8885800`, PR #178)  
> **Statut** : statuts **recoupés sur le code et sur l’API GitHub**, pas sur l’analyse d’origine du 12/09  
> **Sources de vérité produit :** [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md), [TODO.md](TODO.md)  
> **Vagues A, B, C, A4 et D : livrées.** Mandat « roadmap complète » clos
> ([AVANCEMENT.md](docs/roadmap-goal/AVANCEMENT.md)). `portail-externe` (PR #138) et
> `signature-electronique` (PR #149, stub sans provider) : livrés.
> **File active :** [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §7 (exemple riverain).
> D0 = C (TODO §70). TODO §71 reste `[ ]` (PARITY / RESOURCES / SECRETS).
> Ordonnancement Vague D conservé ci-dessous pour mémoire :
> [FILE-ATTENTE.md](FILE-ATTENTE.md) § *File d'attente — Vague D*.

---

## Contexte (vérifié)

FlowForge est un SaaS multi-tenant à architecture hexagonale. Stack **réelle** (pas celle de la v1.0 du 12/09) :

| Couche | Code actuel |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8.0.x Community — BPMN 2.0 **et** DMN (TODO §61, PR #123) |
| Auth | OIDC BFF (Keycloak) — pas de JWT dans le browser |
| Frontend | **Vue 3 + TypeScript + Vite + OpenVue + Pinia**, embarqué dans `static/` (PR #165 : fork MIT de PrimeVue) |
| MCP | **41 tools** sur `POST /api/m2m/mcp` (Streamable HTTP, JWT M2M `API_SERVICE`) — conception, brouillons, parcours, promotion, templates (Vague D) |
| Connecteurs livrés | `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback`, `batch-import`, `kafka`, `imap-inbound`, `graph-mail-inbound`, `azure-service-bus` |
| Antivirus | `AttachmentScanPort` + adaptateur ClamAV opt-in (PR #131) |
| Isolation | 1 instance Spring Boot = 1 tenant (`app.tenant.id`) |

---

## Synthèse — ce qu’il reste réellement à couvrir

| Priorité | Item | Lot | Statut |
|---|---|---|---|
| Livré | **2.4 Signature électronique** | `signature-electronique` | **Livré** ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), TODO §72, lot XL), **socle générique + stub** (aucun provider ; fournisseur réel et eIDAS à cadrer) — [30-signature-electronique.md](analysis%20doc/30-signature-electronique.md) |
| Décision | **D0** Positionnement produit | [analyse 31](analysis%20doc/31-positionnement-produit.md) | ✅ **Tranché = C** (TODO §70) : intégrateurs + couche low-code, décision n° 13 maintenue |
| Vague D | **LC1** Palette métier dans l'éditeur BPMN | [`palette-metier-bpmn`](analysis%20doc/39-palette-metier-bpmn.md) | **Livré** ([PR #151](https://github.com/superjojo79/FlowForge/pull/151), TODO §73, lot M–L : catalogue serveur + delegates + parseur + éditeur) ; suite **livrée** (LC1-suite, TODO §74) : liste des tables DMN déployées dans le panneau |
| Vague D | **LC2** Langage d'expression métier borné | [`langage-expression-metier`](analysis%20doc/40-langage-expression-metier.md) | **Livré** (TODO §75, lot M : JSONata borné hors JUEL, brique « Évaluer une expression », API et outils MCP) ; suite : champs calculés (D1) et mapping (D3b) s'appuient sur ce moteur |
| Vague D | **LC3** Assistant de conception IA | [`assistant-conception-ia`](analysis%20doc/41-assistant-conception-ia.md) | **Livré** (TODO §86 : assistant embarqué, propose/confirme, plafonds) |
| Vague D | **LC4a / LC4b** Éditeurs visuels de formulaires / de templates | [`editeur-formulaires`](analysis%20doc/42-editeur-formulaires.md), [`editeur-templates-wysiwyg`](analysis%20doc/43-editeur-templates-wysiwyg.md) | **Livré** (TODO §84 éditeur visuel de templates, §85 concepteur de formulaires) |
| Vague D | **LC5** Concepteur de parcours compilé en BPMN | [`concepteur-parcours`](analysis%20doc/44-concepteur-parcours.md) | **Livré** (TODO §88 : parcours compilés en brouillon BPMN, sens unique) |
| Vague D | **LC6** Scripts JS/TS en bac à sable | [`scripts-sandbox`](analysis%20doc/45-scripts-sandbox.md) | **Spike réalisé, lot non lancé** (TODO §89 : LC2 suffit, D-SCRIPT-GO = non ; conditions de réouverture en analyse 45 §9) |
| Vague D | **D1** Modèle de champs enrichi (montant/devise, groupes répétables, référence externe) | [`modele-champs-enrichi`](analysis%20doc/32-modele-champs-enrichi.md) | **Livré** (TODO §76 : `MONEY`, `EXTERNAL_REF`, `REPEATABLE_GROUP` ; champs calculés / visibilité / obligation : TODO §80) |
| P2 | Pièce jointe sur « Notifier » | `notifier-piece-jointe` | Backlog riverain ([DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §7) — non cadré |
| P2 | Unicité de champ / recherche depuis un workflow | `unicite-champ-dossier` | Backlog riverain — non cadré |
| P3 | Gabarits de notification, minuteur de parcours, variables de démarrage déclarées | `gabarits-notification`, `parcours-minuteur-bordure`, `variables-demarrage-declarees` | Backlog riverain — non cadré |
| Vague D | **D2** Recherche sur champs dynamiques (JSONB + plein texte) | [`recherche-champs-dynamiques`](analysis%20doc/33-recherche-champs-dynamiques.md) | **Livré** (TODO §81) |
| Vague D | **D3** Intégrations entreprise (OAuth2 client credentials, mapping réponse → dossier, e-mail entrant) | [`integrations-entreprise`](analysis%20doc/34-integrations-entreprise.md) | **Livré** (D3a §77, D3b §78, D3c + D3d §87) |
| Vague D | **D4** Promotion de configuration entre environnements | [`promotion-configuration`](analysis%20doc/35-promotion-configuration.md) | **Livré** (TODO §83) |
| Vague D | **D5** Scalabilité intra-tenant — verrou des jobs planifiés | [`verrou-jobs-planifies`](analysis%20doc/36-verrou-jobs-planifies.md) | **Livré** (TODO §79 : audit des 18 tâches ; 7 rendues sûres par prise en base, sans verrou distribué) |
| Vague D | **D6** Socle MCP low-code (brouillon, publication, provenance, A3-M2) | [`socle-mcp-low-code`](analysis%20doc/37-socle-mcp-low-code.md) | **Livré** (TODO §82) |
| Vague D | **D7** Internationalisation de l'UI | [`internationalisation-ui`](analysis%20doc/38-internationalisation-ui.md) | **Première tranche livrée** (TODO §90 : français et anglais ; libellés métier non traduits) |
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
| 2.4 Signature électronique | ✅ Livré, socle générique + stub sans provider ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), TODO §72) |
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
| 6.2 Tests PostgreSQL réels | ✅ zonky / PostgreSQL 17 embarqué dans `verify` (TODO §91, PR #157) ; smoke Keycloak réel toujours hors file |

### Catégorie 7 : Adéquation métier & industrialisation (Vague D)

> **Origine :** analyse critique fonctionnelle du 28/09/2026 (fonctionnalités × cas d'usage du
> `README.md` : sinistres, subventions, onboarding, réclamations, et intégration ERP type
> D365 F&O). **Lots LC1–LC5 et D1–D7 (1ʳᵉ tranche) livrés** ; LC6 = spike, non lancé.
> Le texte ci-dessous décrit le constat d'alors (`main` @ `f9638c7`). File actuelle :
> [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §7.

#### D0 — Décision de positionnement ✅ tranchée (TODO §70)

> Analyse initiale : [31-positionnement-produit.md](analysis%20doc/31-positionnement-produit.md).

**Constat :** le `README.md` promet « modéliser sans développement », mais la conception d'un
process exige une expertise BPMN (`GUIDE-CONCEPTION-BPMN.md`) et toute logique hors des 7
delegates allowlistés impose du code Java. Le verrouillage est **serveur** (allowlist §57/§69) ;
le manque de no-code est **côté UI** : panneau de propriétés BPMN en champs texte libres
(`flowablePropertiesProvider.ts`), templates en HTML brut (`TemplatesAdminView.vue`), pas de
langage d'expression métier.

**Décision (2026-09-28) : D-POSITIONING = C** — produit pour intégrateurs **avec une couche
low-code / no-code** pour les utilisateurs avancés côté client. Décision n° 13 maintenue
(hébergement dédié par client). L'intégrateur conçoit les processus ; l'utilisateur métier
ajuste formulaires, documents et règles, aidé d'un assistant IA. **Aucun relâchement de
l'allowlist serveur** ; `scriptTask` natif Flowable toujours écarté.

**Conséquences :** D4 remonte (indispensable à un intégrateur), D5 reste à l'audit, D6 devient
prérequis de LC3, les champs calculés de D1 et le mapping de D3b s'appuient sur LC2.
`README.md` (vision, § Statut) à nuancer au premier lot livré.

#### Couche low-code — sous-lots LC1 à LC6

> **Exposition MCP :** chaque sous-lot expose ses artefacts au MCP **dans le même lot**
> (principe P1, proposé) ; tools envisagés, principes et décisions dans
> [46-exposition-mcp-couche-low-code.md](analysis%20doc/46-exposition-mcp-couche-low-code.md).
>
> Principe commun : une brique low-code **ne crée pas de nouvelle surface d'expression serveur**.
> Soit elle produit le même XML BPMN que l'existant (validé par `FlowableBpmnParserAdapter` et
> `BpmnExpressionSurfaceScanner`), soit elle passe par un évaluateur borné exécuté hors JUEL.

**LC1 — `palette-metier-bpmn` (S–M).** ([analyse](analysis%20doc/39-palette-metier-bpmn.md)) Blocs prêts à l'emploi dans la palette de l'éditeur BPMN
(« Générer un document », « Appeler un connecteur », « Changer le statut », « Tâche externe
(portail) », « Décision DMN », « Question utilisateur »). Le panneau de propriétés propose des
**listes alimentées par les API existantes** (templates, instances de connecteurs, statuts du
type, tables DMN, schémas de questions) et écrit lui-même `delegateExpression`, `formKey`,
`type`, etc. Validation dès la conception. *Coutures :* frontend `bpmn-properties` et, au besoin,
endpoints de catalogue en lecture ; **aucun changement du parseur serveur**. Parallélisable avec D1
(surfaces disjointes). *Valeur :* rend l'essentiel de `GUIDE-CONCEPTION-BPMN.md` implicite.

**LC2 — `langage-expression-metier` (M).** ([analyse](analysis%20doc/40-langage-expression-metier.md)) Langage borné, sans accès au serveur et à durée
d'évaluation limitée, pour trois usages : conditions de passerelle, **champs calculés** (D1),
**mapping réponse de connecteur → champs** (D3b). *Décision ouverte :* JSONata (recommandé) vs
CEL ; vérifier que le portage Java (Apache 2.0) borne temps et profondeur. *Coutures :* évaluateur
dans le domaine ou un delegate dédié ajouté à `AllowedBpmnDelegates` — jamais évalué en JUEL ;
une condition de passerelle écrite dans ce langage doit passer par une variable produite par ce
delegate, pas par une `conditionExpression` libre. *Valeur :* couvre la majorité des besoins de
« scripting réduit » sans exécuter de code.

**LC3 — `assistant-conception-ia` (M).** ([analyse](analysis%20doc/41-assistant-conception-ia.md)) Un utilisateur métier décrit son besoin ; l'assistant
produit type de dossier, BPMN, DMN et templates via les **27 tools MCP** existants, **en
brouillon** ; relecture dans les éditeurs, `test_decision`, puis publication. *Prérequis :* **D6**
(brouillon et validation humaine des déploiements MCP). LC1 et LC2 améliorent la qualité de
génération (briques typées plutôt que XML libre). *Risque :* qualité des générations → aperçu,
tests et historique obligatoires.

**LC4a — `editeur-formulaires` (M).** ([analyse](analysis%20doc/42-editeur-formulaires.md)) Éditeur visuel des formulaires de tâche (`user-question`,
`dossier-fields`, portail), candidat `form-js` (bpmn.io, même licence que `bpmn-js`).
*Coutures :* `TaskQuestionSchemasAdminView.vue`, schéma `fieldDefinitions` (à coordonner avec D1
si les nouveaux types arrivent en même temps).

**LC4b — `editeur-templates-wysiwyg` (M).** ([analyse](analysis%20doc/43-editeur-templates-wysiwyg.md)) Éditeur WYSIWYG des templates de documents,
variables du dossier insérées par menu au lieu du HTML Thymeleaf brut. *Décision ouverte :*
TipTap (MIT) vs GrapesJS (BSD-3). *Contrainte :* se limiter au sous-ensemble **CSS 2.1** rendu par
Flying Saucer ; l'évaluateur allowlist des templates (§40) reste inchangé.

**LC5 — `concepteur-parcours` (L).** ([analyse](analysis%20doc/44-concepteur-parcours.md)) Vue par étapes (étapes, validations, conditions via DMN ou
LC2, actions : document, notification, connecteur, portail) **compilée en BPMN** ; Flowable reste
le moteur, l'éditeur BPMN reste disponible pour les experts. *Dépend de :* LC1 (briques), LC2
(conditions). *Décision ouverte :* aller-retour vue simplifiée ↔ BPMN (verrouillage par mode ou
conversion à sens unique).

**LC6 — `scripts-sandbox` (M–L, conditionnel).** ([analyse](analysis%20doc/45-scripts-sandbox.md)) Nouvelle brique « Script » : ressource versionnée
(comme une table DMN, jamais inline dans le BPMN), JS ou TypeScript transpilé à l'enregistrement,
exécuté dans **QuickJS compilé en WASM via Chicory** (pur Java, Apache 2.0) : ni système, ni
réseau, ni Java ; mémoire et instructions bornées ; API explicite (`ctx.dossier.fields`,
`ctx.connectorResult`, valeur de retour) ; `.d.ts` généré depuis le schéma du type pour
l'autocomplétion. *Démarche :* **spike de qualification d'abord** (maturité de `quickjs4j`,
performances, limites réelles), lot seulement si LC2 s'avère insuffisant. Tout ajout de moteur JS
au classpath doit être revérifié contre la défense `language` / `scriptFormat` de §69.

**Non retenus :** règles « Quand / Si / Alors » hors BPMN (recouvrement avec les événements BPMN
et l'alerting SLA) ; GraalJS (bac à sable strict réservé à Oracle GraalVM sous licence GFTC, à
reconfirmer — repli seulement si le spike LC6 échoue) ; `scriptTask` natif Flowable (écarté,
§57/§69). Complément hors lot : modèles n8n / Make prêts à l'emploi (documentation).

#### D1 — Modèle de champs dynamiques enrichi

> Analyse initiale : [32-modele-champs-enrichi.md](analysis%20doc/32-modele-champs-enrichi.md).

**Constat vérifié :** `FieldType` = `STRING, NUMBER, DATE, BOOLEAN, SELECT, FILE`
(`domain/dossier/model/FieldType.java`). Pas de montant avec devise, pas de groupe répétable,
pas de référence vers une entité externe, pas de champ calculé ni de règle de visibilité.

**Suite à D0 :** les champs calculés et la visibilité conditionnelle s'appuient sur **LC2** et
passent après lui ; les nouveaux types restent indépendants de LC2.

**Cas d'usage débloqués :** sinistres (garanties, postes de préjudice), subventions (lignes
budgétaires), onboarding fournisseur (référence au compte fournisseur ERP).

**Périmètre à poser en Phase 1 :** `MONEY` (montant + devise ISO 4217, précision décimale),
groupe répétable (lignes typées), `EXTERNAL_REF` (système + identifiant) ; champs calculés et
visibilité conditionnelle en option.

**Coutures :** `FieldDefinition` et sa validation, blocage 409 sur schéma incompatible (PR #87),
formulaires `dossier-fields` (TODO §52), variables des templates PDF, export CSV / BI, masquage
`sensitive`, tool MCP `define_dossier_type`. **Taille :** L.

#### D2 — Recherche sur les champs dynamiques

> Analyse initiale : [33-recherche-champs-dynamiques.md](analysis%20doc/33-recherche-champs-dynamiques.md).

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

> Analyse initiale : [34-integrations-entreprise.md](analysis%20doc/34-integrations-entreprise.md).

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

> Analyse initiale : [35-promotion-configuration.md](analysis%20doc/35-promotion-configuration.md).

**Constat vérifié :** aucun mécanisme d'export/import de configuration. Types de dossier, BPMN,
DMN, templates, schémas de questions et instances de connecteurs se recréent à la main (ou via
MCP) sur chaque environnement.

**Périmètre à poser :** un bundle versionné (export / import / diff à blanc), **sans secrets**
(les secrets de connecteurs sont ressaisis ou injectés par variables sur la cible), idempotent,
journalisé. Indispensable pour un intégrateur qui fait recette puis production (option A de D0).
**Taille :** L.

#### D5 — Scalabilité intra-tenant : verrou des jobs planifiés

> Analyse initiale : [36-verrou-jobs-planifies.md](analysis%20doc/36-verrou-jobs-planifies.md).

**Constat :** 15 méthodes `@Scheduled` (recompté le 28/09), aucun ShedLock ni verrou distribué trouvé. Certains jobs
ont leur propre état persisté (`connector_schedule_state`) : **audit d'abord**, job par job, pour
savoir lesquels s'exécuteraient en double si un tenant passe à 2 pods.

**Périmètre possible :** verrou par advisory lock PostgreSQL — compatible avec la stack figée
(pas de Redis). Le SSE multi-pod et le rate-limit distribué restent **hors scope**
(`FILE-ATTENTE.md` § *Ne pas lancer*). D0 = C (hébergement dédié) : **reste à l'audit**, verrou
seulement si un client impose plusieurs pods. **Taille :** S (audit) + M.

#### D6 — Socle MCP low-code (élargi le 28/09, TODO §71)

> Analyse initiale : [37-socle-mcp-low-code.md](analysis%20doc/37-socle-mcp-low-code.md).

**Constat :** `deploy_workflow`, `deploy_decision_table` et `redeploy_decision_table` agissent
directement sur l'instance (seul `redeploy_decision_table` n'a pas de `dryRun`) ; les brouillons
`BpmnDraft`/`DmnDraft` des éditeurs ne sont pas utilisés par le MCP ; tout JWT M2M reçoit le rôle
constant `API_SERVICE`, équivalent administrateur (risque A3-M2 accepté).

**Périmètre élargi par l'[analyse 46](analysis%20doc/46-exposition-mcp-couche-low-code.md) :**
brouillon par défaut pour les écritures de configuration venant du MCP, politique de publication
(D-MCP-LC-PUBLISH), migration des tools de déploiement directs (D-MCP-LC-MIGRATION), provenance
`UI`/`MCP`/`ASSISTANT` sur brouillons et versions, fermeture d'A3-M2 (D-MCP-LC-SCOPES).
**Prérequis de LC3** et de toute écriture MCP de LC4b, LC5 et LC6 ; LC1 et LC2 (lecture et
exécution à blanc) peuvent le précéder. **Taille :** M.

#### D7 — Internationalisation de l'UI

> Analyse initiale : [38-internationalisation-ui.md](analysis%20doc/38-internationalisation-ui.md).

**Constat vérifié :** pas de `vue-i18n` ; l'UI est en une seule langue, alors que les templates
PDF gèrent déjà des variantes par langue. Priorité selon le marché visé (D0). **Taille :** M.

---

## Priorisation

> **Vague D livrée (2026-10-01, mandat « roadmap complète »).** L'ordre ci-dessous est
> historique. File actuelle : [FILE-ATTENTE.md](FILE-ATTENTE.md) § *File active — backlog riverain*
> et [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §7.
>
> Détail d'alors (surfaces d'écriture, collisions, plafond de 3 agents) :
> [FILE-ATTENTE.md](FILE-ATTENTE.md) § *File d'attente — Vague D*.

**Vague 1 — en parallèle (surfaces disjointes)** — *livrée*

1. ~~`signature-electronique`~~ — **livré** (PR #149). ~~LC1 palette métier BPMN~~ — **livré** (PR #151).
3. **D1** (nouveaux types uniquement) **ou LC2** — LC2 raccourcit le chemin critique (il débloque
   D3b, les champs calculés de D1 et LC5). **D3a** (OAuth2) en 4ᵉ agent si accepté.
4. **D5** audit des jobs planifiés, en fond (lecture seule).

**Vague 2**

5. **D3b** (mapping réponse → champs) et champs calculés de D1 — après LC2.
6. **D2** recherche sur champs dynamiques — après D1.
7. **D6** socle MCP low-code, puis **D4** promotion de configuration — *arbitrage à trancher* :
   D6 avant D4 (recommandé, l'import réutilise le mécanisme de brouillon) ou D4 avant D6
   (ordre précédent de ce document).

**Vague 3**

8. **LC3** assistant de conception IA — après D6.
9. **LC4a** éditeur de formulaires — après D1 ; **LC4b** éditeur de templates — sans dépendance,
   dès qu'un slot est libre.
10. **D3c** (+ **D3d** si confirmé) — e-mail entrant et bus Azure, un seul cycle catalogue.

**Vague 4**

11. **LC5** concepteur de parcours — après LC1 et LC2.
12. **LC6** — spike de qualification, puis lot seulement si LC2 ne suffit pas ; **D7** i18n selon le marché.
13. Vérifications d'environnement hors file : `ClamAvAttachmentScanIT` sous Docker, smoke Keycloak réel.

Correctif documentaire : le § *Statut* du `README.md` a été mis à jour le 29/09/2026, puis
réaligné le 22/10/2026 (Vague D sur `main`, file riverain).


---

## Historique des versions

| Version | Date | Auteur | Changements |
|---|---|---|---|
| 1.0 | 12/09/2026 | Assistant IA (analyse code) | Création initiale — **plusieurs statuts et la stack étaient faux** (DMN editor, MCP 25+, React, FEEL, PG tests, provisioning). |
| 1.1 | 14/09/2026 | Vérification `main` + Vague B | Statuts recoupés sur le code ; Vague B marquée en cours ; liste réelle de ce qu’il reste à couvrir. |
| 1.2 | 23/09/2026 | Rattrapage post-merges #131/#132/#135 | Vagues A/B/C/A4, reporting et inbox passés en livré ; catalogue connecteurs à 8 ; portail et signature scindés en deux lots ; DMN et portail en cours. |
| 1.3 | 28/09/2026 | Analyse critique fonctionnelle | Ajout de la Vague D (Catégorie 7) : décision de positionnement D0 et lots D1–D7 recoupés sur `main` @ `f9638c7` ; MCP à 27 tools ; priorisation étendue après `signature-electronique`. |
| 1.4 | 28/09/2026 | Décision D0 (TODO §70) | D0 tranché = C (intégrateurs + couche low-code) ; sous-lots LC1–LC6 ; priorisation revue (D4 remonte, LC2 avant D3b et champs calculés). |
| 1.5 | 28/09/2026 | Cadrage MCP low-code (TODO §71) | Analyse 46 référencée ; D6 élargi en socle MCP low-code ; exposition MCP dans le même lot pour chaque `LC*`. |
| 1.6 | 28/09/2026 | Analyses initiales de la Vague D | Analyses 31–45 créées (D0–D7, LC1–LC6), cadrage MCP renuméroté 31 → 46 ; lots nommés et liés à leur analyse. |
| 1.7 | 29/09/2026 | Ordonnancement de la Vague D | Priorisation réécrite en 4 vagues (alignée sur `FILE-ATTENTE.md`), arbitrage D4/D6 signalé, en-tête recalé sur `main` @ `2283c92`, README (Statut, positionnement) aligné. |
| 1.8 | 30/09/2026 | Objectif « roadmap complète » | LC1-suite (TODO §74), LC2 (TODO §75) et D1 (TODO §76) livrés ; décisions consignées dans `docs/roadmap-goal/DECISIONS.md`. |
| 1.9 | 22/10/2026 | Alignement post-Vague D | File active = backlog riverain (§7) ; D1 champs calculés marqués livrés (§80) ; frontend OpenVue ; connecteurs IMAP/Graph/Service Bus. |
