# Points ouverts › Security

> Section de l'ancien `TODO.md`, conservée pour son texte d'introduction · [registre](README.md)

Revue adversariale du dépôt complet (`main` @ `8080abf`, 2026-09-07), après clôture des
points **40–46**. Périmètre : BFF OIDC, M2M/MCP, webhooks/callbacks, connecteurs, BPMN/Flowable,
templates, stockage. Méthode : checklist skill `security-review` (secrets, injection, authz,
logique métier, exposition, DoS, crypto, config). Les IDs `H*` / `M*` / `L*` sont stables.

Les findings S-1…S-5 (2026-08-25) restent **clos**. Reliquats S-6…S-10 (AV, rate-limit JVM,
CSRF cookie, headers HTTP, `allowUnknownFields`) non rouverts — voir
[DEFERRED-FEATURES.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §4.

Le cycle `dev-lifecycle` `revue-adversariale-securite` (TODO §57) **a livré** les High et
Medium (Q1–Q12 = A, stakeholder 2026-09-07). Les Low L1–L7 restent hors correctif (Q12=A).

**Statut :** **clos** — mergé sur `main`
([PR #113](https://github.com/superjojo79/FlowForge/pull/113), 2026-09-07).
CI GitHub verte sur `e96121a`. HMAC : partenaires à prévenir (Q5). Une contre-revue adversariale
post-merge (même jour) a trouvé un H1 incomplet et deux Medium supplémentaires — voir **§58**.

## Points de cette section

- [§57 — Revue adversariale 2026-09-07 — durcissement High / Medium](057-revue-adversariale-2026-09-07-durcissement-high.md) — clos
- [§58 — Contre-revue adversariale post-merge PR #113 — H1 incomplet + 2 Medium](058-contre-revue-adversariale-post-merge-pr-113.md) — clos
- [§59 — Formulaire admin connecteurs généré depuis `configSchema` (lot A2)](059-formulaire-admin-connecteurs-genere-depuis-configschema-lot.md) — clos
- [§60 — Écritures métier MCP — `create_dossier` / `add_dossier_element` / `start_workflow` / `generate_document`](060-ecritures-metier-mcp-create-dossier-add-dossier.md) — clos
- [§61 — Introduction de DMN — lever l'exclusion V1](061-introduction-de-dmn-lever-l-exclusion-v1.md) — clos
- [§63 — Headers HTTP globaux — CSP / HSTS / X-Frame-Options (lot A4)](063-headers-http-globaux-csp-hsts-x-frame.md) — clos
- [§64 — Durcissement post-merge des en-têtes HTTP (revue adversariale PR #128)](064-durcissement-post-merge-des-en-tetes-http.md) — clos
- [§65 — Enrichissement du reporting — alerting SLA, widgets, export BI](065-enrichissement-du-reporting-alerting-sla-widgets-export.md) — clos
- [§66 — Inbox de notifications persistée — historique utilisateur](066-inbox-de-notifications-persistee-historique-utilisateur.md) — clos
- [§67 — Outillage DMN — évaluation ad hoc, jeux de tests, historique/rollback](067-outillage-dmn-evaluation-ad-hoc-jeux-de.md) — clos
- [§68 — Portail externe tokenisé — tâche externe par lien magique](068-portail-externe-tokenise-tache-externe-par-lien.md) — clos
- [§69 — Surfaces UEL du BPMN hors allowlist — suite de H1-bis (High)](069-surfaces-uel-du-bpmn-hors-allowlist-suite.md) — clos
- [§70 — Positionnement produit (D0) — intégrateurs + couche low-code](070-positionnement-produit-d0-integrateurs-couche-low-code.md) — clos
- [§71 — Exposition MCP de la couche low-code — cadrage transverse](071-exposition-mcp-de-la-couche-low-code.md) — ouvert
- [§72 — Signature électronique — socle générique et stub (livré)](072-signature-electronique-socle-generique-et-stub.md) — clos
