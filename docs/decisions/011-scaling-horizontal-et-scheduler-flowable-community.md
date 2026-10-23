# §11 — Scaling horizontal et scheduler Flowable Community

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §11 · [registre](README.md)

**Problème :** Risque perçu de double exécution des jobs Flowable en déploiement multi-instances Kubernetes.

**Décision retenue :** Le job executor de Flowable Community gère nativement le verrouillage optimiste au niveau ligne (`lockOwner`/`lockExpirationTime`) et supporte le clustering sans contrainte d'instance unique. La configuration des délais de verrou (`lockTimeInMillis`, `defaultAsyncJobAcquireWaitTime`) est validée par un test de charge au niveau cible. Si la contention devient un problème à l'échelle visée, séparation opérationnelle entre pods API (`asyncExecutorActivate: false`) et pods worker (`asyncExecutorActivate: true`, eux-mêmes réplicables pour la haute disponibilité), plutôt qu'une limitation à une seule instance globale.

> **Mise à jour** — avec la décision n° 13, un tenant occupe par défaut une seule instance : aucune
> contention entre pods d'un même tenant n'est possible tant qu'il n'est pas scalé. Le repli
> « pods API / pods worker » ne redevient pertinent que dans le pool de répliques d'un tenant scalé,
> jamais entre tenants. Répercuté dans
> [05-workflow-engine.md](../specs/05-workflow-engine.md).

**Documenté dans :** [05-workflow-engine.md](../specs/05-workflow-engine.md).
