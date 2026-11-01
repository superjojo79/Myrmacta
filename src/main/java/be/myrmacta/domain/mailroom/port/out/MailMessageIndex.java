package be.myrmacta.domain.mailroom.port.out;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** TODO §113 (D-MAILROOM-INDEX) — index des identifiants des e-mails reçus et envoyés. */
public interface MailMessageIndex {

    /** Inscrit l'entrée ; une entrée identique (même clé, même dossier, même sens) n'est pas dupliquée. */
    void record(MailIndexEntry entry);

    /** L'entrée la plus récente pour cette clé, s'il y en a une. */
    Optional<MailIndexEntry> find(String tenantId, MailIndexEntry.Kind kind, String key);

    /** TODO §113 (D-MAILROOM-MCP) — les e-mails reçus et envoyés du fil d'un dossier, du plus récent au plus ancien ({@code limit} au plus). */
    List<MailIndexEntry> findByDossier(String tenantId, UUID dossierId, int limit);
}
