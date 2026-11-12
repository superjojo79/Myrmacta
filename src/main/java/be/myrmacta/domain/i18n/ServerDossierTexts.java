package be.myrmacta.domain.i18n;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * TODO §133 (D-I18N-DOSSIER-TEXT) — textes posés sur un dossier par le serveur, rédigés à l'écriture dans la langue
 * par défaut du tenant. Anglais si la langue est {@code en} ; français sinon (seules ces deux langues démarrent,
 * D-I18N-LANGS). Un texte déjà persisté n'est pas repris.
 */
public final class ServerDossierTexts {

    private ServerDossierTexts() {}

    public static String deadlineEscalationNote(Locale locale, LocalDate deadline, String configured, String group,
            List<String> reassigned) {
        String head = english(locale)
                ? "Deadline of " + deadline + " passed"
                : "Échéance du " + deadline + " dépassée";
        if (group == null) {
            if (configured == null) {
                return head + (english(locale) ? ": no escalation group" : " : aucun groupe d'escalade");
            }
            return head + (english(locale)
                    ? ": escalation group not found (" + configured + ")"
                    : " : groupe d'escalade introuvable (" + configured + ")");
        }
        if (reassigned.isEmpty()) {
            return head + (english(locale)
                    ? ": group " + group + " notified, no open task"
                    : " : groupe " + group + " prévenu, aucune tâche ouverte");
        }
        return head + (english(locale)
                ? ": " + reassigned.size() + " task(s) reassigned to group " + group + " ("
                : " : " + reassigned.size() + " tâche(s) réaffectée(s) au groupe " + group + " (")
                + String.join(", ", reassigned) + ")";
    }

    public static String deadlineReminderTitle(Locale locale, String reference) {
        return (english(locale) ? "Deadline approaching — " : "Échéance proche — ") + reference;
    }

    public static String deadlineReminderBody(Locale locale, String reference, LocalDate deadline) {
        return english(locale)
                ? "Dossier " + reference + " is due on " + deadline + "."
                : "Le dossier " + reference + " arrive à échéance le " + deadline + ".";
    }

    public static String deadlineEscalationTitle(Locale locale, String reference) {
        return (english(locale) ? "Deadline passed — " : "Échéance dépassée — ") + reference;
    }

    public static String deadlineEscalationBody(Locale locale, String reference, LocalDate deadline, String group) {
        String body = english(locale)
                ? "Dossier " + reference + " missed its deadline of " + deadline + "."
                : "Le dossier " + reference + " a dépassé son échéance du " + deadline + ".";
        if (group == null) {
            return body;
        }
        return body + (english(locale) ? " Escalated to group " + group + "." : " Escaladé au groupe " + group + ".");
    }

    public static String supplierReplyLabel(Locale locale, String mailReference) {
        return (english(locale) ? "Reply " : "Réponse ") + mailReference;
    }

    private static boolean english(Locale locale) {
        return "en".equals(Objects.requireNonNull(locale, "locale").getLanguage());
    }
}
