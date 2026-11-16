package be.myrmacta.domain.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.promotion.artifact.PromotionSupport;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** TODO §144 — un exemple de chaque détail du plan et du rapport, en français et en anglais. */
class PromotionDetailTextsTest {

    @ParameterizedTest
    @MethodSource("details")
    void eachDetailFollowsTheLocale(String english, String french) {
        assertThat(PromotionDetailTexts.localize(english, Locale.FRENCH)).isEqualTo(french);
        assertThat(PromotionDetailTexts.localize(english, Locale.forLanguageTag("fr-BE"))).isEqualTo(french);
        assertThat(PromotionDetailTexts.localize(english, Locale.ENGLISH)).isEqualTo(english);
        assertThat(PromotionDetailTexts.localize(english, null)).isEqualTo(english);
    }

    @Test
    void aWarningKeepsItsPlaceAndTheFollowingReasonIsTranslatedToo() {
        String english = PromotionSupport.WARNING
                + "routes to user group(s) unknown here and absent from the bundle: [JURIDIQUE] "
                + "(such mail goes to the sorting queue)."
                + PromotionSupport.AFTER_WARNING
                + "saved as a draft to publish";
        assertThat(PromotionDetailTexts.localize(english, Locale.FRENCH)).isEqualTo(
                "AVERTISSEMENT : route vers un ou des groupes d'utilisateurs inconnus ici et absents du bundle : "
                        + "[JURIDIQUE] (ce courrier va à la file de tri)."
                        + PromotionSupport.AFTER_WARNING
                        + "enregistré comme brouillon à publier");
    }

    @Test
    void anUnknownSentenceStaysAsWritten() {
        assertThat(PromotionDetailTexts.localize("validator said no", Locale.FRENCH)).isEqualTo("validator said no");
        assertThat(PromotionDetailTexts.localize("", Locale.FRENCH)).isEmpty();
        assertThat(PromotionDetailTexts.localize(null, Locale.FRENCH)).isEmpty();
    }

    static Stream<Arguments> details() {
        return Stream.of(
                args("created without members: add them on this instance",
                        "créé sans membres : ajoutez-les sur cette instance"),
                args("label updated (members kept)", "libellé mis à jour (membres conservés)"),
                args("label updated (members and identity-provider group kept)",
                        "libellé mis à jour (membres et groupe du fournisseur d'identité conservés)"),
                args("identity-provider group of this instance kept ('sg-prod' here, 'sg-recette' in the bundle)",
                        "groupe du fournisseur d'identité de cette instance conservé (« sg-prod » ici, « sg-recette » dans le bundle)"),
                args("identity-provider group of this instance kept ('O'Brien' here, 'Service d'envoi' in the bundle)",
                        "groupe du fournisseur d'identité de cette instance conservé (« O'Brien » ici, « Service d'envoi » dans le bundle)"),
                args("identity-provider group of this instance kept (none here, 'sg-recette' in the bundle)",
                        "groupe du fournisseur d'identité de cette instance conservé (aucun ici, « sg-recette » dans le bundle)"),
                args("label updated (members kept); identity-provider group of this instance kept ('sg-prod' here, none in the bundle)",
                        "libellé mis à jour (membres conservés) ; groupe du fournisseur d'identité de cette instance conservé (« sg-prod » ici, aucun dans le bundle)"),
                args("definition saved, not compiled", "définition enregistrée, non compilée"),
                args("replaces the definition of this instance (version 4), not compiled",
                        "remplace la définition de cette instance (version 4), sans compilation"),
                args("created (version 2), not compiled", "créé (version 2), non compilé"),
                args("updated (version 3), not compiled", "mis à jour (version 3), non compilé"),
                args("saved as a draft to publish", "enregistré comme brouillon à publier"),
                args("saved as a draft to publish; publishing it will be refused until these referenced resources, "
                                + "which exist but are not active, are activated: documentTemplates 'lettre'",
                        "enregistré comme brouillon à publier ; la publication sera refusée tant que ces ressources "
                                + "référencées, présentes mais inactives, ne sont pas activées : documentTemplates 'lettre'"),
                args("created disabled: review its endpoints, then enable it",
                        "créé désactivé : relisez ses adresses, puis activez-le"),
                args("created disabled — review its endpoints, then enable it",
                        "créé désactivé — relisez ses adresses, puis activez-le"),
                args("configuration updated (enabled state and secrets kept)",
                        "configuration mise à jour (état activé et secrets conservés)"),
                args("configuration updated (enabled state and secrets kept): {url=(absent) → https://erp.example}",
                        "configuration mise à jour (état activé et secrets conservés) : {url=(manquant) → https://erp.example}"),
                args("configuration updated (enabled state and secrets kept): {path=https://erp.example/(absent)/hook → https://erp.example/x}",
                        "configuration mise à jour (état activé et secrets conservés) : {path=https://erp.example/(absent)/hook → https://erp.example/x}"),
                args("created", "créé"),
                args("updated", "mis à jour"),
                args("unchanged", "inchangé"),
                args("draft saved — publish it from 'Brouillons à publier'",
                        "brouillon enregistré — publiez-le depuis « Brouillons à publier »"),
                args("draft saved with its workflow configuration — publish it from 'Brouillons à publier'",
                        "brouillon enregistré avec sa configuration de workflow — publiez-le depuis « Brouillons à publier »"),
                args("joined to the BPMN draft, applied when it is published",
                        "jointe au brouillon BPMN, appliquée à sa publication"),
                args("not applied through MCP (only BPMN, DMN and document template drafts are): use the admin import screen",
                        "non appliqué par MCP (seuls les brouillons BPMN, DMN et de templates le sont) : utilisez l'écran d'import d'administration"),
                args("its BPMN process is blocked: the configuration is not applied alone on top of the old diagram",
                        "son processus BPMN est bloqué : la configuration n'est pas appliquée seule par-dessus l'ancien diagramme"),
                args("its BPMN draft was not saved, so the configuration was not applied",
                        "son brouillon BPMN n'a pas été enregistré, la configuration n'a donc pas été appliquée"),
                args("the template has no content", "le template n'a pas de contenu"),
                args("the template exceeds 5000000 bytes", "le template dépasse 5000000 octets"),
                args("its syntax would be refused at activation: unknown tag",
                        "sa syntaxe serait refusée à l'activation : unknown tag"),
                args("it would be refused at activation: subject is required",
                        "il serait refusé à l'activation : subject is required"),
                args("already saved as draft version 7: activate it in 'Templates'",
                        "déjà enregistré comme brouillon version 7 : activez-le dans « Templates »"),
                args("saved as a new draft version: activate it in 'Templates'",
                        "enregistré comme nouvelle version brouillon : activez-la dans « Templates »"),
                args("draft version 8 saved: activate it in 'Templates'",
                        "brouillon version 8 enregistré : activez-le dans « Templates »"),
                args("already saved as draft version 2: activate it in the notification templates screen",
                        "déjà enregistré comme brouillon version 2 : activez-le dans l'écran des gabarits de notification"),
                args("saved as a new draft version: activate it in the notification templates screen",
                        "enregistré comme nouvelle version brouillon : activez-la dans l'écran des gabarits de notification"),
                args("draft version 3 saved: activate it in the notification templates screen",
                        "brouillon version 3 enregistré : activez-le dans l'écran des gabarits de notification"),
                args("2 existing dossier(s) would no longer match this schema (e.g. [DOS-1, DOS-2])",
                        "2 dossier(s) existant(s) ne correspondraient plus à ce schéma (p. ex. [DOS-1, DOS-2])"),
                args("2 connectors are named 'ERP' on this instance: rename or delete the duplicates first",
                        "2 connecteurs portent le nom « ERP » sur cette instance : renommez ou supprimez les doublons d'abord"),
                args("2 connectors are named 'Service d'envoi' on this instance: rename or delete the duplicates first",
                        "2 connecteurs portent le nom « Service d'envoi » sur cette instance : renommez ou supprimez les doublons d'abord"),
                args("a connector named 'ERP' exists with another type (smtp)",
                        "un connecteur nommé « ERP » existe avec un autre type (smtp)"),
                args("a connector named 'Service d'envoi' exists with another type (smtp)",
                        "un connecteur nommé « Service d'envoi » existe avec un autre type (smtp)"),
                args("cannot be created: url is required", "ne peut pas être créé : url is required"),
                args("no workflow configuration for 'facture', in the bundle or on this instance",
                        "aucune configuration de workflow pour « facture », ni dans le bundle ni sur cette instance"),
                args("depends on the decision table 'delai', which is only a draft: publish it first, then run the import again",
                        "dépend de la table de décision « delai », qui n'est qu'un brouillon : publiez-la d'abord, puis relancez l'import"),
                args("depends on the document template 'lettre', which this import saves as a draft: once it is saved, "
                                + "run the import again (and activate the template before publishing this process)",
                        "dépend du template de document « lettre », que cet import enregistre comme brouillon : une fois "
                                + "enregistré, relancez l'import (et activez le template avant de publier ce processus)"),
                args("dossier type 'FACTURE' is neither on this instance nor applicable in the bundle",
                        "le type de dossier « FACTURE » n'est ni sur cette instance ni applicable dans le bundle"),
                args("child dossier type 'ENFANT' is neither on this instance nor applicable in the bundle",
                        "le type de dossier enfant « ENFANT » n'est ni sur cette instance ni applicable dans le bundle"),
                args("the workflow configuration 'facture' only exists once its BPMN draft is published: publish it, then run the import again",
                        "la configuration de workflow « facture » n'existera qu'à la publication de son brouillon BPMN : publiez-le, puis relancez l'import"),
                args("no workflow configuration 'facture' on this instance or applicable in the bundle",
                        "aucune configuration de workflow « facture » sur cette instance ou applicable dans le bundle"),
                args("'echeance' is not a DATE field of the dossier type 'FACTURE'",
                        "« echeance » n'est pas un champ DATE du type de dossier « FACTURE »"),
                args("'date d'échéance' is not a DATE field of the dossier type 'FACTURE'",
                        "« date d'échéance » n'est pas un champ DATE du type de dossier « FACTURE »"),
                args("the escalation group 'ACHATS' is neither on this instance nor applicable in the bundle",
                        "le groupe d'escalade « ACHATS » n'est ni sur cette instance ni applicable dans le bundle"),
                args("invalid calendar", "calendrier invalide"),
                args("calendar holidays and entries are mutually exclusive",
                        "les jours fériés et les entrées du calendrier s'excluent"),
                args("connector 'ERP' needs the environment parameter 'url_erp'",
                        "le connecteur « ERP » a besoin du paramètre d'environnement « url_erp »"),
                args("connector 'Service d'envoi' needs the environment parameter 'url_erp'",
                        "le connecteur « Service d'envoi » a besoin du paramètre d'environnement « url_erp »"),
                args("user group 'ACHATS' needs the environment parameter 'idp_groupe'",
                        "le groupe d'utilisateurs « ACHATS » a besoin du paramètre d'environnement « idp_groupe »"),
                args("code must be 2 to 32 characters: A-Z, 0-9 or _, starting with a letter",
                        "le code doit faire de 2 à 32 caractères : A-Z, 0-9 ou _, et commencer par une lettre"),
                args("label is required, at most 200 characters, without control characters",
                        "le libellé est obligatoire, 200 caractères au plus, sans caractère de contrôle"),
                args("idpGroup must be at most 200 characters, without control characters",
                        "le groupe du fournisseur d'identité fait 200 caractères au plus, sans caractère de contrôle"),
                args("a group has at most 500 listed members", "un groupe a au plus 500 membres listés"),
                args("on the source, this definition changed after its last compilation or its deployed process is not "
                                + "that compilation: the BPMN of this bundle may not match it.",
                        "sur la source, cette définition a changé après sa dernière compilation, ou le processus déployé "
                                + "n'est pas cette compilation : le BPMN de ce bundle peut ne pas lui correspondre."),
                args("an address of an ENABLED connector changes, its secrets are kept and will be sent there.",
                        "une adresse d'un connecteur ACTIVÉ change ; ses secrets sont conservés et y seront envoyés."),
                args("names user group(s) unknown here and absent from the bundle: [RH, COMPTA] and 3 more "
                                + "(their tasks stay unassigned, mail goes to the sorting queue).",
                        "nomme un ou des groupes d'utilisateurs inconnus ici et absents du bundle : [RH, COMPTA] et 3 de plus "
                                + "(leurs tâches restent non assignées, le courrier va à la file de tri)."),
                args("routes to user group(s) unknown here and absent from the bundle: [JURIDIQUE] "
                                + "(such mail goes to the sorting queue). The user groups of the bundle are not applied "
                                + "through MCP: import them from the admin import screen.",
                        "route vers un ou des groupes d'utilisateurs inconnus ici et absents du bundle : [JURIDIQUE] "
                                + "(ce courrier va à la file de tri). Les groupes d'utilisateurs du bundle ne sont pas "
                                + "appliqués par MCP : importez-les depuis l'écran d'import d'administration."),
                args("document template key(s) without an active version here nor a template in the bundle: [lettre] "
                                + "(generating these documents will fail).",
                        "clé(s) de template de document sans version active ici ni template dans le bundle : [lettre] "
                                + "(la génération de ces documents échouera)."));
    }

    private static Arguments args(String english, String french) {
        return Arguments.of(english, french);
    }
}
