package com.flowforge.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.port.in.HandleSignatureCallbackUseCase;
import com.flowforge.domain.signature.port.out.SignaturePort;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/** Revue adversariale n°2 : le retour du fournisseur est atomique (issue + pièces signées + notifications). */
class TransactionalHandleSignatureCallbackUseCaseTest {

    private final HandleSignatureCallbackUseCase delegate = mock(HandleSignatureCallbackUseCase.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    private final TransactionalHandleSignatureCallbackUseCase useCase =
            new TransactionalHandleSignatureCallbackUseCase(delegate, tm);
    private final SignaturePort.SignatureCallbackEvent event =
            new SignaturePort.SignatureCallbackEvent("ref", SignatureOutcome.SIGNED, Map.of());

    @Test
    void commitsWhenTheDelegateSucceeds() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(tm.getTransaction(any())).thenReturn(status);
        when(delegate.apply(event)).thenReturn(true);

        assertThat(useCase.apply(event)).isTrue();
        verify(tm).commit(status);
        verify(tm, never()).rollback(any());
    }

    @Test
    void rollsBackEverythingWhenAttachingTheSignedDocumentFails() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(tm.getTransaction(any())).thenReturn(status);
        when(delegate.apply(event)).thenThrow(new IllegalStateException("storage down"));

        assertThatThrownBy(() -> useCase.apply(event)).isInstanceOf(IllegalStateException.class);
        verify(tm).rollback(status);
        verify(tm, never()).commit(any());
    }
}
