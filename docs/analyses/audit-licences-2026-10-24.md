# Audit des licences — double licence FlowForge

Date : 2026-10-24. Arbre audité : `main` à `f313d433`, puis l'épingle Liquibase 4.33.0
(décision n° 103). Décision :
[n° 103](../decisions/103-double-licence-agpl-et-licence-commerciale.md).

Chaque artefact est listé dans
[l'annexe](audit-licences-2026-10-24-annexe.md) : coordonnées Maven, paquet npm, licence déclarée,
et s'il entre dans un livrable.

> **Actualisation (2026-11-03).** La licence publique du Logiciel est désormais la PolyForm
> Noncommercial License 1.0.0 avec un addendum de droit belge
> ([décision n° 118](../decisions/118-licence-polyform-noncommercial-et-addendum-belge.md)), et non
> l'AGPL : celle-ci n'a jamais été distribuée, le dépôt étant resté privé. L'inventaire des
> composants tiers reste valable. Les conditions relevées ci-dessous pour une distribution sous
> licence commerciale valent aussi pour la distribution sous PolyForm : notices, remplaçabilité des
> bibliothèques LGPL (permise par l'article 9 de l'addendum), filigrane bpmn.io, police OFL. Les
> passages qui raisonnent sur une distribution AGPL sont historiques. L'analyse datée n'est pas
> réécrite.

## Verdict

### Liquibase — clos

`org.liquibase:liquibase-core:4.33.0` est dans le jar de production. Licence **Apache-2.0** : le
texte et le POM concordent. C'est la dernière version dans ce cas. La propriété
`liquibase.version` du `pom.xml` surcharge le 5.0.3 du BOM Spring Boot 4.1.1 (décision n° 103).

`dependency:tree -Dincludes=org.liquibase` (tous scopes) ne résout que `liquibase-core` 4.33.0,
au scope compile. Même résultat avec `-Pembedded-db` et `-Psecurity-scan`.
`dependency:resolve-plugins` ne résout aucun artefact Liquibase. Aucune 5.x.

Les 5.0.1 et suivantes sont FSL-1.1-ALv2
([texte](https://fsl.software/FSL-1.1-ALv2.template.md)). La 5.0.0 a un POM qui dit Apache-2.0 et
un `LICENSE.txt` de tag qui est un « Software License Subscription and Support Agreement » de
Liquibase Inc. ([liquibase/liquibase#7333](https://github.com/liquibase/liquibase/issues/7333)).
Dependabot ignore `org.liquibase:*` pour les versions `>= 5.0.0`.

L'épingle change deux versions déjà présentes, sans ajouter ni retirer d'artefact :
`com.opencsv:opencsv` 5.12.0 → 5.11.2 (Apache 2, dépendance directe de Liquibase 4.33.0) et
`org.apache.commons:commons-text` 1.15.0 → 1.13.1 (Apache-2.0). Les deux restent permissives.

### Pas de copyleft fort dans les livrables

Aucun artefact GPL (sans exception de classpath), AGPL, SSPL, Commons Clause ou licence payante
n'est résolu.

**iText est absent.** `flying-saucer-pdf` 10.6.0 tire `com.github.librepdf:openpdf:3.0.5`. Les
classes `org.xhtmlrenderer.pdf.IText*` gardent l'ancien nom ; ce n'est pas la bibliothèque iText.
iText 7 / pdfHTML reste écarté (décision n° 103, spec
[07](../specs/07-generation-documents.md)) : son AGPL ne se relicencie pas vers la licence
commerciale.

Hibernate ORM `7.4.5.Final` est sous **Apache-2.0**. Le site Hibernate confirme le passage à
l'ASL 2.0 depuis `7.0.0.Beta5` (`https://hibernate.org/community/license/`). Les versions
antérieures, encore LGPL, ne sont pas dans l'arbre.

### Pour l'avocat, non bloquant — EPL-2.0

Logback 1.5.38 (`logback-classic`, `logback-core`) est **EPL-2.0 OU LGPL-2.1-only**. AspectJ
weaver 1.9.25.1 est **EPL-2.0**. Les deux sont dans le jar de production. Ce n'est pas une FSL et
ce n'est pas un copyleft fort : ce point ne bloque pas la licence commerciale au sens où la FSL
peut le faire. L'avocat le voit quand même. LGPL-2.1-only n'est pas compatible avec l'AGPL-3, et
la licence secondaire de l'EPL-2.0 est la GPL, pas l'AGPL. Alternative si l'avocat l'exige :
Log4j 2 (Apache-2.0), déjà présent via POI, en changeant l'implémentation SLF4J. Non fait ici.

### Obligations, sans bloquer la licence commerciale

Ces composants peuvent accompagner une distribution AGPL et une distribution propriétaire. Leurs
propres conditions restent. La licence commerciale du Logiciel les rappelle et ne les absorbe pas.

| Composant | Licence constatée | Livrable | Conséquence |
|---|---|---|---|
| Flying Saucer `flying-saucer-core` et `flying-saucer-pdf` 10.6.0 | LGPL-2.1 **or later** (POM) | jar de production | Copyleft faible. « Or later » permet la LGPL-3, compatible avec l'AGPL-3. Une offre propriétaire doit laisser remplacer la bibliothèque (fat jar : documenter le remplacement, ou livrer le jar à part) et conserver les notices. |
| OpenPDF 3.0.5 | `MPL-2.0 OR LGPL-2.1+` (`META-INF/LICENSES.md` du jar). Le POM dit « LGPL 2.1 » et « MPL 2.0 » sans le « + » ; le fichier du jar prime. Pas de mention « Incompatible With Secondary Licenses » dans ce fichier. | jar de production | Élection recommandée : **MPL-2.0**. Copyleft par fichier, licence secondaire compatible AGPL-3.0, distribution propriétaire possible. Fournir le source des fichiers MPL modifiés (aucun fork ici). |
| Logback 1.5.38 (`logback-classic`, `logback-core`) | **EPL-2.0 OU LGPL-2.1-only** | jar de production | Pour la licence commerciale : EPL-2.0 (copyleft par fichier) ou LGPL avec possibilité de remplacement. Pour l'AGPL : LGPL-2.1-only n'est pas compatible AGPL-3 ; l'EPL-2.0 a pour licence secondaire la GPL, pas l'AGPL. Tension réelle, de même nature qu'un assemblage, pas un copyleft fort. Alternative si un juriste l'exige : Log4j 2 (Apache-2.0), déjà présent via POI, en changeant l'implémentation SLF4J de Spring Boot. Non fait ici. |
| AspectJ weaver 1.9.25.1 | EPL-2.0 | jar de production | Même famille que Logback. Non bloquant pour la licence commerciale. |
| API Jakarta (`annotation`, `mail`, `transaction`) et Angus Mail | EPL-2.0 ou **GPL-2.0 avec exception de classpath** (et EDL 1.0 pour le mail) | jar de production | L'exception de classpath autorise l'assemblage avec l'AGPL et avec du code propriétaire. Les modules indépendants ne deviennent pas GPL. |
| JAXB, activation, istack (`EDL 1.0`) | Eclipse Distribution License 1.0, texte de type BSD-3 | jar de production | Permissive. Conserver la notice. |
| JPA API 3.2.0 | EPL-2.0 et EDL 1.0 | jar de production | Élection EDL (permissive) ou EPL (copyleft par fichier). |
| bpmn-js 18.24.0, dmn-js 17.10.2 et modules `dmn-js-*` | Permission Camunda / bpmn.io, **plus** obligation de conserver le filigrane qui pointe vers `https://bpmn.io`. Ce n'est pas un champ MIT. | bundle frontend, donc jar | Compatible avec les deux distributions **si le filigrane reste visible et non masqué**. Le champ npm est `SEE LICENSE IN LICENSE` : le texte a été lu sur le paquet 18.24.0 / 17.10.2, pas déduit du champ. Aucune règle du dépôt (`frontend/src`) ne masque `bjs-powered-by`. |
| `@bpmn-io/properties-panel` 3.51.2 | MIT (champ npm). Peer de `bpmn-js-properties-panel`, importé par le code frontend (`flowablePropertiesProvider`, `blockPropertiesProvider`, `BpmnEditorCanvas`). | bundle frontend, donc jar | Dépendance de production (livrable). Le lockfile le marque `peer`, pas `dev`. |
| `component-props` 1.1.1 | Champ licence vide. Le `Readme.md` du paquet (unpkg 1.1.1) dit MIT. Tiré par `dmn-js`. | graphe npm de production | Traité comme MIT. Risque résiduel : métadonnée absente. Alternative si l'on veut fermer le doute : remplacer la chaîne `selection-ranges` ou épingler un fork qui déclare MIT. |
| Police Liberation Sans | SIL OFL-1.1 (`src/main/resources/fonts/LICENSE-LiberationSans.txt`) | jar de production | Peut être embarquée et vendue avec un logiciel, sous AGPL ou sous licence propre. La police elle-même reste OFL. Ne pas la vendre seule. Noms réservés : Liberation, Arimo, Tinos, Cousine. |
| Binaires PostgreSQL zonky 17.11.0 | POM zonky : Apache-2.0. Les binaires sont le logiciel PostgreSQL, licence PostgreSQL (permissive, type BSD). | Quatre binaires au scope **test** (`windows-amd64`, `darwin-amd64`, `linux-amd64`, `linux-amd64-alpine`), tirés par `embedded-postgres` en scope test. Deux binaires **arm64** (`linux-arm64v8`, `darwin-arm64v8`) seulement avec le profil `-Pembedded-db` (jar démo/QA). Pas le jar de production. | `scripts/check-no-embedded-db-in-jar.sh` garde le jar de production. Compatible avec les deux licences. |

Apache-2.0 (Spring, Flowable, Tomcat, Hibernate ORM 7, POI, Bouncy Castle « Bouncy Castle Licence »
permissive de type MIT, xmlbeans, Standard Webhooks MIT, etc.) : compatible avec l'AGPL et avec la
licence commerciale. Obligation : conserver les notices. Le fichier [`NOTICE`](../../NOTICE) à la
racine les rassemble. Les en-têtes SPDX restent une suite (décision n° 103).

### Hors livrable

Dépendances de test Maven (JUnit 4 en EPL-1.0, JUnit 6 en EPL-2.0, JNA en `LGPL-2.1-or-later OR
Apache-2.0` — l'élection Apache-2.0 suffit) : elles ne sont pas dans le jar. Le dépôt de sources
les contient ; un licencié commercial qui reçoit les tests les reçoit avec ces licences, limitées
à ces bibliothèques.

npm : `lightningcss` et ses paquets natifs sont **MPL-2.0**, tirés par Vite seulement. Outil de
build. Le CSS produit n'embarque pas le code MPL. Les `devDependencies` (Vitest, Playwright,
ESLint, TypeScript) ne sont pas copiées dans `static/`.

Les copies de skills `.cursor/skills` et `.claude/skills` (licences MIT de paquets Vue) sont de
l'outillage. Elles ne sont pas dans le jar.

## Livrables examinés

1. **Jar de production** : dépendances Maven `compile` et `runtime` du POM par défaut
   (`dependency:list`). Le frontend construit est copié dans `src/main/resources/static/` puis dans
   ce jar (décision n° 27).
2. **Jar démo/QA** : les mêmes, plus le profil `embedded-db` (zonky et binaires PostgreSQL).
3. **Bundle frontend** : `dependencies` de `frontend/package.json` et leur graphe dans
   `package-lock.json`. Pas les `devDependencies`, sauf mention ci-dessus.
4. **Ressources vendorisées** dans `src/main` : la police Liberation et sa licence OFL. Un DMN
   d'exemple (`grille-eligibilite-client.dmn`) est du contenu du projet, pas une œuvre tierce.
   Aucune autre police, aucun schéma OMG, aucun JavaScript minifié tiers dans `src/main/resources`.
5. **Assets du squelette Vite**, dans `frontend/public/`, copiés dans le build puis dans le jar.
   `favicon.svg` est le logo Vite du modèle Vue ; `frontend/index.html` le sert comme icône. Ce
   n'est pas le logo du Logiciel. `icons.svg` est la planche du même squelette (marques de
   services tiers : GitHub, Discord, Bluesky) ; le code de `frontend/src` ne la référence pas,
   mais `public/` part entier dans le build. Le modèle Vite est sous MIT. Ces fichiers ne sont
   pas une cession de marque.
6. **Tests et build** qui n'entrent pas dans ces jar : listés dans l'annexe, colonne « hors
   livrable ».

### Réserve — images Docker

Les images ne sont pas dans le jar. Leurs licences n'ont pas été lues image par image. Avant une
distribution qui les embarque, l'avocat voit au moins :

- `eclipse-temurin:25-jdk` et `eclipse-temurin:25-jre` (`Dockerfile`, stages build et runtime) ;
- `minio/minio:latest` et `minio/mc:latest` (`docker-compose.yml`) : tag flottant, licence de
  l'image non figée par cet audit ;
- `clamav/clamav:1.4` (`docker-compose.yml`) : processus séparé, pas lié dans le jar.

Les autres images du compose (PostgreSQL, Keycloak, Kafka, et les tags `latest` de RustFS,
SeaweedFS, Prometheus) sont dans le même angle mort. Ce n'est pas un second point FSL : c'est une
réserve sur ce que l'audit n'a pas ouvert.

## Méthode

- Maven : `./mvnw -Dskip.frontend=true dependency:list` pour `compile`, `runtime` et `test`, puis
  le même `runtime` avec `-Pembedded-db`. Licence lue dans le POM local (`~/.m2`) et, à défaut,
  dans le POM parent. Les POM sans espace de noms XML (Standard Webhooks, Bouncy Castle) ont été
  relus sans namespace : le premier passage les marquait à tort « non déclarées ».
- Contrôles manuels des cas sensibles : `LICENSES.md` d'OpenPDF, POM Flying Saucer, page Hibernate,
  texte FSL, `LICENSE` unpkg de bpmn-js 18.24.0 et dmn-js 17.10.2, `Readme.md` de
  `component-props` 1.1.1, licence OFL déjà dans le dépôt.
- npm : champ `license` de `frontend/package-lock.json`, ramené à **503 noms de paquets
  distincts** (les copies imbriquées d'un même nom ne sont pas recomptées). Graphe de production
  = fermeture des `dependencies` de la racine. Aucun champ ne contient GPL, AGPL, LGPL, SSPL ou
  UNLICENSED.
- Auteurs : `git log --format='%an <%ae>'` sur `main` (1 091 commits).

Limite : une licence déclarée dans un POM ou un `package.json` peut diverger du texte embarqué
dans le jar. Les divergences vues (OpenPDF, binaires PostgreSQL, `component-props`) sont notées.
Les en-têtes de chaque fichier source d'OpenPDF n'ont pas été relus un par un ; le `LICENSES.md`
de l'artefact ne porte pas la clause d'incompatibilité MPL.

## Contributeurs

Historique de `main`, du 2026-03-14 au 2026-10-24, 1 091 commits. Auteur et, quand il diffère,
committer.

| Commits | Auteur | Nature |
|---|---|---|
| 429 | Claude `<noreply@anthropic.com>` | agent |
| 397 | Cursor Agent `<cursoragent@cursor.com>` | agent |
| 173 | `superjojo79` `<77111757+superjojo79@users.noreply.github.com>` | humain, compte GitHub propriétaire du dépôt (170 commits commités par `GitHub <noreply@github.com>`, 3 par le compte lui-même) |
| 51 | Benoit `<benoit.joseph@gmail.com>` | humain |
| 29 | dependabot[bot] | bot |
| 12 | cursor[bot] `<206951365+cursor[bot]@users.noreply.github.com>` | bot |

Les lignes `Co-authored-by` ne font apparaître que `superjojo79`, Claude, Cursor Agent et
dependabot. Pas d'autre signataire.

**Aucun tiers humain** n'a commité. Relicencier n'exige donc pas l'accord d'un contributeur
extérieur, **si** Benoit confirme qu'il contrôle les deux identités `Benoit` et `superjojo79`.
Le dépôt GitHub `superjojo79/FlowForge` est le sien : c'est l'indice retenu. Les agents et les
bots ne sont pas des personnes dont il faudrait un CLA pour le passé. Le droit d'auteur sur ce
qu'ils ont produit est un point pour l'avocat, distinct d'un accord de tiers.

Le CLA (`CLA.md`) vise les contributions humaines **futures**. Il n'est pas rétroactif sur cet
historique.

## En-têtes SPDX

Non ajoutés. Justification dans la décision n° 103 : aucun en-tête existant, 1 946 sources, conflit
avec la PR #220. Les champs Maven et npm suffisent pour l'identification SPDX du projet
(`AGPL-3.0-only`). Le fichier [`NOTICE`](../../NOTICE) est livré.

## Vérification de l'épingle

Avec `liquibase.version` à 4.33.0 dans le `pom.xml` :
`LC_ALL=C.UTF-8 ./mvnw -B verify -Dmaven.compiler.release=21` — BUILD SUCCESS, exit 0,
7 min 50 s, terminé 2026-10-24T22:11:51Z (3 638 tests, 0 échec, 5 ignorés). JDK 21 sur la
machine, le pom reste en Java 25. `ChangelogChecksumStabilityIT` (1 test, 0,330 s) et
`LiquibasePartialFailureIT` (2 tests, 0,143 s) passent. Aucun changeSet n'est modifié.

Le détail des autres jobs locaux est dans la décision n° 103, section Vérification.
