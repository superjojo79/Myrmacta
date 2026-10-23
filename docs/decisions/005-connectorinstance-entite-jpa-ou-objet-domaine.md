# §5 — `ConnectorInstance` — entité JPA ou objet domaine ?

> **Statut :** clos · **Section :** [Décisions actées › Architecture](section-decisions-architecture.md) · ancien `TODO.md` §5 · [registre](README.md)

**Problème :** L'entité JPA `ConnectorInstance` était passée directement aux ports `TriggerConnector` et `ServiceConnector`, couplant le domaine à l'infrastructure.

**Décision retenue :** Introduction de `ConnectorInstanceData`, objet domaine pur (config déjà déchiffrée) consommé par les ports `TriggerConnector`/`ServiceConnector`. L'entité JPA est renommée `ConnectorInstanceJpaEntity` et reste confinée à `infrastructure.persistence.connector` ; l'adaptateur assure la conversion (et le déchiffrement) — conforme au pattern déjà appliqué à `WorkflowInstance`/`WorkflowJpaEntity`.

> La généralisation de cette règle aux autres entités du domaine n'a pas été faite — voir le point
> ouvert 17.

**Documenté dans :** [06-connecteurs.md](../specs/06-connecteurs.md), [01-architecture-hexagonale.md](../specs/01-architecture-hexagonale.md).
