package com.flowforge.domain.workflow.palette.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.palette.port.out.BlockReference;
import java.util.List;

/** Listes minimales {@code {value, label}} des trois sources réservées à l'administration (D-PALETTE-REFERENCES). */
public interface ListBlockReferencesUseCase {

    List<BlockReference> list(String source, AccessContext.User actor);
}
