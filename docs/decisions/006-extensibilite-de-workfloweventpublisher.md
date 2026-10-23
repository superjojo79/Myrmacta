# §6 — Extensibilité de `WorkflowEventPublisher`

> **Statut :** clos · **Section :** [Décisions actées › Architecture](section-decisions-architecture.md) · ancien `TODO.md` §6 · [registre](README.md)

**Problème :** L'interface déclarait une méthode par type d'événement — chaque nouvel état du workflow imposait une modification de l'interface et de tous ses adaptateurs.

**Décision retenue :** Signature générique `void publish(WorkflowEvent event)` avec `WorkflowEvent` en interface scellée (`sealed interface`, Java 21). Un nouvel événement = un nouveau type implémentant l'interface scellée, sans modification de `WorkflowEventPublisher`. Les adaptateurs qui routent par type utilisent un `switch` exhaustif sur les types permis, ce qui conserve la vérification à la compilation malgré la signature générique.

**Documenté dans :** [05-workflow-engine.md](../specs/05-workflow-engine.md).
