package com.flowforge.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import com.flowforge.domain.workflow.portal.port.in.IssuePortalLinkUseCase.ExternalTaskCreated;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssuePortalLinkUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final PortalRecipientSource SOURCE =
            new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "contactEmail");

    private final InMemoryPortalLinkRepository repository = new InMemoryPortalLinkRepository();
    private final TaskQuestionSchemaRepository schemaRepository = mock(TaskQuestionSchemaRepository.class);
    private final PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
    private final PortalParticipantNotifier notifier = mock(PortalParticipantNotifier.class);
    private IssuePortalLinkUseCaseImpl useCase;
    private final UUID dossierId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new IssuePortalLinkUseCaseImpl("tenant_dev", repository, schemaRepository, resolver, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC), 7, 30);
        when(schemaRepository.findByKey("tenant_dev", "avis")).thenReturn(Optional.of(mock(TaskQuestionSchema.class)));
    }

    private ExternalTaskCreated event(Integer ttl) {
        return new ExternalTaskCreated("task-1", "pi-1", dossierId, new PortalTaskDefinition("tiers", "avis", SOURCE, ttl));
    }

    @Test
    void validAddress_createsPendingSendWithDefaultTtl() {
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of(" alice@example.org "));

        PortalLink link = useCase.onExternalTaskCreated(event(null)).orElseThrow();

        assertThat(link.status()).isEqualTo(PortalLinkStatus.PENDING_SEND);
        assertThat(link.recipientEmail()).isEqualTo("alice@example.org");
        assertThat(link.ttlDays()).isEqualTo(7);
        assertThat(link.tokenHash()).isNull();
        verify(notifier, never()).notifyNotSent(any());
    }

    @Test
    void missingAddress_createsNotSentAndNotifies() {
        // T-ISSUE-2
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.empty());

        PortalLink link = useCase.onExternalTaskCreated(event(null)).orElseThrow();

        assertThat(link.status()).isEqualTo(PortalLinkStatus.NOT_SENT);
        assertThat(link.statusReason()).isEqualTo(PortalLinkStatusReason.NO_ADDRESS);
        verify(notifier).notifyNotSent(link);
    }

    @Test
    void blankAddress_isNoAddress_invalidAddress_isInvalid() {
        // T-ISSUE-3
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("  "));
        assertThat(useCase.onExternalTaskCreated(event(null)).orElseThrow().statusReason())
                .isEqualTo(PortalLinkStatusReason.NO_ADDRESS);

        repository.links.clear();
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("pas-une-adresse"));
        assertThat(useCase.onExternalTaskCreated(event(null)).orElseThrow().statusReason())
                .isEqualTo(PortalLinkStatusReason.INVALID_ADDRESS);
    }

    @Test
    void missingSchema_createsNotSentSchemaMissing() {
        // T-ISSUE-4
        when(schemaRepository.findByKey("tenant_dev", "avis")).thenReturn(Optional.empty());
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("alice@example.org"));

        PortalLink link = useCase.onExternalTaskCreated(event(null)).orElseThrow();

        assertThat(link.statusReason()).isEqualTo(PortalLinkStatusReason.SCHEMA_MISSING);
    }

    @Test
    void requestedTtlAboveCeiling_isCapped() {
        // T-ISSUE-5
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("alice@example.org"));

        assertThat(useCase.onExternalTaskCreated(event(90)).orElseThrow().ttlDays()).isEqualTo(30);
    }

    @Test
    void secondCreationEventForSameTask_isIdempotent() {
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("alice@example.org"));
        useCase.onExternalTaskCreated(event(null));

        assertThat(useCase.onExternalTaskCreated(event(null))).isEmpty();
        assertThat(repository.links).hasSize(1);
    }

    @Test
    void taskWithoutDossier_createsNothing() {
        ExternalTaskCreated noDossier = new ExternalTaskCreated("task-1", "pi-1", null,
                new PortalTaskDefinition("tiers", "avis", SOURCE, null));

        assertThat(useCase.onExternalTaskCreated(noDossier)).isEmpty();
        assertThat(repository.links).isEmpty();
    }

    @Test
    void addressContainingExpression_isJustAnInvalidString() {
        // T-ISSUE-7 : jamais évaluée
        when(resolver.resolve(SOURCE, dossierId, "pi-1")).thenReturn(Optional.of("${connectorServiceTask}"));

        assertThat(useCase.onExternalTaskCreated(event(null)).orElseThrow().statusReason())
                .isEqualTo(PortalLinkStatusReason.INVALID_ADDRESS);
    }
}
