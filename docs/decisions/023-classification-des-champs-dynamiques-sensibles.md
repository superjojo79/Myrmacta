# §23 — Classification des champs dynamiques sensibles

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §23 · [registre](README.md)

**Partiel (2026-08-03 — feature `autorisation-conformite`) :** flag
`FieldDefinition.sensitive` livré (métadonnée JSON, défaut false, API types).
**Partiel (2026-08-21 — feature `durcissement-donnees-sensibles`) :**
masquage à l'affichage déjà en place (`DossierFieldRedactor`) désormais
également appliqué à l'export CSV (`GET /api/dossiers/export.csv`, même
règle : masqué pour tout acteur non `TENANT_ADMIN`).

**Résolu (2026-08-24, feature `masquage-donnees-sensibles-logs-webhooks`) :**
audit avant implémentation — **aucune fuite active trouvée**, ni dans les
logs (aucun code de production ne loggue un `Dossier`/sa map `fields`
entière) ni dans les webhooks (aucun producteur Java ni process BPMN ne
peuple aujourd'hui le payload de notification avec des champs de dossier).
Le point restait un risque latent sans garde-fou, pas un incident à
corriger — traité comme protection préventive en défense en profondeur :
`Dossier`/`FieldDefinition` ont désormais un `toString()` sûr
(n'imprime plus jamais `fields`/`defaultValue`) ; une règle ArchUnit
restreint l'appel à `Dossier.fields()`/`FieldDefinition.defaultValue()` à
une liste blanche de classes (protection plus large qu'une détection de
logging, qui s'est avérée irréalisable avec ArchUnit — pas d'analyse de
flux de valeurs) ; `SendNotificationUseCaseImpl` masque automatiquement
les valeurs sensibles d'un payload de notification dès lors qu'un
producteur lui fournit le contexte dossier (`DossierFieldsSnapshot`),
avant toute écriture outbox — protège les canaux webhook et browser d'un
seul point de contrôle, sans dépendre de la discipline d'un futur
producteur. Prouvé bout en bout (masquage → sérialisation outbox →
désérialisation → corps HTTP réel) et par contrôle mutant manuel (le
masquage désactivé fait échouer les tests). `./mvnw verify` : 1088 tests,
0 échec.

**Impacte :** [04-dossiers.md](../specs/04-dossiers.md),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
