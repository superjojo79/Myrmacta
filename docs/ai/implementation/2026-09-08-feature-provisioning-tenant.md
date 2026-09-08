---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `provisioning-tenant` (lot A1)

## Development Setup
**How do we get started?**

- Prérequis : `docker` (pour `docker build`/`docker run` — non exécutable dans ce bac à sable, voir
  « Écarts et limites de validation » ci-dessous), PostgreSQL accessible pour tester
  `scripts/provision-tenant.sh --apply`, `./mvnw` (wrapper déjà présent).
- Aucune nouvelle dépendance projet (pas de nouvelle librairie Java/npm).
- Fichiers livrés par ce lot : `Dockerfile`, `.dockerignore`, `scripts/provision-tenant.sh`,
  `src/main/resources/application.yml` (context-path fusionné dans le bloc `server:` existant),
  `src/main/resources/application-local.yml` (neutralisation context-path).

## Code Structure
**How is the code organized?**

- `Dockerfile` (racine) : build multi-stage, stage `build` (compilation) puis `runtime` (image
  finale).
- `.dockerignore` (racine) : exclusions du contexte de build.
- `scripts/provision-tenant.sh` : script shell autonome, cohérent avec `scripts/setup-mcp.sh` et
  `scripts/vendor-ai-devkit-skills.sh` déjà présents.
- Pas de nouveau package Java (`domain`/`application`/`infrastructure` inchangés) — ce lot est
  purement ops/config, aucun impact sur l'architecture hexagonale.

## Implementation Notes
**Key technical details to remember:**

### Dockerfile
- Stage `build` : `eclipse-temurin:25-jdk`, copie `.mvn/`/`mvnw`/`pom.xml` avant `src/`/`frontend/`
  pour profiter du cache Docker sur les rebuilds qui ne changent que le code. `./mvnw -B clean
  package -DskipTests` — les tests tournent en CI/`./mvnw verify`, pas dans le build d'image.
- Stage `runtime` : `eclipse-temurin:25-jre`, utilisateur système dédié `flowforge` (UID/GID
  10001, `--no-create-home`, shell `/usr/sbin/nologin`), jar copié avec `--chown`.
  `ENTRYPOINT` en forme shell (`sh -c "exec java ... $JAVA_OPTS -jar app.jar"`) pour permettre un
  override des options JVM sans reconstruire l'image.
- `-XX:MaxRAMPercentage=75.0` : le heap suit la mémoire allouée au conteneur plutôt qu'un `-Xmx`
  fixe (décision Phase 1, requirements).
- `EXPOSE 8080` uniquement (port applicatif) — le port management (actuator, D-42) reste
  volontairement absent de l'image, bind `127.0.0.1` par défaut.

### Script de provisioning
- Validation de l'identifiant de tenant **avant toute génération SQL** (regex `^[a-z][a-z0-9_]
  {2,52}$`, 3 à 53 caractères) — seule entrée utilisateur interpolée dans le SQL généré (hors mot
  de passe, échappé par doublement du guillemet simple). Borne à 53 et non 63 (`NAMEDATALEN`) : le
  rôle généré préfixe `flowforge_` (10 caractères), et `flowforge_<tenant_id>` doit lui-même rester
  ≤ 63 octets — un `tenant_id` de 63 caractères aurait produit un nom de rôle tronqué
  silencieusement par PostgreSQL, avec un risque de collision entre deux tenants longs. **Trouvé et
  corrigé pendant la revue Phase 7** (Check Implementation), pas détecté par `hadolint`/`shellcheck`
  (angle mort logique, pas syntaxique).
- `TENANT_DB_PASSWORD` requis dans tous les modes (dry-run inclus) : le SQL produit contient
  toujours `CREATE ROLE ... PASSWORD`, il n'a pas de sens sans mot de passe, même pour une simple
  revue.
- `--apply` : garde anti-écrasement via une requête `information_schema.schemata` avant toute
  application — échec explicite si le schéma existe déjà, jamais de `DROP`/écrasement implicite.
- SQL généré strictement calqué sur `docker/postgres/init/01-init-tenant-schema.sql` (mêmes
  `GRANT`/`REVOKE`), avec le nom de schéma/rôle substitué.

### Context-path
- `server.servlet.context-path: /${app.tenant.id}` vit dans le bloc `server:` **déjà existant**
  d'`application.yml` (pas un second bloc top-level — YAML n'autorise pas les clés dupliquées,
  contrairement aux fichiers `.properties`).
- `application-local.yml` neutralise à `context-path: ""` pour préserver le parcours dev local
  (racine `/`) — pattern déjà en place pour `app.security.cookie.secure`.
- Le profil `test` (`src/test/resources/application.properties`) n'a **pas** cette neutralisation :
  le context-path reste actif pendant `./mvnw verify`, ce qui exerce réellement le mécanisme.

### Patterns & Best Practices
- Réutilisation stricte du gabarit SQL existant plutôt qu'une réécriture (script = paramétrage, pas
  une nouvelle implémentation du modèle de permissions).
- Réutilisation du pattern `application-local.yml` déjà en place dans le dépôt pour toute
  neutralisation de propriété par défaut en profil local.

## Integration Points
**How do pieces connect?**

- Le script de provisioning produit le même SQL que celui déjà rejoué manuellement dans
  `DEPLOIEMENT.md` §4 (avant ce lot) — pas de nouveau mécanisme d'authentification/permissions,
  juste l'automatisation d'une procédure déjà décidée (D-41).
- Le `Dockerfile` produit un artefact fonctionnellement identique à `java -jar
  target/flowforge-*.jar` (déjà documenté dans `DEPLOIEMENT.md` §1), pas un nouveau mode de
  démarrage de l'application.
- `context-path` s'intègre au routage reverse-proxy déjà documenté (décision n°14,
  `analysis doc/02-multi-tenant.md`) — aucun changement du comportement applicatif au-delà du
  préfixe d'URL.

## Error Handling
**How do we handle failures?**

- `scripts/provision-tenant.sh` : `set -euo pipefail`, tous les cas d'erreur (identifiant invalide,
  mot de passe absent, connexion PostgreSQL échouée, schéma déjà existant) sortent avec un code
  non-zéro et un message explicite sur stderr, avant toute modification destructive.
- Le `Dockerfile` échoue le build si `./mvnw package` échoue (comportement Docker standard, aucune
  logique supplémentaire nécessaire).

## Performance Considerations
**How do we keep it fast?**

- Ordre des couches Docker (`.mvn`/`mvnw`/`pom.xml` avant `src`/`frontend`) pour maximiser la
  réutilisation du cache Docker sur les rebuilds incrémentaux.
- `-XX:MaxRAMPercentage=75.0` : pas de tuning plus fin demandé par ce lot (cf. testing doc,
  performance hors scope).

## Security Notes
**What security measures are in place?**

- Utilisateur non-root dans l'image finale.
- Aucun secret en dur : mot de passe du rôle PostgreSQL uniquement via `TENANT_DB_PASSWORD` (jamais
  un argument positionnel visible dans `ps`/l'historique shell), jamais de valeur par défaut
  devinable.
- Validation stricte de l'identifiant de tenant avant toute interpolation SQL — pas d'injection SQL
  possible via un identifiant malformé (rejeté par regex avant que la fonction `generate_sql` ne
  soit même appelée).
- Isolation cross-tenant **vérifiée avec une vraie base PostgreSQL** pendant ce cycle (voir
  « Écarts et limites de validation ») : un rôle de tenant ne peut pas écrire dans le schéma d'un
  autre tenant.

## Écarts et limites de validation

- **Docker Hub inaccessible dans ce bac à sable** (politique réseau de la session, host
  `production.cloudfront.docker.com` explicitement rejeté — constaté systémique, pas propre à une
  image). Le `Dockerfile` n'a donc **pas** été validé par un `docker build`/`docker run` réel dans
  ce cycle. Validation de repli obtenue : `hadolint` (exit 0, aucun avertissement) et cohérence
  `pom.xml` (`java.version=25`, `frontend-maven-plugin` autonome). **À faire à la première occasion
  dans un environnement avec accès registre** (poste ops ou CI) avant un premier déploiement réel —
  ce n'est pas un correctif de ce lot, c'est une limite de l'environnement d'exécution de ce cycle.
- Le smoke test du script de provisioning a utilisé le service PostgreSQL 16 déjà installé
  localement dans ce bac à sable plutôt qu'un conteneur `postgres:17-alpine` jetable (même
  contrainte Docker Hub) — le SQL généré est standard (PostgreSQL 16/17 sans divergence sur les
  primitives utilisées : `CREATE SCHEMA`, `CREATE ROLE`, `GRANT`/`REVOKE`), donc ce n'est pas
  considéré comme un écart matériel, mais c'est noté pour traçabilité.
- Un bug réel a été trouvé et corrigé en cours d'implémentation : la première version de
  l'activation `context-path` ajoutait un second bloc top-level `server:` dans `application.yml`,
  ce qui casse le parsing YAML (SnakeYAML : « found duplicate key server »). Détecté par
  `./mvnw verify` lui-même (échec de démarrage de contexte sur 353 tests avant correctif), corrigé
  en fusionnant `servlet.context-path` dans le bloc `server:` déjà existant (ligne 112, qui portait
  déjà `forward-headers-strategy`/`servlet.session.cookie.*`).
- Un second bug réel a été trouvé en Phase 7 (Check Implementation, relecture ligne à ligne) : le
  rôle PostgreSQL généré préfixe `flowforge_` (10 caractères) au `tenant_id` ; avec la borne
  initiale de 63 caractères sur `tenant_id`, un identifiant proche du maximum produisait un nom de
  rôle > 63 octets (NAMEDATALEN), tronqué silencieusement par PostgreSQL — risque de collision
  entre deux tenants aux identifiants longs, jamais détecté ni par `hadolint` ni par `shellcheck`
  (angle mort logique métier, pas syntaxique). Corrigé en bornant `tenant_id` à 53 caractères ;
  vérifié aux limites (53 accepté, 54 rejeté).

## Alignement avec le design (Phase 7)

- Surface exclusive du lot respectée : `git diff --stat` depuis le début du cycle ne montre que
  `Dockerfile`, `.dockerignore`, `scripts/provision-tenant.sh`, `DEPLOIEMENT.md`,
  `GETTING-STARTED.md`, `.env.example`, `application.yml`/`application-local.yml` (context-path
  uniquement) + `docs/ai/**`. Aucun fichier de `frontend/**`, `infrastructure/mcp/**`,
  `ConnectorsAdminView.vue`, `SecurityConfig.java`, `ConnectorCatalog` ni `docker-compose*.yml`
  touché.
- Les 4 décisions actées en Phase 1 sont bien reflétées dans le code livré : image
  `eclipse-temurin:25-jre`/`-jdk` (`Dockerfile` lignes 11/28), utilisateur non-root
  (`Dockerfile` lignes 30-31/36), heap dynamique `-XX:MaxRAMPercentage=75.0` (`Dockerfile` ligne
  48), `context-path` activé (`application.yml`) et neutralisé en local
  (`application-local.yml`).
- Aucune déviation majeure par rapport au design doc — seul écart : le smoke test PostgreSQL a
  utilisé le service local du bac à sable plutôt qu'un conteneur jetable (contrainte
  d'environnement, pas un choix de conception), déjà documenté ci-dessus.
