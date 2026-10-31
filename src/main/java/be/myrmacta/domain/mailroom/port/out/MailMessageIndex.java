package be.myrmacta.domain.mailroom.port.out;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import java.util.Optional;

/** TODO §113 (D-MAILROOM-INDEX) — index des identifiants des e-mails reçus et envoyés. */
public interface MailMessageIndex {

    /** Inscrit l'entrée ; une entrée identique (même clé, même dossier, même sens) n'est pas dupliquée. */
    void record(MailIndexEntry entry);

    /** L'entrée la plus récente pour cette clé, s'il y en a une. */
    Optional<MailIndexEntry> find(String tenantId, MailIndexEntry.Kind kind, String key);
}
