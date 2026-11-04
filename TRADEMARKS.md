# Politique de marque (projet)

> Ce texte n'est pas un conseil juridique et n'a
> pas de valeur définitive.

<a id="denomination"></a>

## Dénomination — source de vérité du nom

| Élément | Valeur |
|---|---|
| Dépôt | `superjojo79/Myrmacta` |
| Nom commercial | Myrmacta |
| Signature | L'intelligence collective au service de vos dossiers et décisions |
| Logo | [`docs/brand/README.md`](docs/brand/README.md) (SVG et PNG) ; favicon dans `frontend/public/` |
| Encre et corail, fond clair | `#1E2A3A` et `#E4572E` |
| Encre et corail, fond sombre | `#F3F4F6` et `#FF7A52` |

« le Logiciel » désigne le programme de ce dépôt. « le Nom » désigne le nom commercial de ce
tableau : **Myrmacta**. Le dépôt de marque n'est pas fait. Le dépôt, les paquets (`be.myrmacta`),
le `groupId`, l'`artifactId` et les artefacts portent tous ce nom
([décision n° 105](docs/decisions/105-nom-commercial-myrmacta.md), [RENAMING.md](docs/RENAMING.md)).

[LICENSING.md](LICENSING.md), [LICENSE-ADDENDUM.md](LICENSE-ADDENDUM.md),
[CONTRIBUTING.md](CONTRIBUTING.md) et les décisions
[n° 103](docs/decisions/103-double-licence-agpl-et-licence-commerciale.md) et
[n° 118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md) disent
« le Logiciel » ou « le Nom ». **Un changement de nom se fait dans ce tableau.** En cas de
divergence, ce tableau prévaut. L'interface, le README, les guides et le code emploient le Nom.

> **Ancien nom de code, abandonné.** Le projet s'est appelé FlowForge. Ce nom n'est plus employé :
> ni comme nom du dépôt, ni dans le code, ni comme signe. L'ancien dépôt `superjojo79/FlowForge`
> est privé et archivé ([§118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md)).
> Il ne subsiste que dans l'historique (journal des modifications, registre des décisions,
> permaliens vers l'archive, auteur des changeSets Liquibase déjà appliqués) et dans quelques
> lignes de compatibilité marquées `flowforge-legacy`. La liste est dans
> [docs/RENAMING.md](docs/RENAMING.md).
>
> Raison de l'abandon : FlowForge est déjà un nom commercial, notamment celui d'une société belge
> d'automatisation (flowforge.be) ; en 2023, un éditeur low-code articulé autour de Node-RED a dû
> se renommer FlowFuse. Ne pas réemployer ce nom.

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
- l'ancien nom de code FlowForge, abandonné parce qu'il est déjà un nom commercial
  (flowforge.be) et qu'il a fait l'objet d'une contestation en 2023 (un éditeur Node-RED devenu
  FlowFuse) : vérifier que ses traces historiques (archive privée, journal des modifications,
  lignes de compatibilité) ne créent pas de risque ;
- droits sur le visuel avant tout dépôt de marque figurative : le logo A3b (fourmi entière,
  SVG et PNG) et le favicon (tête corail, deux antennes) sont dans le dépôt
  (`docs/brand/`, `frontend/public/`) ;
- force de l'obligation, pour un fork distribué ou hébergé, de changer de nom ;
- étendue de l'usage réservé aux intégrateurs partenaires et aux titulaires de la licence
  commerciale.

---

# Trademark policy (draft)

> **Draft for review by a Belgian lawyer.** This text is not legal advice and is not final.

The repository, the commercial name, the logo and the brand colours are the rows of the table
[Dénomination](#denomination) above. That table is the only place where the name is written. This
English text says **the Software** and **the Name**. If it ever diverges from the table, the table
prevails. A rename is a change to the table, not a rewrite of the clauses.

The Software is the program in this repository. The Name is the commercial name in that table:
**Myrmacta**. It is chosen. Trademark filing is pending the lawyer's review. No ® is used; ™
at most, until that review. The repository, the packages (`be.myrmacta`), the `groupId`, the
`artifactId` and the artifacts all carry the Name.

> **Former code name, abandoned.** The project used to be called FlowForge. That name is no longer
> used: not as the repository name, not in the code, not as a sign. The former
> `superjojo79/FlowForge` repository is private and archived. The name survives only in the
> history (changelog, decision register, permalinks to the archive, author of Liquibase changeSets
> already applied) and in a few compatibility lines marked `flowforge-legacy`. The list is in
> [docs/RENAMING.md](docs/RENAMING.md).
>
> Why it was dropped: FlowForge is already a trade name, notably of a Belgian automation company
> (flowforge.be); in 2023 a low-code editor built around Node-RED had to rename itself FlowFuse.
> Do not reuse that name.

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
- the former code name FlowForge, dropped because it is already a trade name (flowforge.be) and
  was disputed in 2023 (a Node-RED editor that became FlowFuse): check that its historical traces
  (private archive, changelog, compatibility lines) create no risk ;
- **rights in the AI-generated visual** (provisional JPEG, not in the repository) before any
  figurative trademark filing ;
- when and how the official vector logo will be published ;
- how far an obligation on a distributed or hosted fork to change its name can be enforced ;
- the scope of the use reserved for integrator partners and commercial-license holders.
