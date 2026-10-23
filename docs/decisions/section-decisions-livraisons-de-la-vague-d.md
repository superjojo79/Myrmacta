# Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)

> Section de l'ancien `TODO.md`, conservée pour son texte d'introduction · [registre](README.md)

Chaque lot suit `docs/decisions/roadmap-2026/CHARTE.md` : cycle de développement complet, revue adversariale indépendante (corrections avec tests de non-régression), décisions journalisées dans
[`docs/decisions/roadmap-2026/DECISIONS.md`](roadmap-2026/DECISIONS.md), avancement dans [`AVANCEMENT.md`](roadmap-2026/AVANCEMENT.md). Détail fonctionnel : `CHANGELOG.md`, docs SDD : `docs/ai/*/2026-10-01-feature-<lot>.md`.

| § | Lot | État | Décisions ⚠ à relire |
|---|---|---|---|
| 80 | D1 règles — champs calculés, visibilité et obligation conditionnelles | [x] livré | voir DECISIONS (D3b + D1 règles) |
| 81 | D2 — recherche sur champs dynamiques | [x] livré | — |
| 82 | D6 — socle MCP low-code (brouillons, provenance, modes, acteur) | [x] livré | — |
| 83 | D4 — promotion de configuration entre environnements | [x] livré | D-PROMO-CONNECTOR-DISABLED, D-PROMO-INTEGRITY |
| 84 | LC4b — éditeur visuel de templates | [x] livré | — |
| 85 | LC4a — concepteur visuel de formulaires | [x] livré | — |
| 86 | LC3 — assistant de conception IA | [x] livré | D-ASSIST-PROVIDER, D-ASSIST-COST, D-ASSIST-CONFIRM |
| 87 | D3c + D3d — e-mail entrant, bus Azure | [x] livré | D-INTEG-MAIL-PROTOCOL, -REPLAY, -SENDER, D-INTEG-AZURE, D-INTEG-POLL-ACK |
| 88 | LC5 — concepteur de parcours | [x] livré | D-PARCOURS-ROUNDTRIP, -SCOPE, -LIMITS |
| 89 | LC6 — scripts en bac à sable | [x] spike seul, lot non lancé | D-SCRIPT-GO |
| 90 | D7 — internationalisation (fr, en) | [x] première tranche livrée | D-I18N-SCOPE, D-I18N-ERRORS |

Hors périmètre déclaré (« Non traité », voir AVANCEMENT) : fournisseur de signature qualifiée (eIDAS), export BI par JDBC, vérifications d'environnement (antivirus sous Docker, Keycloak réel),
diffusion SSE multi-pods (Redis exclu), Microsoft Graph et Event Grid, étapes parallèles/signature/dossier enfant/escalade dans les parcours, libellés métier traduisibles.

## Points de cette section

- [§80 — D1 règles — champs calculés, visibilité et obligation conditionnelles](080-d1-regles-champs-calcules-visibilite-et-obligation.md) — clos
- [§81 — D2 — recherche sur champs dynamiques](081-d2-recherche-sur-champs-dynamiques.md) — clos
- [§82 — D6 — socle MCP low-code (brouillons, provenance, modes, acteur)](082-d6-socle-mcp-low-code-brouillons-provenance.md) — clos
- [§83 — D4 — promotion de configuration entre environnements](083-d4-promotion-de-configuration-entre-environnements.md) — clos
- [§84 — LC4b — éditeur visuel de templates](084-lc4b-editeur-visuel-de-templates.md) — clos
- [§85 — LC4a — concepteur visuel de formulaires](085-lc4a-concepteur-visuel-de-formulaires.md) — clos
- [§86 — LC3 — assistant de conception IA](086-lc3-assistant-de-conception-ia.md) — clos
- [§87 — D3c + D3d — e-mail entrant, bus Azure](087-d3c-d3d-e-mail-entrant-bus-azure.md) — clos
- [§88 — LC5 — concepteur de parcours](088-lc5-concepteur-de-parcours.md) — clos
- [§89 — LC6 — scripts en bac à sable](089-lc6-scripts-en-bac-a-sable.md) — clos
- [§90 — D7 — internationalisation (fr, en)](090-d7-internationalisation-fr-en.md) — clos
