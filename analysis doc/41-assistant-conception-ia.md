# Assistant de conception IA dans l'UI (LC3)

> **Statut : première analyse (2026-09-28).** Sous-lot **LC3** `assistant-conception-ia` de la
> couche low-code (D0 = C, [31](31-positionnement-produit.md), rang 3). **Prérequis : D6**
> ([37](37-socle-mcp-low-code.md)). Les décisions d'identité et de modes sont déjà tranchées dans
> [46-exposition-mcp-couche-low-code.md](46-exposition-mcp-couche-low-code.md) (TODO §71).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Catalogue d'outils | 27 tools MCP atomiques couvrant types, BPMN, DMN (dont `evaluate_decision`, `test_decision`), connecteurs, questions, dossiers, génération | `infrastructure/mcp/tool/` |
| Intégration LLM | **Aucune** : seul `spring-ai-starter-mcp-server-webmvc` est présent, pas de starter de modèle | `pom.xml`, `application.yml` (« Pas de starter chat/model ») |
| Écriture de templates | Pas de tool (lecture seule) | analyse 46 §2 |
| Brouillons | Existent pour BPMN / DMN, non utilisés par le MCP | analyse 46 §2 |

## 2. Objectif

Un utilisateur métier décrit son besoin en langage naturel ; l'assistant produit **en brouillon**
le type de dossier, le BPMN, les tables DMN, les formulaires et les templates ; l'utilisateur
relit dans les éditeurs existants, teste (`test_decision`, aperçus), puis publie selon
D-MCP-LC-PUBLISH.

## 3. Décisions déjà tranchées (analyse 46)

- **D-MCP-LC-IDENTITY = A** : l'assistant appelle le catalogue **dans le processus**, avec
  l'`AccessContext` de l'utilisateur de la session BFF ; pas de jeton dans le navigateur, pas de
  passage par `/api/m2m/mcp`.
- **D-MCP-LC-MODES = A** : mode *conception* (configuration et catalogues, aucune donnée de
  dossier) séparé du mode *opération*.
- **D-MCP-LC-PUBLISH = C, défaut A** : l'assistant ne publie pas en production.

## 4. Périmètre à poser (Phase 1)

- **Fournisseur de modèle** : appel à un LLM depuis le backend (clé d'API côté serveur, jamais
  dans le navigateur) ; choix du fournisseur et du modèle, région d'hébergement des données.
- **Boucle d'agent** : exécution des tools côté serveur, limites de tours et de coût par
  conversation, journal des appels.
- **UI** : panneau de conversation dans les écrans admin, lien vers chaque brouillon produit,
  aperçu du diff.
- **Qualité** : l'assistant vérifie son travail (`dryRun`, `validate_*`, `test_decision`) avant
  de proposer ; scénarios d'évaluation de référence (sinistre, subvention, réclamation).
- **Conversations** : persistance ou non, rétention, rattachement aux brouillons produits.

## 5. Coutures et dépendances

- **D6** : brouillons, publication, provenance `ASSISTANT`, acteur paramétrable, modes.
- LC1 et LC2 améliorent la qualité de génération (briques typées, expressions validables).
- LC4b pour que l'assistant écrive des templates ; LC4a pour les formulaires.

## 6. Risques

- **Injection de consignes par les données** : mitigée par le mode *conception* (analyse 46 §5).
- **Données envoyées au fournisseur** : sujet RGPD (sous-traitant, localisation) ; en mode
  *conception*, seules des métadonnées partent.
- **Secrets** : jamais via l'assistant (P9) ; un connecteur créé par l'assistant attend ses
  secrets saisis par un humain.
- **Coût** : plafonds par tenant et par conversation.
- **Qualité variable** des générations : brouillons, tests et historique obligatoires.

## 7. Décisions à trancher

1. **D-ASSIST-PROVIDER** — fournisseur et modèle ; hébergement des données.
2. **D-ASSIST-COST** — plafonds (tours, jetons, coût) par conversation et par tenant.
3. **D-ASSIST-HISTORY** — conservation des conversations et durée.
4. **D-ASSIST-ROLES** — rôles autorisés à utiliser l'assistant en mode *conception*.

## 8. Documentation impactée

`GUIDE-UTILISATEUR.md` (assistant), `.env.example` et `GETTING-STARTED.md` (clé et modèle),
`12-autorisation-conformite.md` (sous-traitance des données), `15-serveur-mcp.md`.
