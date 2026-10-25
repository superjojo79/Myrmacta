---
phase: design
title: Pré-vérification IA des dossiers — conception
description: Issue #211 — architecture, données, API et décisions (brouillon de phase 1, à valider en phase 3)
---

# Pré-vérification IA des dossiers — conception

> **Statut :** brouillon de phase 1. Les choix marqués *(proposition)* sont à confirmer en phase 3 ; les questions
> O1 à O4 des exigences sont à trancher en phase 2.

## Architecture Overview

```mermaid
graph TD
  BPMN["Brique « Pré-vérification IA »<br/>aiPrecheckTask (async)"] --> UC
  Fiche["Fiche dossier<br/>bouton Relancer"] -->|POST /api/dossiers/{id}/prechecks| UC
  MCP["MCP mode opération<br/>run / get precheck"] --> UC
  UC["RunDossierPrecheckUseCase<br/>(domaine)"] --> C1["Passe 1 : complétude<br/>DossierFieldValidator + règles"]
  UC --> IN["Entrée masquée<br/>(sensibles exclus)"]
  IN --> EXT["PieceTextExtractorPort<br/>→ PDFBox"]
  IN --> LLM["PrecheckModelPort<br/>→ adaptateur compatible OpenAI"]
  LLM -->|HTTPS, 1 appel, sans outils| Prov[("Fournisseur<br/>Mistral / vLLM / Ollama / Azure UE")]
  UC --> VAL["Validation de la sortie<br/>schéma JSON + sources connues"]
  VAL --> REPO[("precheck_run<br/>precheck_finding")]
  Agent["Agent (EDIT)"] -->|accepter / écarter| DEC["DecidePrecheckFindingUseCase"] --> REPO
  REPO --> Panel["Panneau « Pré-vérification »"]
```

**Composants**

- **Domaine `domain/precheck`** *(proposition)* : modèles (spécification de la brique, entrée, exécution, constat,
  décision), use cases (lancer, lire, décider), ports (modèle, extraction, dépôt, budget).
- **Infrastructure** : adaptateur du modèle (HTTP compatible OpenAI ; Anthropic via l'existant si retenu), extracteur
  PDFBox, dépôt JDBC, délégué Flowable `aiPrecheckTask`, contrôleur REST, outils MCP, propriétés `app.precheck.*`.
- **Frontend** : panneau `PrecheckPanel.vue` sur la fiche dossier (fr/en), marquage « Généré par IA — indicatif ».

## Data Models

**Spécification de la brique** (attributs littéraux du BPMN, validés au déploiement et à l'exécution) *(proposition)* :

| Attribut | Contenu |
|---|---|
| `precheckPieces` | Pièces à lire, une par ligne (forme selon O1) |
| `precheckQuestions` | Questions fermées, une par ligne : `clé = texte de la question` ; 20 au plus |
| `precheckFields` | Champs non sensibles transmis au modèle (liste blanche) ; sensibles refusés au déploiement |
| `precheckResult` *(facultatif)* | Variable qui reçoit un résumé (nombre de constats par type) pour une passerelle |

**Tables (changeSet `039-dossier-precheck.yaml`)** *(proposition)* :

- `precheck_run` : `id`, `dossier_id` (cascade à la purge), `trigger` (`WORKFLOW`, `MANUAL`, `MCP`), `requested_by`,
  `spec_json` (copie de la spécification, pour « Relancer »), `provider`, `model`, `prompt_version`, `input_sha256`,
  `summary`, `status` (`SUCCEEDED`, `FAILED`), `tokens_in`, `tokens_out`, `created_at`.
- `precheck_finding` : `id`, `run_id`, `kind` (`MISSING`, `INCONSISTENT`, `TO_CHECK`, `OK`), `origin`
  (`DETERMINISTIC`, `MODEL`), `source_type` (`FIELD`, `PIECE`, `RULE`, `QUESTION`), `source_ref`, `message`,
  `confidence`, `decision` (`ACCEPTED`, `DISMISSED`, null), `decided_by`, `decided_at`.

**Sortie du modèle** (schéma JSON fermé) *(proposition)* : `{ "summary": string ≤ 800, "findings": [ { "kind",
"sourceType", "sourceRef", "message" ≤ 300, "confidence": 0..1 } ≤ 30 ] }`. Un `sourceRef` absent de l'entrée est
rejeté.

## API Design

*(proposition)*

- `POST /api/dossiers/{id}/prechecks` (EDIT) : relance avec la spécification de la dernière exécution ; 409 si aucune
  exécution antérieure ni brique dans le workflow ; 202 + exécution asynchrone.
- `GET /api/dossiers/{id}/prechecks` (lecture) : exécutions, la plus récente d'abord, avec leurs constats.
- `PUT /api/dossiers/{id}/prechecks/{runId}/findings/{findingId}/decision` (EDIT) : `ACCEPTED` ou `DISMISSED`.
- `GET /api/dossiers/{id}/prechecks/{runId}/export` (TENANT_ADMIN) : journal pour audit (JSON).
- MCP mode opération : `run_dossier_precheck`, `get_dossier_precheck` (H7 : mêmes droits que l'UI).
- Fonction désactivée : routes absentes (`@ConditionalOnProperty`), outils MCP non enregistrés.

## Component Breakdown

| Lot | Contenu |
|---|---|
| A — Fournisseur et extraction | `PrecheckModelPort` + adaptateur compatible OpenAI (O3), faux fournisseur de test, `app.precheck.*`, plafond de jetons, `PieceTextExtractorPort` + PDFBox borné |
| B — Moteur et brique | Spécification et validation, passe 1, construction de l'entrée masquée, prompt versionné, validation de la sortie, tables 039, délégué asynchrone, refus au déploiement, REST, MCP |
| C — Panneau | `PrecheckPanel.vue`, accepter / écarter, relancer, marquage IA, i18n, E2E Playwright |
| D — Docs et évaluation | Spec `48-preverification-ia.md`, registre (point suivant), guides, exemple riverain, jeu d'évaluation, revue adversariale |

## Design Decisions

- **Un appel sans outils, sortie bornée par un schéma** (H1) : ce n'est pas la boucle d'agent de l'assistant. Une
  consigne cachée dans une pièce ne peut ni appeler un outil ni changer la forme de la sortie ; au pire elle fausse un
  constat, que l'agent voit avec sa source.
- **Déterministe d'abord** : la passe 1 ne dépend pas du modèle ; ses constats existent même si le modèle échoue
  *(proposition : passe 1 enregistrée même en cas d'échec de l'appel, avant l'incident)*.
- **Asynchrone obligatoire** : `flowable:async="true"` exigé au déploiement ; l'appel au modèle ne tient jamais dans la
  transaction d'une complétion de tâche.
- **Minimisation** : seuls les champs déclarés (`precheckFields`) et les pièces déclarées partent ; jamais un champ
  sensible.
- **Rupture consignée** : nouveau point du registre ; `McpToolModes` classe les nouveaux outils `OPERATION` ; le test
  d'égalité du catalogue de l'assistant (D-ASSIST-TOOLS) garantit qu'ils ne lui sont jamais exposés.
- **Écartés** : boucle d'agent avec outils (injection), OCR (hors périmètre), Tika (surface), réutilisation de
  `app.assistant.*` (P8).

## Non-Functional Requirements

- **Sécurité** : HTTPS obligatoire hors boucle locale (comme l'assistant) ; pas de redirection suivie ; erreur du
  fournisseur jamais relayée ; clé jamais journalisée ; messages de constat sans valeur sensible.
- **Coût** : plafond de jetons par jour et par instance (H2) ; bornes de taille (H3).
- **Performance** : délai réglable (défaut 120 s) ; extraction bornée en pages et en caractères ; aucun appel dans une
  requête HTTP synchrone.
- **Fiabilité** : 3 tentatives du moteur, puis incident rejouable (§94).
- **Traçabilité** : journal H4, export pour audit, purge avec le dossier.
