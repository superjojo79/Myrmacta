# Exposition MCP de la couche low-code — cadrage transverse

> **Numérotation :** créée en 31, renumérotée 46 le 2026-09-28 pour suivre les analyses des lots
> qu'elle cadre ([31](31-positionnement-produit.md) à [45](45-scripts-sandbox.md)).

> **Statut : première analyse (2026-09-28), décisions ouvertes.** Cadrage demandé dès la décision
> D0 = C (TODO §70), **avant** le premier lot `LC*`, pour que chaque lot expose ses artefacts au
> MCP dès sa livraison au lieu de les rattraper après coup (ce qu'a dû faire
> [24-extension-mcp-flux-metier.md](24-extension-mcp-flux-metier.md) pour trois mécanismes
> livrés sans tool). Suivi : TODO §71.
>
> **Décisions tranchées par l'utilisateur (2026-09-28) :** D-MCP-LC-IDENTITY = **A**,
> D-MCP-LC-PUBLISH = **C** (défaut = A), D-MCP-LC-MIGRATION = **A**, D-MCP-LC-MODES = **A** —
> voir §6. Les cinq autres restent **proposées, non tranchées**.

---

## 1. Pourquoi cadrer maintenant

Avec D0 = C, le serveur MCP change de rôle. Il a été cadré comme **outillage interne**
([15-serveur-mcp.md](15-serveur-mcp.md), « Consommateur ») : un agent de l'équipe ou de
l'intégrateur configure une instance. Le sous-lot **LC3** (assistant de conception IA) en fait
le **moteur du no-code côté client** : un utilisateur métier décrit un besoin, l'assistant
produit type de dossier, BPMN, DMN, formulaires et templates. Trois conséquences :

1. **Chaque artefact low-code doit être manipulable par un agent**, sinon l'assistant ne sait
   produire qu'une partie du résultat.
2. **Le consommateur n'est plus seulement un client M2M de confiance** : l'assistant agit pour le
   compte d'un utilisateur humain, avec ses droits à lui.
3. **Le contenu produit par un modèle est une entrée non fiable**, au même titre qu'un upload.

## 2. Constat vérifié sur `main` @ `e3985fe`

| Élément | État | Preuve |
|---|---|---|
| Catalogue | **27 tools**, atomiques, réutilisant les use cases REST | `infrastructure/mcp/tool/` |
| Aperçu sans effet | `dryRun` sur **9** tools d'écriture, dont `deploy_workflow` et `deploy_decision_table` ; absent de `redeploy_decision_table` | grep `dryRun` |
| Déploiements | **Directs** : un `deploy_workflow` sans `dryRun` publie immédiatement | `DeployWorkflowTool` |
| Brouillons | Existent pour les éditeurs (`BpmnDraft`, `DmnDraft`), **non utilisés par le MCP** ; statut `DRAFT` sur les templates | `domain/workflow/model`, `TemplateStatus` |
| Templates | Lecture seule (`list_document_templates`) ; aucun tool d'écriture ni d'aperçu, alors que l'aperçu REST existe (`POST /preview`, `/preview/pdf`) | `DocumentGenerationController` |
| Identité | Rôle **constant** `API_SERVICE` pour tout JWT M2M, traité comme `TENANT_ADMIN`/`WORKFLOW_ADMIN` ; risque **A3-M2 accepté**, refermé partiellement par `trustedClientIds` | `McpAccessContextFactory` |
| Capacités MCP | `tool: true` seulement ; `resource`, `prompt`, `completion` à `false` | `application.yml` § `spring.ai.mcp` |
| Exclusions | ACL, purge, statut de `DossierWorkflowLink` | 15-serveur-mcp.md |
| Secrets connecteurs | Passent en clair sur l'appel MCP, même flux que l'admin REST (D-MCP-EXT-SECRET) | TODO §56 |

## 3. Principes transverses proposés

**P1 — Parité par conception.** Un lot `LC*` n'est terminé que si ses artefacts sont lisibles,
validables et (sauf exclusion motivée) écrivables via MCP, **dans le même lot**, en réutilisant
les mêmes use cases que l'UI. À ajouter à la *definition of done* de chaque lot.

**P2 — Atomicité maintenue.** La doctrine « tools atomiques » (ROADMAP 1.4) reste. C'est
l'assistant qui orchestre ; aucun tool composite « crée tout le parcours ».

**P3 — Brouillon par défaut pour la configuration.** Toute nouvelle écriture de configuration
venant du MCP crée ou met à jour un **brouillon** ; la publication est une étape distincte
(D-MCP-LC-PUBLISH). Réutiliser `BpmnDraft`, `DmnDraft` et `TemplateStatus.DRAFT` plutôt que
d'inventer un mécanisme. Les écritures **métier** (`create_dossier`, `start_workflow`…) ne sont
pas concernées.

**P4 — L'agent doit pouvoir se vérifier seul.** Chaque artefact exposé en écriture a son outil
de validation ou d'exécution à blanc, sans effet de bord, sur le modèle de `test_decision` :
`dryRun`, `validate_*`, `evaluate_*`, `preview_*`. Bornés en taille et en nombre de cas (repère :
100 cas pour `test_decision`).

**P5 — Formats du domaine comme contrat MCP.** Le contrat MCP porte les formats du domaine
(`fieldDefinitions`, BPMN, DMN, HTML Thymeleaf, modèle de parcours), **jamais** les formats
propres à une bibliothèque d'UI (JSON `form-js`, JSON TipTap, descripteurs d'element templates).
Un changement d'éditeur ne casse pas le contrat. Exception assumée : le catalogue de briques de
LC1, qui *est* un artefact du domaine (voir §4).

**P6 — Aucun nouveau chemin de confiance.** Le contenu soumis par MCP repasse par les mêmes
validateurs que l'UI : parseur BPMN et `BpmnExpressionSurfaceScanner` (§57/§69), allowlist
d'expressions des templates (§40), grammaire du langage LC2, bac à sable LC6.

**P7 — Identité réelle de l'acteur.** Un appel fait pour le compte d'un utilisateur s'exécute
avec **son** `AccessContext` (rôles et ACL de type de dossier), jamais avec `API_SERVICE`.

**P8 — Traçabilité de la provenance.** Chaque brouillon et chaque version publiée enregistre
l'acteur et la provenance (`UI`, `MCP`, `ASSISTANT`) ; pour l'assistant, l'humain qui l'a piloté.

**P9 — Aucun secret via l'assistant.** Un secret saisi dans une conversation est transmis au
fournisseur du modèle. L'assistant peut créer une instance de connecteur **sans** ses secrets ;
un humain les saisit dans l'UI. Le canal M2M interne garde D-MCP-EXT-SECRET.

## 4. Exposition par sous-lot

| Lot | Tools / ressources envisagés | Nature | Points d'attention |
|---|---|---|---|
| **LC1** [palette BPMN](39-palette-metier-bpmn.md) | `list_bpmn_building_blocks` (briques, paramètres typés, sources de valeurs) — ou ressource MCP | Lecture | **Source unique** : le catalogue sert l'UI (element templates générés) *et* le MCP. L'agent compose du BPMN à partir des briques, validé par `deploy_workflow` en `dryRun`. |
| **LC2** [langage d'expression](40-langage-expression-metier.md) | `validate_expression` (syntaxe, champs référencés existants sur le type), `evaluate_expression` (contexte d'exemple fourni) | Lecture / exécution à blanc | Limites de temps et de profondeur **identiques** à l'exécution réelle. Les expressions s'écrivent via les tools existants qui les portent (`define_dossier_type` pour les champs calculés, brique de mapping D3b). |
| **LC3** [assistant IA](41-assistant-conception-ia.md) | Pas de tool propre : consomme le catalogue | — | Porte P3, P7, P8, P9 et les décisions D-MCP-LC-IDENTITY / MODES. |
| **LC4a** [formulaires](42-editeur-formulaires.md) | Extension de `define_task_question_schema` (nouveaux types de D1) ; `preview_form` optionnel | Écriture en brouillon | L'éditeur `form-js` reste une vue ; `fieldDefinitions` reste le contrat (P5). |
| **LC4b** [templates](43-editeur-templates-wysiwyg.md) | **Nouveaux** `define_document_template` (version brouillon), `preview_document` (HTML/PDF sur dossier d'exemple, réutilise `/preview`) | Écriture en brouillon + aperçu | Comble le trou actuel (lecture seule). Allowlist §40 inchangée. |
| **LC5** [parcours](44-concepteur-parcours.md) | `define_parcours` (brouillon), `compile_parcours` (retourne le BPMN généré sans déployer), publication selon D-MCP-LC-PUBLISH | Écriture en brouillon + compilation à blanc | Dépend de la politique d'aller-retour de LC5 : en sens unique, l'agent n'édite que le parcours. |
| **LC6** [scripts](45-scripts-sandbox.md) | `define_script` (brouillon), `test_script` (bac à sable, contexte d'exemple, cas bornés), `get_script_typings` (`.d.ts` du type) | Écriture en brouillon + exécution à blanc | Via MCP il n'y a pas de navigateur pour transpiler le TypeScript → D-MCP-LC-TS. |
| **D6** [socle MCP](37-socle-mcp-low-code.md) | Politique de publication, migration des tools de déploiement directs vers le brouillon, provenance | Transverse | Devient le **socle** de cette exposition (voir §6). |

Tools de publication : un `publish_draft` générique, ou un par artefact, selon D-MCP-LC-PUBLISH.

## 5. Risques propres à l'assistant embarqué

- **Injection de consignes par les données.** Un assistant qui lit des dossiers (texte saisi par
  un tiers, y compris via le portail) puis écrit de la configuration peut être détourné par le
  contenu lu. Mitigation proposée : **modes séparés** (D-MCP-LC-MODES) — le mode *conception*
  n'a accès qu'aux tools de configuration et aux catalogues, pas aux données de dossier ; le mode
  *opération* inverse.
- **Données transmises au fournisseur du modèle.** Tout ce que l'assistant lit part chez le
  fournisseur du LLM : sujet RGPD (sous-traitance, localisation). Le mode *conception* limite
  l'exposition aux métadonnées ; le masquage des champs `sensitive` s'applique déjà aux vues MCP.
- **Déni de service par les outils d'évaluation.** `evaluate_expression`, `test_script` et
  `compile_parcours` sont appelables en boucle : bornes par appel (P4) et limitation de débit
  sur le canal.
- **Élévation via A3-M2.** Tant que tout JWT M2M vaut administrateur complet, ouvrir un second
  consommateur est risqué : dériver les rôles des scopes réels du jeton, ou exiger
  `trustedClientIds`, **avant** LC3.

## 6. Décisions à trancher

| ID | Question | Options | Recommandation |
|---|---|---|---|
| **D-MCP-LC-PARITY** | La parité MCP entre-t-elle dans la *definition of done* de chaque lot `LC*` ? | A. oui · B. rattrapage par un lot MCP dédié en fin de vague | **A** — évite un second doc 24 |
| **D-MCP-LC-PUBLISH** | Qui publie un brouillon créé par MCP ? | A. un humain dans l'UI uniquement · B. un tool `publish_*` réservé à un client M2M scopé · C. propriété de démarrage : publication directe autorisée hors production, humaine en production | ✅ **Tranché = C, défaut = A** (2026-09-28) — l'intégrateur itère vite en recette, la production reste gardée |
| **D-MCP-LC-IDENTITY** | Comment l'assistant embarqué (LC3) appelle-t-il les tools ? | A. catalogue partagé exécuté **dans le processus** avec l'`AccessContext` de l'utilisateur de la session BFF · B. MCP avec jeton délégué (échange de jeton RFC 8693 via Keycloak) · C. endpoint MCP ouvert à la session navigateur | ✅ **Tranché = A** (2026-09-28) — ni jeton dans le navigateur (contraire au BFF), ni `/api/m2m` exposé ; un seul catalogue, deux points d'entrée |
| **D-MCP-LC-MODES** | Séparer les modes *conception* et *opération* de l'assistant ? | A. oui · B. un seul mode | ✅ **Tranché = A** (2026-09-28) — mitigation principale de l'injection de consignes |
| **D-MCP-LC-RESOURCES** | Activer les capacités MCP `resources` (et `prompts`) ? | A. oui : catalogue LC1, grammaire LC2, typings LC6, guide de conception · B. tout passer en tools | **A** — contexte chargé sans appel d'outil, à borner en taille |
| **D-MCP-LC-SCOPES** | Fermer A3-M2 avant LC3 ? | A. rôles dérivés des scopes du JWT · B. `trustedClientIds` obligatoire · C. laisser en l'état | **B** suffit, D-MCP-LC-IDENTITY = A ne faisant pas passer l'assistant par le canal M2M |
| **D-MCP-LC-SECRETS** | Secrets de connecteur via l'assistant ? | A. jamais (saisie humaine) · B. comme le canal M2M | **A** (P9) |
| **D-MCP-LC-TS** | TypeScript soumis via MCP (LC6) ? | A. JavaScript seulement via MCP · B. transpilation côté serveur, dans le même bac à sable | À trancher **après le spike LC6** |
| **D-MCP-LC-MIGRATION** | Les tools de déploiement directs existants (`deploy_workflow`, `deploy_decision_table`, `redeploy_decision_table`) passent-ils au brouillon ? | A. oui, dans D6 · B. conservés tels quels, seuls les nouveaux tools sont en brouillon | ✅ **Tranché = A** (2026-09-28) — sinon l'assistant contourne la politique par l'ancien chemin. **Rupture de comportement** pour les agents internes existants, à annoncer dans le lot D6 |

## 7. Impact sur la roadmap

- **D6** devient le **socle MCP low-code** : politique de brouillon et de publication, migration
  des tools directs, provenance, fermeture d'A3-M2. Prérequis de **LC3**, et de toute écriture
  MCP des lots LC4b, LC5 et LC6. LC1 et LC2 (lecture et exécution à blanc) peuvent le précéder.
- Chaque lot `LC*` gagne une ligne « Exposition MCP » dans sa Phase 1, alimentée par le §4.
- `15-serveur-mcp.md` § Décisions de cadrage (« Consommateur ») amendé le 2026-09-28 suite à
  D-MCP-LC-IDENTITY = A.

### Conséquences des décisions tranchées

- **IDENTITY = A** : le catalogue de tools doit être invocable depuis deux points d'entrée
  (serveur MCP M2M et assistant in-process). La résolution de l'acteur, aujourd'hui figée dans
  `McpAccessContextFactory` (JWT M2M → `API_SERVICE`), devient un paramètre de l'invocation :
  `AccessContext` de l'utilisateur de la session pour l'assistant, inchangé pour le M2M. Le
  canal `/api/m2m/mcp` ne change pas.
- **PUBLISH = C (défaut A)** : nouvelle propriété de démarrage (nom à fixer en design, lue au
  démarrage comme `app.tenant.id`) → `.env.example`, `docs/guides/GETTING-STARTED.md` et `docs/guides/DEPLOIEMENT.md`
  à mettre à jour dans D6. La publication humaine exige le rôle qui publie déjà dans l'UI
  (`WORKFLOW_ADMIN` / `TENANT_ADMIN`).
- **MIGRATION = A** : `deploy_workflow`, `deploy_decision_table` et `redeploy_decision_table`
  écrivent un brouillon par défaut ; le comportement direct n'existe plus que via la propriété
  de PUBLISH hors production. Rupture de contrat à signaler dans `CHANGELOG.md`.
- **MODES = A** : deux jeux de tools distincts exposés à l'assistant selon le mode ; le mode
  *conception* n'inclut ni `get_dossier`, ni `search_dossiers`, ni les écritures métier.
