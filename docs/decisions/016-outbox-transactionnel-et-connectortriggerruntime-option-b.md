# §16 — Outbox transactionnel et `ConnectorTriggerRuntime` — option B

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §16 · [registre](README.md)

**Problème :** la décision n° 7 posait un outbox, tandis que `06`/`04` décrivaient un `ConnectorTriggerRuntime` entièrement synchrone (dossier → réserve → Flowable → confirme) sans outbox. Les deux séquences étaient incompatibles ; le pseudo-code du relais marquait en outre l'événement traité même après échec de publication.

**Décision retenue — option B (outbox après création/résolution du dossier) :**

1. **Phase producteur** (`ConnectorTriggerRuntime.onIncomingEvent`, synchrone, transaction unique) : authentification déjà faite par l'adaptateur web → `TriggerConnector.onEvent(...)` → pour chaque événement, créer le dossier **ou** résoudre `existingDossierReference` → écrire `outbox_event` (payload normalisé + `dossierId` + `connectorInstanceId`) → ACK webhook. **Jamais** de démarrage Flowable ici.

2. **Phase relais** (`@Scheduled`, transaction dédiée par événement) : lire les pending → réserver le `DossierWorkflowLink` (ou reprendre un lien `RESERVED` existant au rejeu) → démarrer Flowable → `confirmStarted` → `markProcessed` **uniquement en cas de succès, dans la même transaction**. Échec → incrément attempts, retry ; au-delà du plafond → dead-letter. Purge des événements `PROCESSED` au-delà d'une rétention configurable.

3. **Contenu de l'outbox :** `WorkflowTriggerEvent` déjà normalisé + `dossierId` (pas le payload brut) — la transformation n'est pas rejouable si elle échoue avant l'écriture (compromis accepté de l'option B).

4. **Idempotence au rejeu :** le relais ne recrée jamais le dossier ; si un lien `RESERVED`/`ACTIVE` existe déjà pour ce dossier + process, il le reprend. `WorkflowAlreadyRunningException` sur un autre workflow actif → dead-letter (le dossier existe déjà, l'ACK a été donné).

5. **`FOR UPDATE SKIP LOCKED` :** uniquement si le tenant est scalé sur plusieurs répliques (décision n° 13) ; pas une exigence du cas par défaut mono-instance.

**Écartées :** A (tout synchrone — rouvre n° 7), C (outbox du brut — ACK avant validation métier), D (hybride par source — double chemin).

**Documenté dans :** [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [05-workflow-engine.md](../specs/05-workflow-engine.md) (outbox + relais corrigés), [06-connecteurs.md](../specs/06-connecteurs.md) (`ConnectorTriggerRuntime` scindé) et [04-dossiers.md](../specs/04-dossiers.md) (création batch alignée).
