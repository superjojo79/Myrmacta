# §20 — Contrôle d'accès au niveau type de dossier — `AccessContext` + `dossier_type_acl` + policy

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §20 · [registre](README.md)

**Problème :** le contrôle de rôle ne protège pas contre l'accès aux dossiers d'un type hors périmètre de l'utilisateur. L'esquisse `DossierAccessPort` ne distinguait pas appelant humain et système. Une première formulation de B2 posait une ACL **par instance** de dossier — amendée au profit d'une ACL **par `DossierType`**.

**Décision retenue — A2 + B2 (amendée) + C1 :**

1. **A2 — `AccessContext`.** Inchangé : `User` (ACL) · `System` (contournement) · `Service` (refus dossier). `DossierAwareTask` / `ConnectorTriggerRuntime` passent `AccessContext.system(...)`.

2. **B2 — table `dossier_type_acl`.** Source de vérité pour `DOSSIER_MANAGER` / `DOSSIER_VIEWER` : `(tenant_id, dossier_type_code, subject_user_id, permission)` avec `permission ∈ {VIEW, EDIT}` (`EDIT` ⇒ `VIEW`). Un grant sur le type s'applique à **toutes** les instances. Administration via `GrantDossierTypeAccessUseCase` / `RevokeDossierTypeAccessUseCase` (admins tenant). Création de dossier (humain) exige `EDIT` sur le type ; liste/recherche filtrée aux types `VIEW`. Pas de grant par instance, ni à la création, ni à l'assignation Flowable. `TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent. Table à intégrer au point 25. Couvre en pratique la restriction « rôle × sous-ensemble de `DossierType` » du point 19.

3. **C1 — `DossierAccessPolicy`.** Service domaine ; `DossierTypeAclRepository` ; évaluation sur `dossierTypeCode` (directement ou via le dossier chargé). Refus → `AccessDeniedException` → 403 (point 24).

**Écartées / abandonnées :** ACL par instance de dossier (première B2) ; A1, A3, B1, B3, C2.

**Documenté dans :** [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [12-autorisation-conformite.md](../specs/12-autorisation-conformite.md), [04-dossiers.md](../specs/04-dossiers.md) et [06-connecteurs.md](../specs/06-connecteurs.md).
