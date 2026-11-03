# §118 — Licence PolyForm Noncommercial 1.0.0 et addendum de droit belge

> **Statut :** clos · point de premier niveau · [registre](README.md)

**Énoncé.** Le [§103](103-double-licence-agpl-et-licence-commerciale.md) avait retenu l'PolyForm-Noncommercial-1.0.0
avec une licence commerciale. Le projet n'est pas en phase commerciale : c'est d'abord une vitrine. Les
objectifs sont de pouvoir mettre le code à disposition, de se prémunir contre un usage commercial non
souhaité ou non rémunéré, et de passer le dépôt en public. L'AGPL autorise l'usage commercial : elle ne
répond pas au deuxième objectif. La PR 289 proposait une licence non commerciale rédigée sur mesure,
inspirée de PolyForm, avec un CLA, une licence-cadre commerciale et un bon de commande, tous à l'état de
projet ; le fichier `LICENSE` restait AGPL.

Constat du 2026-11-03 : le dépôt est privé, sans fork, et aucune copie n'a été remise à un tiers. Les
seuls auteurs humains sont le titulaire, sous deux identités ; le reste vient d'agents et de bots
(§103, contributeurs). L'AGPL n'a donc été accordée à personne et la licence peut changer librement
avant la publication.

**Options présentées :**

- **A.** Garder l'PolyForm-Noncommercial-1.0.0 (§103). Écarté : l'usage commercial reste permis.
- **B.** Business Source License 1.1, usage de production réservé, bascule open source après quatre
  ans. Écarté : le titulaire veut que les autorités publiques puissent utiliser le Logiciel en
  production sans licence.
- **C.** Licence non commerciale sur mesure (PR 289). Écarté : un texte non standard coûte une
  relecture complète, inquiète les utilisateurs, et ses articles 4.3 et 4.4 ouvraient gratuitement le
  marché des prestataires des pouvoirs locaux.
- **D.** PolyForm Noncommercial 1.0.0, texte officiel non modifié, et un addendum de droit belge qui
  précise et ajoute des permissions sans retirer de droit.

**Décision (Benoit, 2026-11-03) : D.**

- **D-LICENSE-POLYFORM-NC** — `LICENSE` est le texte officiel de la PolyForm Noncommercial License
  1.0.0, non modifié (SHA-256 `c0ea4a896d2c8c394b29f9427589996db826cd501c512279ff0ed3ef48fabbe5`,
  source `https://raw.githubusercontent.com/polyformproject/polyform-licenses/1.0.0/PolyForm-Noncommercial-1.0.0.md`,
  identique mot pour mot au texte SPDX). Identifiant SPDX `PolyForm-Noncommercial-1.0.0` dans
  `pom.xml`, `frontend/package.json` et le label de l'image.
- **D-LICENSE-ADDENDUM-BE** — `LICENSE-ADDENDUM.md`, en français (qui prévaut) et en anglais.
  Contenu : titulaire Benoit Joseph et transmission à une société contrôlée par le titulaire ; versions couvertes ;
  acceptation, gratuité, durée et actes couverts (Code de droit économique, livre XI) ; *fair use* lu
  comme les exceptions légales belges ; exemples belges de *government institution* (État,
  Communautés, Régions, provinces, communes, CPAS, zones de police et de secours), liste non
  limitative ; usage en entreprise ou par un indépendant, même interne, non permis ; **un prestataire
  rémunéré qui installe, héberge, exploite, adapte ou maintient le Logiciel pour un tiers, y compris un
  organisme public, a besoin d'une licence commerciale**, l'usage de l'organisme restant permis ;
  confirmation écrite sur demande ; **évaluation hors production de 90 jours** pour toute
  organisation, nouvelle période à chaque version majeure ; droit moral ; notices et marques ;
  composants tiers et clause de compatibilité LGPL ; responsabilité (dol, faute intentionnelle,
  dommage corporel, responsabilités impératives dont celle du fait des produits défectueux,
  consommateurs) ; notification d'une violation ; langue ; droit belge.
  Les intercommunales, régies et ASBL paralocales ne sont pas citées : l'addendum ne peut pas les
  exclure, elles relèvent du seul texte PolyForm (choix de Benoit). Deux lignes `Required Notice:` en
  tête de `NOTICE` imposent, par le mécanisme même de la PolyForm, que l'addendum accompagne toute
  copie. Il est livré à `META-INF/LICENSE-ADDENDUM.md` dans le jar et sous `/usr/share/doc/myrmacta/`
  dans l'image ; `scripts/check-license-notice-in-jar.sh` le contrôle.
- **D-LICENSE-COMMERCIAL-ON-REQUEST** — licence commerciale sur demande à contact@myrmacta.be,
  conclue par écrit au cas par cas. Aucun modèle de contrat n'est publié dans le dépôt :
  `COMMERCIAL-LICENSE.md` est retiré ; les projets de la PR 289 (licence-cadre, bon de commande)
  restent dans sa branche, hors du dépôt public, pour la relecture d'un avocat avant le premier client.
- **D-CONTRIB-CLOSED** — pas de contribution externe de code ; les issues restent ouvertes.
  `CLA.md` est retiré. Un accord de contribution sera rédigé si les contributions s'ouvrent ; il devra
  autoriser expressément l'exploitation sous licence publique (libre ou non commerciale) et sous
  licence commerciale, sans figer la licence publique future.
- **Publication.** Avant le passage en public, l'historique est réécrit pour retirer l'AGPL des
  commits où elle figure (choix de Benoit) ; la procédure n'est pas un fichier du dépôt.
- D-LICENSE-SPDX, D-LICENSE-MARK et D-LICENSE-NAME (§103) restent valables. `TRADEMARKS.md` ne raisonne
  plus sur l'article 7 de l'AGPL et donne le contact.

Les identifiants de ce point ne sont pas cités par le code : ils ne sont pas ajoutés à
`identifiants.md`.

**Suites, pas des points ouverts.** Relecture de l'addendum par un avocat de droit belge,
recommandée avant la publication. Dépôt de la marque Myrmacta (plus tard, choix de Benoit). Création
d'une société et transmission des droits avant le premier contrat commercial. Droit d'auteur sur la
part du code produite par des agents (§103), qui borne ce que toute licence peut protéger.

**Responsabilité du fait des produits défectueux, pour l'avocat (ajout du 2026-11-03).** La
directive (UE) 2024/2853, à transposer pour le 9 décembre 2026, range les logiciels parmi les
produits et couvre aussi la perte ou la corruption de données d'un particulier. Ce régime ne peut
pas être écarté par contrat. Elle exclut les logiciels libres et open source fournis hors d'une
activité commerciale ; une licence non commerciale n'est pas open source et le titulaire exerce
une activité d'indépendant complémentaire : l'exclusion n'est donc pas acquise. L'article 10 de
l'addendum réserve désormais expressément cette responsabilité, pour que l'exonération de la
PolyForm ne paraisse pas la viser ; il n'en crée aucune. À vérifier par l'avocat : application de
la directive au Logiciel distribué gratuitement, qualité de « fabricant », et articulation avec le
règlement (UE) 2024/2847 sur la cyberrésilience.

**Impacte.** `LICENSE`, `LICENSE-ADDENDUM.md` (nouveau), `NOTICE`, `LICENSING.md`, `CONTRIBUTING.md`,
`CLA.md` et `COMMERCIAL-LICENSE.md` (retirés), `README.md`, `TRADEMARKS.md`, `ROADMAP.md`, `pom.xml`
(`<licenses>`, copie de l'addendum dans le jar), `frontend/package.json`, `frontend/package-lock.json`,
`Dockerfile`, `.dockerignore`, `.github/workflows/ci.yml`, `scripts/check-license-notice-in-jar.sh`,
`docs/guides/DEPLOIEMENT.md`, `docs/specs/00-index.md`, `docs/specs/07-generation-documents.md`,
`docs/analyses/audit-licences-2026-10-24.md` (actualisation), §103 (clos). Pas de code applicatif,
pas d'écran, pas de variable d'environnement, pas de changeSet Liquibase.
