# §64 — Durcissement post-merge des en-têtes HTTP (revue adversariale PR #128)

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §64 · [registre](README.md)

**Énoncé :** revue adversariale du lot A4 ([PR #128](https://github.com/superjojo79/FlowForge/pull/128))
menée après merge. Cinq constats, tous traités dans ce point :

1. **Périmètre de confiance HSTS trop large** — `isLoopbackAddress() || isSiteLocalAddress()`
   couvre tout RFC1918, donc aussi les CIDR Docker bridge / Kubernetes pod par défaut, pas
   seulement « le reverse-proxy Apache local ». Remplacé par une allowlist de CIDR configurable
   (`app.security.headers.trusted-proxy-cidrs`, `TrustedProxyCidrMatcher`), défaut inchangé
   (loopback + RFC1918) pour ne pas modifier le comportement des déploiements existants — à
   restreindre à l'IP réelle du proxy avant toute migration hors du modèle Docker+Apache
   mono-host.
2. **Correctif A4-HSTS-001 limité au symptôme** — `server.forward-headers-strategy: framework`
   fait toujours confiance à tout `X-Forwarded-*` pour tous les usages (redirections OAuth2/OIDC,
   `{baseUrl}` du logout, etc.), pas seulement pour HSTS. Non retraité ici (changement plus large,
   impact sur l'authentification) — **reliquat noté**, à évaluer si le modèle de déploiement
   s'éloigne du reverse-proxy unique documenté.
3. **Kill switch sans garde-fou** — `APP_SECURITY_HEADERS_ENABLED=false` désactivait tout le
   paquet A4 sans contrôle. Ajout de `SecurityHeadersEnvironmentPostProcessor` (même doctrine
   D-43 que `SecretsEnvironmentPostProcessor`) : refuse de démarrer hors profils `local`/`test`.
4. **`style-src 'unsafe-inline'` non tracé comme risque accepté** — nécessaire pour PrimeVue ;
   risque résiduel d'exfiltration CSS pur si une injection HTML/attribut existe ailleurs dans
   l'app. Ajouté à la table des risques acceptés (L8).
5. **Pas de télémétrie CSP** — aucune visibilité sur les violations en prod. Ajout d'un endpoint
   anonyme et rate-limité `POST /api/security/csp-reports` (`CspReportController`, chaîne Security
   dédiée `@Order(2)`), directive `report-uri`/`report-to` + header `Reporting-Endpoints`,
   journalisation tronquée via `SecurityAuditLog.cspViolationReported` (jamais le corps brut).
6. **Duplication non vérifiée `CONTENT_SECURITY_POLICY` (Java) / `A4_CSP` (TypeScript,
   `frontend/e2e/pilotage.spec.ts`)** — un test Java lit désormais le fichier TS et échoue si les
   deux dérivent (voir `SecurityHeadersCustomizerTest`/vérification équivalente), plutôt qu'un
   simple commentaire de convention.

**Impacte :** `SecurityConfig.java`, `SecurityHeadersCustomizer.java`, `SecurityHeadersProperties.java`,
nouveaux `TrustedProxyCidrMatcher`, `SecurityHeadersEnvironmentPostProcessor`,
`CspReportController`/`CspReportRateLimiter`/`CspReportBodySizeLimitFilter`, `application.yml`,
`.env.example`, [docs/guides/GETTING-STARTED.md](../guides/GETTING-STARTED.md), [docs/guides/DEPLOIEMENT.md](../guides/DEPLOIEMENT.md).
