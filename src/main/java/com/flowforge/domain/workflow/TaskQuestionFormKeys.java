package com.flowforge.domain.workflow;

import java.util.Optional;

/**
 * Single source of truth for the {@code user-question} {@code formKey} convention
 * ({@code "user-question:<schemaKey>"}) — shared by {@code CompleteTaskUseCaseImpl} and
 * {@code GetTaskQuestionSchemaForTaskUseCaseImpl} so both sides parse it identically. TODO §52
 * Phase 9 found a real bug from the same schemaKey/variable convention being duplicated instead
 * of shared (mismatched names between a task form and the serviceTask reading them) — this class
 * exists specifically so that mistake cannot repeat here (D-USERQ-FORMKEY-PARSING).
 */
public final class TaskQuestionFormKeys {

    private static final String PREFIX = "user-question:";

    private TaskQuestionFormKeys() {}

    public static String formKeyFor(String schemaKey) {
        return PREFIX + schemaKey;
    }

    public static Optional<String> schemaKeyOf(String formKey) {
        if (formKey == null || !formKey.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(formKey.substring(PREFIX.length()));
    }
}
