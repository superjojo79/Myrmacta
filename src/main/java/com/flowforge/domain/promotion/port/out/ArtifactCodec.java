package com.flowforge.domain.promotion.port.out;

/**
 * TODO §83 (D4) — encodage canonique des représentations d'artefacts (JSON, clés triées, sans champ inconnu à la lecture) :
 * deux représentations égales donnent les mêmes octets, ce qui fonde la détection de l'« inchangé ».
 */
public interface ArtifactCodec {

    byte[] encode(Object doc);

    /** @throws com.flowforge.domain.promotion.model.InvalidBundleException contenu illisible ou champ inconnu */
    <T> T decode(byte[] content, Class<T> type);
}
