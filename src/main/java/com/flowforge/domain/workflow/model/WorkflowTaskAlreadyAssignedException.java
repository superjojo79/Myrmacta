package com.flowforge.domain.workflow.model;

/** Prise d'une tâche déjà assignée à quelqu'un d'autre (D-ASSIGN-CLAIM) — 409. */
public class WorkflowTaskAlreadyAssignedException extends RuntimeException {

    public WorkflowTaskAlreadyAssignedException(String message) {
        super(message);
    }
}
