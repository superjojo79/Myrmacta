# §7 — Durabilité des événements de déclenchement workflow

> **Statut :** clos · **Section :** [Décisions actées › Architecture](section-decisions-architecture.md) · ancien `TODO.md` §7 · [registre](README.md)

**Problème :** Le bus `ApplicationEventPublisher` est synchrone et en mémoire — un crash entre la réception d'un trigger et le démarrage de l'instance Flowable entraînait une perte sans rejeu possible.

**Décision retenue :** Pattern outbox transactionnel — l'événement de trigger est écrit dans une table `outbox_event` (schéma du tenant) dans la même transaction que la création / résolution du `Dossier`, puis un relais (scheduler de polling, avec chemin d'évolution vers Debezium/CDC si le volume l'exige) démarre Flowable et marque l'événement traité. Solution retenue car elle ne nécessite pas de nouvelle brique d'infrastructure immédiate tout en garantissant la durabilité.

> Articulation avec `ConnectorTriggerRuntime` tranchée par la décision n° 16 (option B) : le runtime
> crée/résout le dossier et écrit l'outbox ; le relais réserve le lien, démarre Flowable et confirme.

**Documenté dans :** [05-workflow-engine.md](../specs/05-workflow-engine.md),
[06-connecteurs.md](../specs/06-connecteurs.md) (décision n° 16).
