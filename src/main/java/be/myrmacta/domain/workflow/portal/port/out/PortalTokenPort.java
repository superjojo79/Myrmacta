package be.myrmacta.domain.workflow.portal.port.out;

/** Génération et hachage des jetons portail (D-PORTAL-AUTH : 256 bits, seul le hash est stocké). */
public interface PortalTokenPort {

    /** Nouveau jeton opaque, à ne jamais persister ni journaliser. */
    String newToken();

    /** Empreinte stockée et recherchée en base (SHA-256 hexadécimal). */
    String hash(String token);
}
