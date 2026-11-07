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
- **Concurrence (revue de #33).** Deux administrateurs peuvent agir en même temps. Le téléchargement et la lecture se
  font hors verrou, sur un instantané. Toute écriture passe par `TenantCalendarRepository.locked` (transaction qui tient
  le verrou de la ligne `tenant_calendar`) : l'état y est relu, les plafonds, les noms et l'empreinte revérifiés, puis on
  n'écrit que ce que l'on change. Une opération sur une source ne réécrit jamais les saisies manuelles ni le fuseau.
  Un échec de rechargement est enregistré sur la source relue sous verrou. L'application d'un calendrier de bundle
  (promotion) tient un seul verrou.

**D-CAL-ICS** — ical4j 4.x, en infrastructure seulement ; le domaine ne reçoit que des périodes. Sont retenus les
VEVENT « journée entière » (`DTSTART;VALUE=DATE`, `DTEND` exclusif ; plusieurs jours : une période). Sont ignorés et
comptés dans l'aperçu : événements horaires, `STATUS:CANCELLED`, `TRANSP:TRANSPARENT`. `RRULE`, `RDATE` et `EXDATE`
sont déroulés de l'année N-1 à N+2 (fuseau du tenant) et réévalués à chaque rechargement. Plafonds : 1 Mio par fichier,
5 000 VEVENT. Le résumé (`SUMMARY`) devient le libellé, tronqué à 120 caractères. Une fréquence plus fine que
quotidienne est refusée (événement `INVALID`).
- **Surcharges d'occurrence (`RECURRENCE-ID`).** Un VEVENT de même UID qu'une série en remplace une occurrence : celle-ci
  quitte la série (annulée, déplacée ou rendue transparente), le VEVENT de remplacement est lu comme un événement à part.
  Sans série dans le fichier, il n'ôte rien. `RANGE=THISANDFUTURE` n'est pas géré (traité comme une occurrence).

**D-CAL-ICS-PREVIEW** — l'import se fait en deux temps. L'aperçu renvoie les jours ajoutés, retirés, masqués par une
surcharge manuelle, les événements ignorés par motif et le SHA-256. L'application renvoie le SHA-256 attendu ; si le
contenu a changé entre-temps : 409. **L'empreinte décrit ce que le fichier donne** (périodes et libellés, triés), non
ses octets : une source par URL est téléchargée une fois pour l'aperçu et une fois pour l'application, et certains
agendas en ligne régénèrent `DTSTAMP` ou `PRODID` à chaque téléchargement (revue de #33).

**D-CAL-ICS-URL** — https seulement, via `OutboundUrlGuard` et `PinnedHttpClient` (§44). Délai de 10 s au total
(connexion 10 s, lecture 5 s par lecture, corps coupé au-delà de 10 s), 1 Mio au plus, aucune redirection suivie,
aucun en-tête d'authentification. L'URL est un secret : chiffrée par `ConnectorSecretCipher` (`url_ciphertext`),
masquée dans l'API (`schéma://hôte[:port]/…`, changeset 048 : `masked_url`), absente des journaux, des messages
d'erreur (seul l'hôte y figure) et de l'export. Une URL avec identifiants (`user:mot-de-passe@`) est refusée.
Rechargement manuel par un bouton ; aucun job planifié.
- **Garde réseau (revue de #33).** `OutboundUrlGuard` contrôle l'adresse qu'il fige, pas seulement la graphie de l'hôte :
  une IPv6 littérale qui désigne une adresse interne (IPv4-mappée en hexadécimal, `::1` en forme longue, `::`, IPv4-
  compatible, NAT64 `64:ff9b::/96`, 6to4 `2002::/16`) est refusée comme sa forme IPv4. Le délai de 10 s est tenu par un
  chien de garde qui coupe la connexion, en-têtes de réponse comprises.
- **Rechargement.** Sans URL dans la requête, le serveur relit celle qu'il conserve ; avec une URL, il la remplace. Une
  source importée par promotion n'a pas d'URL : l'écran dit « URL à ressaisir ». Si la clé de chiffrement a changé et
  que l'URL ne se lit plus, le serveur la redemande de la même façon. Une source qui change de type, ou dont l'URL est à
  ressaisir, ne garde aucun secret chiffré.
- **Échec.** Un rechargement dont le téléchargement ou la lecture échoue garde les jours de la source, retient l'erreur
  (500 caractères au plus, sans l'URL) dans `lastError` et répond 400 ; le prochain rechargement réussi l'efface.
  L'aperçu n'écrit rien, pas même l'erreur.
- **Types.** Une source `ICS_URL` ne se recharge pas par fichier, ni une `ICS_FILE` par URL (400).

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
`ManageTenantCalendarUseCase`, `JdbcTenantCalendar`, changesets 047 et 048, `PinnedHttpClient`, `HttpsIcsFetcher`,
`TenantCalendarController`, `TypeSettingsArtifacts`
et `CalendarDoc` (promotion), `openapi/openapi.yaml`, écran Admin → Types → Calendrier, spec 04, guide utilisateur.
