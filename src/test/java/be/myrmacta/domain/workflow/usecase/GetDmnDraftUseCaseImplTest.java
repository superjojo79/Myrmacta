package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetDmnDraftUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final DmnDraftRepository draftRepository = mock(DmnDraftRepository.class);
    private final GetDmnDraftUseCaseImpl useCase = new GetDmnDraftUseCaseImpl(TENANT, draftRepository);

    @Test
    void get_existingDraftForKey_returnsIt() {
        DmnDraft draft =
                new DmnDraft(TENANT, "testDecision", "<dmn/>", Instant.parse("2026-09-11T00:00:00Z"), "alice");
        when(draftRepository.findByTenantIdAndDecisionKey(TENANT, "testDecision")).thenReturn(Optional.of(draft));

        Optional<DmnDraft> result = useCase.get("testDecision");

        assertThat(result).contains(draft);
    }

    @Test
    void get_noDraftForKey_returnsEmpty() {
        when(draftRepository.findByTenantIdAndDecisionKey(TENANT, "missingDecision")).thenReturn(Optional.empty());

        Optional<DmnDraft> result = useCase.get("missingDecision");

        assertThat(result).isEmpty();
    }
}
