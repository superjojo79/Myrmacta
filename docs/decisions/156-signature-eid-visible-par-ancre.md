# §156 — Signature eID visible, placée par ancre

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-22, issue [#137](https://github.com/superjojo79/Myrmacta/issues/137)). Prolonge
[§138](138-signature-eid-sans-agent-signature-apportee.md) (D-EID-BRIDGE) et
[§109](109-fournisseurs-de-signature-yousign-et-docusign.md) (D-SIGNATURE-ANCHOR).

**Constat.** Avec le fournisseur `eid`, `DssCardSignatureAssembler` produisait une signature PAdES invisible : le
lecteur PDF ne la montre que dans son panneau Signatures (Foxit, recette #98). Le modèle de document porte déjà,
pour Yousign et DocuSign, une ancre `{{sN|signature|85|37}}` par rang de signataire, en texte blanc.

**Décisions.**

- **D-EID-VISIBLE.** La signature par pont navigateur est visible quand le document remis porte l'ancre du rang du
  signataire (rang = position + 1, comme DocuSign). Le cadre fait 85 × 37 points ; son coin haut gauche est celui du
  texte de l'ancre, comme une ancre intelligente Yousign. L'apparence est du texte PDF : « Signé avec la carte eID »,
  le nom lu dans le certificat (prénoms et nom, sinon le CN sans « (Signature) », 60 caractères au plus) et la date
  `jj/mm/aaaa hh:mm` en heure de Bruxelles. Police Liberation Sans, déjà livrée pour les PDF, incluse en
  sous-ensemble (accents). Jamais le numéro de registre national (D-EID-NRN). La première occurrence d'une ancre
  dupliquée l'emporte.
- **Ancre inutilisable : signature invisible (tranché par Benoit le 2026-11-22).** La signature reste invisible
  comme avant quand l'ancre du rang est absente, sur une page tournée, quand le cadre sortirait de la MediaBox,
  toucherait une annotation existante (lien, champ, cadre d'un signataire précédent) ou couvrirait du contenu (ancre
  écrite en noir, texte voisin : même critère de zone vide que le vérificateur, `BlankZone`, partagé avec
  `FinalGraphWalk`). Un avertissement est journalisé à la préparation avec le rang et la catégorie de motif, sans donnée
  personnelle. Sans ces contrôles, DSS lèverait une alerte (cadre hors page ou chevauchant) ou le vérificateur
  refuserait après la saisie du PIN, à chaque essai. Écarté : refuser comme Yousign, qui casserait les modèles
  existants alors que la signature invisible reste une `QES` valide. Choix de Benoit (carte de décision du
  2026-11-22).
- **Déterminisme.** `prepare` et `assemble` restent sans état : l'apparence est une fonction pure du document remis,
  du certificat, de l'heure de signature et du rang. Le rang entre donc dans `SignaturePort.CardSignatureInput`
  (`signerRank`). Dessinateur natif de DSS (`PdfBoxNativeObjectFactory`) ; l'étiquette du sous-ensemble de police est
  dérivée des glyphes. Seul le délai de lecture de l'ancre (20 s) dépend de l'horloge : dépassé, la préparation ou
  l'assemblage échoue au lieu de retomber sur une signature invisible, qui aurait changé les octets signés.
- **Nom affiché.** Tronqué à 60 points de code ; un caractère absent de la police devient « ? », pour que le dessin
  n'échoue jamais sur un nom.
- **Vérification inchangée.** Le PDF assemblé passe toujours par `DssSignedPdfVerifier` et sa règle de zone vide
  (règle MOYEN E de `FinalGraphWalk`), dont le calcul est désormais dans `BlankZone`.
- **Limites connues.** Une annotation qui touche seulement le bord du cadre suffit au repli (contrôle plus strict que
  DSS). Le rendu de zone vide suppose que préparation et assemblage tournent sur la même image (polices système
  identiques pour un texte non incorporé). Le rendu de page n'a pas de borne propre de temps ni de mémoire, comme
  dans le vérificateur : suivi en issue [#148](https://github.com/superjojo79/Myrmacta/issues/148).
- **Non pris en charge.** Une ancre dont le texte est tourné par sa propre matrice (hors rotation de page) serait mal
  placée.

**Hors lot.** Le téléversement (l'apparence est celle de l'outil du signataire), une image manuscrite, une ancre de
taille libre, l'ancre placée par le BPMN.

**Impacte.** `docs/specs/30-signature-electronique.md` (fournisseur eid), `docs/guides/GUIDE-UTILISATEUR.md`
(Signer avec votre carte eID), `docs/decisions/identifiants.md`.
