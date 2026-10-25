package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import be.myrmacta.domain.signature.port.out.SignatureOutcomeListener;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tentative immédiate de reprise du process (TODO §72, D-SIGNATURE-DISPATCH), <em>après</em> validation
 * de la transaction qui a enregistré l'issue — jamais à l'intérieur, pour que la reprise ne puisse
 * pas défaire la décision. Un échec est sans conséquence : le job du répartiteur reprend.
 *
 * <p>La reprise s'exécute dans une <strong>nouvelle</strong> transaction ({@code REQUIRES_NEW}) : dans
 * {@code afterCommit}, les ressources de la transaction terminée sont encore liées au thread, et une
 * propagation {@code REQUIRED} rejoindrait cette transaction morte — le travail serait perdu sans
 * erreur.
 */
public final class AfterCommitSignatureOutcomeListener implements SignatureOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitSignatureOutcomeListener.class);

    private final DispatchSignatureOutcomesUseCase dispatcher;
    private final TransactionTemplate newTransaction;

    public AfterCommitSignatureOutcomeListener(
            DispatchSignatureOutcomesUseCase dispatcher, PlatformTransactionManager transactionManager) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.newTransaction = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void outcomeRecorded(UUID requestId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    attempt(requestId);
                }
            });
        } else {
            attempt(requestId);
        }
    }

    private void attempt(UUID requestId) {
        try {
            newTransaction.executeWithoutResult(status -> dispatcher.dispatch(requestId));
        } catch (RuntimeException ex) {
            log.warn("Immediate signature dispatch failed for request {}; the dispatcher job will retry: {}",
                    requestId, ex.getClass().getSimpleName());
        }
    }
}
