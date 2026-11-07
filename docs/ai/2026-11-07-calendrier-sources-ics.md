# Calendrier du tenant : saisie par période, sources ICS — document de travail

Issue : #33 · Spec du domaine : docs/specs/04-dossiers.md · Points du registre : §120, §121

## Cadrage

- Problème et utilisateurs : l'administrateur du tenant saisit ses jours fermés dans un textarea de dates ISO, sans
  période, libellé ni origine, et ne peut pas importer de calendrier ICS (issue #33).
- Périmètre / hors-périmètre : voir [§121](../decisions/121-calendrier-saisie-sources-ics.md) (cadrage de l'issue #33,
  tranché le 2026-11-07).
- Critères de succès (vérifiables) : les six critères « Terminé quand » du commentaire de l'issue #33.
- Contraintes : pas de nouvelle dépendance front ; ical4j en infrastructure seulement ; URL de source secrète ; aucun
  outil MCP ; changeset 046 intact.
- Validé par l'utilisateur le : 2026-11-07 (mandat `/goal`, pauses de dépendance, migration et contrat levées).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Plafond de mémoire des périodes déroulées | aucun / 200 000 jours / autre | 200 000 jours, 10 000 périodes par source | tranchée → §121 (D-CAL-ENTRY, D-CAL-SOURCE) |

## Conception

- Faits vérifiés dans le code ou les sources :
  - `TenantCalendar` (046) était une liste plate de dates en CSV ; `BusinessCalendar.isBusinessDay` ne connaissait que
    week-end et fériés.
  - `JsonArtifactCodec` refuse les champs inconnus : `CalendarDoc` garde `holidays` (lecture seule) pour les anciens
    bundles.
  - `OpenApiContractIT` compare le YAML à `openapi/openapi.yaml` (`-Dopenapi.update=true` pour régénérer).
  - `Liquibase.update(int, String)` applique les N premiers changesets (test de reprise du 047).
- Composants touchés : domaine (`CalendarEntry`, `CalendarSource`, `TenantCalendar`, `BusinessCalendar`), ports,
  `JdbcTenantCalendar`, `TenantCalendarController`, promotion (`CalendarDoc`, `TypeSettingsArtifacts`), SPA.
- Scénarios de test : voir la section « Implémentation » de la spec 04.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Modèle, saisie manuelle, changeset 047, source `BE_FEDERAL`, promotion v2, écran | livré | a10e1d7b |
| 2 | Sources ICS par fichier : ical4j, aperçu/application SHA-256, rechargement, suppression | livré | voir commit |
| 3 | Sources ICS par URL : fetcher anti-SSRF, URL chiffrée et masquée | livré | voir commit |
| 4 | Revue indépendante, documentation de clôture, suppression de ce document | à faire | — |

Prochaine action : lot 4 (revue indépendante, clôture).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-07 | `./mvnw verify -Dmaven.compiler.release=21` (frontend compris, PostgreSQL 16 local, `max_connections=800`) après le lot 2 | 4 327 tests, 0 échec, `BUILD SUCCESS` |
| 2026-11-07 | `cd frontend && npm run lint && npx vitest run` | lint propre, 884 tests passés |
| 2026-11-07 | `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test e2e/pilotage-admin-types.spec.ts -g E2E-CAL` | 8 passés (`E2E-CAL-1` à `8`) |
| 2026-11-07 | `./mvnw verify -Dmaven.compiler.release=21` après le lot 3 (changeset 048 compris) | 4 379 tests, 0 échec, `BUILD SUCCESS` |
| 2026-11-07 | `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test e2e/pilotage-admin-types.spec.ts -g E2E-CAL` | 11 passés (`E2E-CAL-1` à `11`) |
| 2026-11-07 | `./mvnw verify -Dmaven.compiler.release=21` après les corrections de la revue (IPv6, verrou, `RECURRENCE-ID`, empreinte, chien de garde) | 4 433 tests, 0 échec, `BUILD SUCCESS` |
| 2026-11-07 | `scripts/check-docs.sh` | vert |

## Revue de clôture

Revue indépendante (sous-agent Opus, sans l'historique ; le modèle Fable demandait des crédits d'usage), grille de
`myrmacta-feature` étape 4.1, sur `1ac7b954..9acf93a3`. Chaque constat a été vérifié avant correction.

| # | Constat | Sévérité | Correctif ou issue |
|---|---|---|---|
| 1 | SSRF : `OutboundUrlGuard` ne contrôlait que la graphie d'un hôte littéral ; des IPv6 (`[::ffff:7f00:1]`, `[::]`, forme longue de `::1`, NAT64, 6to4) joignaient la boucle locale ou les métadonnées | Haute | Corrigé à la source (adresse figée contrôlée, IPv4 embarquée comprise) ; 26 cas dans `OutboundUrlGuardTest`, 7 dans `HttpsIcsFetcherTest` |
| 2 | Perte de mise à jour : `saveSettings` réécrivait fuseau et saisies manuelles depuis un instantané périmé | Moyenne | Corrigé : `touch` à la place, écritures sous `locked` ; tests du cas d'usage et `JdbcTenantCalendarIT` |
| 3 | Plafonds (10 sources, 200 000 jours) vérifiés sur un instantané : état hors plafond, lectures en 400 | Moyenne | Corrigé : état relu et revérifié sous verrou ; course sur la dixième source dans `JdbcTenantCalendarIT` |
| 4 | `RECURRENCE-ID` ignoré : occurrence annulée ou déplacée restée fermée | Moyenne | Corrigé dans le parseur ; 5 tests |
| 5 | Empreinte sur les octets : un agenda qui régénère `DTSTAMP` aurait toujours donné 409 | Moyenne | Corrigé : empreinte des périodes et libellés ; tests du parseur et de l'API |
| 6 | Délai de 10 s non tenu sur les en-têtes de réponse | Basse/moyenne | Corrigé : chien de garde qui coupe la connexion ; test à serveur brut (30 s avant, 3,7 s la classe après) |
| 8 | `url_ciphertext` conservé après un changement de type de source | Basse | Corrigé dans `saveSource` ; test d'intégration |
| 9 | Upsert refusé en silence pour l'identifiant d'un autre tenant | Basse | Corrigé : exception, aucune période écrite ; test d'intégration |
| 10 | Application d'un bundle en transactions séparées | Basse | Corrigé : un seul verrou ; test de promotion |
| 12 | `toString()` des enregistrements `Origin` et `Plan` portait l'URL | Basse | Corrigé |
| 7, 11 et résidus de 3, 6, 9 | Coût de `toCalendar()`, multipart de 25 Mo, bornes d'en-têtes, FK composite, `THISANDFUTURE`, journaux DEBUG | Basse | Issue #34 |

Seconde revue indépendante : voir ci-dessous.
