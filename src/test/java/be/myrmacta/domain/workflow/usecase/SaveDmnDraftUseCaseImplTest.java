package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaveDmnDraftUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String XML = "<dmn/>";

    private final DmnDraftRepository draftRepository = mock(DmnDraftRepository.class);
    private final DmnParserPort dmnParserPort = mock(DmnParserPort.class);
    private final SaveDmnDraftUseCaseImpl useCase =
            new SaveDmnDraftUseCaseImpl(TENANT, draftRepository, dmnParserPort);

    @Test
    void save_wellFormedDmnWithMatchingKey_delegatesToRepositoryAndReturnsSavedDraft() {
        when(dmnParserPort.extractDecisionKey(XML.getBytes(StandardCharsets.UTF_8))).thenReturn("testDecision");
        DmnDraft saved = new DmnDraft(TENANT, "testDecision", XML, Instant.parse("2026-09-11T00:00:00Z"), "alice");
        when(draftRepository.save(any())).thenReturn(saved);

        DmnDraft result = useCase.save("testDecision", XML, "alice");

        assertThat(result).isEqualTo(saved);
        verify(draftRepository).save(any());
    }

    @Test
    void save_malformedDmn_propagatesInvalidDmnExceptionAndNeverCallsRepository() {
        when(dmnParserPort.extractDecisionKey(XML.getBytes(StandardCharsets.UTF_8)))
                .thenThrow(new InvalidDmnException("not valid DMN"));

        assertThatThrownBy(() -> useCase.save("testDecision", XML, "alice"))
                .isInstanceOf(InvalidDmnException.class);

        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_pathKeyDoesNotMatchXmlDecisionId_rejectsAndNeverCallsRepository() {
        when(dmnParserPort.extractDecisionKey(XML.getBytes(StandardCharsets.UTF_8))).thenReturn("otherDecision");

        assertThatThrownBy(() -> useCase.save("testDecision", XML, "alice"))
                .isInstanceOf(InvalidDmnException.class);

        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_nullXml_rejectsWithInvalidDmnExceptionAndNeverCallsParserOrRepository() {
        assertThatThrownBy(() -> useCase.save("testDecision", null, "alice"))
                .isInstanceOf(InvalidDmnException.class);

        verify(dmnParserPort, never()).extractDecisionKey(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_blankXml_rejectsWithInvalidDmnExceptionAndNeverCallsParserOrRepository() {
        assertThatThrownBy(() -> useCase.save("testDecision", "   ", "alice"))
                .isInstanceOf(InvalidDmnException.class);

        verify(dmnParserPort, never()).extractDecisionKey(any());
        verify(draftRepository, never()).save(any());
    }
}
