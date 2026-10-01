package com.flowforge.domain.promotion.port.out;

import com.flowforge.domain.promotion.model.ConfigurationBundle;

/** TODO §83 (D4) — archivage du bundle (ZIP) : écriture, et lecture défensive (sommes de contrôle, chemins, tailles). */
public interface ConfigurationBundleCodec {

    byte[] write(ConfigurationBundle bundle);

    /** @throws com.flowforge.domain.promotion.model.InvalidBundleException archive altérée, format inconnu, chemin hors périmètre */
    ConfigurationBundle read(byte[] archive);
}
