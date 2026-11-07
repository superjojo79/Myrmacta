package be.myrmacta.infrastructure.notification.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Politique du corps HTML d'un gabarit de notification (TODO §123).
 *
 * <p>Deux usages, deux postures :
 *
 * <ul>
 *   <li>{@link #violations(String)} à l'activation : <strong>refuse</strong> et liste ce qui sort de la politique. Rien
 *       n'est nettoyé en silence ;
 *   <li>{@link #clean(String)} au rendu, sur le HTML déjà évalué : défense en profondeur, mêmes balises et mêmes
 *       attributs (sans les attributs {@code th:*}, déjà consommés par Thymeleaf).
 * </ul>
 *
 * <p>Les messages nomment la balise, l'attribut ou la règle (et la ligne quand jsoup la donne), jamais une valeur.
 */
final class NotificationHtmlPolicy {

    static final Set<String> ALLOWED_TAGS = Set.of(
            "a", "b", "blockquote", "br", "caption", "code", "div", "em", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i",
            "li", "ol", "p", "pre", "small", "span", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th",
            "thead", "tr", "u", "ul",
            // document complet
            "html", "head", "body", "title", "meta");

    /** Attributs permis sur toutes les balises. */
    static final Set<String> ALLOWED_GLOBAL_ATTRIBUTES = Set.of("style", "class", "align", "colspan", "rowspan", "width", "title");

    /** Attributs Thymeleaf permis (sans le préfixe) ; {@code block} est l'élément {@code th:block}. */
    static final Set<String> ALLOWED_THYMELEAF_NAMES =
            Set.of("text", "if", "unless", "each", "with", "switch", "case", "remove", "block");

    static final Set<String> ALLOWED_LINK_PROTOCOLS = Set.of("http", "https", "mailto");

    private static final Pattern THYMELEAF_NAME =
            Pattern.compile("(?<![A-Za-z0-9_-])(?:th:|data-th-)([A-Za-z][A-Za-z0-9_-]*)", Pattern.CASE_INSENSITIVE);

    private static final List<String> FORBIDDEN_STYLE_TOKENS = List.of("url(", "expression(", "@import", "javascript:");

    private static final Safelist SAFELIST = buildSafelist();

    private NotificationHtmlPolicy() {}

    /** Liste les violations du gabarit HTML (vide : conforme). */
    static List<String> violations(String html) {
        List<String> violations = new ArrayList<>();
        scanThymeleafNames(html, violations);
        Document document = Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
        for (Element element : document.getAllElements()) {
            String tag = element.normalName();
            if (element == document) {
                continue;
            }
            if (isThymeleafBlock(tag)) {
                continue;
            }
            if (!ALLOWED_TAGS.contains(tag)) {
                violations.add("tag <%s> is not allowed%s".formatted(tag, where(element)));
                continue;
            }
            for (Attribute attribute : element.attributes()) {
                checkAttribute(tag, attribute, element, violations);
            }
        }
        return List.copyOf(violations);
    }

    /** Nettoie le HTML rendu : tout ce qui sort de la politique est retiré. Renvoie un document complet, sans mise en forme. */
    static String clean(String renderedHtml) {
        Document dirty = Jsoup.parse(renderedHtml);
        for (Element element : dirty.getAllElements()) {
            if (element.hasAttr("style") && !isSafeStyle(element.attr("style"))) {
                element.removeAttr("style");
            }
        }
        Document cleaned = new Cleaner(SAFELIST).clean(dirty);
        cleaned.outputSettings().prettyPrint(false);
        return cleaned.outerHtml();
    }

    static boolean isSafeStyle(String style) {
        if (style.indexOf('\\') >= 0) {
            return false;
        }
        String compact = compact(style);
        return FORBIDDEN_STYLE_TOKENS.stream().noneMatch(compact::contains);
    }

    static boolean isAllowedLink(String href) {
        String value = stripLeading(href).toLowerCase(Locale.ROOT);
        return ALLOWED_LINK_PROTOCOLS.stream().anyMatch(protocol -> value.startsWith(protocol + ":"))
                && (!value.startsWith("http") || value.startsWith("http://") || value.startsWith("https://"));
    }

    private static void checkAttribute(String tag, Attribute attribute, Element element, List<String> violations) {
        String name = attribute.getKey().toLowerCase(Locale.ROOT);
        String value = attribute.getValue();
        if (isThymeleafAttribute(name)) {
            // Autorisé s'il figure dans la liste ; les autres th:* sont déjà signalés par le balayage du texte brut.
            return;
        }
        boolean allowed = ALLOWED_GLOBAL_ATTRIBUTES.contains(name)
                || ("a".equals(tag) && "href".equals(name))
                || ("meta".equals(tag) && "charset".equals(name));
        if (!allowed) {
            violations.add("attribute %s on <%s> is not allowed%s".formatted(name, tag, where(element)));
            return;
        }
        if ("style".equals(name) && !isSafeStyle(value)) {
            violations.add("attribute style on <%s> must not contain url(, expression(, @import, javascript: or a backslash%s"
                    .formatted(tag, where(element)));
        }
        if ("href".equals(name) && !isAllowedLink(value)) {
            violations.add("attribute href on <a> must start with http://, https:// or mailto:%s".formatted(where(element)));
        }
    }

    /** Balayage du texte brut : une analyse du DOM par jsoup ne vaut pas celle du parseur de Thymeleaf. */
    private static void scanThymeleafNames(String html, List<String> violations) {
        Matcher matcher = THYMELEAF_NAME.matcher(html);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_THYMELEAF_NAMES.contains(name)) {
                String prefix = matcher.group().toLowerCase(Locale.ROOT).startsWith("th:") ? "th:" : "data-th-";
                violations.add("Thymeleaf %s%s is not allowed (allowed: th:text, th:if, th:unless, th:each, th:with, "
                        .formatted(prefix, name)
                        + "th:switch, th:case, th:remove and the th:block element) at line %d"
                                .formatted(lineOf(html, matcher.start())));
            }
        }
    }

    private static boolean isThymeleafBlock(String tag) {
        return "th:block".equals(tag);
    }

    private static boolean isThymeleafAttribute(String name) {
        return name.startsWith("th:") || name.startsWith("data-th-");
    }

    private static String where(Element element) {
        Range range = element.sourceRange();
        return range.isTracked() ? " (line %d)".formatted(range.start().lineNumber()) : "";
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Minuscules, sans blanc ni caractère de contrôle : {@code U R L (} ne doit pas échapper au contrôle. */
    private static String compact(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isISOControl(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static String stripLeading(String value) {
        int start = 0;
        while (start < value.length()
                && (Character.isWhitespace(value.charAt(start)) || Character.isISOControl(value.charAt(start)))) {
            start++;
        }
        return value.substring(start);
    }

    private static Safelist buildSafelist() {
        Safelist safelist = new Safelist()
                .addTags(ALLOWED_TAGS.toArray(String[]::new))
                .addAttributes(":all", ALLOWED_GLOBAL_ATTRIBUTES.toArray(String[]::new))
                .addAttributes("a", "href")
                .addAttributes("meta", "charset")
                .addProtocols("a", "href", ALLOWED_LINK_PROTOCOLS.toArray(String[]::new));
        return safelist;
    }
}
