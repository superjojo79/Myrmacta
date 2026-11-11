package be.myrmacta.infrastructure.demo;

/**
 * TODO §127 (D-DEMO-PACKS, issue #55) — un paquet de la démo : un exemple (mail room, onboarding, riverain) ou un jeu
 * d'états, installé par les vrais cas d'usage. {@link DemoSeeder} installe, dans l'ordre, chaque paquet que
 * {@link #installed()} dit absent. Build {@code embedded-db} seulement (D-DEMO-SCOPE).
 */
public interface DemoPack {

    /** Code court du paquet, pour les journaux et les erreurs. */
    String code();

    /** Rang d'installation (croissant, unique) : un paquet peut s'appuyer sur ceux qui le précèdent. */
    int order();

    /** Vrai si le paquet est déjà présent sur la base : il n'est alors pas réinstallé. */
    boolean installed();

    /** Installe le paquet sur une base où il est absent. */
    void install();
}
