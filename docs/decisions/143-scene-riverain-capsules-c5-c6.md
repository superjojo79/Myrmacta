# §143 — Scène riverain des capsules C5 et C6 et du screenshot S13

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-16, issue [#101](https://github.com/superjojo79/Myrmacta/issues/101), vague `vague-vitrine`).
Cadrage validé par l'utilisateur le 2026-11-16. C'est la suite de [§140](140-capsules-courtes-du-site.md) : les capsules
C5 et C6 et le screenshot S13 avaient été reportés, faute de données. Lot 1 : variante anglaise de l'exemple et scène.
Build `embedded-db` et outillage de développement seulement, jamais en production. Comportement :
[spec 47](../specs/47-postgresql-embarque-tests-demo.md).

**Constat.** C5 (« L'usager complète sa demande sans compte » : le portail et son lien à jeton), C6 (« Les pièces
incohérentes repérées avant l'agent » : la pré-vérification IA) et S13 (le portail sur mobile) se montrent sur
l'exemple `exemples/autorisation-riverain`. Or cet exemple a été retiré de la démo complète (§127) et n'avait pas de
variante anglaise. Sa variante « avec portail » n'avait jamais été déployée par le serveur, faute de portail activé.

**Décisions tranchées par l'utilisateur (2026-11-16).**

- **D-VIDEO-RIVERAIN-SCENE** : une scène de tournage à part, le profil `demo-video-riverain`
  (`scripts/video.sh start c05-…`).
  - Le paquet `RiverainDemoPack` installe l'exemple par les vrais cas d'usage, dans l'ordre de mise en service de son
    README :
    - le type, avec ses types de pièces ;
    - les questionnaires et la configuration du workflow ;
    - le modèle de décision et le gabarit de demande d'informations, activés ;
    - en dernier, la variante du processus avec portail.
  - La scène active le portail (liens envoyés à Mailpit). Elle active aussi la pré-vérification IA, avec le modèle
    local d'Ollama (`gemma4:12b` par défaut) : aucune donnée ne part chez un tiers.
  - Le riverain ne revient pas dans la démo complète, et `scripts/video.sh shorts` saute C5 et C6.
- **Variante anglaise complète** de l'exemple (D-DEMO-LOCALE-FILES), vérifiée par `DemoTranslationsTest` :
  - `en/config/` : type, questionnaires, configuration, modèle et gabarit ;
  - `en/names.json` pour les trois BPMN ;
  - `en/template-decision.html`.
- **Défauts révélés par la variante avec portail** : un correctif court est fait ici, en TDD ; pour un défaut plus
  lourd, on s'arrête et on ouvre une issue.

**Hypothèses validées (2026-11-16).**

- Les trois BPMN de l'exemple partagent la clé `autorisationRiverain`. La scène déploie la variante avec portail, et
  C6 publie elle-même, hors champ, la variante avec pré-vérification. L'exemple n'est pas modifié.
- En anglais, les pièces de C6 restent en français : ce sont des documents bruxellois réalistes, et seuls l'interface
  et les sous-titres sont traduits.

**Vérifié au lot 1.** La variante avec portail se déploie sur un serveur dont le portail est activé
(`RiverainDemoPackIT`, en français et en anglais).
