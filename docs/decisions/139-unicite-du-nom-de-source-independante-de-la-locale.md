# §139 — Unicité du nom de source de calendrier indépendante de la locale

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#96](https://github.com/superjojo79/Myrmacta/issues/96)). Mandat « Traite l'issue 96 », 2026-11-15.
Complète [§121](121-calendrier-saisie-sources-ics.md) (D-CAL-SOURCE). Comportement : [spec 04](../specs/04-dossiers.md),
section « calendrier du tenant ».

**Constat.** L'index `uq_tenant_calendar_source_name` (changeset 047) est `(tenant_id, lower(name))`.
`lower()` sans collation suit `LC_CTYPE`. Sur une base créée avec la locale `C` et l'encodage UTF-8,
`lower('É')` reste `É` : « Fériés » et « FÉRIÉS » sont deux noms. Le domaine, lui, compare avec
`String.toLowerCase(Locale.ROOT)` (`CalendarSource.nameKey`). La base partagée des tests et l'image
`postgres:17-alpine` sont en `C.utf8` ou `en_US.utf8`, où `lower()` plie déjà ces lettres : le test
d'origine passe en CI et le défaut ne se voit que sur une base en locale `C`.

**D-CAL-SOURCE-CASE** — le nom reste unique par tenant en ignorant la casse, y compris celle des lettres
accentuées, et cette règle ne dépend pas de la locale de la base.

1. Changeset 052 : l'index devient `(tenant_id, lower(name COLLATE "pg_c_utf8"))`. `pg_c_utf8` est une
   collation standard de PostgreSQL 17 (fournisseur intégré, encodage UTF-8) : casse simple Unicode,
   mêmes règles sur chaque plateforme, stables pour une version majeure. Le changeset 047 n'est pas modifié.
2. Les accents restent distincts : « Feries » et « Fériés » coexistent. Un autre tenant peut réutiliser le nom.
3. Si deux noms déjà enregistrés ne diffèrent que par cette casse, la création de l'index échoue et la
   migration s'arrête : on les départage avant de relancer. PostgreSQL annule alors aussi la suppression
   de l'ancien index (DDL transactionnel).
4. Écart accepté avec `Locale.ROOT` : le casse simple de `pg_c_utf8` et le casse plein de Java divergent
   sur quelques lettres hors latin courant (`İ` devient `i` d'un côté et `i` suivi de la marque d'un point
   de l'autre). Les libellés de calendrier sont des noms latins ; le contrôle du domaine sous verrou reste
   celui de l'API. Écarté : une collation ICU (`und-x-icu`), présente dans les binaires du dépôt mais dont
   le comportement peut changer avec la version d'ICU, et un simple changement de locale du PostgreSQL de
   test, qui laisserait une base de production en locale `C` exposée.

**Impacte :** changeset 052, `CalendarSource.nameKey`, `TenantCalendarEntriesMigrationIT`, spec 04.
