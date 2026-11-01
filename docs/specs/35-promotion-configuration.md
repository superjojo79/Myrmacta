# Promotion de configuration entre environnements (D4)

> **Statut : LIVRÉ — première tranche (2026-10-01, TODO §83, décisions D-PROMO-* dans `docs/decisions/roadmap-2026/DECISIONS.md`),
> seconde tranche (2026-11-01, issue #279, TODO §117) : voir la section 9.** *(Analyse initiale du 2026-09-28 conservée ci-dessous.)* Lot **D4**
> `promotion-configuration` de la Vague D (`ROADMAP.md` Catégorie 7). Priorité **remontée** par
> D0 = C ([31](31-positionnement-produit.md)) : un intégrateur fait recette puis production.
> À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

Aucun mécanisme d'export / import de configuration. Chaque environnement se configure à la main
(écrans admin) ou par MCP, élément par élément.

**Ce qui constitue la configuration d'un tenant** (tables Liquibase + moteur) :

| Artefact | Stockage | Particularité |
|---|---|---|
| Types de dossier (schéma, templates associés) | `dossier_type` | Compatibilité 409 avec les dossiers existants |
| Configurations de workflow (statuts, lien au type) | `workflow_type_config` | — |
| Processus BPMN et tables DMN déployés | Moteur Flowable (`ACT_RE_*`, `ACT_DMN_*`) | Versionnés par le moteur ; brouillons dans `bpmn_draft`, `dmn_draft` |
| Templates de documents (versions, variantes) | `document_template` | Statut `DRAFT` / actif |
| Schémas de questions de tâche | `task_question_schema` | — |
| Instances de connecteurs | `connector_instance` | **Secrets chiffrés** avec la DEK du tenant (`tenant_connector_dek`) |
| ACL par type | `dossier_type_acl` | Dépend des groupes / rôles du fournisseur d'identité |
| Réglages tenant | `tenant_retention_settings`, `tenant_sla_settings`, `tenant_sla_alert_webhook`, `notification_webhook` | URLs propres à chaque environnement |

Hors configuration : dossiers, éléments, liens de workflow, outbox, inbox, portail, états de job.

## 2. Objectif

Un **bundle versionné** : exporter la configuration d'un environnement, l'examiner (diff), puis
l'importer sur un autre, de façon idempotente et journalisée.

## 3. Périmètre à poser (Phase 1)

- **Format** : archive de fichiers lisibles (JSON/YAML + `.bpmn20.xml` + `.dmn` + HTML), versionnable
  dans Git par l'intégrateur.
- **Import à blanc** : diff par artefact (créé / modifié / inchangé / conflit) avant application.
- **Ordre d'application** : types → schémas de questions → templates → connecteurs → DMN → BPMN →
  configurations de workflow → ACL et réglages.
- **Secrets** : jamais exportés ; ressaisis sur la cible ou injectés par variables
  d'environnement. Les DEK diffèrent par tenant.
- **Paramètres d'environnement** : URLs de webhooks, points d'accès des connecteurs → valeurs
  substituables par environnement.
- **Idempotence et atomicité** : que se passe-t-il si l'import échoue à mi-parcours (les
  déploiements Flowable ne sont pas transactionnels avec PostgreSQL applicatif) ?

## 4. Coutures et dépendances

- Réutiliser les use cases existants (création / mise à jour de type, déploiement BPMN et DMN,
  templates, connecteurs) : **aucune écriture directe en base**, pour garder validations,
  allowlists (§57, §69, §40) et règle 409.
- Brouillons (D6, [37](37-socle-mcp-low-code.md)) : un import en production pourrait créer des
  brouillons à publier plutôt que publier directement — cohérent avec D-MCP-LC-PUBLISH.
- Rôles : export et import réservés à `TENANT_ADMIN`.
- Provisioning (lot A1, `scripts/provision-tenant.sh`) : l'import peut compléter le provisioning
  d'un tenant neuf.

## 5. Exposition MCP

`export_configuration` (lecture) et `import_configuration` en **brouillon + diff** (principes P3
et P4 de l'[analyse 46](46-exposition-mcp-couche-low-code.md)) ; jamais de secrets (P9).
À trancher : l'import MCP est-il souhaitable, vu son rayon d'action ?

## 6. Risques

- Import partiel laissant un environnement incohérent.
- ACL importées vers un fournisseur d'identité où les groupes n'existent pas.
- Divergence de version applicative entre source et cible (format de bundle à versionner).

## 7. Décisions à trancher

1. **D-PROMO-FORMAT** — archive de fichiers ou document unique ; versionnement du format.
2. **D-PROMO-SCOPE** — artefacts inclus dans la première tranche (ACL et réglages inclus ?).
3. **D-PROMO-SECRETS** — ressaisie manuelle ou injection par variables d'environnement.
4. **D-PROMO-APPLY** — publication directe ou création de brouillons sur la cible.
5. **D-PROMO-MCP** — import exposé au MCP ou réservé à l'UI / CLI.

## 8. Documentation impactée

`docs/guides/DEPLOIEMENT.md` (procédure recette → production), `docs/guides/GUIDE-UTILISATEUR.md` (écran d'export /
import), `docs/guides/GETTING-STARTED.md` si une CLI est fournie.

## 9. Tranche 2 : groupes, templates, parcours, réglages de type

> Livrée le 2026-11-01 (issue [#279](https://github.com/superjojo79/Myrmacta/issues/279)). Décisions :
> [§117](../decisions/117-promotion-de-configuration-tranche-2.md).

**Format 2 (D-PROMO-FORMAT2) :**

- L'export écrit le format 2. L'import lit les formats 1 et 2. Un bundle de format 1 qui contient un artefact de la
  tranche 2 est refusé.
- Le format est contrôlé avant les types à partir de cette version. Une instance de la tranche 1 refuse un bundle de format 2
  par « unknown artifact type ».
- Le rang des constantes d'`ArtifactType`, et donc l'empreinte d'un bundle déjà journalisé, ne change pas. L'ordre
  d'application est un rang à part.

**Artefacts transportés**, dans l'ordre d'application :

| Artefact | Répertoire | Contenu | À l'import |
|---|---|---|---|
| `USER_GROUP` | `user-groups/` | code, libellé, `idpGroup` ; jamais les membres | création avec l'`idpGroup` du bundle (`{{paramètre}}` accepté, absent = bloqué) ; en mise à jour, seul le libellé change |
| `DOSSIER_TYPE` | `dossier-types/` | tranche 1 | tranche 1 ; avertissement pour une clé de template sans version active ni template dans le bundle |
| `TASK_QUESTION_SCHEMA` | `task-question-schemas/` | tranche 1 | tranche 1 |
| `DOCUMENT_TEMPLATE` | `document-templates/` | version active d'une combinaison clé, variante, langue ; fichier `clé.variante.langue` | nouvelle version **DRAFT**, jamais activée ; *Inchangé* si la version active ou un brouillon est identique ; taille et syntaxe contrôlées |
| `CONNECTOR_INSTANCE` | `connectors/` | tranche 1 | tranche 1 |
| `DMN` | `dmn/` | tranche 1 | brouillon ; avertissement pour un groupe `routageService` inconnu |
| `BPMN` | `bpmn/` | tranche 1 | brouillon ; avertissement pour un groupe inconnu (`groupeTri`, `candidateGroups`) ; bloqué s'il dépend d'un template que le bundle apporte, jusqu'au passage suivant |
| `WORKFLOW_CONFIG` | `workflow-configs/` | tranche 1 | tranche 1 |
| `REPLY_WORKFLOW` | `reply-workflows/` | mode, workflow, type enfant | par `ManageReplyWorkflowUseCase` ; bloqué si une dépendance manque, ou si la configuration n'existera qu'à la publication d'un brouillon |
| `DEADLINE` | `deadlines/` | champ, rappel, groupe d'escalade | par `ManageDeadlineUseCase` ; bloqué si le champ `DATE` (lu dans le type tel que le bundle l'écrit) ou le groupe manque |
| `PARCOURS` | `parcours/` | définition, repère `inSync` | enregistrée sans compilation, à la version courante ; avertissement si `inSync` est faux |

**Règles communes :**

- **Références orphelines (D-PROMO-ORPHAN) :** une référence absente de la cible et du bundle bloque quand l'écriture
  serait refusée. Sinon elle produit un avertissement `WARNING:` en tête de la raison, et l'artefact reste appliqué.
  L'avertissement est séparé de la suite par ` | ` ; la ligne du rapport d'application le reprend avant son message, donc
  le journal `config_import` aussi, que l'artefact soit *Appliqué* ou *Inchangé* (issue #285). Le plan connaît le canal :
  par MCP, un groupe que seul le bundle apporte ne compte pas comme connu, puisque ce canal n'applique pas les groupes, et
  l'avertissement le dit.
- **Repérage des groupes :** `XmlGroupReferenceExtractor` (StAX, sans DTD ni entité externe) ne retient que les valeurs
  littérales en forme de code de groupe.
- **Canal MCP (D-PROMO-MCP-TEMPLATE) :** il applique les brouillons BPMN, DMN et de templates. Groupes, parcours,
  workflows « réponse » et échéances restent réservés à l'écran. `export_configuration` ne renvoie jamais le HTML d'un
  template : il le liste dans `omitted`.
- **Taille d'un fichier :** 12 Mio au plus (un template de 5 Mio, encodé en JSON) ; 50 Mio pour le bundle.
- **Rien n'est jamais supprimé.** Restent hors du bundle : ACL, réglages du tenant (rétention par défaut, alertes SLA,
  webhooks), membres des groupes, historique des versions de templates, signature du bundle.

**Implémentation :**

- Domaine : `domain.promotion.artifact` (`UserGroupArtifacts`, `TypeSettingsArtifacts`, `DocumentTemplateArtifacts`,
  `ParcoursArtifacts`, `GroupReferenceCheck`, `EnvironmentParameters`, regroupés par `AdditionalArtifacts`), appelés
  par `ExportConfigurationUseCaseImpl` et `ImportConfigurationUseCaseImpl`.

| Lot | PR | Contenu |
|---|---|---|
| 1 | #280 | format 2, groupes, groupes orphelins |
| 2 | #281 | workflow « réponse », échéance |
| 3 | #282 | templates en brouillon, clés orphelines, MCP |
| 4 | #283 | parcours |
| 5 | (cette PR) | aller-retour de l'exemple mail room, E2E, documentation, revue de clôture |

**Tests de référence :**

- `ConfigurationPromotionUseCaseTest` (règles du plan et de l'application) ;
- `ConfigurationPromotionApiTest` (chaîne HTTP pour chaque artefact, canal MCP des templates, parcours compilé puis
  publié) ;
- `XmlGroupReferenceExtractorTest` ;
- `MailRoomExampleIT` (l'exemple entier relu inchangé, réglages recréés) ;
- Playwright `E2E-PROMO-2`.

