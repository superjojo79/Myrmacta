package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaveBpmnDraftUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String XML = "<bpmn/>";

    private final BpmnDraftRepository draftRepository = mock(BpmnDraftRepository.class);
    private final BpmnParserPort bpmnParserPort = mock(BpmnParserPort.class);
    private final SaveBpmnDraftUseCaseImpl useCase =
            new SaveBpmnDraftUseCaseImpl(TENANT, draftRepository, bpmnParserPort);

    @Test
    void save_wellFormedBpmnWithMatchingKey_delegatesToRepositoryAndReturnsSavedDraft() {
        when(bpmnParserPort.extractProcessDefinitionKey(XML.getBytes(StandardCharsets.UTF_8)))
                .thenReturn("testProcess");
        BpmnDraft saved = new BpmnDraft(TENANT, "testProcess", XML, Instant.parse("2026-08-24T00:00:00Z"), "alice");
        when(draftRepository.save(any())).thenReturn(saved);

        BpmnDraft result = useCase.save("testProcess", XML, "alice");

        assertThat(result).isEqualTo(saved);
        verify(draftRepository).save(any());
    }

    @Test
    void save_malformedBpmn_propagatesInvalidBpmnExceptionAndNeverCallsRepository() {
        when(bpmnParserPort.extractProcessDefinitionKey(XML.getBytes(StandardCharsets.UTF_8)))
                .thenThrow(new InvalidBpmnException("not valid BPMN"));

        assertThatThrownBy(() -> useCase.save("testProcess", XML, "alice"))
                .isInstanceOf(InvalidBpmnException.class);

        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_pathKeyDoesNotMatchXmlProcessId_rejectsAndNeverCallsRepository() {
        when(bpmnParserPort.extractProcessDefinitionKey(XML.getBytes(StandardCharsets.UTF_8)))
                .thenReturn("otherProcess");

        assertThatThrownBy(() -> useCase.save("testProcess", XML, "alice"))
                .isInstanceOf(InvalidBpmnException.class);

        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_nullXml_rejectsWithInvalidBpmnExceptionAndNeverCallsParserOrRepository() {
        assertThatThrownBy(() -> useCase.save("testProcess", null, "alice"))
                .isInstanceOf(InvalidBpmnException.class);

        verify(bpmnParserPort, never()).extractProcessDefinitionKey(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void save_blankXml_rejectsWithInvalidBpmnExceptionAndNeverCallsParserOrRepository() {
        assertThatThrownBy(() -> useCase.save("testProcess", "   ", "alice"))
                .isInstanceOf(InvalidBpmnException.class);

        verify(bpmnParserPort, never()).extractProcessDefinitionKey(any());
        verify(draftRepository, never()).save(any());
    }
}
