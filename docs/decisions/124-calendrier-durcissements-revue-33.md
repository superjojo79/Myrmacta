# §124 — Calendrier du tenant : durcissements de gravité basse (revue de #33)

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · [registre](README.md)

**Contexte (issue #34).** La revue indépendante de la PR qui ferme #33 (§121) a laissé dix constats de gravité basse
(complétés par une seconde revue). Les constats hauts et moyens étaient déjà corrigés dans cette PR. Cadrage et
arbitrage de l'agent sous mandat « Traite l'issue 34 », 2026-11-09. **Complète §121** sans rouvrir son périmètre produit.

**D-CAL-CACHE** — `TenantCalendars.get` conserve le `BusinessCalendar` déroulé tant que `tenant_calendar.updated_at`
n'a pas changé (une instance = un tenant). Un miss relit les périodes et appelle `toCalendar()` une fois. L'année civile
du tenant (ICS, fériés belges) se calcule avec `LocalDate.ofInstant(…, ZoneId.of(zoneId))`, sans dérouler. Les jours
ouvrés forcés masquant une source se prennent sur les saisies manuelles `OPEN`, sans second `toCalendar()`.

**D-CAL-API-HOLIDAYS** — le champ de réponse `holidays` (D-CAL-API, déprécié) reste présent pour la compatibilité des
clients anciens, mais n'est plus rempli : liste vide. Les clients lisent `entries` et `sources`. Évite de sérialiser
jusqu'à 200 000 dates sur un `GET` d'administration.

**D-CAL-ICS-SIZE** — avant le parse multipart Spring (plafond global 25 Mo), un filtre refuse sur `Content-Length` les
POST vers `/api/tenant/calendar/sources` et `/api/tenant/calendar/sources/**` au-delà de 1 MiB plus une marge de
formulaire (64 KiB). Le contrôle applicatif à 1 MiB sur le fichier reste.

**D-CAL-HTTP-HEADERS** — `PinnedHttpClient` configure HttpClient 5 (`Http1Config`) : au plus 100 en-têtes de réponse,
ligne d'au plus 8 KiB. Complète le délai total de 10 s (D-CAL-ICS-URL).

**D-CAL-FK-SOURCE** — changeset 050 : contrainte unique `(tenant_id, id)` sur `tenant_calendar_source`, puis FK
`(tenant_id, source_id)` de `tenant_calendar_entry` vers cette clé. Une période ne peut plus référencer la source d'un
autre tenant.

**D-CAL-THISANDFUTURE** — un `RECURRENCE-ID;RANGE=THISANDFUTURE` retire de la série toutes les occurrences à partir de
cette date (bornes de la fenêtre ICS). Sans `RANGE`, comportement inchangé (une occurrence).

**D-CAL-URL-PURGE** — si `url_ciphertext` ne se déchiffre plus, `findSourceUrl` l'efface et met `masked_url` à nul, sous
le verrou du calendrier. L'écran « URL à ressaisir » reflète alors l'état réel (aligné sur D-CAL-ICS-URL).

**D-CAL-LOG-HC** — ne pas passer `org.apache.hc` (ni `org.apache.http`) en DEBUG en exploitation : la ligne de requête
peut contenir l'URL secrète d'une source ICS. Documenté dans `docs/guides/DEPLOIEMENT.md` ; un test de configuration
vérifie que le niveau effectif n'est pas DEBUG ; un test capture les journaux applicatifs d'un rechargement pour
prouver que l'URL n'y figure pas.

**Migration 047.** Les dates de l'ancienne colonne `holidays` étaient validées à l'écriture (§120, `LocalDate.parse`).
Avant un déploiement sur un jeu réel ancien, vérifier qu'aucune valeur ne ferait échouer `btrim(d)::date` — sinon le
changeset 047 bloque le démarrage. Note d'exploitation dans `DEPLOIEMENT.md`.

**Hors périmètre.** Refonte de `BusinessCalendar` sur intervalles (reporté si le cache ne suffit plus) ;
rechargement planifié ; authentification sur l'URL.

**Impacte.** `JpaTenantCalendarRepositoryAdapter`, `ManageTenantCalendarUseCaseImpl`, `TenantCalendarController`,
`PinnedHttpClient`, `Ical4jIcsCalendarParser`, changeset 050, filtre taille calendrier, `SecurityConfig`,
`application.yml`, `docs/guides/DEPLOIEMENT.md`, `docs/specs/04-dossiers.md`, tests associés.
