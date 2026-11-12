# §135 — Reprise d'un paquet de démo interrompu, faux services bornés

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#71](https://github.com/superjojo79/Myrmacta/issues/71), vague `vague-vitrine`). Mandat cloud agent
2026-11-12. Build `embedded-db` seulement, jamais en production. Comportement :
[spec 47](../specs/47-postgresql-embarque-tests-demo.md) § « Démo par paquets ».

**Constat.** `installed()` regardait le premier effet du paquet (SLA de FACTURE, type ONBOARDING). Un `install()`
interrompu plus loin — l'incident SIRH qui dépasse les 120 s, le brouillon MCP qui échoue — laissait l'effet en base,
et le redémarrage sautait le paquet. Les faux SIRH, IAM et ITSM lisaient le corps avec `readAllBytes()` et
empilaient chaque requête sans plafond.

**D-DEMO-INSTALL** — un paquet n'est installé que lorsque son **dernier** effet est présent, et `install()` peut
être rejoué sans dupliquer ce qui est déjà là :

1. « états » : le brouillon `routageCourrier`. Les factures déjà créées (même libellé) et l'incident Nadia déjà en
   dead letter ne sont pas refaits ;
2. onboarding : une tâche `validationAcces` **de Sarah** (Élise passe par la même tâche plus tôt et ne compte pas).
   Le type, les questionnaires, la configuration et le processus déjà déployés ne sont pas recréés ; une arrivée dont
   le dossier existe n'est pas recréée. Si le dossier existe mais qu'aucun processus n'a démarré (échec après la
   création, avant `start`), le processus est démarré sur ce dossier. Un processus déjà terminé (historique) ou déjà
   en incident n'est pas relancé. Le brouillon `routageCourrier` n'est proposé que si l'incident Nadia existe.

Élise ou Thomas laissés à mi-chemin ne sont pas repris étape par étape : leur dossier existe, on ne les duplique pas.
Écarté : un marqueur dans une table nouvelle (schéma de production pour un outil de démo) ; se fier au premier effet.

**D-DEMO-FAKE-BOUNDS** — le serveur de la boucle locale lit au plus 64 Kio de corps (`readNBytes`) et ne retient que
les 256 dernières requêtes. L'écoute reste `InetAddress.getLoopbackAddress()`.

**Impacte :** `StatesDemoPack`, `OnboardingDemoPack`, `DemoFakeApis`, spec 47.
