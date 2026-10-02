---
phase: testing
title: PostgreSQL embarqué (zonky) — tests
description: Résultats avant/après
status: done
created: 2026-10-02
updated: 2026-10-02
feature: zonky-embedded-postgres
---

# Testing — `zonky-embedded-postgres`

| Mesure | Avant (H2, hors ITs) | Après (PostgreSQL embarqué) |
|---|---|---|
| Tests exécutés par `./mvnw verify -Dskip.frontend=true` | 2653 | **2747** (+87 `*IT`, +1 garde-fou checksums, +6 tests du post-processeur `embedded-db`) |
| Échecs / erreurs | 0 / 1 (environnement : pas de build frontend) | **0 / 0** (index.html factice dans `target/classes/static`, le build npm n'étant pas possible dans ce bac à sable) |
| Ignorés | 3 | 5 : 3 `SignatureBrowserE2eTest` (`-Dsignature.e2e=true`), 2 `ClamAvAttachmentScanIT` (conteneur ClamAV) — **0 faute de PostgreSQL/Docker** |
| Jacoco (LINE 85 % / BRANCH 75 %) | tenu | tenu |
| Durée totale | 4 min 07 s (téléchargements inclus) | 4 min 02 s (ITs inclus, dépendances en cache) |

Commandes :

```bash
runuser -u ubuntu -- ./mvnw verify                                   # sous root : utilisateur non-root requis
./mvnw -Pembedded-db test -Dtest='ZonkyEmbeddedDbLauncherTest,EmbeddedDbProfileRestartIT'
scripts/check-no-embedded-db-in-jar.sh                               # après ./mvnw package (profil standard)
```

Non vérifié ici : `SignatureBrowserE2eTest` (Playwright, build frontend indisponible), Vitest/Playwright
du frontend (inchangés), construction de l'image Docker de démo.
