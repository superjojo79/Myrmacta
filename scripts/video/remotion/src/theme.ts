// Cartons du teaser (TODO §148, D-VIDEO-REMOTION, issue #59) : mêmes couleurs, police et logo que les cartons des
// capsules (frontend/capture/panel/card.html), pour que le teaser et les capsules aient le même habillage.

export const FPS = 30;
export const WIDTH = 1920;
export const HEIGHT = 1080;

export const colors = {
  bg1: "#0d1b2a",
  bg2: "#1b263b",
  accent: "#4cc9f0",
  text: "#f1f5f9",
  muted: "#94a3b8",
};

// Police système, comme card.html : aucun appel réseau au rendu.
export const fontFamily = '"Segoe UI", "Inter", system-ui, sans-serif';

// Lu dans --public-dir (frontend/src/assets/brand) : le logo n'est pas recopié dans ce projet.
export const LOGO = "myrmacta-logo-dark.svg";

export const background = `radial-gradient(circle at 30% 20%, ${colors.bg2}, ${colors.bg1} 70%)`;

// Une sortie forte et douce, sans rebond : le ton d'un produit, pas d'un jeu.
export const EASE_OUT = [0.16, 1, 0.3, 1] as const;
