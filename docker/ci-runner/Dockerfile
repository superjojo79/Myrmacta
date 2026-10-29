# Runner GitHub Actions auto-hébergé pour la CI de Myrmacta (TODO §110, issue #219).
# L'image ne porte que les dépendances système. Le runner est téléchargé au premier démarrage dans le volume
# /home/runner, où il se met à jour seul ; JDK et Node viennent de setup-java et setup-node, comme sur ubuntu-latest.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    TZ=Etc/UTC

# Outils des workflows et des scripts (git, unzip, python3, jq) et dépendances du runner (.NET : ICU, Kerberos,
# OpenSSL, LTTng). Les bibliothèques de Chromium viennent de l'étape `npx playwright install --with-deps` des
# workflows, comme sur ubuntu-latest.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates curl git jq unzip zip python3 sudo tzdata \
      libicu74 libkrb5-3 liblttng-ust1t64 libssl3t64 zlib1g \
 && rm -rf /var/lib/apt/lists/*

# Utilisateur non-root : initdb (PostgreSQL embarqué zonky) refuse root. sudo sans mot de passe, comme sur les
# runners GitHub, pour `npx playwright install --with-deps` ; il ne vaut qu'à l'intérieur du conteneur.
RUN useradd --create-home --uid 1001 --shell /bin/bash runner \
 && echo 'runner ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/runner \
 && chmod 0440 /etc/sudoers.d/runner

COPY --chmod=0755 entrypoint.sh /usr/local/bin/ci-runner-entrypoint.sh

USER runner
WORKDIR /home/runner
ENTRYPOINT ["/usr/local/bin/ci-runner-entrypoint.sh"]
