# Reporting & SLA

> **Statut : livré** — feature `reporting-sla`
> ([PR #80](https://github.com/superjojo79/FlowForge/pull/80), 2026-08-26).
> Ce document reste l'analyse de cadrage d'origine ; le contrat acté est
> dans `docs/ai/**/2026-08-26-feature-reporting-sla.md`.
>
> **Séquencement produit :** prérequis du point 4 (stats) du
> [serveur MCP](15-serveur-mcp.md#dépendance-reporting--sla-point-4) —
> **livré**, ce qui autorise `query_sla_summary` en Phase 1 MCP.
>
> **Décision de cadrage (2026-08-26) :** les **deux lots** (Reporting **et**
> SLA) sont requis pour débloquer le MCP à coup sûr — voir
> [Proposition de découpage en deux lots](#proposition-de-découpage-en-deux-lots).
> Question ouverte n° 1 tranchée en ce sens.

---

## Contexte

Le produit a déjà tranché, à plusieurs reprises et indépendamment, que
« Rapports / SLA » est hors périmètre V1 :

- `DEFERRED-FEATURES.md` §1.6 (`frontend`) : « Écrans Rapports / SLA —
  Hors scope V1 (mock UI de référence uniquement) ».
- `docs/ai/requirements/2026-08-20-feature-observabilite.md` (non-goals) :
  « Rapports / SLA (FILE-ATTENTE hors V1) », distinct de l'observabilité
  technique (Prometheus/MDC) qui couvre la santé de la **plateforme**, pas
  les indicateurs **métier** sur les dossiers/workflows.
- `frontend/src/views/pilotage/SupervisionView.vue` teste explicitement
  l'**absence** de KPI/SLA sur l'écran de supervision actuel
  (`SupervisionView.spec.ts` : `not.toContain('KPI')`, `not.toContain('SLA')`).

Il n'existe donc aucune donnée dérivée, aucun contrat d'API et aucun
modèle de métriques pour ce périmètre — tout est à définir. Les données
**sources**, elles, existent déjà et n'ont pas besoin d'être créées :
`Dossier` (statut, `createdAt`, `closedAt`, `dossierTypeCode`) et
`DossierWorkflowLink` (`startedAt`, `endedAt`, `processDefinitionKey`,
`status`) — voir [04-dossiers.md](04-dossiers.md).

---

## Proposition de découpage en deux lots

Sur le modèle déjà appliqué par le produit (ex. rétention livrée avant
purge, décisions n° 21 puis 22) : séparer un lot minimal exploitable d'un
lot plus structurant, plutôt qu'une feature monolithique.

| Lot | Contenu | Nécessaire pour débloquer le MCP (point 4) ? |
|---|---|---|
| **Lot 1 — Reporting** | Comptages et durées, calculés à la volée, sans notion de seuil | **Oui** — c'est le socle minimal que `query_dossiers` (MCP) consommerait |
| **Lot 2 — SLA** | Seuils configurables par type de dossier + statut de conformité (respecté/à risque/dépassé) | **Oui** — tranché (2026-08-26) : le MCP doit pouvoir exposer un statut de conformité (`get_workflow_stats` ou équivalent), pas seulement des comptages bruts. Les deux lots sont donc requis avant de rouvrir le point 4 côté MCP. |

**Décision :** les deux lots sont dans le périmètre à livrer avant le MCP —
pas de version dégradée « Lot 1 seul » pour débloquer le MCP. Ils restent
néanmoins deux livraisons séquencées (Lot 1 puis Lot 2), sur le modèle
rétention → purge, pour limiter la taille de chaque itération ; c'est
l'ensemble des deux qui conditionne la levée de la dépendance MCP, pas
chacun indépendamment.

---

## Lot 1 — Reporting (comptages et durées)

### Métriques proposées

- **Volumétrie** : nombre de dossiers par `dossierTypeCode` × `status`,
  sur une période (`createdAt` dans `[from, to]`).
- **Durée de traitement d'un dossier** : `closedAt - createdAt` — moyenne,
  médiane, p90, par `dossierTypeCode`. C'est la mesure de bout en bout,
  indépendante du nombre de `DossierWorkflowLink` successifs sur le
  dossier (cohérent avec le modèle « plusieurs workflows, un seul
  dossier » de [04-dossiers.md](04-dossiers.md)).
- **Durée par exécution de workflow** : `endedAt - startedAt` sur
  `DossierWorkflowLink`, par `processDefinitionKey` — mesure plus fine,
  utile pour diagnostiquer *quel* processus est lent plutôt que le
  dossier dans son ensemble.
- **Répartition des dossiers actifs** : nombre par statut, à l'instant T
  (vue « en cours », sans filtre de période).

### Pas de nouvelle table de faits

Cohérent avec le non-goal déjà acté dans `observabilite` (« pas
d'historique / time-series persisté ») : les agrégats sont calculés à la
demande par requête sur `dossier` et `dossier_workflow_link`, pas
matérialisés dans une table dédiée. Nécessitera des index dédiés — voir
[Risques](#risques-et-points-de-vigilance).

### API

```
GET /api/reporting/dossiers/summary
    ?dossierTypeCode=&from=&to=&groupBy=status|dossierTypeCode|period

GET /api/reporting/workflow-links/summary
    ?processDefinitionKey=&from=&to=
```

### UI

Écran `/admin/reporting` (ou `/pilotage/reporting`) : quelques cartes KPI
(dossiers ouverts, clos sur la période, durée moyenne) + un tableau
filtrable. **Pas de graphes** (cohérent avec le non-goal déjà acté dans
`observabilite` : « Chart.js, histogramme UI... » écarté). Cet écran
remplace la maquette de référence par une première implémentation réelle,
volontairement sobre.

---

## Lot 2 — SLA (seuils et statut de conformité)

### Extension du modèle — même patron que la rétention (décision n° 21)

`DossierType` porterait un seuil optionnel, sur le même schéma que la
politique de rétention déjà livrée (défaut tenant obligatoire + override
par type) :

```java
// domain/dossier/model — extension de DossierType (décision n° 21 comme précédent direct)
Integer slaTargetHours,       // nullable — override par type ; NEVER = pas de SLA pour ce type
                               // null = hérite du défaut tenant (obligatoire, > 0)
```

### Calcul du statut SLA — à la volée, pas stocké

| État du dossier | Calcul |
|---|---|
| Ouvert (non clos) | `now - createdAt` vs seuil → `ON_TRACK` / `AT_RISK` (ex. 80 % du délai) / `BREACHED` |
| Clos | `closedAt - createdAt` vs seuil → indicateur figé (respecté / dépassé) |

### API/UI

Ajout d'une colonne « Statut SLA » et d'un filtre « en dépassement » sur
l'écran reporting du Lot 1. **Pas d'alerte proactive** (notification de
dépassement) en V1 — un tel mécanisme toucherait
[09-notifications.md](09-notifications.md) et mérite sa propre analyse une
fois le Lot 2 stabilisé, plutôt que d'être improvisé ici.

---

## Hors périmètre (les deux lots)

- **Notifications de dépassement SLA** — hors scope, à recadrer séparément
  si le besoin se confirme après un premier usage du Lot 2.
- **Export CSV/Excel** — cohérent avec la sobriété du reste du produit ;
  peut suivre dans une itération ultérieure.
- **Historisation / séries temporelles persistées** — non-goal déjà acté
  dans `observabilite`, repris ici à l'identique.
- **SLA contractuel avec pénalités** — indicateur opérationnel interne
  uniquement, pas une notion contractuelle/légale.
- **Dashboards temps réel / SSE** — cohérent avec le non-goal `observabilite`
  (pas de poll automatique, bouton Rafraîchir manuel si besoin).
- **Reporting cross-tenant** — une instance Spring Boot = un tenant
  (décision n° 13), comme partout ailleurs dans le produit.

---

## Architecture envisagée

Nouveau module `domain.reporting`, en lecture seule — pas d'entité
métier propre, uniquement des use cases d'agrégation :

```
domain
└── reporting
    ├── port
    │   ├── in
    │   │   ├── GetDossierReportingSummaryUseCase
    │   │   └── GetWorkflowLinkReportingSummaryUseCase
    │   └── out
    │       └── ReportingQueryPort   # agrégations SQL — pas d'entité domaine à mapper
    └── usecase
        ├── GetDossierReportingSummaryUseCaseImpl
        └── GetWorkflowLinkReportingSummaryUseCaseImpl

infrastructure
├── web.reporting.ReportingController
└── persistence.reporting.ReportingQueryAdapter   # JPQL/SQL natif d'agrégation
```

`ReportingQueryPort` est un port de sortie dédié aux agrégations —
distinct des repositories `DossierRepository`/`DossierWorkflowLinkRepository`
existants, qui restent centrés sur le CRUD d'entités individuelles.

### RBAC

Aligné sur `observabilite` (écran réservé à `TENANT_ADMIN`) en première
approche, avec `WORKFLOW_ADMIN` en plus vu que les métriques couvrent les
workflows — **à confirmer en design**. Un scoping `DOSSIER_MANAGER` par
ACL de type (décision n° 20) serait plus cohérent avec le reste du RBAC
mais complexifie l'agrégation (filtrage des types accessibles avant
comptage) — voir [Questions ouvertes](#questions-ouvertes).

---

## Risques et points de vigilance

- **Coût des agrégats à la volée.** Pas d'index aujourd'hui sur
  `dossier.created_at`/`closed_at` ni sur
  `dossier_workflow_link.started_at`/`ended_at` pour ce type de requête —
  prévoir des index dédiés (migration Liquibase) avant d'exposer l'API.
- **Confusion de vocabulaire.** Un futur « statut SLA » (conformité délai)
  ne doit pas être confondu avec `Dossier.status` (métier, piloté par
  BPMN) ni `DossierWorkflowLink.status` (statut d'exécution) — les trois
  axes sont indépendants, comme déjà actée l'indépendance des deux
  premiers dans [04-dossiers.md](04-dossiers.md#cycle-de-vie-du-statut).
- **Fuite d'information par agrégat.** Même sans accès au détail d'un
  dossier, un agrégat « nombre de dossiers en dépassement SLA » sur un
  type auquel l'appelant n'a pas accès peut être une fuite — à examiner
  avec `DossierAccessPolicy` (décision n° 20) avant d'exposer l'API à
  d'autres rôles que `TENANT_ADMIN`.

---

## Questions ouvertes

1. ~~Le Lot 1 seul suffit-il à débloquer le point 4 du MCP, ou le MCP a
   besoin du statut SLA (Lot 2) dès sa première itération de lecture ?~~
   **Tranché (2026-08-26) : les deux lots sont requis** — voir
   [Proposition de découpage en deux lots](#proposition-de-découpage-en-deux-lots).
2. **Seuil d'alerte `AT_RISK`** — pourcentage fixe (ex. 80 %) ou
   paramétrable par tenant ?
3. **RBAC exact** — `TENANT_ADMIN` seul, `+ WORKFLOW_ADMIN`, ou scoping
   `DOSSIER_MANAGER` par ACL de type (plus cohérent mais plus complexe à
   agréger) ?
4. **Granularité de `groupBy`** — période jour/semaine/mois nécessaire dès
   le Lot 1, ou uniquement par type/statut pour commencer ?
5. **`slaTargetHours` obligatoire ou optionnel par défaut** — même
   contrainte que la rétention (défaut tenant obligatoire, décision n° 21)
   ou SLA désactivé tant qu'aucun seuil n'est configuré ?

---

## Prochaines étapes

Une fois ce cadrage validé, cette feature suit le cycle `dev-lifecycle`
complet (requirements → design → planning → implementation → testing →
review) comme les autres features du produit, et doit être positionnée
dans `FILE-ATTENTE.md` **avant** `serveur-mcp`, conformément à la
dépendance actée côté MCP
([15-serveur-mcp.md](15-serveur-mcp.md#dépendance-reporting--sla-point-4)).
