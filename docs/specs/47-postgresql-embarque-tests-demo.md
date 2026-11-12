# PostgreSQL embarqué (zonky) — tests et démos/QA

> **Statut : livré** (TODO §91). Remplace la décision de test **D-TESTCONTAINERS-EXEC** et la partie
> PostgreSQL de **D-TESTCONTAINERS** (feature `fiabilisation-tests-plateforme`). Docs SDD :
> [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-10-02-feature-zonky-embedded-postgres.md),
> [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-02-feature-zonky-embedded-postgres.md),
> [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-10-02-feature-zonky-embedded-postgres.md).

## Décision

| Sujet | Choix |
|---|---|
| Moteur de test | PostgreSQL **réel et embarqué** (`io.zonky.test:embedded-postgres`), version majeure **17** alignée sur docker-compose. H2 et son mode `PostgreSQL` sont supprimés. |
| Isolation | Une instance par JVM de test, **une base par contexte Spring**, initialisée par `docker/postgres/init/01-init-tenant-schema.sql` (schéma `tenant_dev` + rôle applicatif non-superuser). |
| Exécution | Sans Docker. `initdb` refuse root : utilisateur non-root, ou `MYRMACTA_TEST_PG_URL` pour un PostgreSQL externe. |
| `*IT` | Inclus dans `./mvnw verify` (ils n'y étaient pas : pas de Failsafe). |
| Changelogs | Plus aucune branche `dbms: h2` ; checksums PostgreSQL inchangés, gardés par `ChangelogChecksumStabilityIT`. |
| Démo/QA | Profil Spring `embedded-db` + profil Maven `embedded-db` : base **persistante** (`APP_EMBEDDED_DB_DATA_DIR`), absente du jar de production, interdite avec un profil `prod`/`production` ou `APP_ENVIRONMENT=production` (défaut). |

## Démo par paquets (profil `demo`, TODO §127)

Sur la base embarquée, le profil Spring `demo` installe une démo complète, pour les présentations et les captures du
site ([§127](../decisions/127-demo-par-paquets-et-captures-du-site.md), issue #55) :

- **Paquets** (`DemoPack`) : chacun installe un exemple, ou un jeu d'états, par les vrais cas d'usage. `DemoSeeder`
  les installe dans l'ordre (`order()`, unique), au démarrage de l'application, et passe ceux que `installed()` dit
  présents. Un paquet en échec arrête la démo.
- **Paquets livrés :**
  - mail room (`MailRoomDemoPack`, ordre 10) ; le profil `demo-mailroom` n'active que lui (§113) ;
  - onboarding collaborateur (`OnboardingDemoPack`, ordre 20) : connecteurs sur les faux SIRH, IAM et ITSM de
    `DemoFakeApis` (D-DEMO-FAKE-APIS, serveur HTTP du JDK sur la boucle locale, port `app.demo.fake-apis.port`, 18090
    par défaut, fixe pour que les connecteurs installés restent valables au redémarrage) ; trois arrivées semées par le
    vrai processus (provisionnée, en attente du manager, en attente de l'IT). Le paquet attend que ses processus
    atteignent la tâche attendue (délai de 120 s) : en démo réelle, l'exécuteur asynchrone de Flowable mène les jobs ;
    quand cet exécuteur est coupé (tests), le paquet les exécute lui-même et relance aussitôt un job en attente de relance ;
  - « états » (`StatesDemoPack`, ordre 40) : objectifs SLA (FACTURE 72 h, COURRIER 24 h) ; onze factures sur quatre
    semaines, menées par les cas d'usage puis antidatées par `DossierRepository` (D-DEMO-BACKDATE) : traitées dans le
    délai, en retard, ouvertes dans le délai et au SLA dépassé ; une arrivée « Nadia Claes » dont l'appel au SIRH tombe
    sur une coupure (adresse en `.invalid`) et finit en incident rejouable ; un brouillon MCP de la table
    `routageCourrier` (une règle « imprimerie » de plus), en attente de publication.
- **Langue** (D-DEMO-LOCALE, D-DEMO-LOCALE-FILES) : `app.demo.locale` (`APP_DEMO_LOCALE`), `fr` ou `en`, une langue
  par démarrage. `DemoExample` lit un exemple dans cette langue : en anglais, la copie JSON traduite `en/<chemin>` si
  elle existe, et les BPMN et DMN dont les attributs `name` et `label` et les éléments `description` passent par
  `en/names.json` (texte brut, échappé pour le XML) ; le `name` d'un élément qui le lit comme un code (message, signal,
  sortie ou donnée d'entrée DMN, élément `flowable:…`) n'est jamais traduit ; les textes écrits dans les paquets ont leurs deux langues. `DemoTranslationsTest` (suite normale)
  garde les copies alignées sur le français : mêmes clés, mêmes codes, mêmes expressions `${…}`, et une traduction
  pour chaque nom des BPMN et DMN, et aucune pour un code.
- **Compte de démo** (D-DEMO-MEMBERS) : `DemoGroupMembership` ajoute, à sa connexion, le compte `demo` à tous les
  groupes (après l'avoir inscrit dans l'annuaire), pour que ses tâches de groupe s'affichent.
- **Captures du site** (D-DEMO-CAPTURE) : `frontend/playwright.capture.config.ts` et `frontend/capture/`
  (`npm run capture`, `CAPTURE_LANG=fr|en`) pilotent une démo démarrée par `scripts/demo.sh` : connexion Keycloak
  mémorisée, puis 14 screenshots (S1 à S15 sans S13) dans `frontend/capture/out/<langue>/`, non versionnés. Procédure :
  [GETTING-STARTED](../guides/GETTING-STARTED.md), « Captures du site ».
- **Capsule vidéo « Digital Mail Room »** (§128, D-VIDEO-MAIL, D-VIDEO-DEADLINE) : profil `demo-video-mailroom`. La
  mail room s'installe sans ses trois courriers d'exemple ; `VideoMailRoomDemoPack` (ordre 30) crée la boîte IMAP de
  l'exemple pointée sur le GreenMail local (`localhost:3143`, sans chiffrement : D-IMAP-LOCAL-LOOPBACK), inscrit l'agent
  IA de la capsule (`app.demo.video.agent-subject`, défaut `agent-ia-tri`) dans l'annuaire et la file de tri (COURRIER),
  et fait arriver deux factures du fournisseur connu dont l'échéance tombe dans 3 jours et avant-hier : le vrai job des
  échéances envoie le rappel et escalade la seconde vers DIRECTION_FINANCIERE. Les e-mails du tournage partent en SMTP
  vers GreenMail (`scripts/video/send_mail.py`) et suivent le vrai chemin ; ceux que Myrmacta envoie vont à Mailpit.
  L'agent IA (D-VIDEO-AGENT, `scripts/video/agent.py`) relie un modèle local (Ollama, `gemma4:12b`) au serveur MCP M2M
  avec un jeton de développement (HS256, `M2M_JWT_SECRET` ; sujet de l'agent, rôle `API_SERVICE`, tenant) et cinq outils
  seulement (`list_my_tasks`, `get_mail_thread`, `search_dossiers`, `attach_mail_to_dossier`, `complete_task`) ; un même
  appel rejoué plus de deux fois n'est pas refait. Son journal (lignes JSON) s'affiche dans
  `frontend/capture/panel/agent-panel.html`.
  Commandes : `scripts/video.sh build | mail-servers | start | mail <scénario> | reset`.
- **Code :** sous `src/embedded` seulement, donc absent du jar de production (`scripts/check-no-embedded-db-in-jar.sh`
  refuse `infrastructure/demo/`).
- **Commandes :** `scripts/demo.sh build | start | reset`.
- **Tests :** `DemoSeederTest` (ordre, paquet déjà installé, échec), `DemoSeederIT` (profil `demo` sur base vide, puis
  redémarrage sans réinstallation), `MailRoomDemoSeederIT` (contenu du paquet mail room), `DemoFakeApisTest` et `OnboardingDemoPackIT` (paquet onboarding), `StatesDemoPackIT` (paquet « états »), `DemoExampleTest` et `DemoLocaleIT` (démo en anglais), `DemoGroupMembershipTest`,
  `VideoMailRoomDemoPackIT` (scène de la capsule : pas de courriers d'exemple, boîte IMAP relevée par le vrai connecteur
  sur un GreenMail, agent dans la file de tri, rappel et escalade des factures semées).

## Pourquoi pas Supabase / un PostgreSQL managé

Supabase est PostgreSQL + services (Auth, PostgREST, Realtime, Storage) ; seule la base serait utile et
Myrmacta garde Keycloak (BFF), un domaine Java hexagonal et un stockage S3 abstrait. Pour la CI, une base
distante partagée n'est ni hermétique ni parallélisable, et l'absence de superuser / le pooler
(`SET search_path` par connexion) s'écartent du modèle réel de production (rôle et schéma par tenant).

## Limites assumées

- Le mode embarqué écoute 127.0.0.1 en authentification `trust` (zonky) : démos/QA sur machine ou
  conteneur non partagé uniquement, jamais en production.
- Un seul tenant (`tenant_dev`) en mode embarqué.
- Changer la version majeure des binaires exige une migration des données (`pg_dump`/restauration) :
  le lanceur refuse un cluster d'une autre version.
