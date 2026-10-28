# §110 — Runner GitHub Actions auto-hébergé

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-10-28) : image [`docker/ci-runner`](../../docker/ci-runner/compose.yml), variable de dépôt `CI_RUNNER`
lue par les trois workflows. Issue #219. Mode d'emploi : [GETTING-STARTED §19](../guides/GETTING-STARTED.md#19-ci-sur-un-runner-auto-hébergé-optionnel).

**Constat.** Le compte n'a plus de minutes GitHub Actions : chaque job est refusé en 2 à 3 secondes, sans journal,
`main` compris. La CI était rejouée à la main, job par job, et des PR ont été fusionnées sans contrôle automatique.

**Décision (utilisateur, 2026-10-28).** Un runner auto-hébergé, sur une machine de l'utilisateur, exécute les
workflows existants sans les réécrire.

- **Conteneur Docker, pas d'installation sur l'hôte.** L'image (Ubuntu 24.04, comme `ubuntu-latest`) ne porte que
  les dépendances système. Le runner est téléchargé au premier démarrage dans le volume `/home/runner` et s'y met à
  jour seul : GitHub refuse un runner trop ancien, une version figée dans l'image vieillirait. JDK et Node viennent de
  `setup-java` et `setup-node`, les bibliothèques de Chromium de `npx playwright install --with-deps` : les workflows
  restent identiques à ceux d'un runner GitHub.
- **Utilisateur `runner` non-root** (initdb du PostgreSQL embarqué refuse root, §91), avec `sudo` sans mot de passe
  limité au conteneur, comme sur les runners GitHub.
- **Bascule par variable de dépôt.** `runs-on: ${{ vars.CI_RUNNER || 'ubuntu-latest' }}` : la variable
  `CI_RUNNER=myrmacta-ci` envoie les jobs sur le runner ; la supprimer les renvoie sur GitHub quand les minutes
  reviennent, sans commit.
- **Isolation.** Pas de socket Docker monté (un job piloterait l'hôte) ; seul le volume du runner est écrit. Les
  workflows n'utilisent aucun secret (`permissions: contents: read`). Le dépôt est privé : seuls ses collaborateurs
  et les agents qui y poussent (Claude Code, Cursor, Jules) font tourner du code sur la machine.
- **Écarté :** `act` (rejeu local dans Docker, couverture incomplète des actions, issue #219) ; un runner éphémère
  réenregistré à chaque job (il faudrait un jeton d'administration du dépôt en permanence sur la machine).

**Limites connues.**

- Un seul runner : les jobs d'un même workflow passent l'un après l'autre. Runner absent : les jobs restent en file
  d'attente, ils n'échouent pas.
- `ClamAvAttachmentScanIT` s'ignore sans démon Docker, donc sur ce runner.
- Le cache `actions/cache` (scan OWASP) et les artefacts de `mutation.yml` passent toujours par le stockage GitHub du
  compte.

**Impacte :** `.github/workflows/ci.yml`, `.github/workflows/mutation.yml`, `.github/workflows/dependency-check.yml`,
`docker/ci-runner/`, `docs/guides/GETTING-STARTED.md` §19.
