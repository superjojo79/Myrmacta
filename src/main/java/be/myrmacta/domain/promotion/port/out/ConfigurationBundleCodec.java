package be.myrmacta.domain.promotion.port.out;

import be.myrmacta.domain.promotion.model.ConfigurationBundle;

/** TODO §83 (D4) — archivage du bundle (ZIP) : écriture, et lecture défensive (sommes de contrôle, chemins, tailles). */
public interface ConfigurationBundleCodec {

    byte[] write(ConfigurationBundle bundle);

    /** @throws be.myrmacta.domain.promotion.model.InvalidBundleException archive altérée, format inconnu, chemin hors périmètre */
    ConfigurationBundle read(byte[] archive);
}
