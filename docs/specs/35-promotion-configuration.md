# Promotion de configuration entre environnements (D4)

> **Statut : LIVRÉ en première tranche (2026-10-01, TODO §83) — décisions D-PROMO-* dans `docs/decisions/roadmap-2026/DECISIONS.md`, spec dans `docs/ai/*/2026-10-01-feature-d4-promotion-configuration.md`.** *(Analyse initiale du 2026-09-28 conservée ci-dessous.)* Lot **D4**
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
