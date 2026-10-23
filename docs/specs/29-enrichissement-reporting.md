# Enrichissement du reporting — alerting SLA, dashboard, export BI

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Origine : `ROADMAP.md` v1.1 (14/09/2026), items **5.2** (Alerting SLA proactif),
> **3.3** (Dashboard opérationnel — partiellement livré) et **5.3** (Export BI personnalisable), tous
> classés « Produit, pas cadré » ou incrément. Regroupés en **un seul** document parce que les trois
> sont des **extensions** du même socle déjà livré — `reporting-sla` (PR #80) — pas des features
> autonomes : même écran (`/rapports`), mêmes use cases (`GetSlaSummaryUseCaseImpl`,
> `GetSlaReportUseCaseImpl`), même modèle (`SlaStatus`).

---

## 1. Constat vérifié (pas supposé)

| Élément | État réel | Preuve |
|---|---|---|
| Écran `/rapports` | **Livré** | KPI, conformité, filtres période/type, inbox tâches avec `slaRemainingHours` (PR #80) |
| Endpoints reporting | **Livrés** | `SlaReportController` (`GET /api/reports/sla`), `GetSlaSummaryUseCaseImpl` (`query_sla_summary` MCP compris) |
| Statuts SLA calculés | **Livrés** | `SlaStatus` : `ON_TRACK`/`AT_RISK`/`BREACHED`, `TypeSlaMeta`/`SlaClock` |
| Export CSV | **Livré, basique** | `SlaReportExportController` (CSV rapports SLA), `DossierExportController` (`GET /api/dossiers/export.csv`, champs sensibles masqués — durcissement-donnees-sensibles, PR #62) |
| Job d'alerte proactif (email/escalade sur `AT_RISK`/`BREACHED`) | **Absent** | Aucun `@Scheduled`/job ne lit `SlaStatus` pour déclencher une notification — grep confirmé sur les jobs existants (`WorkflowRetentionJob`, `EvaluateDossierRetentionJob`, `PurgeOutboxJob`, `SchedulerConnectorTriggerJob`, `ReservedReconcileJob`, `OutboxRelayScheduler`) : aucun ne touche au reporting SLA |
| Widgets « files bloquées / instances en erreur » | **Absents** | `/rapports` couvre conformité + inbox tâches, pas d'agrégat sur les instances de process en erreur |
| Export BI (JDBC/Power BI, planification SFTP) | **Absent** | Le CSV existant est un export **ponctuel** déclenché par l'utilisateur, pas une source interrogeable en continu ni un job planifié |

**Point clé** : contrairement aux trois autres documents de ce lot, le calcul métier
(`AT_RISK`/`BREACHED`) **existe déjà** — ce n'est pas un gap de domaine, c'est un gap de
**branchement** : rien ne consomme aujourd'hui `SlaStatus` en dehors de l'affichage passif dans
`/rapports`.

---

## 2. Ce que chaque sous-item impliquerait

### 2.1 Alerting SLA proactif (5.2)

- **Techniquement le plus simple des trois** : réutiliser le patron job déjà 6 fois éprouvé dans le
  dépôt (`WorkflowRetentionJob` et consorts) + le canal de notification déjà livré
  (`SendNotificationUseCaseImpl`, email/webhook/SSE) — aucune brique technique nouvelle, seulement du
  câblage entre deux mécanismes existants.
- Le seul vrai sujet est produit : seuils, fréquence, canal, destinataire, anti-spam (ne pas
  ré-alerter à chaque poll sur un dossier déjà signalé) — à trancher en Phase 1.

### 2.2 Dashboard — widgets supplémentaires (3.3)

- **Incrément**, pas une page à construire : `/rapports` existe déjà avec filtres période/type.
- Nouveaux agrégats envisagés (« files bloquées », « instances en erreur ») nécessitent une requête
  sur l'état des instances de process Flowable (`ACT_RU_EXECUTION`/`ACT_RU_JOB` côté moteur, ou
  équivalent applicatif) — à vérifier en Phase 1 si une telle lecture existe déjà ailleurs
  (ex. `WorkflowLinksList.vue`, PR #65) avant d'écrire une nouvelle requête.

### 2.3 Export BI personnalisable (5.3)

- Le CSV actuel est un **instantané** demandé par un humain — un usage BI veut une source
  **interrogeable en continu** (JDBC) ou une **livraison planifiée** (SFTP).
- Deux architectures possibles, non tranchées :
  1. Vue/schéma PostgreSQL en lecture seule dédié au reporting (pas de nouveau service, juste une
     exposition contrôlée d'un existant) ;
  2. Job planifié qui pousse un export (réutiliser `document-storage` — port déjà généralisé S3/FS,
     PR #99 — plutôt qu'un connecteur SFTP dédié à inventer).
- **Contrainte stack figée** : pas de Redis (rate-limit distribué/SSE multi-pod déjà exclus par
  [`FILE-ATTENTE.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md) § *Ne pas lancer*) — tout mécanisme de planification doit rester dans le mono-pod
  actuel (`@Scheduled` classique, comme les jobs existants), pas une queue distribuée.

---

## 3. Pourquoi un seul document, pas nécessairement un seul cycle de code

Contrairement à l'outillage DMN (§28) qui partage un **port technique unique**, les trois items ici
partagent surtout un **écran et un domaine** (`/rapports`, `SlaStatus`) mais peuvent se découper en
livraisons séparées sans conflit de fichiers majeur — l'alerting touche `infrastructure.flowable`/
`infrastructure.notification` (jobs), le dashboard touche `frontend/src/views/ReportsView.vue`/
nouveaux endpoints de lecture, le BI touche soit la persistence soit un nouveau connecteur
d'export. À confirmer en Phase 1 si un découpage en plusieurs PR est possible (contrairement à B1
connecteurs où le catalogue partagé imposait un seul cycle).

## 4. Points ouverts (Phase 1 `dev-lifecycle`)

| ID | Question |
|---|---|
| D-ALERT-CHANNEL | Canal d'alerte (email, webhook, les deux) et réutilisation telle quelle de `SendNotificationUseCase` ? |
| D-ALERT-THRESHOLD | Seuils fixes ou configurables par `DossierType` (comme `slaTargetHours` déjà configurable) ? |
| D-ALERT-DEDUP | Anti-spam : une alerte par franchissement de seuil, ou répétée tant que non résolu ? |
| D-DASHBOARD-WIDGETS-SCOPE | Quels agrégats précis au lancement (files bloquées, instances en erreur, autre) ? |
| D-BI-EXPORT-MECHANISM | Vue JDBC en lecture seule vs job planifié vers `document-storage` vs les deux ? |
| D-BI-EXPORT-SCOPE | Périmètre de données exposées (dossiers, SLA, les deux) et masquage des champs sensibles (aligné sur `export.csv` existant) ? |

Aucune décision de priorité n'est prise par ce document — il documente uniquement l'écart réel
constaté contre le code.
