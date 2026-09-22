package com.flowforge.domain.reporting;

/**
 * Runs one unit of work. Production jobs supply a transaction; unit tests run the action inline.
 */
@FunctionalInterface
public interface UnitOfWork {

    void run(Runnable action);
}
