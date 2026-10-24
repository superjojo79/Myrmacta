# §103 — Double licence AGPL-3.0-only et licence commerciale

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

**Énoncé.** Le dépôt n'avait aucune licence. Le propriétaire (Benoit) veut un double licence :
AGPL pour les usages libres, licence commerciale pour les intégrateurs et les SaaS fermés qui ne
veulent pas des obligations de l'AGPL. Le produit est le Logiciel. Le go-to-market vise la
Belgique francophone, via un intégrateur partenaire. La marque n'est pas déposée.

**Options présentées :**

- **A.** AGPL-3.0-only pour le public, licence commerciale sur demande, CLA par octroi (pas
  cession), politique de marque séparée. Le titulaire garde la main sur les versions suivantes de
  l'AGPL.
- **B.** AGPL-3.0-or-later. Plus simple pour les combinaisons futures, moins de maîtrise : une
  AGPL v4 s'appliquerait seule aux destinataires qui la choisissent.
- **C.** Apache-2.0 ou MIT, et une marque pour monétiser. Écarté : ne crée pas l'obligation de
  partager le code d'un SaaS (article 13), qui est le levier du double licence.
- **D.** Cession des droits dans le CLA (modèle Harmony « assignment »). Écarté : le contributeur
  doit rester titulaire ; l'octroi suffit à relicencier.

**Décision (Benoit, 2026-10-24) : A.**

- **D-LICENSE-AGPL-ONLY** — licence publique `AGPL-3.0-only`. Le fichier `LICENSE` est le texte
  officiel GNU, non modifié (SHA-256 `0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0`,
  source `https://www.gnu.org/licenses/agpl-3.0.txt`). L'alternative or-later est écartée pour
  garder la maîtrise des versions ; elle pourra être rouverte explicitement.
- **D-LICENSE-COMMERCIAL** — licence commerciale sur demande, modèle dans `COMMERCIAL-LICENSE.md`,
  marqué « à faire valider par un juriste (droit belge) ». Contact, prix, territoire, durée et
  tribunaux ne sont pas tranchés (crochets `[à compléter]`). L'AGPL n'interdit pas l'usage
  commercial ; la licence commerciale sert à écarter, pour le seul licencié, les obligations de
  l'AGPL, en particulier l'article 13. Cet écart ne vaut que pour le code dont le concédant est
  titulaire ou qu'il tient du CLA. Les composants tiers restent sous leurs licences.
- **D-LICENSE-CLA** — CLA individuel par **octroi de licence**, pas par cession, calqué sur le
  principe de l'ICLA Apache et de Harmony (`CLA.md`). `CONTRIBUTING.md` l'exige. Pas d'automate
  GitHub Actions : le compte n'a plus de minutes. Un CLA d'entité n'est pas rédigé.
- **D-LICENSE-SPDX** — pas d'en-têtes SPDX dans les sources. Aucun en-tête n'existe aujourd'hui
  (1 946 fichiers `.java`, `.vue` et `.ts` sous `src/` et `frontend/src`). Les poser serait un
  diff massif, sans convention préalable, et entrerait en conflit avec la PR #220
  (`cursor/unicite-champ-dossier-lot-c-b02d`), qui touche les mêmes arbres. Suite proposée, après
  cette PR : en-têtes SPDX sur les fichiers nouveaux, puis balayage du stock. Le fichier
  `NOTICE` (composants tiers et endroit de l'offre de source, article 13) est livré à la racine.
  Aucun écran ne l'affiche : c'est un suivi. Les champs `pom.xml` `<licenses>` et `frontend/package.json`
  `license` portent déjà `AGPL-3.0-only`.
- **D-LICENSE-MARK** — `TRADEMARKS.md` (français et anglais) : l'AGPL ne donne aucun droit de
  marque ; usages factuels autorisés sans demande (« basé sur » suivi du Nom) ; fork ou SaaS
  portant le Nom, logo modifié et apparence de partenariat interdits sans écrit ; un fork
  distribué ou hébergé change de nom. Ce changement de nom est une politique de marque séparée :
  ne pas le respecter n'est pas une violation de l'AGPL (article 7). L'usage comme signe de
  l'offre est réservé aux intégrateurs partenaires et aux titulaires de la licence commerciale.
  **D-LICENSE-NAME** — le nom de code du dépôt, le nom commercial et le logo
  définitif sont écrits dans le seul tableau
  [Dénomination](../../TRADEMARKS.md#denomination). Les clauses disent « le Logiciel » ou « le
  Nom ». Elles ne recopient pas ces valeurs. Un changement se fait dans ce tableau, pas dans les
  clauses, et pas dans le code. **Complément du 24 octobre 2026 (Benoit).** Aucun nom commercial
  n'est choisi. La ligne « Nom commercial retenu » du tableau vaut « à déterminer ». Une recherche
  formelle TMview, OBPI/BOIP, EUIPO et BCE/KBO précède tout choix. Le dépôt n'est pas fait. Les
  constats publics et les réserves pour l'avocat sont dans `TRADEMARKS.md` ; ils ne sont pas
  recopiés ici. Le logo définitif reste à produire en vectoriel. Aucun fichier de logo, aucun
  chemin. Le code (packages, `groupId`, artefacts, nom du dépôt) conserve le nom de code du
  tableau. Son renommage sera une PR séparée.

Les identifiants `D-LICENSE-*` ne sont pas cités par le code : ils ne sont pas ajoutés à
`identifiants.md`.

**Audit (même décision).** Inventaire dans
[docs/analyses/audit-licences-2026-10-24.md](../analyses/audit-licences-2026-10-24.md) et son
[annexe](../analyses/audit-licences-2026-10-24-annexe.md).

- Aucune dépendance GPL ou AGPL, et pas d'iText, dans le jar de production ni dans le bundle
  frontend. Les classes `IText*` de Flying Saucer 10.6.0 sont un reliquat de nom ; l'artefact
  résolu est OpenPDF 3.0.5.
- **Liquibase, clos (Benoit, 2026-10-24).** Le `pom.xml` épingle `liquibase.version` à
  **4.33.0**, Apache-2.0 (texte et POM). C'est la dernière version dans ce cas. Plus de FSL dans
  le jar. La 5.0.3 du BOM Spring Boot est sous FSL-1.1-ALv2. La 5.0.0 n'est pas Apache-2.0 : le
  POM le dit, le `LICENSE.txt` du tag `v5.0.0` est un contrat de souscription (issue
  liquibase/liquibase#7333, corrigée en FSL dans la 5.0.1). Dependabot ignore
  `org.liquibase:*` >= 5.0.0. `dependency:tree -Dincludes=org.liquibase` (tous scopes, profils `embedded-db` et
  `security-scan`) et `dependency:resolve-plugins` ne résolvent aucune 5.x.
- Copyleft faible ou permissif, distinct de la FSL, compatible avec la licence commerciale sous
  conditions : Flying Saucer (LGPL-2.1-or-later), OpenPDF (`MPL-2.0 OR LGPL-2.1+`, élection
  MPL-2.0 recommandée), API Jakarta (EPL-2.0 ou GPL-2.0 avec exception de classpath), police
  Liberation (OFL-1.1), bpmn-js et dmn-js (permission Camunda, filigrane bpmn.io obligatoire).
  Hibernate ORM 7.4.5 est Apache-2.0 (relicencié depuis 7.0.0.Beta5).
- **EPL-2.0, pour l'avocat, non bloquant.** Logback (EPL-2.0 ou LGPL-2.1-only) et AspectJ
  (EPL-2.0) sont dans le jar. Ce n'est pas une FSL et ce n'est pas un copyleft fort. L'avocat le
  voit à part.

**Contributeurs.** Sur `main` (1 091 commits, du 2026-03-14 au 2026-10-24) : Benoit
`<benoit.joseph@gmail.com>` (51) et `superjojo79` (173, compte GitHub propriétaire du dépôt) ;
agents Claude (429) et Cursor Agent (397) ; bots dependabot (29) et cursor[bot] (12). Aucun autre
humain. Aucun accord de tiers n'est requis pour relicencier si Benoit confirme qu'il contrôle les
deux identités — la propriété du dépôt `superjojo79/FlowForge` va dans ce sens. Le droit
d'auteur sur la part produite par les agents est laissé à l'avocat ; ce n'est pas un contributeur
humain tiers.

**Point clos.** Liquibase : 4.33.0, Apache-2.0. Plus de FSL dans le jar.

**Points laissés ouverts.** Le registre reste ouvert : ces points attendent l'avocat. Le choix A
et l'épingle Liquibase sont déjà actés.

1. Validation juridique belge de `COMMERCIAL-LICENSE.md`, `CLA.md` et `TRADEMARKS.md`.
2. Recherche formelle TMview, OBPI/BOIP, EUIPO et BCE/KBO **avant tout choix** de nom
   commercial. La ligne du tableau Dénomination n'en retient pas. Le dépôt n'est pas fait.
   Les réserves sont dans `TRADEMARKS.md`. Le renommage du code (packages, `groupId`,
   artefacts, dépôt) est une PR séparée.
3. Droits sur le visuel généré par IA avant tout dépôt de marque figurative. Le logo définitif
   reste à produire en vectoriel.
4. CLA. Le modèle individuel ne couvre pas le passé, ni la part des commits d'agents. Le §5
   brevets ne mentionne pas les conditions commerciales. Il n'existe pas de CLA d'entité.
5. EPL-2.0 (Logback, AspectJ) : pour l'avocat, non bloquant. Ce n'est pas une FSL.

Contact commercial et prix (`[à compléter]`) restent des trous pour Benoit. Ils ne sont pas
tranchés ici. Suites techniques, pas des points ouverts : en-têtes SPDX après la PR #220 (le
fichier `NOTICE` est livré) ; offre de source dans l'interface pour une version modifiée servie
en réseau (article 13), sans écran dans cette PR (`LICENSING.md`, `NOTICE`).

**Impacte.** `LICENSE`, `LICENSING.md`, `COMMERCIAL-LICENSE.md`, `CLA.md`, `CONTRIBUTING.md`,
`TRADEMARKS.md`, `README.md`, `pom.xml` (`<licenses>`, `liquibase.version`), `frontend/package.json` (`license`),
`.github/dependabot.yml`,
`docs/analyses/audit-licences-2026-10-24.md`, `docs/analyses/audit-licences-2026-10-24-annexe.md`,
`docs/specs/00-index.md`, `docs/specs/07-generation-documents.md`, `NOTICE`. Pas d'écran admin, pas de
variable d'environnement, pas de changeSet Liquibase, pas de renommage dans le code.

**Vérification.** Rejeu local des jobs de `.github/workflows/ci.yml`, JDK 21
(`-Dmaven.compiler.release=21`), `liquibase.version` 4.33.0 dans le pom.

- `docs` : `scripts/check-docs.sh --self-test` puis `scripts/check-docs.sh`, exit 0.
- `build` : `LC_ALL=C.UTF-8 ./mvnw -B verify -Dmaven.compiler.release=21` — BUILD SUCCESS,
  exit 0, 7 min 50 s, terminé 2026-10-24T22:11:51Z (3 638 tests, 0 échec, 5 ignorés).
  `ChangelogChecksumStabilityIT` (1 test, 0,330 s) et `LiquibasePartialFailureIT` (2 tests,
  0,143 s) passent. Les autres tests d'intégration appliquent les migrations. Aucun changeSet
  modifié. `scripts/check-no-embedded-db-in-jar.sh` : OK. Profil `embedded-db`
  (`ZonkyEmbeddedDbLauncherTest`, `EmbeddedDbProfileRestartIT`) : 6 tests, 0 échec, BUILD
  SUCCESS. `SignatureBrowserE2eTest` (`-Dsignature.e2e=true`) : 3 tests, 0 échec, BUILD SUCCESS.
  `dependency:tree -Dincludes=org.liquibase` (défaut, `-Pembedded-db`, `-Psecurity-scan`) ne
  résout que `liquibase-core` 4.33.0. `dependency:resolve-plugins` ne résout aucun Liquibase.
- `frontend` : `npm ci`, `npm run lint`, `npm run test` (127 fichiers, 741 tests), `npm run build`,
  Playwright 131 tests, exit 0.

Le scan OWASP (`.github/workflows/dependency-check.yml`) n'est pas bloquant. `NVD_API_KEY` est
absente : le job n'a pas été lancé. Pas d'en-tête SPDX dans `src/`.
