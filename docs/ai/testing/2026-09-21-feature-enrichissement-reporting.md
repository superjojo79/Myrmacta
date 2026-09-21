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
- Les scénarios ci-dessous sont des cases `- [x]`. Chacun est repris
  par au moins une tâche du planning.

## Unit Tests

### Réglages

- [x] T-SET-1 : bornes rejetées (intervalle alerte < 15 ou > 10080, BI < 1 h ou > 720 h, rétention hors 1–365, plus de 20 e-mails ou webhooks).
- [x] T-SET-2 : e-mail mal formé rejeté ; webhook d’un autre tenant ou désactivé rejeté à l’enregistrement.
- [x] T-SET-3 : activation alerting écrit un snapshot de chaque dossier scoré sans appeler `SendNotificationUseCase`.
- [x] T-SET-4 : réactivation (false → true) refait un snapshot sans envoi.
- [x] T-SET-5 : `complianceTargetPercent` hors 1–100 reste rejeté (non-régression).

### Alerting

- [x] T-ALR-1 : feature éteinte → aucun envoi, aucun changement d’état.
- [x] T-ALR-2 : scan avant échéance → no-op.
- [x] T-ALR-3 : `ON_TRACK` → `AT_RISK` envoie une alerte et mémorise `AT_RISK`.
- [x] T-ALR-4 : second scan toujours `AT_RISK` → pas de second envoi.
- [x] T-ALR-5 : `AT_RISK` → `BREACHED` envoie une alerte `BREACHED` seulement.
- [x] T-ALR-6 : `ON_TRACK` → `BREACHED` dans le même scan (fenêtre manquée) envoie seulement `BREACHED`.
- [x] T-ALR-7 : `BREACHED` → `ON_TRACK` mémorise sans envoi ; un retour `AT_RISK` ré-alerte.
- [x] T-ALR-8 : `slaTargetHours` null → pas de ligne, pas d’alerte.
- [x] T-ALR-9 : dossier clos dans les temps → pas `AT_RISK` ; clos en retard → `BREACHED` seulement s’il n’était pas déjà mémorisé ainsi.
- [x] T-ALR-10 : fan-out EMAIL (une outbox par adresse), WEBHOOK (webhook encore activé seulement), BROWSER (participants dédupliqués). Payload sans valeur de champ.
- [x] T-ALR-11 : liste d’e-mails vide → pas d’EMAIL ; BROWSER part quand même.
- [x] T-ALR-12 : échec d’envoi → l’état de ce dossier n’est pas avancé, les outbox de ce dossier sont annulées, `last_alert_scan_at` avance quand même.
- [x] T-ALR-13 : un dossier créé hors de la fenêtre de 30 jours du rapport est quand même évalué.

### Widgets

- [x] T-OPS-1 : le use case renvoie les deux compteurs du port, sans lire les interrupteurs.

### Export BI

- [x] T-BI-1 : feature éteinte → aucun store, horloge inchangée.
- [x] T-BI-2 : avant échéance (référence activation ou dernier succès + intervalle) → pas de nouveau fichier. Premier tour : pas d’export immédiat.
- [x] T-BI-9 : le CSV stocké retire les clés sensibles même si le job n’est pas un `TENANT_ADMIN`. L’export manuel admin reste en clair.
- [x] T-BI-3 : un fichier `DOSSIERS` par type, colonnes identiques à l’export manuel, champs sensibles masqués.
- [x] T-BI-4 : un fichier `SLA` sans filtre, en-tête compliance et colonnes de l’export manuel, période 30 jours.
- [x] T-BI-5 : succès → `last_bi_export_at` avance et la rétention supprime storage + lignes trop vieux.
- [x] T-BI-6 : échec après store → delete best-effort, horloge non avancée.
- [x] T-BI-7 : la liste exclut un CSV de type si `canViewType` est faux ; le CSV SLA reste ; téléchargement interdit → 404.
- [x] T-BI-8 : export désactivé → pas de nouveau fichier, mais les lignes plus vieilles que la rétention sont supprimées (storage inclus).

## Integration Tests

- [x] T-IT-1 : `PUT /api/tenant/sla-settings` persiste interrupteurs, e-mails et webhooks ; un non-admin reçoit 403.
- [x] T-IT-2 : activation puis job : un dossier déjà `BREACHED` avant activation ne part pas ; un dossier qui passe `AT_RISK` après snapshot part une fois sur les trois canaux (outbox).
- [x] T-IT-3 : `GET /api/reports/operations` compte un dead-letter et une tâche sans assigné créés dans le test, rôle rapport requis.
- [x] T-IT-4 : export BI contre storage filesystem de test, liste filtrée par ACL, téléchargement du CSV, purge de rétention.

## End-to-End Tests

- [x] T-E2E-1 : admin enregistre alerting + BI sur l’écran des types ; rechargement affiche les valeurs.
- [x] T-E2E-2 : `/rapports` affiche les deux compteurs mockés.
- [x] T-E2E-3 : `/rapports` liste un export autorisé et pas un export d’un type sans `VIEW` (réponse API déjà filtrée, l’UI n’invente pas de ligne).
- [x] T-E2E-4 : régression des specs Playwright rapports / admin types existantes.
- [x] T-E2E-5 : mocks `frontend/e2e/**` alignés sur les nouveaux corps JSON (`sla-settings`, `operations`, `bi-exports`).
- [x] T-SSE-1 : le store traite l’événement nommé `notification` et affiche `payload.title` / `payload.body`. Un événement sans titre garde le libellé de repli.

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
