# Image de déploiement FlowForge — voir DEPLOIEMENT.md §1 et
# docs/ai/design/2026-09-08-feature-provisioning-tenant.md pour le contexte des choix
# (image de base, utilisateur non-root, heap JVM).

# --- Stage 1 : build ---------------------------------------------------------
# Compile le backend et le frontend en un seul artefact Spring Boot exécutable
# (frontend-maven-plugin build le SPA pendant la phase generate-resources, comme en dev
# local). Nécessite un accès réseau sortant le temps du build (téléchargement de Maven
# par le wrapper puis de Node par frontend-maven-plugin), au même titre que
# `./mvnw clean package` en local.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Couches de dépendances séparées du code source : un rebuild qui ne change que src/ ou
# frontend/ réutilise le cache Docker de .mvn/mvnw/pom.xml.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
COPY frontend/ frontend/

RUN ./mvnw -B clean package -DskipTests

# --- Stage 2 : runtime --------------------------------------------------------
# Image minimale : JRE seule (pas de JDK, pas de sources, pas de Maven), utilisateur
# applicatif non-root dédié (UID/GID fixes).
FROM eclipse-temurin:25-jre AS runtime

RUN groupadd --system --gid 10001 flowforge \
    && useradd --system --uid 10001 --gid flowforge --no-create-home --shell /usr/sbin/nologin flowforge

WORKDIR /app
COPY --from=build --chown=flowforge:flowforge /workspace/target/flowforge-*.jar app.jar

USER flowforge

# Port applicatif par défaut (server.port). Le port management (actuator, D-42) bind
# volontairement 127.0.0.1 côté conteneur et n'est pas exposé ici — à publier
# explicitement au niveau de l'orchestrateur si un scrape externe en a besoin.
EXPOSE 8080

# JAVA_OPTS permet d'ajuster les options JVM sans reconstruire l'image. Le heap suit la
# mémoire allouée au conteneur (-XX:MaxRAMPercentage) plutôt qu'une valeur -Xmx figée à
# retrancher manuellement par tenant — décision actée en Phase 1 du cycle
# provisioning-tenant (docs/ai/requirements/2026-09-08-feature-provisioning-tenant.md).
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 $JAVA_OPTS -jar app.jar"]
