---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-22
updated: 2026-08-22
feature: modele-physique-consolide
design_ref: docs/ai/design/2026-08-22-feature-modele-physique-consolide.md
---

# Testing Strategy — `modele-physique-consolide`

Feature documentaire (aucun code, aucun schéma modifié) — pas de
Vitest/JUnit/Playwright applicable. "Test" ici = vérification factuelle
du contenu du nouveau document contre le code source réel, et
non-régression du reste de la documentation.

## Vérification de contenu (remplace Unit/Integration Tests)

### Exactitude factuelle de `13-modele-physique.md`
- [x] Les 17 tables listées dans l'ERD correspondent exactement aux
      `tableName:` des changelogs (`grep tableName: db/changelog/changes/*.yaml`,
      pas de table oubliée ni inventée)
- [x] Les 4 index uniques partiels documentés correspondent aux `CREATE
      UNIQUE INDEX ... WHERE`/colonnes générées réels (pas de 5e index
      oublié, pas de description erronée de la condition)
- [x] La distinction FK DB-contrainte (2, sur `dossier_relation`
      seulement) vs FK logique (le reste) est correcte — vérifiée
      contre les seuls `addForeignKeyConstraint` réellement présents
- [x] `dossier_type_acl`, `tenant_connector_dek`, `outbox_event` sont
      décrites avec leurs colonnes réelles, pas approximées

### Cohérence avec les décisions actées (Phase 1)
- [x] Aucune mention d'un `TenantRegistry` actif ou consulté à chaque
      requête (D-MPC-SCOPE)
- [x] Aucune mention de contextes Liquibase `tenant-init`/`tenant-migration`
      séparés comme s'ils existaient (D-MPC-SCOPE)
- [x] `platform.tenant_migration_status` présentée comme option non
      retenue, pas comme une table existante (D-MPC-PLATFORM)
- [x] Aucun changelog Liquibase, aucune classe Java ajoutée ou modifiée
      (feature doc pure — vérifié par `git diff --stat` avant de
      clore la feature)

## Non-régression documentaire (remplace End-to-End Tests)

- [x] `00-index.md` référence le nouveau `13-modele-physique.md` sans
      casser le tableau existant (12 lignes → 13, numérotation
      cohérente)
- [x] Le diagramme mermaid est syntaxiquement valide (rendu sans erreur
      — vérifié visuellement, pas d'outil de lint mermaid dans ce
      projet)
- [x] Les liens croisés vers `02-multi-tenant.md`, `04-dossiers.md`,
      `05-workflow-engine.md` résolvent vers des ancres/fichiers
      existants
- [x] `TODO.md` §25 : passage à `[x]`, note de résolution cohérente
      avec le contenu réel du nouveau document (pas de promesse non
      tenue)
- [x] `FILE-ATTENTE.md` : feature déplacée vers "Récemment livré" avec
      la bonne référence TODO/PR (PR #68 mergée)

## Test Data

Aucune — pas de fixture, pas de seed, pas de base de données impliquée.

## Test Reporting & Coverage

Pas de couverture de code applicable (aucun fichier `.java`/`.vue`/`.ts`
modifié). Preuve de complétude = revue manuelle ligne par ligne du
nouveau document contre `grep`/parsing des changelogs (voir ci-dessus),
consignée en Phase 7/8.

## Manual Testing

Revue humaine du document final recommandée après merge (lisibilité,
clarté du diagramme) — hors périmètre de cette session automatisée,
signalé comme tel plutôt que faussement coché.

## Performance Testing

Non applicable.

## Bug Tracking

Aucun processus dédié — toute inexactitude trouvée en revue (Phase 7)
est corrigée directement dans le document avant de clore la feature.
