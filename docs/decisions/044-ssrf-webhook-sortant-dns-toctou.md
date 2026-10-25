# §44 — SSRF webhook sortant — DNS TOCTOU

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §44 · [registre](README.md)

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-44 = **P**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — pin IP
(`resolveSafe` un lookup, POST URI IP + `Host` / TLS hostname d'origine,
redirects NEVER). Pas de proxy egress.

**Durci (revue post-merge, 2026-08-26) :** le finding « Moyenne » de la
Phase 9 était réel — la JDK `HttpURLConnection` droppe silencieusement le
header `Host` restreint et envoie l'IP pinnée à la place (observé : test
`WebhookRestAdapterUrlConnectionTest` rouge en suite complète, vert en
isolation — la course entre forks Surefire réutilisés déterminait quelle
classe chargeait `HttpURLConnection` en premier). Corrigé par
`sun.net.http.allowRestrictedHeaders=true`, posé au plus tôt à trois
endroits complémentaires (`MyrmactaApplication.main`, static init
`WebhookRestAdapter`, `systemPropertyVariables` Surefire pour les forks de
test) — le pin IP / TOCTOU (D-44 lui-même) n'était pas affecté, seul le
header `Host` du serveur cible l'était. Détail :
[`docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md)
§ Findings.

**Énoncé :** `OutboundUrlGuard` bloque privé/metadata après résolution DNS,
mais le client HTTP n'est pas pinté sur l'IP vérifiée (javadoc explicite).

**Options :** pin IP sur le `HttpClient` ; proxy egress allowlist.

**À produire :** décision + tests de rebinding.

**Impacte :** `OutboundUrlGuard`, `WebhookRestAdapter`,
[09-notifications.md](../specs/09-notifications.md).

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §4 S-5.
