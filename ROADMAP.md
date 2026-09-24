# FlowForge — Roadmap Fonctionnelle

> **Document de référence** — Dernière vérification code : 23 septembre 2026 (`main` @ `b3f03f9`, PR #135)  
> **Statut** : statuts **recoupés sur le code et sur l’API GitHub**, pas sur l’analyse d’origine du 12/09  
> **Sources de vérité produit :** [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md), [TODO.md](TODO.md)  
> **Vagues A, B, C et A4 : livrées.** `enrichissement-reporting` (PR #132), `inbox-notifications-persistee` (PR #135) et `outillage-dmn` ([PR #134](https://github.com/superjojo79/FlowForge/pull/134)) : livrés.
> `portail-externe` ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), TODO §68) : livré. **Prochain :** `signature-electronique`.

---

## Contexte (vérifié)

FlowForge est un SaaS multi-tenant à architecture hexagonale. Stack **réelle** (pas celle de la v1.0 du 12/09) :

| Couche | Code actuel |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.x · Maven mono-module |
| Workflow | Flowable 8.0.x Community — BPMN 2.0 **et** DMN (TODO §61, PR #123) |
| Auth | OIDC BFF (Keycloak) — pas de JWT dans le browser |
| Frontend | **Vue 3 + TypeScript + Vite + PrimeVue + Pinia**, embarqué dans `static/` |
| MCP | **23 tools** sur `POST /api/m2m/mcp` (Streamable HTTP, JWT M2M `API_SERVICE`) |
| Connecteurs livrés | `http-rest`, `flowable-signal`, `scheduler`, `smtp`, `document-storage`, `flowable-callback`, `batch-import`, `kafka` |
| Antivirus | `AttachmentScanPort` + adaptateur ClamAV opt-in (PR #131) |
| Isolation | 1 instance Spring Boot = 1 tenant (`app.tenant.id`) |

---

## Synthèse — ce qu’il reste réellement à couvrir

| Priorité | Item | Lot | Statut |
|---|---|---|---|
| Ensuite | **2.4 Signature électronique** | `signature-electronique` | Non démarré, **après le portail** — [30-signature-electronique.md](analysis%20doc/30-signature-electronique.md) |
| Non planifié | **5.3 bis** Export BI JDBC / Power BI | — | Écarté du lot reporting (D-BI-MECHANISM) ; nouveau produit si besoin confirmé |

### Déjà couvert par le code — ne plus traiter comme un gap

| Item v1.0 | Preuve |
|---|---|
| **2.1 Import batch (SFTP / CSV / Excel)** | Connecteur `batch-import` (lot B1, PR #129). « ERP » = cas d’usage de `batch-import`. |
| **2.2 Kafka** | Connecteur `kafka` (lot B1, PR #129). |
| **2.3 Antivirus** | `ClamAvAttachmentScanAdapter` opt-in (lot C, PR #131). |
| **3.2 Inbox notifications persistée** | `user_notification`, `NotificationInboxController`, badge serveur (PR #135, TODO §66). |
| **3.3 Dashboard opérationnel** | `/rapports` + widgets dead-letter et tâches sans assigné (PR #132). |
| **4.1 Éditeur DMN tableur** | `/admin/dmn-editor` + `dmn-js` Modeler (TODO §61). |
| **4.2 Visualiseur DRD** | Sous-vue DRD du même Modeler. |
| **5.2 Alerting SLA** | Alertes EMAIL/WEBHOOK/BROWSER par palier (PR #132, TODO §65). |
| **5.3 Export BI** | Export CSV planifié vers `DocumentStoragePort` (PR #132). JDBC/Power BI non couverts. |
| **Tests PostgreSQL ciblés** | Testcontainers : isolation schéma, GRANT par rôle, séquence multi-pods (PR #66). |
| **Provisioning tenant** | `Dockerfile` + `scripts/provision-tenant.sh` (lot A1, PR #119). |
| **Headers HTTP** | CSP/HSTS/XFO (lot A4, PR #128/#130). |

### Hors scope actif (ne pas couvrir tant que V1 n’est pas rouvert)

| Item v1.0 | Motif |
|---|---|
| **6.1 SSE multi-pod / Redis** | `FILE-ATTENTE.md` : *Ne pas lancer* (pas de Redis dans la stack figée). |
| **1.4 Tools MCP composites** | Doctrine MCP : tools atomiques qui réutilisent les use cases REST. |
| Validation FEEL temps réel | Flowable 8 évalue le DMN en **JUEL**, pas FEEL. Allowlist au deploy. |
| Simulation DMN depuis l’éditeur | Non-goal de `introduction-dmn`. |
| Chart.js SLA, Cockpit Flowable, codegen OpenAPI, OTel/Grafana | Décisions V1 déjà tranchées. |

---

## Détail par catégorie

### Catégorie 1 : MCP & IA

| Item | Statut |
|---|---|
| 1.1 `evaluate_decision` | ✅ Livré (`outillage-dmn`, PR #134, TODO §67) |
| 1.2 `TestDecisionTool` | ✅ Livré (`test_decision`, JSON et CSV) — même PR |
| 1.3 Historique / rollback DMN | ✅ Livré — même PR (versioning Flowable, « Redéployer la version N ») |
| 1.4 Tools composites | ⛔ Ne pas couvrir (doctrine MCP atomique) |

### Catégorie 2 : Connecteurs & Intégrations

| Item | Statut |
|---|---|
| 2.1 Import batch | ✅ Livré (`batch-import`, PR #129) |
| 2.2 Kafka | ✅ Livré (`kafka`, PR #129) |
| 2.3 Antivirus ClamAV | ✅ Livré (PR #131) — `ClamAvAttachmentScanIT` à jouer avec Docker |
| 2.4 Signature électronique | ❌ Non démarré — lot `signature-electronique`, **après le portail** |
| 2.5 ERP | ✅ Couvert par `batch-import` (décision Phase 1 de B1) |

### Catégorie 3 : Portail externe & UX

| Item | Statut |
|---|---|
| 3.1 Portail externe tokenisé | ✅ Livré (PR #138, TODO §68) — opt-in `APP_PORTAL_ENABLED` |
| 3.2 Inbox notifications | ✅ Livré (PR #135) |
| 3.3 Dashboard opérationnel | ✅ Livré (PR #80 + widgets PR #132) |

### Catégorie 4 : Édition & visualisation DMN

| Item | Statut |
|---|---|
| 4.1 Éditeur de tables | ✅ Livré (TODO §61) |
| 4.2 Visualiseur DRD | ✅ Livré |

### Catégorie 5 : Sécurité, conformité & observabilité

| Item | Statut |
|---|---|
| 5.1 Versioning & rollback DMN (UI) | ✅ Livré (PR #134) |
| 5.2 Alerting SLA | ✅ Livré (PR #132) |
| 5.3 Export BI | ⚠️ CSV planifié livré (PR #132) ; JDBC / Power BI non planifié |

### Catégorie 6 : Performance & qualité interne

| Item | Statut |
|---|---|
| 6.1 SSE multi-pod | ⛔ Hors V1 (pas de Redis) |
| 6.2 Tests PostgreSQL réels | ⚠️ Partiel volontaire (D-TESTCONTAINERS, adoption ciblée) |

---

## Priorisation

1. `signature-electronique` — démarrable (portail livré, PR #138) ; fournisseur et niveau eIDAS à trancher en Phase 1.
2. Vérifications d’environnement hors file : `ClamAvAttachmentScanIT` sous Docker, smoke Keycloak réel.

---

## Historique des versions

| Version | Date | Auteur | Changements |
|---|---|---|---|
| 1.0 | 12/09/2026 | Assistant IA (analyse code) | Création initiale — **plusieurs statuts et la stack étaient faux** (DMN editor, MCP 25+, React, FEEL, PG tests, provisioning). |
| 1.1 | 14/09/2026 | Vérification `main` + Vague B | Statuts recoupés sur le code ; Vague B marquée en cours ; liste réelle de ce qu’il reste à couvrir. |
| 1.2 | 23/09/2026 | Rattrapage post-merges #131/#132/#135 | Vagues A/B/C/A4, reporting et inbox passés en livré ; catalogue connecteurs à 8 ; portail et signature scindés en deux lots ; DMN et portail en cours. |
