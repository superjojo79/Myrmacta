# §21 — Politique de rétention par `DossierType`

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §21 · [registre](README.md)

**Implémenté (2026-08-19 — feature `retention-dossier-type`, PR #55) :**

| Sujet | Choix livré |
|---|---|
| Durée | Défaut **tenant obligatoire** (> 0 jours) + override **par type** (jours \| `NEVER` \| héritage) |
| À expiration | `ARCHIVE` + `purgeEligibleAt` + event **`purge_outbox`** (`DOSSIER_PURGE_REQUESTED`) ; **pas** de purge physique |
| Horloge | Depuis **`closedAt`** à l’entrée en **`CLOTURE`** ; non clos = pas d’expiration |
| UI | Config défaut tenant + champs sur admin `DossierType` |
| Bootstrap | Pas de seed ; GET 404 ; job no-op jusqu’au PUT |

**Suite livrée :** purge physique PG + object store → point **22** /
`purge-stockage` (PR #56).

**Impacte :** [04-dossiers.md](../specs/04-dossiers.md),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
