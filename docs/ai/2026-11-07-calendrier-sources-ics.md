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
| 1 | Modèle, saisie manuelle, changeset 047, source `BE_FEDERAL`, promotion v2, écran | livré (à pousser) | — |
| 2 | Sources ICS par fichier : ical4j, aperçu/application SHA-256, rechargement, suppression | à faire | — |
| 3 | Sources ICS par URL : fetcher anti-SSRF, URL chiffrée et masquée | à faire | — |
| 4 | Revue indépendante, documentation de clôture, suppression de ce document | à faire | — |

Prochaine action : lot 2.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-07 | `./mvnw verify -Dmaven.compiler.release=21 -Dskip.frontend=true` (PostgreSQL 16 local, root) | voir PR |
| 2026-11-07 | `cd frontend && npx vitest run src/components/admin/` | 24 passés |
| 2026-11-07 | `cd frontend && npx playwright test e2e/pilotage-admin-types.spec.ts` | 18 passés |
| 2026-11-07 | `scripts/check-docs.sh` | vert |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
