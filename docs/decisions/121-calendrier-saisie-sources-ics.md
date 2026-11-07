# §121 — Calendrier du tenant : saisie par période, sources ICS, surcharges et rechargement

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · [registre](README.md)

**Contexte (issue #33).** §120 a livré le calendrier du tenant (fuseau, week-end, jours fermés) sous la forme d'une
liste plate de dates, saisie dans un textarea. Une date n'avait ni libellé ni origine, le bouton des fériés belges
fusionnait sans retour, et aucun calendrier ICS ne s'importait. Cadrage du 2026-11-07. **Complète §120** : la liste
plate de D-CAL-HOLIDAYS est remplacée par D-CAL-ENTRY et D-CAL-SOURCE ; les autres décisions de §120 (comptage,
fuseau, escalade, minuteurs) ne changent pas.

**D-CAL-ENTRY** — une saisie manuelle est une ligne `(début, fin, nature, libellé)`, bornes incluses ; un seul jour a
`fin = début`. Nature : `CLOSED` (jour fermé) ou `OPEN` (jour ouvré forcé). La saisie se fait par `<input type="date">`
natif (Du, Au facultatif), sans dépendance front. Les périodes sont stockées en tant que périodes. Plafonds : 366 jours
par période, 2 000 lignes manuelles, libellé de 120 caractères au plus. **Garde-fou ajouté en cours de lot :** le
calendrier déroule au plus 200 000 jours en mémoire (`TenantCalendar.MAX_EXPANDED_DAYS`), toutes lignes confondues.

**D-CAL-OVERRIDE** — ordre de priorité, du plus fort au plus faible : manuel `OPEN` (rouvre même un samedi ou un
dimanche), manuel `CLOSED`, jour fermé d'une source, motif de week-end. Un rechargement ne touche jamais aux saisies
manuelles. Un jour de source recouvert par un `OPEN` est signalé à l'écran.

**D-CAL-SOURCE** — une source est nommée (unique par tenant en ignorant la casse, 80 caractères au plus), de type
`ICS_FILE`, `ICS_URL` ou `BE_FEDERAL`. Elle ne produit que des jours `CLOSED`. Au plus 10 sources par tenant et, ajout
du lot 1, 10 000 périodes par source. On garde la date, l'auteur, le SHA-256 du contenu et l'erreur du dernier
chargement. Recharger remplace atomiquement toutes les périodes de la source. Une source en échec garde les jours de
son chargement précédent.

**D-CAL-ICS** — ical4j 4.x, en infrastructure seulement ; le domaine ne reçoit que des périodes. Sont retenus les
VEVENT « journée entière » (`DTSTART;VALUE=DATE`, `DTEND` exclusif ; plusieurs jours : une période). Sont ignorés et
comptés dans l'aperçu : événements horaires, `STATUS:CANCELLED`, `TRANSP:TRANSPARENT`. `RRULE`, `RDATE` et `EXDATE`
sont déroulés de l'année N-1 à N+2 (fuseau du tenant) et réévalués à chaque rechargement. Plafonds : 1 Mio par fichier,
5 000 VEVENT. Le résumé (`SUMMARY`) devient le libellé, tronqué à 120 caractères.

**D-CAL-ICS-PREVIEW** — l'import se fait en deux temps. L'aperçu renvoie les jours ajoutés, retirés, masqués par une
surcharge manuelle, les événements ignorés par motif et le SHA-256. L'application renvoie le SHA-256 attendu ; si le
contenu a changé entre-temps : 409.

**D-CAL-ICS-URL** — https seulement, via `OutboundUrlGuard` et `PinnedHttpClient` (§44). Délai de 10 s, 1 Mio au plus,
aucune redirection suivie, aucun en-tête d'authentification. L'URL est un secret : chiffrée par `ConnectorSecretCipher`,
masquée dans l'API (hôte + `…`), absente des journaux et de l'export. Rechargement manuel par un bouton ; aucun job
planifié.

**D-CAL-BE-SOURCE** — le bouton « fériés fédéraux belges » crée ou recharge la source `BE_FEDERAL` (années N et N+1,
libellés français), sans aperçu. Les dates déjà saisies sous §120 sont reprises en lignes manuelles `CLOSED` sans
libellé, leur origine étant inconnue (changeset 047, qui supprime ensuite la colonne `holidays` ; le 046 n'est pas
modifié).

**D-CAL-API** — contrat additif. `PUT /api/tenant/calendar` accepte `entries` ou l'ancien `holidays` (converti en
lignes manuelles `CLOSED`), jamais les deux (400) ; il ne touche pas aux sources. La réponse ajoute `entries` et
`sources` ; `holidays` reste, calculé (jours fermés effectifs) et marqué déprécié. Un client ancien qui relit puis
réécrit `holidays` convertit donc aussi les jours des sources en saisies manuelles : il doit passer à `entries`.

**D-CAL-PROMO-2** — l'artefact `CALENDAR` porte un instantané complet : fuseau, week-end, saisies manuelles et chaque
source avec ses périodes déjà déroulées. Une source `ICS_URL` est exportée sans son URL ; l'import ne fait jamais
d'appel réseau. Une source du bundle de même nom qu'une source d'ici en garde l'identifiant (et l'URL) ; une source d'ici
absente du bundle est supprimée. Un ancien bundle à liste plate s'importe en lignes manuelles `CLOSED`.

**D-CAL-MCP** — inchangé (§120) : aucun outil MCP.

**Hors périmètre.** Rechargement planifié des URL, authentification sur l'URL, événements horaires, recalcul des
échéances déjà posées (§120), demi-journées, calendrier par type, export ICS, vue mensuelle cliquable.

**Impacte.** `CalendarEntry`, `CalendarSource`, `TenantCalendar`, `BusinessCalendar`, `BelgianFederalHolidays`,
`ManageTenantCalendarUseCase`, `JdbcTenantCalendar`, changeset 047, `TenantCalendarController`, `TypeSettingsArtifacts`
et `CalendarDoc` (promotion), `openapi/openapi.yaml`, écran Admin → Types → Calendrier, spec 04, guide utilisateur.
