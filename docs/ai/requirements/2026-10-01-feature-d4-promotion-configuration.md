---
phase: requirements
title: Requirements — D4 (promotion de configuration entre environnements)
status: reviewed
created: 2026-10-01
feature: d4-promotion-configuration
lot: D4
todo_ref: TODO.md §83 · analyse 35
---

# Requirements — D4

## Problème
Types de dossier, schémas de questions, instances de connecteur, tables DMN, processus BPMN et configurations de workflow se recréent **à la main** (ou par MCP) sur chaque environnement. Un intégrateur qui fait une recette puis une production n'a aucun moyen de rejouer une configuration éprouvée, ni de voir ce qui différerait avant de l'appliquer.

## Objectifs
- G1. **Exporter** la configuration d'un environnement en un **bundle** versionné, lisible (fichiers JSON + XML), versionnable dans Git.
- G2. **Importer à blanc** : un plan par artefact (créé / modifié / inchangé / bloqué / en conflit) **sans rien écrire**, avec les mêmes validations qu'une saisie manuelle.
- G3. **Appliquer** le bundle de façon **idempotente** (rejouer converge, ne duplique pas), dans l'ordre des dépendances, avec un **rapport par artefact** et une **trace** (qui, quand, quel bundle).
- G4. **Aucun secret** dans un bundle ; les secrets de connecteur se saisissent sur la cible (écran) ou sont **injectés à l'import**, jamais journalisés.
- G5. **Aucune écriture directe en base** : tout passe par les cas d'usage existants (validations, listes blanches, règle 409). BPMN et DMN arrivent en **brouillons à publier** (D6), jamais en ligne.
- G6. Exposition MCP : `export_configuration` (lecture) et `import_configuration` (plan à blanc ; application en brouillons uniquement).
- G7. Réservé à `TENANT_ADMIN` (export, plan, application).

## Hors périmètre (première tranche)
ACL par type (dépendent des groupes du fournisseur d'identité) ; réglages tenant (rétention, SLA, webhooks : URLs propres à chaque environnement) ; templates de documents (cycle de vie versions/variantes, repris avec l'éditeur LC4b) ; dossiers et données d'exploitation.

## Critères d'acceptation
- Export → import sur une instance vide recrée types, schémas, connecteurs (inactifs sans secrets), brouillons BPMN/DMN et configurations de workflow ; un second import ne change rien (tout « inchangé »).
- Le bundle ne contient aucune valeur de champ de connecteur de type mot de passe / secret, ni la DEK ; un bundle forgé qui en contient est refusé à l'import.
- Un bundle d'une version de format inconnue, d'une somme de contrôle incorrecte ou avec un chemin d'archive hors périmètre (`..`, absolu) est refusé en 400 sans écrire.
- Le plan à blanc et l'application s'accordent : ce qui est annoncé « bloqué » est ignoré (pas d'échec global) et rapporté.
- Un type de dossier dont la mise à jour est incompatible avec des dossiers existants est signalé « conflit » (409 métier) et jamais forcé.
- L'import ne publie jamais de BPMN/DMN : ils apparaissent dans *Brouillons à publier* avec la provenance « Import ».
