package be.myrmacta.infrastructure.connector.builtin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §112 (D-MAILROOM-THREAD) — en-têtes qui rattachent un e-mail entrant à un fil : {@code In-Reply-To}, {@code References}
 * (RFC 5322 §3.6.4) et, côté Microsoft Graph, {@code conversationId}. Ils viennent de l'expéditeur : bornés en longueur et en
 * nombre, réduits à des identifiants {@code <…>} sans espace ni caractère de contrôle. Ils ne prouvent rien seuls : le
 * rattachement à un dossier exige aussi un expéditeur attesté (D-INTEG-MAIL-SENDER).
 */
record MailThread(String inReplyTo, List<String> references, String conversationId) {

    /** Références gardées : les plus récentes, en fin d'en-tête. */
    static final int MAX_REFERENCES = 10;
    /** Longueur d'un identifiant (Message-ID ou conversationId). */
    static final int MAX_ID_CHARS = 250;
    /** Part d'un en-tête lue : au-delà, le reste est ignoré. */
    static final int MAX_HEADER_CHARS = 8_000;

    static final MailThread NONE = new MailThread("", List.of(), "");

    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s\\p{Cntrl}]+>");
    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9+/=_-]+");

    MailThread {
        inReplyTo = inReplyTo == null ? "" : inReplyTo;
        references = references == null ? List.of() : List.copyOf(references);
        conversationId = conversationId == null ? "" : conversationId;
    }

    static MailThread of(String inReplyTo, String references, String conversationId) {
        List<String> replyIds = ids(inReplyTo);
        Deque<String> kept = new ArrayDeque<>();
        for (String id : ids(references)) {
            if (kept.size() == MAX_REFERENCES) {
                kept.removeFirst();
            }
            kept.addLast(id);
        }
        String conversation = conversationId == null ? "" : conversationId.strip();
        if (conversation.length() > MAX_ID_CHARS || !CONVERSATION_ID.matcher(conversation).matches()) {
            conversation = "";
        }
        MailThread thread = new MailThread(replyIds.isEmpty() ? "" : replyIds.getFirst(), List.copyOf(kept), conversation);
        return thread.isEmpty() ? NONE : thread;
    }

    boolean isEmpty() {
        return inReplyTo.isEmpty() && references.isEmpty() && conversationId.isEmpty();
    }

    private static List<String> ids(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        String bounded = header.length() > MAX_HEADER_CHARS ? header.substring(0, MAX_HEADER_CHARS) : header;
        Matcher matcher = MESSAGE_ID.matcher(bounded);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group();
            if (id.length() <= MAX_ID_CHARS) {
                found.add(id);
            }
        }
        return found;
    }
}
