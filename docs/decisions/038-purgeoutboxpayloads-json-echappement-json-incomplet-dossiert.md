# §38 — `PurgeOutboxPayloads.json` — échappement JSON incomplet + `DossierType.code` sans validation de format

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §38 · [registre](README.md)

**Constat (revue PR #55, `retention-dossier-type`) :** `EvaluateDossierRetentionUseCaseImpl.payloadJson`
construisait le JSON de `purge_outbox.payload_json` à la main, avec un échappement limité à `\` et `"`
— les caractères de contrôle (ex. saut de ligne) que RFC 8259 impose d'échapper dans une chaîne
JSON ne le sont pas. Or `DossierType.code`, injecté tel quel dans ce payload, n'a **aucune
validation de format/charset** dans le domaine (`DossierType` ne fait qu'un `requireNonNull`).
`purge_outbox.payload_json` est une colonne `json` PostgreSQL : un `code` contenant un caractère de
contrôle brut ferait échouer l'`INSERT`.

**Mise à jour (revue PR #56, `purge-stockage`) :** le constructeur JSON manuel a été mutualisé dans
`PurgeOutboxPayloads.json()` (`domain/dossier/model/`), désormais utilisé à la fois par le job de
rétention (§21) **et** par le nouvel enqueue admin `EnqueueDossierPurgeUseCaseImpl` (§22,
`POST /api/dossiers/{id}/purge-requests`) — l'échappement lui-même n'a **pas** été corrigé lors de
cette mutualisation (`escape()` copié tel quel). Le rayon d'impact est donc plus large qu'au constat
initial : un `code` mal formé bloque désormais aussi l'enqueue admin, pas seulement le job planifié.
Le job de rétention reste en outre le plus exposé : il tourne dans une seule transaction
(`EvaluateDossierRetentionJob.evaluate`), donc un `INSERT` en échec y annule **tout le lot**,
bloquant silencieusement la rétention pour l'ensemble du tenant jusqu'à correction du type fautif.
`PurgeOutboxPayloads` fait aussi de l'extraction JSON manuelle en lecture (`parseEligibleAt`,
`parseTenantId` — `indexOf`/`substring`, pas un vrai parseur), même famille de fragilité.

Auto-infligé (seul un `TENANT_ADMIN` peut définir `code`), donc pas un vecteur d'attaque externe —
mais un point de robustesse réel, susceptible de stopper silencieusement un mécanisme de
conformité RGPD (rétention **et**, désormais, droit à l'effacement à la demande).

**Corrigé (2026-08-19, cette session) :** les deux options envisagées ont été retenues ensemble,
sans introduire Jackson dans le domaine — `PurgeOutboxPayloads` porte déjà le commentaire délibéré
« no Jackson in domain » et `mvn dependency:tree` confirme qu'aucun module Jackson n'est même
résolu dans ce projet (Spring Boot 4.1 / `spring-boot-starter-web` ne l'entraîne pas ici) ; le
remplacer aurait ajouté une dépendance, pas seulement corrigé un bug.

- `PurgeOutboxPayloads.escape()` réécrit pour échapper tous les caractères de contrôle RFC 8259
  (formes courtes `\n \r \t \b \f`, repli `\u00XX` pour le reste de la plage `< 0x20`) — la
  lecture (`parseEligibleAt`/`parseTenantId`) n'a pas eu besoin de changer, l'écriture était le
  seul point de rupture.
- `DossierType` valide désormais `code` contre `^[A-Za-z0-9_-]+$` dans le constructeur compact du
  record (même point que `validateRetention`) — défense en profondeur, cohérent avec l'usage réel
  (`CLAIM`, `LEGAL`, `LOG`…) et avec son usage en `@PathVariable` (`DossierTypeController`).
- Tests neufs : `PurgeOutboxPayloadsTest` (échappement `\`/`"`, formes courtes, repli `\u00XX`,
  absence de tout caractère de contrôle brut en sortie, round-trip lecture) ;
  `DossierTypeTest` (code valide accepté, code avec espace/contrôle/vide rejeté).
- `./mvnw verify -Dmaven.compiler.release=21` : 848 tests (839 + 9 nouveaux), 0 échec, ArchUnit et
  couverture Jacoco au vert — aucune régression, aucun `code` existant en base/tests hors charset.

**Impacte :** `PurgeOutboxPayloads.java`, `DossierType.java`,
[04-dossiers.md](../specs/04-dossiers.md).
