package be.myrmacta.domain.deadline.model;

/** TODO §121 (D-CAL-ICS) — pourquoi un événement d'un fichier ICS ne devient pas un jour fermé. */
public enum IcsIgnoreReason {
    /** Événement horaire : seules les journées entières comptent. */
    TIMED,
    /** {@code STATUS:CANCELLED}. */
    CANCELLED,
    /** {@code TRANSP:TRANSPARENT} : n'occupe pas le calendrier. */
    TRANSPARENT,
    /** Dates illisibles ou incohérentes, période de plus de 366 jours, récurrence refusée. */
    INVALID
}
