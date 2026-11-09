# Calendrier du tenant — durcissements de gravité basse (#34) — document de travail

Issue : #34 · Spec du domaine : docs/specs/04-dossiers.md · Points du registre : §121, §124

## Cadrage

- Problème et utilisateurs : la revue indépendante de #33 a laissé dix constats de gravité basse (coût de `toCalendar`, multipart trop large avant contrôle, en-têtes HTTP non bornés, FK incomplete, `RANGE=THISANDFUTURE`, journaux HTTP/URL, URL indéchiffrable, couverture du délai TLS, note migration 047). Les administrateurs tenant et l'exploitation sont concernés ; les minuteurs d'échéance et le routage courrier paient le coût de lecture.
- Périmètre / hors-périmètre : les dix points de #34 et du commentaire de seconde revue. Hors périmètre : rechargement planifié, auth sur URL, événements horaires, recalcul d'échéances déjà posées (hors §121).
- Critères de succès (vérifiables) : voir §124 ; tous couverts.
- Contraintes : stack figée, JPA seulement (§122), une instance = un tenant, pas de nouvelle dépendance d'exécution, TDD, doc guides.
- Validé par l'utilisateur le : mandat « Traite l'issue 34 » (2026-11-09) — décisions tranchées et tracées au §124.

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| `holidays` API encore expansé ? | (a) garder (b) vider (c) plafonner | (b) champ déprécié, SPA n'utilise pas | tranchée → §124 |
| Cache `get` | (a) par `updated_at` (b) intervalles dans `BusinessCalendar` | (a) pour #34 ; (b) reporté | tranchée → §124 |
| DEBUG `org.apache.hc` | (a) doc seule (b) test de config | les deux | tranchée → §124 |

## Conception

- Faits vérifiés : `JpaTenantCalendarRepositoryAdapter.get` ; `ManageTenantCalendarUseCaseImpl` ; `PinnedHttpClient` ; FK 047 ; `overriddenOccurrences` ; `findSourceUrl` ; filtres Content-Length existants.
- Composants touchés : domaine, use case, adaptateur JPA, changeset 050, `PinnedHttpClient`, filtre taille, parser ICS, `SecurityConfig`, `application.yml`, DEPLOIEMENT, spec 04, OpenAPI, tests Java.
- Scénarios de test : livrés (cache IT, URL purge, THISANDFUTURE, headers, TLS, logging, filter, API holidays vide).

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1–3 | Ensemble des durcissements #34 (§124) | livré | (cette PR) |

Prochaine action : revue de clôture indépendante, puis suppression de ce document.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-09 | `scripts/check-docs.sh` | ok (liens, skills, todo-refs, decision-ids, versions, decisions-index) |
| 2026-11-09 | `./mvnw verify -Dskip.frontend=true -Dmaven.compiler.release=21` | BUILD SUCCESS (Jacoco ok) |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
