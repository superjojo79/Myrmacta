---
phase: testing
title: Testing — D3b
feature: d3b-mapping-reponse-connecteur
---

| Couche | Preuves |
|---|---|
| Domaine | `FieldMappingSpecTest` (format, bornes), `BuildingBlockCatalogTest` (13 briques, `multiline`), `BlockAttributeRulesTest`, `WorkflowVariablesTest` (`connectorResult` réservée) |
| Déploiement | `FlowableBpmnParserAdapterBlockTest` : mapping valide, expression invalide (champ + activité), format invalide, attribut sur un autre delegate |
| Delegate | `FieldMappingDelegateTest` (15) : patch unique, tout ou rien, sensible non lisible, corps non JSON / volumineux, attribut littéral seulement, revalidation, type inconnu fermé, cible non déclarée, erreur nommée, auteur, audit |
| Contexte | `BusinessExpressionContextTest` : `json`, réponse piégée évaluée par le vrai moteur, précision exacte, bornes |
| Moteur réel | `FieldMappingSmokeIT` (4) : Flowable + use case + dossier réels : enrichissement en une étape de version, valeur invalide ⇒ rien d'écrit, montant au-delà de l'échelle refusé, sensible cible/source |
| API catalogue | `BuildingBlockApiTest` : 13 briques, `multiline` publié |
| SPA | `blockDiagnostics.spec`, `blockValues.spec` (multiligne) ; Playwright `E2E-PALETTE-9` (zone de texte multiligne, aucun faux avertissement, enregistrement) |

Résultats finaux : `./mvnw verify` 2857 tests / 0 échec ; Vitest 543/543 ; Playwright 111/111 (`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium-1194/chrome-linux/chrome`).
