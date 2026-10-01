---
phase: implementation
title: Implémentation — D5 (jobs planifiés sûrs sous double exécution)
feature: d5-verrou-jobs-planifies
---

# Implémentation — D5 (TODO §79)

Mécanisme et audit : voir le design. Migrations additives : 027 (`outbox_event.claimed_until`), 028 (`portal_link.claimed_until`), 029 (`dossier_workflow_link.reconcile_claimed_until`).
Chaque prise est un `UPDATE` conditionnel autonome ; les ports gagnent `lockNextPending` (renvoie les lignes prises), `renewClaim`, `claimForSend`,
`claimTrigger`/`releaseTrigger`, `claimForReconcile`, `advanceLastBiExportAt`, `SlaAlertStateRepository.transition`/`insertIfAbsent`.

## Revue adversariale (Phase 10) — 1 passe, sous-agent indépendant ; sondes exécutées sur H2 **et sur un PostgreSQL 16 réel en READ COMMITTED**
0 Critique, **1 Haute**, 4 Moyennes, 3 Basses. Les tests D5 existants passaient sur PostgreSQL (UPDATE conditionnels bien réévalués après attente de verrou ; `@Transactional` des méthodes d'interface
Spring Data bien appliqué ; `clearAutomatically` sans effet de bord).

| # | Sév. | Constat | Correctif |
|---|---|---|---|
| 1 | **Haute** | Alerte SLA en double sur le chemin d'**insertion** (première évaluation d'un dossier en retard) : `save` est une fusion JPA (SELECT puis INSERT *ou UPDATE*) — le perdant écrasait la ligne du gagnant et notifiait aussi (`enqueued=2`) | `insertIfAbsent` : insertion HQL stricte (violation d'unicité ⇒ `false`), on ne notifie que si une ligne a été insérée ; `SlaAlertConcurrencyIT` couvre maintenant les deux chemins |
| 2 | Moyenne | Bail d'outbox pris pour tout le lot, issues finales inconditionnelles : à `now+301 s` un second exécutant renvoyait la ligne ; l'échec tardif du premier la ressuscitait (troisième envoi) ; JavaMail sans délai | Bail **renouvelé juste avant chaque ligne** (`renewClaim`, jeton = `next_attempt_at`) ; `markProcessed`/`markDead`/`recordFailure` en `UPDATE … WHERE status = PENDING` ; délais SMTP 10/30/30 s |
| 3 | Moyenne | Distribution des signatures : le correctif de relecture ne servait à rien (le CAS de version protégeait déjà) ; la vraie course (CAS `dispatched` perdu après corrélation réussie ⇒ `dispatchGaveUp` à tort) restait ouverte | Après corrélation réussie, `dispatched` est relu et **réappliqué** (idempotent) ; deux tests de course réels |
| 4 | Moyenne | Cron « au plus une fois » : un pod tué entre prise et déclenchement perd le créneau | Décision D-JOBS-CRON amendée : arrêt gracieux (`await-termination` 30 s) pour les déploiements progressifs ; **résidu ⚠ consigné** : arrêt brutal ⇒ un créneau perdu |
| 5 | Moyenne | Réconciliation : si `confirmStarted` échoue et que le process (court) se termine, le passage suivant ne le trouve pas (runtime seul) et le relance | `findProcessInstanceIdByBusinessKey` interroge aussi l'**historique** ; `BusinessKeyLookupIT` |
| 6 | Basse | `releaseTrigger` inopérant sur H2 avec un `Instant` à la nanoseconde (colonnes à la microseconde) | Instant du créneau tronqué à la microseconde dans les deux déclencheurs |
| 7 | Basse | Test de concurrence des notifications instable (le vrai relais `@Scheduled` consommait les lignes de test datées 2020) | Lignes datées de 2099 |
| 8 | Basse | `claimForSend` avec implémentation par défaut `true` ; famine portail sur prises orphelines ; purge BI non protégée | Défaut supprimé (méthode abstraite ; double de test fidèle). Famine (réessai 10 s → 2 min après exception avant transition) et purge BI : **consignés, non traités** (effets mineurs, hors double envoi) |

Non traité, consigné : réconciliation vs `OutboxRelayScheduler` (même lien `RESERVED`, mêmes protections que le relais d'avant — limitée aux liens orphelins de plus de 5 min) ; relais outbox workflow : bail posé au début du lot
(atténué par l'idempotence du lien et de la business key) ; CI uniquement sur H2 (Docker absent ici : la revue a rejoué les tests sur PostgreSQL réel).
Passe 2 non requise après correction de la Haute ? **Si** : une Haute a été trouvée — seconde passe indépendante à lancer sur le correctif (voir AVANCEMENT).
