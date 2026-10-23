---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `provisioning-tenant` (lot A1)

## Problem Statement
**What problem are we solving?**

- Le modèle multi-tenant cible (une instance Spring Boot dédiée par tenant, routage par chemin,
  isolation PostgreSQL par schéma + rôle LOGIN dédié) est **décidé** (décisions n°13-14,
  [docs/specs/02-multi-tenant.md](../../specs/02-multi-tenant.md) ; D-41,
  [TODO.md](../../../TODO.md) point 41) mais son **outillage** ne l'est pas :
  - aucun `Dockerfile` n'existe dans le dépôt ;
  - le provisioning d'un nouveau tenant (schéma + rôle PG, instance Boot, bloc reverse-proxy) est
    une procédure **manuelle**, documentée pas à pas dans
    [DEPLOIEMENT.md](../../../DEPLOIEMENT.md) §4, jamais outillée par un script.
- Affecté : quiconque déploie FlowForge hors poste de dev (ops / le directeur IT qui provisionne un
  nouveau client-tenant). Aujourd'hui, provisionner un tenant = recopier à la main des instructions
  SQL/YAML/Apache depuis un document Markdown, sans validation ni reproductibilité.
- Contournement actuel : suivre [DEPLOIEMENT.md](../../../DEPLOIEMENT.md) §1-§4 manuellement,
  bâtir le jar avec `./mvnw clean package`, l'exécuter avec `java -jar`, copier/adapter le SQL de
  `docker/postgres/init/01-init-tenant-schema.sql` à la main.

## Goals & Objectives
**What do we want to achieve?**

Primary goals :

- Fournir un `Dockerfile` construisant une image exécutable du jar Spring Boot (build multi-stage),
  avec les décisions techniques actées ci-dessous (image de base, utilisateur, heap).
- Fournir un script de provisioning (`scripts/`) qui génère le SQL de création du schéma + rôle
  PostgreSQL dédié pour un nouveau tenant (gabarit = `docker/postgres/init/01-init-tenant-schema.sql`,
  paramétré par un identifiant de tenant), en cohérence avec D-41 (option A, un rôle LOGIN par
  tenant).
- Activer réellement `server.servlet.context-path: /${app.tenant.id}` dans `application.yml`
  (actuellement commenté) et vérifier/adapter ce qui en dépend (tests existants, doc), pour que le
  routage par chemin documenté en décision n°14 soit un mécanisme éprouvé et non plus seulement
  décrit.
- Mettre à jour [DEPLOIEMENT.md](../../../DEPLOIEMENT.md), [GETTING-STARTED.md](../../../GETTING-STARTED.md),
  `docker-compose*.yml` et `.env.example` pour refléter le nouvel outillage (remplacer le runbook
  manuel par les commandes du nouvel outillage, garder le detail SQL comme référence si utile).

Secondary goals :

- Rendre le provisioning idempotent/rejouable sans casser un tenant existant (le script ne doit pas
  écraser silencieusement un schéma déjà provisionné).
- Documenter les décisions Dockerfile actées (image de base, non-root, heap) dans
  [DEPLOIEMENT.md](../../../DEPLOIEMENT.md) pour qu'elles ne soient plus des points ouverts.

Non-goals (hors scope de ce lot) :

- Toute automatisation d'infrastructure au-delà d'un script shell (pas de Terraform/Ansible/Helm —
  hors stack figée du projet, non demandé par FILE-ATTENTE.md).
- Provisioning Keycloak (réalm, clients OIDC/M2M) — déjà couvert par [DEPLOIEMENT.md](../../../DEPLOIEMENT.md)
  §5/§7, reste une procédure manuelle, hors surface de ce lot.
- Sauvegarde/restauration PostgreSQL, montée de version — explicitement hors du guide
  ([DEPLOIEMENT.md](../../../DEPLOIEMENT.md) « Ce qui n'est pas (encore) couvert »), reste un point
  ouvert distinct.
- Tout ce qui touche `frontend/**`, `infrastructure/mcp/**`, `ConnectorsAdminView.vue`,
  `SecurityConfig.java`, `ConnectorCatalog` — surfaces exclusives des lots A2/A3/A4 exécutés en
  parallèle sur d'autres branches.
- Publication de l'image Docker sur un registre, CI de build d'image — pas demandé par le lot,
  pas de registre configuré dans ce projet à ce jour.
- Adaptateur antivirus ClamAV / Compose associé — c'est le lot **Vague C**, qui attend explicitement
  que A1 reste propriétaire de Compose avant de démarrer.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu'opérateur ops, je veux `docker build` un artefact FlowForge prêt à déployer, sans avoir
  à écrire moi-même un Dockerfile ni trancher l'image de base/l'utilisateur/le heap.
- En tant qu'opérateur ops, je veux exécuter un script qui me donne (ou applique) le SQL de
  provisioning d'un nouveau tenant à partir d'un seul identifiant, plutôt que de copier-coller et
  adapter à la main un fichier `.sql` d'exemple.
- En tant qu'opérateur ops, je veux que [DEPLOIEMENT.md](../../../DEPLOIEMENT.md) me donne la
  séquence de commandes réelle (build image, run script provisioning, démarrer l'instance avec
  `context-path` actif, ajouter le bloc reverse-proxy) plutôt qu'un runbook purement manuel.
- Edge cases à couvrir :
  - Rejouer le script de provisioning sur un tenant déjà provisionné → échec explicite, pas d'écrasement.
  - Identifiant de tenant invalide (caractères non conformes à un nom de schéma PostgreSQL) → rejeté
    avant toute exécution SQL.
  - `context-path` actif : vérifier que les endpoints existants (REST, webhooks, actuator) restent
    accessibles et que la suite de tests ne suppose pas un déploiement à la racine `/`.

## Success Criteria
**How will we know when we're done?**

- `Dockerfile` présent à la racine, construit avec succès (`docker build .`) si Docker est
  disponible dans l'environnement d'exécution ; sinon, revue manuelle du contenu + justification
  documentée si le smoke build n'a pas pu être exécuté.
- Nouveau script de provisioning dans `scripts/`, testé manuellement contre le SQL attendu (dry-run
  ou génération de fichier), cohérent avec `docker/postgres/init/01-init-tenant-schema.sql` et D-41.
- `server.servlet.context-path` décommenté dans `application.yml` ; `./mvnw verify` (scope backend)
  vert avec ce changement actif.
- [DEPLOIEMENT.md](../../../DEPLOIEMENT.md), [GETTING-STARTED.md](../../../GETTING-STARTED.md),
  `.env.example`, `docker-compose*.yml` mis à jour et cohérents avec le nouvel outillage ; plus
  aucune mention de « aucun Dockerfile/CLI de provisioning n'existe » dans ces documents pour ce qui
  est désormais livré.
- `TODO.md` point 31 mis à jour (outillage provisioning livré, plus seulement « partiel »).
- `CHANGELOG.md` mis à jour (entrée « Non publié ») une fois une PR ouverte, par convention du
  projet.

## Constraints & Assumptions
**What limitations do we need to work within?**

Constraints :

- Stack figée : Java 25 / Spring Boot 4.1.x / Maven mono-module ; pas de nouvelle dépendance
  d'infrastructure hors stack (`flowforge-conventions`).
- Surface exclusive de ce lot (à respecter strictement, cf. FILE-ATTENTE.md Vague A) :
  `Dockerfile` (nouveau), `scripts/` provisioning (nouveau), `DEPLOIEMENT.md`, `GETTING-STARTED.md`,
  `docker-compose*.yml`, `.env.example` ; `application.yml` uniquement pour `context-path`/tenant.
  **Interdit de toucher** : `frontend/**`, `infrastructure/mcp/**`, `ConnectorsAdminView.vue`,
  `SecurityConfig.java`, `ConnectorCatalog`.
- Ce lot tourne en parallèle des lots A2 (`admin-connector-form-schema`) et A3
  (`mcp-ecritures-metier`) sur des branches distinctes — ne pas supposer leur état.
- Décision d'isolation PostgreSQL déjà tranchée par TODO §41 (option A, rôle LOGIN par tenant) —
  ce lot l'outille, ne la rouvre pas.
- `docker-compose*.yml` est déjà utilisé par le dev local (Postgres + Keycloak) ; toute modification
  doit rester rétrocompatible avec `GETTING-STARTED.md` (parcours dev local inchangé par défaut).

Décisions actées lors de la clarification de cette phase (via `AskUserQuestion`, réponses
utilisateur du 2026-09-08) :

| Décision | Choix retenu |
|---|---|
| Image de base Docker | `eclipse-temurin:25-jre` (build multi-stage : `eclipse-temurin:25-jdk` pour `./mvnw package`, puis copie du jar dans l'image JRE) |
| Utilisateur du conteneur | Non-root dédié (UID/GID fixes), `USER` avant `ENTRYPOINT`/`CMD` |
| Heap JVM par défaut | Dynamique, `-XX:MaxRAMPercentage` (pas de `-Xmx` fixe) — s'adapte à la limite mémoire du conteneur fixée au provisioning |
| `server.servlet.context-path` | Activé réellement dans ce lot (décommenté + tests vérifiés/adaptés), pas seulement documenté |

Assumptions (à défaut de contre-indication) :

- Le script de provisioning est un script shell (`bash`), cohérent avec les scripts déjà présents
  dans `scripts/` (`setup-mcp.sh`, `vendor-ai-devkit-skills.sh`) — pas de CLI compilée/empaquetée
  séparément, pas de nouvel outil hors stack.
- Le script de provisioning cible PostgreSQL (schéma + rôle) ; la partie « démarrer l'instance
  Boot » et « ajouter le bloc reverse-proxy » restent des étapes documentées (commande / gabarit de
  config) plutôt qu'automatisées de bout en bout — automatiser le lancement d'un process applicatif
  ou la configuration d'un reverse-proxy externe dépasse la portée « script + Dockerfile » attendue
  du lot A1 et n'a pas été demandé explicitement.
- Pas d'environnement Docker garanti disponible dans le bac à sable d'exécution de ce cycle ; en son
  absence, la validation du `Dockerfile` se limite à une revue statique + `hadolint`/lint manuel si
  disponible, documentée comme telle plutôt que prétendre un smoke build réussi.

## Questions & Open Items
**What do we still need to clarify?**

Aucune question matérielle ouverte à ce stade — les 4 décisions bloquantes identifiées en amont
(image de base, utilisateur, heap, activation `context-path`) ont été tranchées ci-dessus. Points à
reconfirmer en Phase 2 (revue requirements) si des détails d'implémentation en Phase 3 (design) font
apparaître un nouvel arbitrage :

- Nom exact et interface du script de provisioning (`scripts/provision-tenant.sh <tenant_id>` ?
  génère un `.sql` vs l'applique directement via `psql` ?) — à trancher en Phase 3 (design), pas
  bloquant pour les requirements.
- Étendue exacte des tests impactés par l'activation de `context-path` — recensée en Phase 3/5, pas
  bloquante pour figer le scope ici.
