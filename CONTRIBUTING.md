# Contribuer au Logiciel

Le Logiciel est concédé sous la [PolyForm Noncommercial License 1.0.0](LICENSE), complétée par
l'[addendum de droit belge](LICENSE-ADDENDUM.md). Le résumé est dans [LICENSING.md](LICENSING.md),
la marque dans [TRADEMARKS.md](TRADEMARKS.md). Le nom du dépôt, le nom commercial et le logo sont
dans le tableau [Dénomination](TRADEMARKS.md#denomination).

## Contributions de code : pas pour l'instant

Le dépôt n'accepte pas de contribution externe de code, de documentation ou d'autre contenu
destiné à être intégré. Une pull request externe est fermée sans fusion. Le titulaire doit pouvoir
accorder des licences commerciales sur tout le code ; une contribution externe exigerait un accord
de contribution signé, qui n'est pas encore en place. Si les contributions s'ouvrent, ce fichier
décrira l'accord à signer.

## Ce qui est bienvenu

- **Issues** : signaler un bogue, poser une question, proposer une amélioration. Décrivez le
  besoin ; ne joignez pas de code à intégrer. Le titulaire peut reprendre librement les idées
  exprimées dans une issue.
- **Faille de sécurité** : ne l'ouvrez pas en issue publique. Écrivez à **contact@myrmacta.be**.
- **Licence commerciale, partenariat d'intégration, question sur un usage permis** :
  **contact@myrmacta.be**.

## Marque

Une issue ou un échange ne donne aucun droit sur le Nom ni sur le logo. Ne nommez pas un fork ou
un service du Nom. Voir [TRADEMARKS.md](TRADEMARKS.md).

## Règles du code (mainteneur et agents)

Les règles de construction du dépôt sont dans [AGENTS.md](AGENTS.md) et le skill
`myrmacta-conventions`. En résumé : Java dans `be.myrmacta`, domaine sans Spring, tests
`./mvnw verify`, frontend `cd frontend && npm run lint && npm run test:all` quand le SPA change,
documentation tenue par `scripts/check-docs.sh`.

---

## Contributing (short English version)

The Software is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE) with a
[Belgian-law addendum](LICENSE-ADDENDUM.md); see [LICENSING.md](LICENSING.md).

External code contributions are **not accepted for now**: external pull requests are closed
without merging, because the owner must be able to license all code commercially and no
contributor agreement is in place yet. Issues (bugs, questions, suggestions) are welcome; describe
the need and do not attach code meant for integration. Report security issues privately to
**contact@myrmacta.be**, which is also the contact for commercial licenses.

Do not use the Name for a fork or a hosted service ([TRADEMARKS.md](TRADEMARKS.md)).
