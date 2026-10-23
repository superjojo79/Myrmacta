# §78 — Mapping d'une réponse de connecteur → champs du dossier (D3b) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §78 · [registre](README.md)

**Énoncé :** sous-lot **D3b**, analyse [34-integrations-entreprise.md](../specs/34-integrations-entreprise.md), sur le moteur d'expressions de LC2 (§75). Docs SDD :
[requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-01-feature-d3b-mapping-reponse-connecteur.md), [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-01-feature-d3b-mapping-reponse-connecteur.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-01-feature-d3b-mapping-reponse-connecteur.md), [testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-10-01-feature-d3b-mapping-reponse-connecteur.md).

**Décisions.** D-INTEG-MAPPING-TARGET = champs du dossier seulement ; D-MAP-FORMAT = lignes `champ = expression` ; D-MAP-UNDEFINED = échec explicite ; D-MAP-ATOMIC = un patch, tout ou rien ;
D-MAP-BODY-JSON = `connectorResult.json` fourni par le contexte (nombres exacts en texte au-delà de 15 chiffres) ; D-MAP-SENSITIVE = cible oui (auditée), source non ; D-MAP-TARGETS = cibles
déclarées seulement ; D-MAP-RESERVED = `connectorResult` variable réservée.

**Livré.** Delegate `fieldMappingTask` (allowlist étendue d'un seul bean), brique `map-fields` (catalogue, 13 briques), `FieldMappingSpec` (format), validation au déploiement (chaque expression), `BusinessExpressionContext`
partagé (`json`), catalogue : drapeau `multiline` → zone de texte dans l'éditeur. Aucune migration. **Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3septies, §7), `docs/specs/34`, `ROADMAP.md`, `CHANGELOG.md`.
**Revue adversariale** : voir le document d'implémentation.
