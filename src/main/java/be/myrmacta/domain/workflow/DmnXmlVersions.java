package be.myrmacta.domain.workflow;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * DMN version helpers for the integrated editor ({@code dmn-js} opens DMN 1.3 only).
 * TODO §130 (D-DMN-OPEN-CONVERT, D-DMN-GENERATE-13).
 */
public final class DmnXmlVersions {

    /** OMG DMN 1.1 model namespace (legacy tables still shipped or imported). */
    public static final String DMN_11_NAMESPACE = "http://www.omg.org/spec/DMN/20151101";

    /** OMG DMN 1.3 model namespace — target for generation and for {@code dmn-js}. */
    public static final String DMN_13_NAMESPACE = "https://www.omg.org/spec/DMN/20191111/MODEL/";

    /**
     * Exact {@code xmlns="…"} / {@code xmlns='…'} for the DMN 1.1 model namespace — avoids rewriting
     * the same URI if it appears inside a cell text or a Camunda schema location.
     */
    private static final Pattern DMN_11_XMLNS = Pattern.compile(
            "xmlns\\s*=\\s*([\"'])" + Pattern.quote(DMN_11_NAMESPACE) + "\\1");

    private DmnXmlVersions() {}

    /**
     * Rewrites a DMN 1.1 model {@code xmlns} to DMN 1.3 so {@code dmn-js} can open the document,
     * and strips XML comments before the root element so Flowable's schema validator accepts the
     * result ({@code cvc-elt.1.a} when a comment precedes {@code <definitions>} in 1.3).
     * Already-1.3 XML without a leading comment is returned unchanged aside from that strip.
     * Non-DMN XML is returned unchanged. Does not alter rule cell text.
     */
    public static String to13(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        boolean has11 = xml.contains(DMN_11_NAMESPACE);
        boolean has13 = xml.contains(DMN_13_NAMESPACE);
        if (!has11 && !has13) {
            return xml;
        }
        String out = stripCommentsBeforeRoot(xml);
        if (has11) {
            out = DMN_11_XMLNS.matcher(out).replaceAll("xmlns=$1" + DMN_13_NAMESPACE + "$1");
        }
        return out;
    }

    /** Byte-array overload of {@link #to13(String)}; {@code null} stays {@code null}. */
    public static byte[] to13(byte[] xml) {
        if (xml == null) {
            return null;
        }
        return to13(new String(xml, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    private static final String COMMENT_OPEN = "<!--";
    private static final String COMMENT_CLOSE = "-->";
    private static final String XML_DECL_OPEN = "<?xml";
    private static final String XML_DECL_CLOSE = "?>";

    /**
     * Removes {@code <!-- … -->} that appear only before the first element (after an optional
     * {@code <?xml …?>}). Linear in the length of {@code xml}: an unclosed comment stops the scan
     * and leaves the remainder intact. Relies on {@link String#startsWith(String, int)} (no separate
     * length guard — that check is redundant and only produced equivalent PIT mutants).
     */
    static String stripCommentsBeforeRoot(String xml) {
        StringBuilder out = new StringBuilder(xml.length());
        int i = 0;
        int n = xml.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(xml.charAt(i))) {
                out.append(xml.charAt(i));
                i++;
            }
            if (xml.startsWith(COMMENT_OPEN, i)) {
                int searchFrom = i + COMMENT_OPEN.length();
                int end = xml.indexOf(COMMENT_CLOSE, searchFrom);
                // end must fall at/after searchFrom; -1 and any earlier hit are treated as unclosed.
                if (end < searchFrom) {
                    out.append(xml, i, n);
                    return out.toString();
                }
                i = end + COMMENT_CLOSE.length();
                continue;
            }
            if (xml.startsWith(XML_DECL_OPEN, i)) {
                int searchFrom = i + XML_DECL_OPEN.length();
                int end = xml.indexOf(XML_DECL_CLOSE, searchFrom);
                if (end < searchFrom) {
                    out.append(xml, i, n);
                    return out.toString();
                }
                int next = end + XML_DECL_CLOSE.length();
                out.append(xml, i, next);
                i = next;
                continue;
            }
            out.append(xml, i, n);
            break;
        }
        return out.toString();
    }
}
