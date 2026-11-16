package be.myrmacta.domain.promotion;

import be.myrmacta.domain.promotion.artifact.PromotionSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §144 (D-PROMO-DETAIL-I18N) — phrases du plan et du rapport, rédigées en anglais par le domaine (journal, MCP,
 * contrôles internes) et rendues ici dans la langue de la requête. Une phrase inconnue reste telle quelle : un message
 * de validation venu d'ailleurs n'est pas inventé.
 */
public final class PromotionDetailTexts {

    private static final String MCP_GROUPS_EN =
            " The user groups of the bundle are not applied through MCP: import them from the admin import screen.";
    private static final String MCP_GROUPS_FR =
            " Les groupes d'utilisateurs du bundle ne sont pas appliqués par MCP : importez-les depuis l'écran d'import d'administration.";
    private static final String LABEL_KEPT_EN = "label updated (members kept); ";
    private static final String IDP_PREFIX = "identity-provider group of this instance kept (";
    private static final String IDP_HERE = " here, ";
    private static final String IDP_SUFFIX = " in the bundle)";
    private static final Pattern AND_MORE = Pattern.compile(" and (\\d+) more");

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("created without members: add them on this instance",
                    "créé sans membres : ajoutez-les sur cette instance"),
            Map.entry("label updated (members kept)", "libellé mis à jour (membres conservés)"),
            Map.entry("label updated (members and identity-provider group kept)",
                    "libellé mis à jour (membres et groupe du fournisseur d'identité conservés)"),
            Map.entry("definition saved, not compiled", "définition enregistrée, non compilée"),
            Map.entry("saved as a draft to publish", "enregistré comme brouillon à publier"),
            Map.entry("created disabled: review its endpoints, then enable it",
                    "créé désactivé : relisez ses adresses, puis activez-le"),
            Map.entry("created disabled — review its endpoints, then enable it",
                    "créé désactivé — relisez ses adresses, puis activez-le"),
            Map.entry("configuration updated (enabled state and secrets kept)",
                    "configuration mise à jour (état activé et secrets conservés)"),
            Map.entry("created", "créé"),
            Map.entry("updated", "mis à jour"),
            Map.entry("unchanged", "inchangé"),
            Map.entry("draft saved — publish it from 'Brouillons à publier'",
                    "brouillon enregistré — publiez-le depuis « Brouillons à publier »"),
            Map.entry("draft saved with its workflow configuration — publish it from 'Brouillons à publier'",
                    "brouillon enregistré avec sa configuration de workflow — publiez-le depuis « Brouillons à publier »"),
            Map.entry("joined to the BPMN draft, applied when it is published",
                    "jointe au brouillon BPMN, appliquée à sa publication"),
            Map.entry("not applied through MCP (only BPMN, DMN and document template drafts are): use the admin import screen",
                    "non appliqué par MCP (seuls les brouillons BPMN, DMN et de templates le sont) : utilisez l'écran d'import d'administration"),
            Map.entry("its BPMN process is blocked: the configuration is not applied alone on top of the old diagram",
                    "son processus BPMN est bloqué : la configuration n'est pas appliquée seule par-dessus l'ancien diagramme"),
            Map.entry("its BPMN draft was not saved, so the configuration was not applied",
                    "son brouillon BPMN n'a pas été enregistré, la configuration n'a donc pas été appliquée"),
            Map.entry("the template has no content", "le template n'a pas de contenu"),
            Map.entry("saved as a new draft version: activate it in 'Templates'",
                    "enregistré comme nouvelle version brouillon : activez-la dans « Templates »"),
            Map.entry("saved as a new draft version: activate it in the notification templates screen",
                    "enregistré comme nouvelle version brouillon : activez-la dans l'écran des gabarits de notification"),
            Map.entry("invalid calendar", "calendrier invalide"),
            Map.entry("calendar holidays and entries are mutually exclusive",
                    "les jours fériés et les entrées du calendrier s'excluent"),
            Map.entry("on the source, this definition changed after its last compilation or its deployed process is not "
                            + "that compilation: the BPMN of this bundle may not match it.",
                    "sur la source, cette définition a changé après sa dernière compilation, ou le processus déployé "
                            + "n'est pas cette compilation : le BPMN de ce bundle peut ne pas lui correspondre."),
            Map.entry("an address of an ENABLED connector changes, its secrets are kept and will be sent there.",
                    "une adresse d'un connecteur ACTIVÉ change ; ses secrets sont conservés et y seront envoyés."));

    private PromotionDetailTexts() {}

    public static String localize(String text, Locale locale) {
        if (text == null || text.isEmpty() || !french(locale)) {
            return text == null ? "" : text;
        }
        return render(text);
    }

    private static boolean french(Locale locale) {
        return locale != null && "fr".equalsIgnoreCase(locale.getLanguage());
    }

    private static String render(String text) {
        if (!text.startsWith(PromotionSupport.WARNING)) {
            return sentence(text);
        }
        String warnings = PromotionSupport.warningsOf(text);
        String rest = text.substring(warnings.length());
        if (rest.startsWith(PromotionSupport.AFTER_WARNING)) {
            rest = rest.substring(PromotionSupport.AFTER_WARNING.length());
        }
        List<String> translated = new ArrayList<>();
        for (String part : warnings.split(Pattern.quote(PromotionSupport.AFTER_WARNING), -1)) {
            String body = part.startsWith(PromotionSupport.WARNING)
                    ? part.substring(PromotionSupport.WARNING.length())
                    : part;
            translated.add("AVERTISSEMENT : " + sentence(body));
        }
        String head = String.join(PromotionSupport.AFTER_WARNING, translated);
        return rest.isEmpty() ? head : head + PromotionSupport.AFTER_WARNING + sentence(rest);
    }

    private static String sentence(String text) {
        String suffix = "";
        if (text.endsWith(MCP_GROUPS_EN)) {
            suffix = MCP_GROUPS_FR;
            text = text.substring(0, text.length() - MCP_GROUPS_EN.length());
        }
        String exact = EXACT.get(text);
        if (exact != null) {
            return exact + suffix;
        }
        String patterned = pattern(text);
        return (patterned == null ? text : patterned) + suffix;
    }

    private static String pattern(String text) {
        if (text.startsWith(LABEL_KEPT_EN)) {
            String note = idpNote(text.substring(LABEL_KEPT_EN.length()));
            if (note != null) {
                return "libellé mis à jour (membres conservés) ; " + note;
            }
        }
        String idp = idpNote(text);
        if (idp != null) {
            return idp;
        }
        String dated = dateField(text);
        if (dated != null) {
            return dated;
        }
        String connectorParameter = connectorParameter(text);
        if (connectorParameter != null) {
            return connectorParameter;
        }
        return first(text,
                rule("replaces the definition of this instance \\(version (\\d+)\\), not compiled",
                        m -> "remplace la définition de cette instance (version " + m.group(1) + "), sans compilation"),
                rule("created \\(version (\\d+)\\), not compiled",
                        m -> "créé (version " + m.group(1) + "), non compilé"),
                rule("updated \\(version (\\d+)\\), not compiled",
                        m -> "mis à jour (version " + m.group(1) + "), non compilé"),
                rule("saved as a draft to publish; publishing it will be refused until these referenced resources, "
                                + "which exist but are not active, are activated: (.+)",
                        m -> "enregistré comme brouillon à publier ; la publication sera refusée tant que ces ressources "
                                + "référencées, présentes mais inactives, ne sont pas activées : " + m.group(1)),
                rule("configuration updated \\(enabled state and secrets kept\\): (.+)",
                        m -> "configuration mise à jour (état activé et secrets conservés) : " + absent(m.group(1))),
                rule("the template exceeds (\\d+) bytes",
                        m -> "le template dépasse " + m.group(1) + " octets"),
                rule("its syntax would be refused at activation: (.+)",
                        m -> "sa syntaxe serait refusée à l'activation : " + m.group(1)),
                rule("it would be refused at activation: (.+)",
                        m -> "il serait refusé à l'activation : " + m.group(1)),
                rule("already saved as draft version (\\d+): activate it in 'Templates'",
                        m -> "déjà enregistré comme brouillon version " + m.group(1) + " : activez-le dans « Templates »"),
                rule("draft version (\\d+) saved: activate it in 'Templates'",
                        m -> "brouillon version " + m.group(1) + " enregistré : activez-le dans « Templates »"),
                rule("already saved as draft version (\\d+): activate it in the notification templates screen",
                        m -> "déjà enregistré comme brouillon version " + m.group(1)
                                + " : activez-le dans l'écran des gabarits de notification"),
                rule("draft version (\\d+) saved: activate it in the notification templates screen",
                        m -> "brouillon version " + m.group(1) + " enregistré : activez-le dans l'écran des gabarits de notification"),
                rule("(\\d+) existing dossier\\(s\\) would no longer match this schema \\(e\\.g\\. (.+)\\)",
                        m -> m.group(1) + " dossier(s) existant(s) ne correspondraient plus à ce schéma (p. ex. " + m.group(2) + ")"),
                rule("(\\d+) connectors are named '(.+)' on this instance: rename or delete the duplicates first",
                        m -> m.group(1) + " connecteurs portent le nom « " + m.group(2)
                                + " » sur cette instance : renommez ou supprimez les doublons d'abord"),
                rule("a connector named '(.+)' exists with another type \\(([^)]*)\\)",
                        m -> "un connecteur nommé « " + m.group(1) + " » existe avec un autre type (" + m.group(2) + ")"),
                rule("cannot be created: (.+)", m -> "ne peut pas être créé : " + m.group(1)),
                rule("no workflow configuration for '([^']*)', in the bundle or on this instance",
                        m -> "aucune configuration de workflow pour « " + m.group(1) + " », ni dans le bundle ni sur cette instance"),
                rule("depends on the decision table '([^']*)', which is only a draft: publish it first, then run the import again",
                        m -> "dépend de la table de décision « " + m.group(1)
                                + " », qui n'est qu'un brouillon : publiez-la d'abord, puis relancez l'import"),
                rule("depends on the document template '([^']*)', which this import saves as a draft: once it is saved, "
                                + "run the import again \\(and activate the template before publishing this process\\)",
                        m -> "dépend du template de document « " + m.group(1)
                                + " », que cet import enregistre comme brouillon : une fois enregistré, relancez l'import "
                                + "(et activez le template avant de publier ce processus)"),
                rule("child dossier type '([^']*)' is neither on this instance nor applicable in the bundle",
                        m -> "le type de dossier enfant « " + m.group(1) + " » n'est ni sur cette instance ni applicable dans le bundle"),
                rule("dossier type '([^']*)' is neither on this instance nor applicable in the bundle",
                        m -> "le type de dossier « " + m.group(1) + " » n'est ni sur cette instance ni applicable dans le bundle"),
                rule("the workflow configuration '([^']*)' only exists once its BPMN draft is published: publish it, then run the import again",
                        m -> "la configuration de workflow « " + m.group(1)
                                + " » n'existera qu'à la publication de son brouillon BPMN : publiez-le, puis relancez l'import"),
                rule("no workflow configuration '([^']*)' on this instance or applicable in the bundle",
                        m -> "aucune configuration de workflow « " + m.group(1)
                                + " » sur cette instance ou applicable dans le bundle"),
                rule("the escalation group '([^']*)' is neither on this instance nor applicable in the bundle",
                        m -> "le groupe d'escalade « " + m.group(1) + " » n'est ni sur cette instance ni applicable dans le bundle"),
                rule("user group '([^']*)' needs the environment parameter '([^']*)'",
                        m -> "le groupe d'utilisateurs « " + m.group(1) + " » a besoin du paramètre d'environnement « " + m.group(2) + " »"),
                rule("code must be 2 to (\\d+) characters: A-Z, 0-9 or _, starting with a letter",
                        m -> "le code doit faire de 2 à " + m.group(1) + " caractères : A-Z, 0-9 ou _, et commencer par une lettre"),
                rule("label is required, at most (\\d+) characters, without control characters",
                        m -> "le libellé est obligatoire, " + m.group(1) + " caractères au plus, sans caractère de contrôle"),
                rule("idpGroup must be at most (\\d+) characters, without control characters",
                        m -> "le groupe du fournisseur d'identité fait " + m.group(1) + " caractères au plus, sans caractère de contrôle"),
                rule("a group has at most (\\d+) listed members",
                        m -> "un groupe a au plus " + m.group(1) + " membres listés"),
                rule("names user group\\(s\\) unknown here and absent from the bundle: (.+) "
                                + "\\(their tasks stay unassigned, mail goes to the sorting queue\\)\\.",
                        m -> "nomme un ou des groupes d'utilisateurs inconnus ici et absents du bundle : " + more(m.group(1))
                                + " (leurs tâches restent non assignées, le courrier va à la file de tri)."),
                rule("routes to user group\\(s\\) unknown here and absent from the bundle: (.+) "
                                + "\\(such mail goes to the sorting queue\\)\\.",
                        m -> "route vers un ou des groupes d'utilisateurs inconnus ici et absents du bundle : " + more(m.group(1))
                                + " (ce courrier va à la file de tri)."),
                rule("document template key\\(s\\) without an active version here nor a template in the bundle: (.+) "
                                + "\\(generating these documents will fail\\)\\.",
                        m -> "clé(s) de template de document sans version active ici ni template dans le bundle : " + m.group(1)
                                + " (la génération de ces documents échouera)."));
    }

    @SafeVarargs
    private static String first(String text, java.util.function.Function<String, String>... rules) {
        for (var rule : rules) {
            String rendered = rule.apply(text);
            if (rendered != null) {
                return rendered;
            }
        }
        return null;
    }

    private static java.util.function.Function<String, String> rule(String regex, java.util.function.Function<Matcher, String> render) {
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        return text -> {
            Matcher matcher = pattern.matcher(text);
            return matcher.matches() ? render.apply(matcher) : null;
        };
    }

    /** Les deux valeurs sont {@code none} ou une valeur entre apostrophes, apostrophe intérieure permise. */
    private static String idpNote(String text) {
        if (!text.startsWith(IDP_PREFIX) || !text.endsWith(IDP_SUFFIX)) {
            return null;
        }
        String inner = text.substring(IDP_PREFIX.length(), text.length() - IDP_SUFFIX.length());
        int cut = inner.indexOf(IDP_HERE);
        if (cut < 0) {
            return null;
        }
        String here = inner.substring(0, cut);
        String bundle = inner.substring(cut + IDP_HERE.length());
        if (!quotedOrNone(here) || !quotedOrNone(bundle)) {
            return null;
        }
        return "groupe du fournisseur d'identité de cette instance conservé ("
                + place(here) + " ici, " + place(bundle) + " dans le bundle)";
    }

    private static boolean quotedOrNone(String token) {
        return "none".equals(token) || (token.length() >= 2 && token.startsWith("'") && token.endsWith("'"));
    }

    private static String dateField(String text) {
        String mid = "' is not a DATE field of the dossier type '";
        if (!text.startsWith("'") || !text.endsWith("'")) {
            return null;
        }
        int cut = text.indexOf(mid);
        if (cut <= 0) {
            return null;
        }
        String field = text.substring(1, cut);
        String type = text.substring(cut + mid.length(), text.length() - 1);
        if (field.isEmpty() || type.isEmpty() || type.indexOf('\'') >= 0) {
            return null;
        }
        return "« " + field + " » n'est pas un champ DATE du type de dossier « " + type + " »";
    }

    private static String connectorParameter(String text) {
        String prefix = "connector '";
        String mid = "' needs the environment parameter '";
        if (!text.startsWith(prefix) || !text.endsWith("'")) {
            return null;
        }
        int cut = text.indexOf(mid);
        if (cut <= prefix.length()) {
            return null;
        }
        String name = text.substring(prefix.length(), cut);
        String parameter = text.substring(cut + mid.length(), text.length() - 1);
        if (name.isEmpty() || !parameter.matches("[A-Za-z0-9_.-]{1,64}")) {
            return null;
        }
        return "le connecteur « " + name + " » a besoin du paramètre d'environnement « " + parameter + " »";
    }

    private static String place(String token) {
        if ("none".equals(token)) {
            return "aucun";
        }
        return "« " + token.substring(1, token.length() - 1) + " »";
    }

    private static String more(String list) {
        return AND_MORE.matcher(list).replaceAll(" et $1 de plus");
    }

    private static String absent(String changes) {
        return changes.replace("(absent) →", "(manquant) →");
    }
}
