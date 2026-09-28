# Socle MCP low-code — brouillon, publication, provenance (D6)

> **Statut : première analyse (2026-09-28).** Lot **D6** `socle-mcp-low-code` de la Vague D
> (`ROADMAP.md` Catégorie 7), élargi le 2026-09-28 par le cadrage transverse
> [46-exposition-mcp-couche-low-code.md](46-exposition-mcp-couche-low-code.md) (TODO §71).
> Ce document décrit **le lot** ; les principes P1–P9 et le raisonnement complet sont dans
> l'analyse 46. Quatre décisions y sont déjà tranchées (rappelées §3).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Déploiements MCP | `deploy_workflow`, `deploy_decision_table`, `redeploy_decision_table` publient **immédiatement** ; seul le dernier n'a pas de `dryRun` | `infrastructure/mcp/tool/` |
| Brouillons | `BpmnDraft`, `DmnDraft` (tables `bpmn_draft`, `dmn_draft`) utilisés par les éditeurs, **pas par le MCP** ; `TemplateStatus.DRAFT` pour les templates | `domain/workflow/model`, `013-`, `021-` |
| Identité MCP | Tout JWT M2M → rôle constant `API_SERVICE`, équivalent `TENANT_ADMIN` / `WORKFLOW_ADMIN` (risque A3-M2 accepté) ; `trustedClientIds` vide par défaut | `McpAccessContextFactory` |
| Audit | `createdBy` / `updatedBy` = sujet du JWT (D-AUDIT = C) ; pas de provenance | `017-mcp-audit-authors.yaml` |

## 2. Objectif

Rendre sûres les écritures de configuration venant d'un agent **avant** d'ouvrir l'assistant
embarqué (LC3, [41](41-assistant-conception-ia.md)) et les écritures MCP de LC4b, LC5 et LC6.

## 3. Décisions déjà tranchées (analyse 46, 2026-09-28)

| ID | Décision | Traduction dans ce lot |
|---|---|---|
| D-MCP-LC-PUBLISH = C, défaut A | Publication directe autorisable hors production par propriété de démarrage ; humaine dans l'UI sinon | Nouvelle propriété lue au démarrage (nom à fixer) ; publication humaine avec le rôle qui publie déjà (`WORKFLOW_ADMIN` / `TENANT_ADMIN`) |
| D-MCP-LC-MIGRATION = A | Les trois tools de déploiement passent au brouillon | Rupture de comportement pour les agents internes existants, annoncée dans `CHANGELOG.md` |
| D-MCP-LC-IDENTITY = A | Assistant in-process avec l'`AccessContext` de l'utilisateur | La résolution de l'acteur devient un paramètre de l'invocation d'un tool (voir §4) |
| D-MCP-LC-MODES = A | Modes *conception* et *opération* séparés | Deux sous-ensembles du catalogue, déclarés dans ce lot, consommés par LC3 |

## 4. Périmètre à poser (Phase 1)

- **Brouillon par défaut** pour les écritures de configuration MCP ; réutiliser `bpmn_draft`,
  `dmn_draft` et `DRAFT` des templates. Étendre aux types de dossier, schémas de questions et
  instances de connecteurs ? (aujourd'hui écrits directement, avec `dryRun`).
- **Publication** : écran listant les brouillons en attente (provenance, auteur, diff avec la
  version publiée), bouton Publier ; tool de publication seulement si la propriété l'autorise.
- **Provenance** `UI` / `MCP` / `ASSISTANT` (+ humain pilote pour l'assistant) sur brouillons et
  versions publiées.
- **Acteur paramétrable** : extraire de `McpAccessContextFactory` la résolution de l'acteur, pour
  que le même tool s'exécute avec le JWT M2M (canal actuel, inchangé) ou avec l'`AccessContext`
  de la session BFF (assistant).
- **Modes** : marquage de chaque tool (*conception*, *opération*, ou les deux).
- **A3-M2** : `trustedClientIds` renseigné obligatoirement hors local (D-MCP-LC-SCOPES, B
  recommandé, encore ouvert).

## 5. Coutures

`infrastructure/mcp` (tools, fabrique d'acteur), use cases de déploiement BPMN / DMN (écrire un
brouillon au lieu de déployer), écrans admin des éditeurs (liste de brouillons), configuration
de démarrage. ArchUnit : la notion de provenance vit dans le domaine, sans dépendance au MCP.

## 6. Risques

- Casser les agents internes existants qui supposent un déploiement immédiat (D-MCP-LC-MIGRATION).
- Brouillons orphelins qui s'accumulent : rétention ou purge à prévoir.
- Double chemin de publication (UI et tool) divergent : un seul use case de publication.

## 7. Décisions encore ouvertes

1. **D-SOCLE-DRAFT-SCOPE** — artefacts soumis au brouillon dans ce lot (BPMN, DMN, templates
   seulement, ou aussi types, schémas et connecteurs).
2. **D-SOCLE-PROPERTY** — nom et valeurs de la propriété de publication.
3. **D-MCP-LC-SCOPES** — fermeture d'A3-M2 (analyse 46).
4. **D-SOCLE-RETENTION** — durée de vie des brouillons non publiés.

## 8. Documentation impactée

`.env.example`, `GETTING-STARTED.md`, `DEPLOIEMENT.md` (propriété de publication),
`GUIDE-UTILISATEUR.md` (brouillons à publier), `15-serveur-mcp.md`, `CHANGELOG.md` (rupture).
