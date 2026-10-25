package be.myrmacta.domain.document.port.in;

import java.util.Map;

public interface GenerateDocumentUseCase {

    byte[] generate(GenerateDocumentCommand command);

    record GenerateDocumentCommand(
            String templateKey, String variantKey, String locale, Map<String, Object> variables) {}
}
