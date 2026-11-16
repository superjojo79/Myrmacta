# §144 — Langue des détails du plan et du rapport de promotion

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#111](https://github.com/superjojo79/Myrmacta/issues/111)). Mandat cloud agent 2026-11-16.
Comportement : [spec 35](../specs/35-promotion-configuration.md) § « Implémentation ».

**Constat.** Dans *Admin → Promotion*, la colonne Détail du plan et celle du rapport viennent du serveur en anglais,
quelle que soit la langue de l'interface. Relevé au tournage de la capsule C12 ([§140](140-capsules-courtes-du-site.md)) :
« created without members: add them on this instance », « updated ».

**D-PROMO-DETAIL-I18N** — l'API d'administration rend ces phrases **déjà traduites** (pas des clés) :

1. le domaine continue d'écrire l'anglais. Les préfixes `WARNING:` et `joined to the BPMN draft` restent des repères
   internes. Le journal `config_import` et le canal MCP gardent ces phrases : un agent et un historique ne changent pas
   de langue selon le navigateur ;
2. `POST /api/admin/configuration/plan` et `/apply` traduisent `reason` et `message` selon `Accept-Language`. `fr`
   (y compris `fr-BE`) donne le français ; toute autre langue, ou l'absence d'en-tête, laisse l'anglais d'origine.
   Le SPA envoie déjà cet en-tête (`D-CONNECTOR-CATALOG-I18N`) ;
3. une phrase absente du catalogue (`PromotionDetailTexts`) reste telle quelle. Les messages de validation venus
   d'un autre module ne sont pas réécrits. Une valeur libre (nom de connecteur, groupe d'IdP, nom de champ) peut
   contenir une apostrophe : elle est reprise telle quelle dans la phrase traduite. Une raison déjà tronquée à
   400 caractères (`PromotionSupport.cap`) dont la fin ne correspond plus au modèle reste en anglais ;
4. écarté : des codes traduits par le SPA. Le MCP et le journal afficheraient des clés, ce que [§142](142-catalogue-connecteurs-libelles-traduits.md)
   a déjà refusé pour le catalogue. Écarté aussi : la langue par défaut du tenant au moment de l'écriture ([§133](133-langue-des-textes-serveur-du-dossier.md)).
   Ces détails ne sont pas posés sur un dossier, et la langue de l'interface peut différer de celle du tenant.

**Impacte :** `PromotionDetailTexts`, `ConfigurationPromotionController`, spec 35, GUIDE-UTILISATEUR (écran Promotion).
