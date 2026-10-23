# §9 — Duplication `usecase/` et `port/in/` dans le domaine dossier

> **Statut :** clos · **Section :** [Décisions actées › Architecture](section-decisions-architecture.md) · ancien `TODO.md` §9 · [registre](README.md)

**Problème :** La structure de packages de `04-dossiers.md` listait les mêmes noms dans `domain/dossier/usecase/` et dans `domain/dossier/port/in/`, incohérent avec `01-architecture-hexagonale.md`.

**Décision retenue :** Convention uniformisée : `port/in/` contient uniquement les interfaces, `usecase/` contient les implémentations suffixées `Impl` (ex. `CreateDossierUseCaseImpl` implémente `CreateDossierUseCase`). Appliqué à tous les modules du domaine (`workflow`, `document`, `connector`, `notification`, `dossier`) et vérifié en revue de code.

**Documenté dans :** [04-dossiers.md](../specs/04-dossiers.md).
