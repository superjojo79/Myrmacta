package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.WorkflowInstance;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkflowRepository implements WorkflowRepository {

    private final ConcurrentHashMap<UUID, WorkflowInstance> store = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowInstance instance) {
        Objects.requireNonNull(instance, "instance");
        store.put(instance.id(), instance);
    }

    @Override
    public Optional<WorkflowInstance> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }
}
