# §40 — SSTI Thymeleaf / SpEL dans les templates stockés

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §40 · [registre](README.md)

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-40 = **B** / B2,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — évaluateur allowlist
(`#vars` / `#dates` / `#numbers` / `#strings` / `#lists` + chemins modèle), pas
l'option A « sans SpEL » au sens moteur nu. `T()` / `#beans` / `new` / `getClass`
→ 400 generate/preview **et** activation (le POST draft reste 201).

**Durci (revue post-merge, 2026-08-26) :** le finding « Basse » de la Phase 9
(`readProperty` via `setAccessible(true)` + méthode "bare name" arbitraire,
y compris privée) était sans pont vers `Runtime` mais dépassait le contrat
« accès lecture seule aux propriétés » voulu. `readProperty` n'accepte
désormais que les getters JavaBean publics et les accesseurs canoniques de
`record` (via `Class#getRecordComponents()`) — plus de `setAccessible`, plus
de méthode privée/non-conventionnelle atteignable. Détail :
[`docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md)
§ Findings.

**Énoncé :** `ThymeleafHtmlTemplateProcessor` utilise `SpringTemplateEngine`
(SpringEL). Un `TENANT_ADMIN` peut faire exécuter `${T(java.lang.Runtime)…}`
sur la JVM via generate / live preview. L'iframe SPA `sandbox=""` ne couvre
que le XSS navigateur. Le sandboxing Thymeleaf avancé a été reporté dès
`templates-documents`.

**Options :** (A) `StandardTemplateEngine` sans SpEL — recommandée ;
(B) allowlist SpEL ; (C) assumer `TENANT_ADMIN` = opérateur de confiance
(incompatible SaaS si le point 41 reste ouvert).

**À produire :** décision A/B/C puis tests d'exploit qui doivent échouer
(`${T(java.lang.Runtime)}` → 400 / non évalué).

**Impacte :** `ThymeleafHtmlTemplateProcessor`,
[08-templates-documents.md](../specs/08-templates-documents.md).

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §4 S-1.
