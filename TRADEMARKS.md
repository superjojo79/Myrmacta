# Politique de marque (projet)

> **Projet à faire valider par un avocat belge.** Ce texte n'est pas un conseil juridique et n'a
> pas de valeur définitive.

<a id="denomination"></a>

## Dénomination — source de vérité du nom

| Élément | Valeur |
|---|---|
| Nom de code du dépôt | FlowForge |
| Nom commercial | Myrmacta |
| Signature | L'intelligence collective au service de vos dossiers et décisions |
| Logo | [`docs/brand/README.md`](docs/brand/README.md) (SVG et PNG) ; favicon dans `frontend/public/` |
| Encre et corail, fond clair | `#1E2A3A` et `#E4572E` |
| Encre et corail, fond sombre | `#F3F4F6` et `#FF7A52` |

« le Logiciel » désigne le programme de ce dépôt. « le Nom » désigne le nom commercial de ce
tableau : **Myrmacta**. Le dépôt de marque n'est pas fait. Le nom de code est la première ligne :
le code (packages, `groupId`, artefacts, nom du dépôt) le conserve jusqu'à la PR technique
([décision n° 105](docs/decisions/105-nom-commercial-myrmacta.md), [RENAMING.md](docs/RENAMING.md)).

[LICENSING.md](LICENSING.md), [LICENSE-ADDENDUM.md](LICENSE-ADDENDUM.md),
[CONTRIBUTING.md](CONTRIBUTING.md) et les décisions
[n° 103](docs/decisions/103-double-licence-agpl-et-licence-commerciale.md) et
[n° 118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md) disent
« le Logiciel » ou « le Nom ». **Un changement de nom se fait dans ce tableau.** En cas de
divergence, ce tableau prévaut. L'interface, le README et les guides affichent le Nom. Le code
n'est pas renommé dans la PR visible : packages, `groupId`, artefacts et nom du dépôt restent le
nom de code tant que la PR technique ne les change pas.

> **Actualisation (2026-10-25).** Les paquets, le `groupId` et l'`artifactId` sont désormais
> `be.myrmacta` / `myrmacta`. Le nom de code du dépôt (première ligne du tableau) reste FlowForge.
> Les phrases datées ci-dessus ne sont pas réécrites. Voir [docs/RENAMING.md](docs/RENAMING.md).

> **Constat, pas une dénomination à recopier.** Le nom de code du dépôt est déjà un nom commercial,
> notamment celui d'une société belge d'automatisation (flowforge.be) ; en 2023, un éditeur
> low-code articulé autour de Node-RED a dû se renommer FlowFuse. C'est pour cela que le code
> garde FlowForge et que le Nom est Myrmacta. Le dépôt de marque de Myrmacta n'est pas fait.

> **Logo.** Logo A3b, publié en SVG et en PNG dans [`docs/brand/`](docs/brand/) : fourmi de profil
> dessinée avec des formes BPMN. Le fichier sombre va sur fond sombre, le fichier clair sur fond
> clair. Couleurs : tableau ci-dessus.

La licence du code (PolyForm Noncommercial 1.0.0 et son addendum de droit belge, [`LICENSE`](LICENSE),
[`LICENSE-ADDENDUM.md`](LICENSE-ADDENDUM.md), expliqués dans [LICENSING.md](LICENSING.md)) ne
donne **aucun droit** sur la marque, le Nom ou le logo. Décision :
[n° 118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md).

Contact pour toute autorisation écrite : **contact@myrmacta.be**.

## Usages autorisés sans demande

Sans accord préalable, et sans laisser entendre une autre relation que celle que ces usages
décrivent :

- une mention factuelle « basé sur » suivie du Nom, ou « based on » suivie du Nom ;
- un lien vers le dépôt ou vers une page publique du Logiciel ;
- un article, un billet, une conférence ou un comparatif qui cite le Nom pour désigner le Logiciel ;
- une issue ou un échange sur le dépôt, dans les conditions de [CONTRIBUTING.md](CONTRIBUTING.md).

Ces usages ne sont pas un partenariat, un agrément ni une certification.

## Usages interdits sans accord écrit

Sans accord écrit du titulaire :

- nommer un fork du Nom, ou choisir un nom qui s'en rapproche au point de créer une confusion ;
- nommer un service, un SaaS ou une offre hébergée du Nom ;
- modifier le logo, le recomposer, ou en publier une variante ;
- laisser croire à un partenariat, à un agrément, à une certification officielle ou à une offre
  éditée par le titulaire.

## Fork distribué ou hébergé

Quiconque distribue un fork, ou le propose en service réseau, **change de nom** et retire le logo.
Il n'emploie pas le Nom. Cette obligation est une **politique de marque**, distincte de la licence
du code : la licence ne donne aucun droit sur le Nom (section « No Other Rights » et addendum,
article 8), et un fork n'est de toute façon permis qu'aux fins qu'elle autorise. Le nouveau nom ne doit pas suggérer que le fork est le Logiciel
d'origine ou une offre officielle.

## Usage réservé

L'usage du Nom et, lorsqu'il aura été publié, du logo définitif, comme signe de l'offre elle-même
(et non comme simple mention factuelle), est réservé :

- aux partenaires intégrateurs liés par un écrit ;
- aux titulaires d'une [licence commerciale](LICENSING.md#licence-commerciale) ;
- au titulaire du Logiciel.

Cet usage suit l'écrit qui l'accorde. Il porte sur le Nom du tableau. Le dépôt de marque n'est
pas fait.

## Points à faire valider par l'avocat

- validité de cette politique au regard du droit belge et du droit des marques Benelux, avant
  tout dépôt ;
- recherche formelle TMview, OBPI/BOIP, EUIPO et BCE/KBO, le nom commercial **Myrmacta**
  étant choisi ; dépôt (non fait, en attente de la revue de l'avocat) à l'OBPI/BOIP, à l'EUIPO,
  ou aux deux (marque verbale, marque figurative) ; pas de ®, ™ au plus tant que l'avocat
  n'a pas tranché ;
- le fait que le nom de code est déjà un nom commercial (flowforge.be) et qu'il a fait l'objet
  d'une contestation en 2023 (un éditeur Node-RED devenu FlowFuse) ;
- droits sur le visuel avant tout dépôt de marque figurative : le logo A3b (fourmi entière,
  SVG et PNG) et le favicon (tête corail, deux antennes) sont dans le dépôt
  (`docs/brand/`, `frontend/public/`) ;
- force de l'obligation, pour un fork distribué ou hébergé, de changer de nom ;
- étendue de l'usage réservé aux intégrateurs partenaires et aux titulaires de la licence
  commerciale ;
- le fait que le code (packages, `groupId`, artefacts, nom du dépôt) conserve le nom de code ;
  le renommage du code est une PR séparée.

---

# Trademark policy (draft)

> **Draft for review by a Belgian lawyer.** This text is not legal advice and is not final.

The code name of the repository, the commercial name, and the definitive logo are the three rows
of the table [Dénomination](#denomination) above. That table is the only place where the name is
written. This English text says **the Software** and **the Name**. If it ever diverges from the
table, the table prevails. A rename is a change to the table, not a rewrite of the clauses. The
code (packages, `groupId`, artifacts, repository name) keeps the code name until another decision
changes it.

The Software is the program in this repository. The Name is the commercial name in that table:
**Myrmacta**. It is chosen. Trademark filing is pending the lawyer's review. No ® is used; ™
at most, until that review. The code name of the repository is the first row. The code keeps it.
Renaming the code will be a separate pull request.

> **Update (2026-10-25).** Packages, `groupId` and `artifactId` are now `be.myrmacta` /
> `myrmacta`. The repository code name (first row of the table) stays FlowForge. The dated
> sentences above are not rewritten. See [docs/RENAMING.md](docs/RENAMING.md).

> **Finding, not a name to copy into the clauses.** The commercial name is Myrmacta. Filing is
> pending the lawyer's review. No ® ; ™ at most. The code name stays the repository name: it is
> already a trade name, notably of a Belgian automation company (flowforge.be); in 2023 a low-code
> editor built around Node-RED had to rename itself FlowFuse.

> **Logo.** Logo A3b and the favicon are in the repository (`docs/brand/`, `frontend/public/`).
> The logo is the full ant drawn with BPMN shapes, as SVG and PNG. The favicon is the coral head
> with two antennae. A figurative filing still waits on the lawyer.

The license of the code (PolyForm Noncommercial 1.0.0 and its Belgian-law addendum,
[`LICENSE`](LICENSE), [`LICENSE-ADDENDUM.md`](LICENSE-ADDENDUM.md), explained in
[LICENSING.md](LICENSING.md)) grants **no right** in the trademark, the Name, or the logo.
Decision: [no. 118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md).

Contact for written permission: **contact@myrmacta.be**.

## Uses allowed without asking

Without prior permission, and without implying any relationship other than the one these uses
describe:

- a factual mention “based on” or « basé sur » followed by the Name ;
- a link to the repository or to a public page of the Software ;
- an article, post, talk, or comparison that uses the Name to identify the Software ;
- an issue or a discussion on the repository, under [CONTRIBUTING.md](CONTRIBUTING.md).

These uses are not a partnership, an endorsement, or a certification.

## Uses that require written permission

Without the owner's written agreement:

- naming a fork with the Name, or choosing a name close enough to cause confusion ;
- naming a service, a SaaS, or a hosted offering with the Name ;
- modifying the logo, reassembling it, or publishing a variant ;
- implying a partnership, an endorsement, an official certification, or an offering published by
  the owner.

## Distributed or hosted forks

Anyone who distributes a fork, or offers it as a network service, **must use a different name**
and must remove the logo. They do not use the Name. That duty is a **trademark policy**, separate
from the license of the code: the license grants no right in the Name (“No Other Rights” section
and addendum, section 8), and a fork is in any case allowed only for the purposes it permits. The
new name must not suggest that the fork is the
original Software or an official offering.

## Reserved use

Use of the Name and, once it has been published, of the definitive logo as the sign of the
offering itself (and not as a mere factual mention) is reserved for:

- integrator partners bound by a written agreement ;
- holders of a [commercial license](LICENSING.md#licence-commerciale) ;
- the owner of the Software.

That use follows the document that grants it. It covers the Name in the table. No trademark
application has been filed.

## Points for the lawyer

- whether this policy holds under Belgian law and Benelux trademark law, before any filing ;
- a formal TMview, OBPI/BOIP, EUIPO and BCE/KBO search **before any choice** of commercial name,
  then filing (not done) with OBPI/BOIP, EUIPO, or both (word mark, figurative mark) ;
- the fact that the code name is already a trade name (flowforge.be) and was disputed in 2023
  (a Node-RED editor that became FlowFuse) ;
- **rights in the AI-generated visual** (provisional JPEG, not in the repository) before any
  figurative trademark filing ;
- when and how the official vector logo will be published ;
- how far an obligation on a distributed or hosted fork to change its name can be enforced ;
- the scope of the use reserved for integrator partners and commercial-license holders ;
- the fact that the code (packages, `groupId`, artifacts, repository name) keeps the code name;
  renaming the code is a separate pull request.
