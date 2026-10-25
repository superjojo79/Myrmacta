package be.myrmacta.domain.workflow.palette.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.palette.port.out.BlockReference;
import java.util.List;

/** Listes minimales {@code {value, label}} des trois sources réservées à l'administration (D-PALETTE-REFERENCES). */
public interface ListBlockReferencesUseCase {

    List<BlockReference> list(String source, AccessContext.User actor);
}
