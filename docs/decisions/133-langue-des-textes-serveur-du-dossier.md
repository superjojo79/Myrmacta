# §133 — Langue des textes serveur posés sur un dossier

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#79](https://github.com/superjojo79/Myrmacta/issues/79), vague `vague-vitrine`). Mandat cloud agent
2026-11-12. Comportement : [spec 04](../specs/04-dossiers.md) § « Implémentation — échéance d'un dossier » ;
[spec 49](../specs/49-digital-mail-room.md) § « Réaction des workflows à une réponse ».

**Constat.** Avec `app.i18n.default-locale=en`, deux textes écrits par le serveur restaient en français : la note
d'escalade d'échéance (`EvaluateDeadlinesUseCaseImpl`) et le libellé du dossier enfant créé quand une réponse
fournisseur rejoint un fil (`AttachToThreadDelegate`, « Réponse DOS-… »). Les notifications de rappel et d'escalade
du même job étaient dans le même cas.

**D-I18N-DOSSIER-TEXT** — ces textes sont rédigés **au moment de l'écriture**, dans la langue par défaut du tenant
(`app.i18n.default-locale`), comme l'invitation de portail et l'invitation de signature :

1. langue `en` : note, notifications de rappel et d'escalade, et libellé « Reply {référence du courrier} » en anglais ;
2. toute autre langue acceptée au démarrage (aujourd'hui seulement `fr`, D-I18N-LANGS) : les phrases françaises
   actuelles, inchangées ;
3. pas de néerlandais : aucun bundle `nl` (interface, `I18nProperties.SUPPORTED`). Une valeur hors `fr|en` est déjà
   refusée au démarrage ;
4. un texte déjà persisté n'est pas réécrit. Le nom des tâches cité dans la note reste celui du BPMN.

Écarté : stocker une clé et des paramètres pour que l'interface traduise. Le libellé et la note sont des chaînes
lues par les listes, la recherche, le MCP et les exports ; une clé brute s'afficherait partout où l'UI ne traduit
pas. La langue choisie dans le navigateur (D-I18N-DETECT) peut différer de celle du tenant : ces textes restent
dans la langue du tenant, comme le message envoyé à un tiers.

**Impacte :** `ServerDossierTexts`, `EvaluateDeadlinesUseCaseImpl`, `DeadlineConfig`, `AttachToThreadDelegate`,
specs 04 et 49.
