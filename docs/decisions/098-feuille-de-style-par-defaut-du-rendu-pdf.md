# §98 — Feuille de style par défaut du rendu PDF

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] corrigé. Relevé par la recette de l'exemple riverain du 2026-10-23 (instance locale, Keycloak, Mailpit) : le PDF de décision
reçu en pièce jointe affichait en tête de page le titre et toute la feuille de style du modèle, comme du texte, et sans aucune
mise en page (tableaux et blocs à la suite, sur une ligne).

**Cause.** Le moteur (Flying Saucer) charge sa propre feuille de style par défaut — `head` masqué, tableaux, titres,
paragraphes — par son URL de classpath (`jar:…!/resources/css/XhtmlNamespaceHandler.css`), via l'agent utilisateur.
`RestrictedResourceUserAgent`, qui bloque toute ressource autre que `data:` pour empêcher SSRF et lecture de fichiers locaux,
la bloquait aussi. La feuille de style du modèle s'appliquait (une règle `display: none` masquait bien son élément), mais pas
les règles par défaut. Tous les documents générés étaient touchés ; aucun test ne regardait le texte rendu.

**D-PDF-UA-DEFAULT-CSS** — l'agent utilisateur laisse passer cette feuille et elle seule, par comparaison exacte avec l'URL que
le moteur calcule lui-même (`ITextRenderer.class.getResource("/resources/css/XhtmlNamespaceHandler.css")`). Toute autre
ressource du classpath ou d'un jar reste bloquée, comme `http(s):` et `file:`. Écarté : autoriser le schéma `jar:` (lecture de
toute ressource embarquée) ; copier la feuille dans les modèles (chaque modèle existant resterait cassé).

**Tests.** `FlyingSaucerPdfStylesheetTest` (texte extrait du PDF : ni titre ni CSS, règle du modèle appliquée, pour trois
formes de balise racine, et le modèle de l'exemple riverain) ; `RestrictedResourceUserAgentTest` (seule l'URL exacte passe).

**Impacte :** aucun document, aucune configuration ; les PDF déjà générés ne sont pas régénérés.
