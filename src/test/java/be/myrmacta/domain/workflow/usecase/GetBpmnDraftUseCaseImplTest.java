package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetBpmnDraftUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final BpmnDraftRepository draftRepository = mock(BpmnDraftRepository.class);
    private final GetBpmnDraftUseCaseImpl useCase = new GetBpmnDraftUseCaseImpl(TENANT, draftRepository);

    @Test
    void get_existingDraftForKey_returnsIt() {
        BpmnDraft draft =
                new BpmnDraft(TENANT, "testProcess", "<bpmn/>", Instant.parse("2026-08-24T00:00:00Z"), "alice");
        when(draftRepository.findByTenantIdAndProcessDefinitionKey(TENANT, "testProcess"))
                .thenReturn(Optional.of(draft));

        Optional<BpmnDraft> result = useCase.get("testProcess");

        assertThat(result).contains(draft);
    }

    @Test
    void get_noDraftForKey_returnsEmpty() {
        when(draftRepository.findByTenantIdAndProcessDefinitionKey(TENANT, "missingProcess"))
                .thenReturn(Optional.empty());

        Optional<BpmnDraft> result = useCase.get("missingProcess");

        assertThat(result).isEmpty();
    }
}
