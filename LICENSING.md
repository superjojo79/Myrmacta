# Licence du code

Le produit est le Logiciel. Le nom du dépôt, le nom commercial et le logo définitif sont
écrits une seule fois, dans le tableau [Dénomination](TRADEMARKS.md#denomination). Le nom
commercial est **Myrmacta**. Le dépôt de marque n'est pas fait.

> Les textes qui font foi sont [`LICENSE`](LICENSE) (PolyForm Noncommercial License 1.0.0, texte
> officiel anglais non modifié) et [`LICENSE-ADDENDUM.md`](LICENSE-ADDENDUM.md) (addendum de droit
> belge). Le présent fichier les résume. Il n'a pas de valeur contractuelle et n'est pas un conseil
> juridique. Décision : [n° 118](docs/decisions/118-licence-polyform-noncommercial-et-addendum-belge.md).

## Français

### En bref

Le code est **visible et réutilisable à des fins non commerciales**. Tout usage commercial
demande une licence commerciale, sur demande à **contact@myrmacta.be**.

| Qui, pour quoi | Sans demander | Licence commerciale |
|---|---|---|
| Particulier : étude, recherche, test, enseignement, loisir | oui | — |
| Association caritative, établissement d'enseignement, organisme de recherche publique, de santé ou de sécurité publique, de protection de l'environnement | oui | — |
| Autorité publique (État, Communautés, Régions, provinces, communes, CPAS, zones de police et de secours), pour ses missions | oui | — |
| Entreprise ou intégrateur qui évalue le Logiciel hors production, 90 jours | oui | — |
| Entreprise ou indépendant qui l'utilise dans son activité, même en interne | — | oui |
| Prestataire rémunéré qui l'installe, l'héberge, l'exploite, l'adapte ou le maintient pour un tiers, y compris une commune | — | oui |
| Vente, SaaS, intégration dans une offre payante | — | oui |

Ce tableau illustre la licence et l'addendum ; en cas de doute, ces textes prévalent et le
titulaire répond par écrit à contact@myrmacta.be (addendum, article 5.4).

### Pas une licence open source

Une licence qui interdit l'usage commercial n'est pas une licence libre ou open source au sens
habituel de ces termes. Le code est publié pour être lu, étudié, évalué et utilisé dans les
limites ci-dessus.

### Pourquoi un addendum

La PolyForm Noncommercial License est un texte standard, rédigé en anglais et sans droit
applicable. L'addendum la complète pour le droit belge sans retirer de droit : identité du
titulaire, versions couvertes, étendue des droits (Code de droit économique, livre XI), exemples
belges d'organismes publics, lecture du titulaire pour les entreprises et les prestataires,
permission d'évaluation, droit moral, composants tiers, responsabilité, langue et droit belge.

### Licence commerciale

Le titulaire accorde des licences commerciales par écrit, au cas par cas : intégrateur, hébergeur,
éditeur, entreprise utilisatrice. Contact : **contact@myrmacta.be**.

### Contributions

Les contributions de code externes ne sont pas acceptées pour l'instant. Les issues sont
bienvenues. Voir [CONTRIBUTING.md](CONTRIBUTING.md).

### Composants tiers et marque

Les composants tiers gardent leurs propres licences. L'inventaire est dans
[l'audit des licences](docs/analyses/audit-licences-2026-10-24.md) et les notices à conserver sont
dans [`NOTICE`](NOTICE). La licence ne donne aucun droit sur le Nom ni sur le logo : voir
[TRADEMARKS.md](TRADEMARKS.md).

### Où se trouvent les textes

`LICENSE`, `LICENSE-ADDENDUM.md` et [`NOTICE`](NOTICE) sont à la racine du dépôt, à la racine du
jar (`META-INF/`) et dans l'image Docker (`/usr/share/doc/myrmacta/`). Les métadonnées Maven
(`pom.xml`), npm (`frontend/package.json`) et de l'image portent l'identifiant SPDX
`PolyForm-Noncommercial-1.0.0`. L'addendum n'a pas d'identifiant SPDX ; le `NOTICE` le rattache à
chaque copie.

---

## English

The authoritative texts are [`LICENSE`](LICENSE) (PolyForm Noncommercial License 1.0.0, official
unmodified English text) and [`LICENSE-ADDENDUM.md`](LICENSE-ADDENDUM.md) (Belgian-law addendum).
This file summarizes them; it is not a contract and not legal advice.

The code is **source-available for noncommercial use**. Individuals, charities, educational and
public research institutions, public safety, health and environmental organizations, and
government institutions — in Belgium including the State, Communities, Regions, provinces,
municipalities, CPAS/OCMW, police and emergency zones — may use it for their purposes or missions.
Companies and integrators may evaluate it outside production for 90 days.

Any other commercial use requires a commercial license: use within a company's or a
self-employed person's business, paid installation, hosting, operation, adaptation or maintenance
for someone else (including a public body), resale, SaaS, or integration into a paid offering.
Contact: **contact@myrmacta.be**. This is not an open-source license.

External code contributions are not accepted for now; issues are welcome (see
[CONTRIBUTING.md](CONTRIBUTING.md)). Third-party components keep their own licenses
([`NOTICE`](NOTICE)). The license grants no trademark rights ([TRADEMARKS.md](TRADEMARKS.md)).
