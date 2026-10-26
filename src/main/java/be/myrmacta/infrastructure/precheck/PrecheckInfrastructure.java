package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.port.out.ModelOutputReaderPort;
import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.infrastructure.config.PrecheckProperties;
import tools.jackson.databind.ObjectMapper;

/** TODO §106 — point d'entrée des adaptateurs de la pré-vérification pour la configuration Spring. */
public final class PrecheckInfrastructure {

    private PrecheckInfrastructure() {}

    public static PrecheckModelPort model(PrecheckProperties properties) {
        return properties.enabled() ? PrecheckModels.create(properties) : new DisabledPrecheckModel();
    }

    public static PieceTextExtractorPort textExtractor() {
        return new PdfBoxPieceTextExtractor();
    }

    public static ModelOutputReaderPort outputReader(ObjectMapper mapper) {
        return new JacksonModelOutputReader(mapper);
    }
}
