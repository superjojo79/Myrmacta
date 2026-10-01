package com.flowforge.domain.parcours.port.out;

import com.flowforge.domain.parcours.model.Parcours;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParcoursRepository {

    /** @param expectedVersion version lue par l'appelant ({@code null} = création) ; {@code false} si elle n'est plus la courante */
    boolean save(Parcours parcours, Integer expectedVersion);

    Optional<Parcours> findByKey(String tenantId, String key);

    List<Parcours> list(String tenantId);

    boolean delete(String tenantId, String key);

    /** Mémorise la dernière compilation (sans changer la version : la définition n'a pas bougé). */
    void markCompiled(String tenantId, String key, Instant at, String sha256);
}
