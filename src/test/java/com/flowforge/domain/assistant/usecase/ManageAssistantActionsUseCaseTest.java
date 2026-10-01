package com.flowforge.domain.assistant.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.assistant.model.AssistantAction;
import com.flowforge.domain.assistant.model.AssistantExceptions.ActionNotFoundException;
import com.flowforge.domain.assistant.model.AssistantExceptions.ActionNotPendingException;
import com.flowforge.domain.assistant.model.AssistantExceptions.ConversationNotFoundException;
import com.flowforge.domain.assistant.model.Conversation;
import com.flowforge.domain.assistant.model.ToolSpec;
import com.flowforge.domain.assistant.port.out.DesignToolsPort;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §86 (LC3, revue M2) — appliquer / écarter une écriture proposée : une seule fois, par son auteur, avec son identité. */
class ManageAssistantActionsUseCaseTest {

    private static final AccessContext.User ALICE = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User BOB = AccessContext.user("bob", Set.of("TENANT_ADMIN"));

    private ChatWithAssistantUseCaseTest.InMemoryRepository repository;
    private final List<String> confirmedAs = new ArrayList<>();
    private String failure;
    private ManageAssistantActionsUseCaseImpl useCase;
    private UUID conversation;
    private UUID action;

    @BeforeEach
    void setUp() {
        repository = new ChatWithAssistantUseCaseTest.InMemoryRepository();
        DesignToolsPort tools = new DesignToolsPort() {
            @Override public List<ToolSpec> specs() { return List.of(); }
            @Override public boolean needsConfirmation(String t) { return true; }
            @Override public String preview(String t, String a, AccessContext.User u) { return "{}"; }
            @Override public String invokeConfirmed(String t, String a, AccessContext.User u) {
                if (failure != null) {
                    throw new ToolInvocationException(failure);
                }
                confirmedAs.add(u.userId());
                return "{\"written\":true}";
            }
            @Override public String invoke(String t, String a, AccessContext.User u) { return "{}"; }
        };
        useCase = new ManageAssistantActionsUseCaseImpl("t1", Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC),
                new TenantAdminGuard(), repository, tools);
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        conversation = UUID.randomUUID();
        action = UUID.randomUUID();
        repository.create(new Conversation(conversation, "t1", "alice", "c", now, now, 0, 0, 0));
        repository.saveAction(new AssistantAction(action, conversation, "define_dossier_type", "{}", AssistantAction.Status.PENDING, null, now, null));
    }

    @Test
    void anActionIsAppliedOnceWithTheIdentityOfTheUser() {
        var applied = useCase.apply(ALICE, conversation, action);
        assertThat(applied.status()).isEqualTo(AssistantAction.Status.APPLIED);
        assertThat(applied.outcome()).contains("written");
        assertThat(confirmedAs).containsExactly("alice");
        assertThatThrownBy(() -> useCase.apply(ALICE, conversation, action)).isInstanceOf(ActionNotPendingException.class);
        assertThat(confirmedAs).hasSize(1);
    }

    @Test
    void aFailureIsKeptWithItsReasonAndIsTerminal() {
        failure = "code must be uppercase";
        var failed = useCase.apply(ALICE, conversation, action);
        assertThat(failed.status()).isEqualTo(AssistantAction.Status.FAILED);
        assertThat(failed.outcome()).contains("uppercase");
        assertThatThrownBy(() -> useCase.apply(ALICE, conversation, action)).isInstanceOf(ActionNotPendingException.class);
    }

    @Test
    void anInternalFailureDoesNotLeakItsMessage() {
        var leaking = new DesignToolsPort() {
            @Override public List<ToolSpec> specs() { return List.of(); }
            @Override public boolean needsConfirmation(String t) { return true; }
            @Override public String preview(String t, String a, AccessContext.User u) { return "{}"; }
            @Override public String invokeConfirmed(String t, String a, AccessContext.User u) { throw new IllegalStateException("jdbc:postgresql://db/secret"); }
            @Override public String invoke(String t, String a, AccessContext.User u) { return "{}"; }
        };
        var uc = new ManageAssistantActionsUseCaseImpl("t1", Clock.systemUTC(), new TenantAdminGuard(), repository, leaking);
        var failed = uc.apply(ALICE, conversation, action);
        assertThat(failed.outcome()).doesNotContain("jdbc").contains("IllegalStateException");
    }

    @Test
    void aDismissedActionIsNeverApplied() {
        assertThat(useCase.dismiss(ALICE, conversation, action).status()).isEqualTo(AssistantAction.Status.DISMISSED);
        assertThatThrownBy(() -> useCase.apply(ALICE, conversation, action)).isInstanceOf(ActionNotPendingException.class);
        assertThat(confirmedAs).isEmpty();
    }

    @Test
    void onlyTheOwnerOfTheConversationAndOnlyDesignersCanAct() {
        assertThatThrownBy(() -> useCase.apply(BOB, conversation, action)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> useCase.list(BOB, conversation)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> useCase.apply(AccessContext.user("m", Set.of("DOSSIER_MANAGER")), conversation, action))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> useCase.apply(ALICE, conversation, UUID.randomUUID())).isInstanceOf(ActionNotFoundException.class);
        assertThat(confirmedAs).isEmpty();
    }
}
