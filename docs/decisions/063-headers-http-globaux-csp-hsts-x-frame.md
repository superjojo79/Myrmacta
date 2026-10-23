# §63 — Headers HTTP globaux — CSP / HSTS / X-Frame-Options (lot A4)

> **Statut :** clos · **Section :** [Points ouverts › Security](section-points-security.md) · ancien `TODO.md` §63 · [registre](README.md)

> Numérotation : B1 a clos **TODO §62** sur `main` (PR #129) en parallèle
> de ce lot, qui s’appelait §62 sur sa branche. Le point A4 est donc **§63**.

**Énoncé :** reliquat **S-9** ([docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md)) —
pas de CSP / HSTS / `X-Frame-Options` applicatifs. Lot **A4**
([FILE-ATTENTE.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md)) : surface exclusive `SecurityConfig.java`.

**Statut (2026-09-20) : clos.** Phases 1–9 closes. Remédiation A4-HSTS-001
incluse. **Mergée sur `main`**
([PR #128](https://github.com/superjojo79/FlowForge/pull/128), CI `build` +
`frontend` verte, merge `8415cee`).

**Décisions Phase 1 :** D-HEADERS-SCOPE = B ; D-HEADERS-CHAINS = B ;
D-CSP-POLICY = B ; D-HSTS = B ; D-CONFIG = C ; D-LOCAL-HEADERS = A.
**Décisions Phase 3 :** D-MECHANISM = B (`SecurityHeadersCustomizer`) ;
D-CSP-STRING = A (dont `frame-src blob:` pour l’iframe PDF) ;
D-SWITCH-OFF = A (kill switch = no-op, défauts Spring conservés).

**Impacte :** `SecurityConfig.java`, tests sécurité, `.env.example`,
[docs/guides/GETTING-STARTED.md](../guides/GETTING-STARTED.md), [docs/guides/DEPLOIEMENT.md](../guides/DEPLOIEMENT.md).
Pas Compose, pas CSRF (S-8), pas `docs/guides/GUIDE-UTILISATEUR.md`.

**Détail :** `docs/ai/{requirements,design,planning,testing,implementation}/2026-09-20-feature-headers-http-globaux.md`.

**Note (revue adversariale post-merge, TODO §64) :** l'exploitation navigateur réelle du bug
HSTS initial (A4-HSTS-001) était limitée — RFC 6797 impose aux UA conformes d'ignorer un header
`Strict-Transport-Security` reçu sur une connexion non chiffrée. La valeur du correctif est
surtout la conformité aux scanners / la défense en profondeur, pas la fermeture d'une brèche
directement exploitable contre un navigateur standard. Durcissements complémentaires en §64.
