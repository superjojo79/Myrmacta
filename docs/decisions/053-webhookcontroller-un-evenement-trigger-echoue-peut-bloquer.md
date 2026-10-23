# §53 — `WebhookController` : un événement TRIGGER échoué peut bloquer les rejeux avec la même `Idempotency-Key`

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §53 · [registre](README.md)

**Statut (2026-09-03) : corrigé.** Même pattern que le correctif F2 de TODO §51 : capture de
`RuntimeException` autour de `readPayload`/`onIncomingEvent`, `idempotencyStore.complete(...)`
appelé avec le statut déterministe (400) pour `ConnectorValidationException`/`IllegalArgumentException`,
`IN_FLIGHT` conservé pour l'imprévu. Tests ajoutés dans `WebhookControllerTest`
(`receive_deterministicValidationFailureReturns400_andCompletesClaim`,
`receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`). `./mvnw verify` : 1541
tests, 0 échec.

**Constat (trouvé en revue de sécurité adversariale sur `connecteur-callback`, 2026-09-02) :**
`WebhookController.receive(...)` n'appelle `idempotencyStore.complete(...)` que sur le chemin de
succès. Si `ConnectorTriggerRuntime.onIncomingEvent(...)` lève (config manquante, référence dossier
inconnue, etc.), la ligne d'idempotence reste `IN_FLIGHT` en base — un rejeu avec la **même**
`Idempotency-Key` (pratique standard de retry idempotent côté systèmes externes) lève
`WebhookIdempotencyInFlightException` (409 « delivery in progress ») pendant toute la durée du TTL
(24 h par défaut), au lieu de recevoir la vraie réponse d'échec ou d'être retraité. Exactement le
même défaut a été corrigé sur `CallbackWebhookController` (composant `JpaWebhookIdempotencyStoreAdapter`
partagé) dans le correctif post-merge de TODO §51 — voir
[`docs/ai/implementation/2026-09-02-feature-connecteur-callback.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-02-feature-connecteur-callback.md) § « Correctifs post-merge »
pour le détail du mécanisme et le pattern de correction déjà écrit et testé (capturer
`RuntimeException`, appeler `complete(...)` avec le statut déterministe pour les échecs dont
l'issue est stable sur le même contenu de requête, laisser `IN_FLIGHT` pour l'imprévu).

**Impact réel plus faible que sur le callback** : les exceptions typiques de
`ConnectorTriggerRuntimeImpl` (config manquante, `DossierNotFoundException`) sont en général des
erreurs permanentes — un rejeu échouerait de toute façon pour la même raison. Pas un blocage
urgent, mais un vrai bug de fiabilité si un système externe retente légitimement après une erreur
transitoire (ex. `DossierNotFoundException` sur une référence pas encore créée par ailleurs).

**À produire :** même correctif que `CallbackWebhookController` (try/catch autour de l'appel à
`onIncomingEvent`, `complete(...)` avec le statut déterministe, exception non reconnue laissée
`IN_FLIGHT`), test de régression miroir de
`receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`.

**Impacte :** `WebhookController.java`.
