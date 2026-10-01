---
phase: requirements
title: Requirements — D5 (jobs planifiés sûrs sous double exécution)
status: reviewed
created: 2026-10-01
feature: d5-verrou-jobs-planifies
lot: D5
todo_ref: TODO.md §79 · analyse 36
---

# Requirements — D5

## Problème
18 méthodes `@Scheduled`, aucun verrou distribué. L'audit (docs/ai/design/…-d5-…md § Audit) montre que, dès que **deux pods** exécutent les jobs en même temps, sept d'entre eux
produisent des **doublons visibles** (e-mails, webhooks, notifications SLA, dossiers, fichiers BI) ou une **corruption** (deux instances de process pour une même business key,
événement marqué DEAD à tort). Le cas n'est pas théorique même avec « 1 instance = 1 tenant » : un **déploiement progressif** (rolling update) fait coexister l'ancien et le
nouveau pod pendant la bascule, avec les mêmes jobs actifs des deux côtés.

## Objectifs
- G1. Chaque job à risque devient sûr si deux exécutants se chevauchent : **au plus un effet externe par unité de travail**.
- G2. Sans nouvelle infrastructure (pas de Redis, pas de nouvelle dépendance), identique sous H2 (tests) et PostgreSQL.
- G3. Aucune régression de comportement pour un pod unique ; les latences des relais (1 s) ne changent pas.
- G4. Documenter pour l'exploitant ce qui est sûr, ce qui ne l'est pas (SSE multi-pod, Kafka au-moins-une-fois) dans `DEPLOIEMENT.md`.

## Hors périmètre (décidé)
SSE multi-pod, rate-limit distribué, Spring Session (pas de Redis) ; idempotence des messages Kafka rejoués après un rebalance.

## Décisions tranchées (DECISIONS.md)
D-JOBS-TRIGGER (corriger dès maintenant), D-JOBS-MECHANISM (claim par ligne, pas d'advisory lock), D-JOBS-LEASE (bail et reprise des unités abandonnées).

## Critères d'acceptation
- Pour chaque job corrigé, un test lance **deux exécutants concurrents** sur la même unité de travail et prouve : effet externe exactement une fois, état final cohérent.
- Une unité « prise » par un exécutant qui meurt est reprise après le bail (jamais perdue ni envoyée deux fois sans attente du bail).
- Les jobs déjà sûrs (purge, rétention, signature, expiration portail) ne sont pas modifiés ; leur preuve reste dans le design.
