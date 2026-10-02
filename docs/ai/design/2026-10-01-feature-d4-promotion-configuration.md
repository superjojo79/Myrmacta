---
phase: design
title: Design — D4 (promotion de configuration)
status: reviewed
created: 2026-10-01
feature: d4-promotion-configuration
lot: D4
---

# Design — D4

## Bundle
Archive ZIP (`java.util.zip`, aucune dépendance) :
```
manifest.json                      formatVersion, generatedAt, sourceLabel, appVersion, artifacts[{type,key,path,sha256}]
dossier-types/<code>.json          DossierType sans identifiants ni audit
task-question-schemas/<key>.json
connectors/<name>.json             type, enabled, config SANS secrets + liste des champs secrets à fournir
dmn/<key>.dmn                      XML de la dernière version déployée
bpmn/<key>.bpmn20.xml              XML de la dernière version déployée
workflow-configs/<key>.json        statuts, statuts de dossier, observateurs
```
`formatVersion` entier (1) ; un format supérieur à celui que l'instance comprend est refusé. Chaque fichier porte son SHA-256 dans le manifeste ; le manifeste est lui-même vérifié (somme jointe).

## Ordre d'application
types de dossier → schémas de questions → connecteurs → DMN (brouillons) → BPMN (brouillons) → configurations de workflow. (Les DMN précèdent les BPMN qui les référencent ; les connecteurs précèdent les processus qui les appellent.)

## Plan à blanc et application
`ImportPlan` : une entrée par artefact — `CREATE | UPDATE | UNCHANGED | CONFLICT | BLOCKED` + raison. « Inchangé » = représentation normalisée identique (même sans secrets) à celle de la cible. `BLOCKED` : secret requis non fourni, référence manquante. `CONFLICT` : refus métier (type incompatible avec des dossiers). L'application recalcule le plan, exécute CREATE/UPDATE dans l'ordre, ignore BLOCKED/CONFLICT, journalise (`config_import`) et rend un `ImportReport` par artefact. Non atomique par nature (Flowable n'est pas transactionnel avec la base) : la validation à blanc attrape l'essentiel, **rejouer converge**.

## Secrets
Jamais dans le bundle ; les champs mot de passe/secret sont retirés à l'export (liste des noms dans le fichier du connecteur). À l'import : mise à jour d'un connecteur existant ⇒ ses secrets sont conservés (fusion existante) ; création ⇒ les secrets requis viennent de `secrets` dans la requête d'import (TLS, jamais journalisés, seuls les noms de champs apparaissent dans la trace) ; sinon l'artefact est `BLOCKED`.

## Brouillons
BPMN et DMN passent par `ProposeDraftUseCase` avec la provenance `IMPORT` (D6) ; la configuration de workflow de chaque BPMN est jointe au brouillon **et** les `workflow-configs/*.json` sont appliqués par `SaveWorkflowTypeConfigUseCase` seulement quand le BPMN correspondant est publiable (voir D-PROMO-WFCONFIG).

## Surfaces
REST `/api/admin/configuration` (`GET export` → ZIP, `POST plan`, `POST apply`) ; MCP `export_configuration` (JSON de manifeste + contenus), `import_configuration` (plan ; `apply=true` → brouillons seulement) ; SPA : écran *Admin → Promotion* (export, dépôt d'un bundle, plan, application).
