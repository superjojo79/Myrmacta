# §137 — Menu shell repliable (bandeau + tiroir)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-13, issue [#89](https://github.com/superjojo79/Myrmacta/issues/89)). Cadrage validé par
l'utilisateur le 2026-11-13. Comportement : [spec 11](../specs/11-frontend.md) § « Shell — menu
repliable ». Impacte : `frontend/src/app/AppShell.vue`, `frontend/src/styles/main.css`,
`frontend/src/composables/useShellNavCollapse.ts`, catalogues i18n shell FR/EN,
`docs/guides/GUIDE-UTILISATEUR.md` (chrome).

**Constat.** La barre latérale du shell occupe 220 px en permanence. Sur un écran étroit, elle réduit
trop la zone utile du contenu ; aucun moyen de la replier n'existait.

**D-SHELL-NAV** — la navigation du shell est repliable, sans mémorisation :

1. Breakpoint **768 px** (`max-width: 767.98px`) : en dessous, la nav démarre **repliée** ; au-dessus,
   **ouverte**. Un changement de largeur qui traverse le seuil remet l'état au défaut du nouveau
   côté (pas de `localStorage`).
2. Replié : un **bandeau vertical** reste visible, avec le bouton burger en haut.
3. Ouvert sur grand écran : la grille pousse le contenu (comportement historique). Ouvert sur petit
   écran : **superposition** (tiroir) au-dessus du contenu, avec fond cliquable qui referme.
4. Sur petit écran, un clic sur un lien de navigation referme le tiroir.
5. Le repli est aussi disponible sur desktop (cohérence).

Écartés : mémorisation de l'état ; nav ouverte par défaut sur mobile ; poussée du contenu sur petit
écran à l'ouverture ; icônes de navigation dans le bandeau replié (hors périmètre de #89).
