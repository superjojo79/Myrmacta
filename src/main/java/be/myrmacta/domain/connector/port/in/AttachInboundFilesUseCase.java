package be.myrmacta.domain.connector.port.in;

import be.myrmacta.domain.connector.model.InboundAttachment;
import java.util.List;
import java.util.UUID;

/** TODO §87 (D3c) — rattache des pièces jointes reçues à un dossier, avec les contrôles de l'upload interne. */
public interface AttachInboundFilesUseCase {

    /** Un fichier refusé n'empêche pas les autres : il est compté et motivé (jamais d'exception pour un fichier). */
    Report attach(UUID dossierId, String addedBy, List<InboundAttachment> files);

    record Report(int attached, List<String> rejections) {
        public Report {
            rejections = List.copyOf(rejections);
        }
    }
}
