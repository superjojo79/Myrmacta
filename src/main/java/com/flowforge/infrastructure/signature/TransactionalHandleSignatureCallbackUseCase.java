package com.flowforge.infrastructure.signature;

import com.flowforge.domain.signature.port.in.HandleSignatureCallbackUseCase;
import com.flowforge.domain.signature.port.out.SignaturePort;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction unique autour du retour d'un fournisseur (revue adversariale de la PR signature, n°2) : la
 * fermeture de la demande, le rattachement du document signé / de la preuve et les notifications réussissent
 * ou échouent ensemble. Sinon un échec de stockage laissait la demande {@code SIGNED} sans pièce, et le rejeu du
 * fournisseur recevait « déjà close ». La route générique {@code /api/callbacks/{id}} n'est pas transactionnelle.
 */
public final class TransactionalHandleSignatureCallbackUseCase implements HandleSignatureCallbackUseCase {

    private final HandleSignatureCallbackUseCase delegate;
    private final TransactionTemplate transaction;

    public TransactionalHandleSignatureCallbackUseCase(
            HandleSignatureCallbackUseCase delegate, PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public boolean apply(SignaturePort.SignatureCallbackEvent event) {
        return Boolean.TRUE.equals(transaction.execute(status -> delegate.apply(event)));
    }
}
