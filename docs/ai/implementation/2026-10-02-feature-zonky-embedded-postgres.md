---
phase: implementation
title: PostgreSQL embarqué (zonky) — constats d'implémentation
description: Ce que PostgreSQL réel a révélé que H2 masquait
status: done
created: 2026-10-02
updated: 2026-10-02
feature: zonky-embedded-postgres
---

# Implementation — `zonky-embedded-postgres`

## Découvertes (ce que H2 cachait)

1. **Bug de production corrigé — `SequentialReferenceGenerator`.** Il faisait `INSERT` puis
   `catch (DuplicateKeyException)` puis `UPDATE` dans la même transaction. Sous PostgreSQL une violation
   de contrainte **avorte la transaction** : dès que la ligne du compteur existait déjà, l'`UPDATE`
   suivant échouait (`current transaction is aborted`). Corrigé par `INSERT … ON CONFLICT DO NOTHING`
   (compatible `UPDATE` atomique, aucun trou ni doublon : `SequentialReferenceMultiPodIT` vert, 8 pods × 10 appels).
2. **Les 34 `*IT` n'étaient jamais exécutés par `verify`** (pas de Failsafe ; surefire n'inclut que
   `*Test`). Dont les ITs PostgreSQL — la mention « exécuté en CI » de leurs Javadocs était fausse.
   Désormais inclus (surefire `includes`).
3. **Point ouvert, non corrigé — `DocumentTemplateJpaEntity.htmlContent`** est `@Lob String` sur une colonne
   `clob` (PostgreSQL `text`). Hibernate écrit/lit un **OID de large object** dans cette colonne texte :
   une ligne insérée en SQL brut (HTML en clair) n'est pas relisible par l'entité
   (`Bad value for type long : <p>x</p>`), et le contenu réel vit dans `pg_largeobject`. Corriger
   change le format de stockage des données existantes : décision à prendre (migration de données).
   `DocumentTemplateSchemaIT` nettoie désormais ses lignes SQL brutes (base de test partagée).
4. Écarts de tests H2/PostgreSQL corrigés : colonne `jsonb` (mise en forme normalisée → comparaison
   JSON), `information_schema.indexes` → `pg_indexes`, paramètres `Instant` → `Timestamp`,
   `LIKE` sur `jsonb` → `CAST(… AS VARCHAR)`.

## Preuves

- Branches `dbms: h2` retirées **sans changer un seul checksum PostgreSQL** (`ChangelogChecksumStabilityIT`,
  50 changeSets ; test de mutation : modifier un changeSet fait échouer le test).
- Reprise idempotente : second `update` sans erreur de checksum ni nouvelle ligne.
- Mode démo : `ZonkyEmbeddedDbLauncherTest` (persistance, rôle non-superuser, instance unique, version
  majeure) et `EmbeddedDbProfileRestartIT` (l'application complète redémarre sur les mêmes données, sans re-migration).
- Jar de production sans zonky ni binaires (`scripts/check-no-embedded-db-in-jar.sh`, appelé par la CI).

## Limites

- Le jar `-Pembedded-db` fait ~366 Mo (binaires de 7 plateformes) ; image Docker de démo non construite ici
  (pas de daemon Docker) : la présence des bibliothèques système requises par les binaires zonky sur
  `eclipse-temurin:25-jre` reste à confirmer lors du premier build.
- Arrêt de la JVM par `SIGKILL` : le postmaster survit ; le lanceur le détecte et demande de l'arrêter.
