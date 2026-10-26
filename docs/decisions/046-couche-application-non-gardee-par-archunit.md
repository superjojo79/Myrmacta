# §46 — Couche `application` non gardée par ArchUnit

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §46 · [registre](README.md)

> **Actualisation (2026-10-25).** Le paquet cité dans l'énoncé daté était `com.flowforge`.
> Il s'appelle `be.myrmacta` depuis la PR paquets. L'énoncé n'est pas réécrit.

**Énoncé :** `ManualStartOrchestrator` (`com.flowforge.application`) importe
`infrastructure.workflow.WorkflowStartConfirmer`. ArchUnit ne contraint que
`domain` (pas `application`). Contraint aux conventions
(`infrastructure → domain`, jamais l'inverse) au sens strict.

**Options :** (A) extraire `WorkflowStartConfirmer` derrière un port domaine ;
(B) règle ArchUnit `application` ↛ `infrastructure` + déplacer l'orchestrateur
en infra ; (C) documenter l'exception (orchestration légère assumée).

**Clos (2026-08-31) :** option **A** retenue, complétée par la garde
structurelle envisagée en B (sans déplacer l'orchestrateur) : nouveau port
`WorkflowStartConfirmationPort` (`domain.workflow.port.out`), implémenté par
`WorkflowStartConfirmer` (`infrastructure.workflow`, inchangé sinon).
`ManualStartOrchestrator` et le bean Spring `WorkflowConfig.manualStartOrchestrator`
dépendent désormais du port, plus de la classe infra. Nouvelle règle ArchUnit
`application_never_depends_on_infrastructure` (symétrique à la règle domaine
existante) ajoutée à `ArchitectureRulesTest` — `application` ne compte qu'une
seule classe (`ManualStartOrchestrator`), aucune autre violation trouvée.
`./mvnw verify` vert (ArchUnit + suite complète).

**Impacte :** `ManualStartOrchestrator`, `WorkflowStartConfirmer`,
`WorkflowStartConfirmationPort` (nouveau), `WorkflowConfig`,
`ArchitectureRulesTest`,
[01-architecture-hexagonale.md](../specs/01-architecture-hexagonale.md).

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §3.3 / §7.
