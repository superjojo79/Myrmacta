# §45 — Dérive documentaire analyse / README / code

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §45 · [registre](README.md)

**Clos** (2026-08-25, `derive-documentaire`, [PR #75](https://github.com/superjojo79/FlowForge/pull/75)) — chore docs, code = source de vérité. `com.monapp` dans `01` déjà clos PR #72. Ne pas élargir le V1 (DMN, connecteurs SERVICE).

**Énoncé (historique) :** le code suit les décisions 13–15, 17, 27 ; plusieurs docs
décrivaient encore l'ancien modèle ou un périmètre non livré.

Corrigé (liste revue §3.3) :

- `README.md` : tenant instance (`app.tenant.id`) + garde OIDC `tenant_id` ; DMN hors V1 ; catalogue connecteurs V1 vs cible ; stockage port S3 ; jsoup 1.23.1 ; `context-path` cible (yaml commenté)
- `docs/specs/12` : `WORKFLOW_ADMIN` → `/admin/bpmn-editor` (plus « accès au cockpit Flowable »)
- `docs/specs/06` : colonnes Cible / V1 livré
- `docs/specs/00` / `07` : jsoup 1.23.1 ; stockage
- `docs/specs/11` : `context-path` = cible ops
- `application.yml` : commentaire d'en-tête Flowable V1 (plus « until real adapters land »)

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §3.3.
