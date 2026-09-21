---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
---

# Testing Strategy — `enrichissement-reporting`

## Test Coverage Goals

- Backend : `./mvnw verify` (Jacoco LINE ≥ 85 %, BRANCH ≥ 75 %).
- Frontend : `cd frontend && npm run test` et `npm run test:e2e`.
- Playwright obligatoire : la feature touche `/rapports`, l’admin des
  types et trois contrats API consommés par le SPA.
- Les scénarios ci-dessous sont des cases `- [ ]`. Chacun est repris
  par au moins une tâche du planning.

## Unit Tests

### Réglages

- [ ] T-SET-1 : bornes rejetées (intervalle alerte < 15 ou > 10080, BI < 1 h ou > 720 h, rétention hors 1–365, plus de 20 e-mails ou webhooks).
- [ ] T-SET-2 : e-mail mal formé rejeté ; webhook d’un autre tenant ou désactivé rejeté à l’enregistrement.
- [ ] T-SET-3 : activation alerting écrit un snapshot de chaque dossier scoré sans appeler `SendNotificationUseCase`.
- [ ] T-SET-4 : réactivation (false → true) refait un snapshot sans envoi.
- [ ] T-SET-5 : `complianceTargetPercent` hors 1–100 reste rejeté (non-régression).

### Alerting

- [ ] T-ALR-1 : feature éteinte → aucun envoi, aucun changement d’état.
- [ ] T-ALR-2 : scan avant échéance → no-op.
- [ ] T-ALR-3 : `ON_TRACK` → `AT_RISK` envoie une alerte et mémorise `AT_RISK`.
- [ ] T-ALR-4 : second scan toujours `AT_RISK` → pas de second envoi.
- [ ] T-ALR-5 : `AT_RISK` → `BREACHED` envoie une alerte `BREACHED` seulement.
- [ ] T-ALR-6 : `ON_TRACK` → `BREACHED` dans le même scan (fenêtre manquée) envoie seulement `BREACHED`.
- [ ] T-ALR-7 : `BREACHED` → `ON_TRACK` mémorise sans envoi ; un retour `AT_RISK` ré-alerte.
- [ ] T-ALR-8 : `slaTargetHours` null → pas de ligne, pas d’alerte.
- [ ] T-ALR-9 : dossier clos dans les temps → pas `AT_RISK` ; clos en retard → `BREACHED` seulement s’il n’était pas déjà mémorisé ainsi.
- [ ] T-ALR-10 : fan-out EMAIL (une outbox par adresse), WEBHOOK (webhook encore activé seulement), BROWSER (participants dédupliqués). Payload sans valeur de champ.
- [ ] T-ALR-11 : liste d’e-mails vide → pas d’EMAIL ; BROWSER part quand même.
- [ ] T-ALR-12 : échec d’envoi → l’état de ce dossier n’est pas avancé.

### Widgets

- [ ] T-OPS-1 : le use case renvoie les deux compteurs du port, sans lire les interrupteurs.

### Export BI

- [ ] T-BI-1 : feature éteinte → aucun store, horloge inchangée.
- [ ] T-BI-2 : avant échéance (dernier succès + intervalle) → no-op.
- [ ] T-BI-3 : un fichier `DOSSIERS` par type, colonnes identiques à l’export manuel, champs sensibles masqués.
- [ ] T-BI-4 : un fichier `SLA` sans filtre, en-tête compliance et colonnes de l’export manuel, période 30 jours.
- [ ] T-BI-5 : succès → `last_bi_export_at` avance et la rétention supprime storage + lignes trop vieux.
- [ ] T-BI-6 : échec après store → delete best-effort, horloge non avancée.
- [ ] T-BI-7 : la liste exclut un CSV de type si `canViewType` est faux ; le CSV SLA reste ; téléchargement interdit → 404.
- [ ] T-BI-8 : export désactivé → pas de nouveau fichier, mais les lignes plus vieilles que la rétention sont supprimées (storage inclus).

## Integration Tests

- [ ] T-IT-1 : `PUT /api/tenant/sla-settings` persiste interrupteurs, e-mails et webhooks ; un non-admin reçoit 403.
- [ ] T-IT-2 : activation puis job : un dossier déjà `BREACHED` avant activation ne part pas ; un dossier qui passe `AT_RISK` après snapshot part une fois sur les trois canaux (outbox).
- [ ] T-IT-3 : `GET /api/reports/operations` compte un dead-letter et une tâche sans assigné créés dans le test, rôle rapport requis.
- [ ] T-IT-4 : export BI contre storage filesystem de test, liste filtrée par ACL, téléchargement du CSV, purge de rétention.

## End-to-End Tests

- [ ] T-E2E-1 : admin enregistre alerting + BI sur l’écran des types ; rechargement affiche les valeurs.
- [ ] T-E2E-2 : `/rapports` affiche les deux compteurs mockés.
- [ ] T-E2E-3 : `/rapports` liste un export autorisé et pas un export d’un type sans `VIEW` (réponse API déjà filtrée, l’UI n’invente pas de ligne).
- [ ] T-E2E-4 : régression des specs Playwright rapports / admin types existantes.
- [ ] T-E2E-5 : mocks `frontend/e2e/**` alignés sur les nouveaux corps JSON (`sla-settings`, `operations`, `bi-exports`).

## Test Data

- Dossier type avec `slaTargetHours` et un type sans cible.
- Webhook activé et webhook désactivé du même tenant.
- Horloge injectée (`Instant` paramètre des use cases), pas `Thread.sleep`.
- Storage filesystem ou fake `DocumentStoragePort` en unitaire ; adaptateur réel en IT si le dépôt a déjà un fake filesystem pour les pièces.

## Test Reporting & Coverage

```bash
./mvnw verify
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Preuve Playwright à coller en Phase 8 avant de marquer la testing doc `verified`.

## Manual Testing

- Activer l’alerting sur un tenant local, avancer un dossier sous la cible, vérifier l’e-mail (ou l’outbox) et le tiroir SSE.
- Vérifier qu’un dossier déjà en retard au moment de l’activation ne produit pas de message.
- Télécharger un CSV BI et le comparer à l’export manuel du même type.
