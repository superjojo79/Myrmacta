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
    vrai processus (provisionnée, en attente du manager, en attente de l'IT). Le paquet exécute lui-même les jobs
    asynchrones de ses processus jusqu'à la tâche attendue, en relançant aussitôt un job en attente de relance ;
  - « états » (`StatesDemoPack`, ordre 40) : objectifs SLA (FACTURE 72 h, COURRIER 24 h) ; onze factures sur quatre
    semaines, menées par les cas d'usage puis antidatées par `DossierRepository` (D-DEMO-BACKDATE) : traitées dans le
    délai, en retard, ouvertes dans le délai et au SLA dépassé ; une arrivée « Nadia Claes » dont l'appel au SIRH tombe
    sur une coupure (adresse en `.invalid`) et finit en incident rejouable ; un brouillon MCP de la table
    `routageCourrier` (une règle « imprimerie » de plus), en attente de publication.
- **Code :** sous `src/embedded` seulement, donc absent du jar de production (`scripts/check-no-embedded-db-in-jar.sh`
  refuse `infrastructure/demo/`).
- **Commandes :** `scripts/demo.sh build | start | reset`.
- **Tests :** `DemoSeederTest` (ordre, paquet déjà installé, échec), `DemoSeederIT` (profil `demo` sur base vide, puis
  redémarrage sans réinstallation), `MailRoomDemoSeederIT` (contenu du paquet mail room), `DemoFakeApisTest` et `OnboardingDemoPackIT` (paquet onboarding), `StatesDemoPackIT` (paquet « états »).

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
