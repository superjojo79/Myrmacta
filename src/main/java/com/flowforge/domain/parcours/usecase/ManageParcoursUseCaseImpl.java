package com.flowforge.domain.parcours.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.parcours.ParcoursCompiler;
import com.flowforge.domain.parcours.model.Parcours;
import com.flowforge.domain.parcours.model.ParcoursConflictException;
import com.flowforge.domain.parcours.model.ParcoursNotFoundException;
import com.flowforge.domain.parcours.port.in.ManageParcoursUseCase;
import com.flowforge.domain.parcours.port.out.ParcoursRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** TODO §88 (LC5) — gestion des parcours : rôle de conception, version attendue, définition toujours compilable. */
public final class ManageParcoursUseCaseImpl implements ManageParcoursUseCase {

    private final String tenantId;
    private final Clock clock;
    private final TenantAdminGuard guard;
    private final ParcoursRepository repository;

    public ManageParcoursUseCaseImpl(String tenantId, Clock clock, TenantAdminGuard guard, ParcoursRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<Parcours> list(AccessContext.User actor) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.list(tenantId);
    }

    @Override
    public Parcours get(AccessContext.User actor, String key) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.findByKey(tenantId, key).orElseThrow(() -> new ParcoursNotFoundException(key));
    }

    @Override
    public Parcours save(AccessContext.User actor, SaveCommand command) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        Instant now = clock.instant();
        Optional<Parcours> existing = repository.findByKey(tenantId, command.key());
        Parcours candidate;
        if (existing.isEmpty()) {
            if (command.expectedVersion() != null) {
                throw new ParcoursNotFoundException(command.key());
            }
            candidate = new Parcours(UUID.randomUUID(), tenantId, command.key(), command.label(), command.definition(), 1,
                    actor.userId(), actor.userId(), now, now, null, null);
        } else {
            Parcours current = existing.get();
            if (command.expectedVersion() == null || command.expectedVersion() != current.version()) {
                throw new ParcoursConflictException(command.key());
            }
            candidate = new Parcours(current.id(), tenantId, current.key(), command.label(), command.definition(), current.version() + 1,
                    current.createdBy(), actor.userId(), current.createdAt(), now, current.lastCompiledAt(), current.lastCompiledSha256());
        }
        // Un parcours enregistré est toujours compilable : on refuse ici un graphe incohérent, avec l'étape fautive nommée.
        ParcoursCompiler.compile(candidate.key(), candidate.label(), candidate.definition(), key -> null);
        if (!repository.save(candidate, command.expectedVersion())) {
            throw new ParcoursConflictException(command.key());
        }
        return candidate;
    }

    @Override
    public void delete(AccessContext.User actor, String key, Integer expectedVersion) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        if (expectedVersion == null) {
            if (!repository.delete(tenantId, key)) {
                throw new ParcoursNotFoundException(key);
            }
            return;
        }
        if (!repository.deleteIfVersion(tenantId, key, expectedVersion)) {
            // la clé n'existe plus, ou une autre modification est passée entre-temps : on les distingue pour que l'UI le dise
            if (repository.findByKey(tenantId, key).isEmpty()) {
                throw new ParcoursNotFoundException(key);
            }
            throw new ParcoursConflictException(key);
        }
    }
}
