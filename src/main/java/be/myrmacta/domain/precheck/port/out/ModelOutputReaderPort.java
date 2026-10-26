package be.myrmacta.domain.precheck.port.out;

import be.myrmacta.domain.precheck.model.ModelOutput;
import java.util.Optional;

/** TODO §106 (HC3) — lecture du texte JSON rendu par le modèle ; vide s'il n'est pas un objet JSON de la forme attendue. */
public interface ModelOutputReaderPort {

    Optional<ModelOutput> read(String json);
}
