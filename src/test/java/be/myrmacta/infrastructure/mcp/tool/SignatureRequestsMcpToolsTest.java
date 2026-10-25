package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.port.in.ManageSignatureUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** S-MCP-1, S-MCP-2, S-MCP-3 — tools MCP de signature (TODO §72, M8c). */
class SignatureRequestsMcpToolsTest {

    private final ManageSignatureUseCase useCase = mock(ManageSignatureUseCase.class);
    private final SignatureRequestsMcpTools tools =
            new SignatureRequestsMcpTools(useCase, new McpAccessContextFactory());

    @BeforeEach
    void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static ManageSignatureUseCase.View view(UUID id) {
        SignatureRequest r = SignatureFixtures.request(id, SignatureMode.PARALLEL);
        Signer s = SignatureFixtures.external(id, 0, true, "alice.martin@example.com");
        return new ManageSignatureUseCase.View(r, List.of(s), true);
    }

    @Test
    void listAndGetNeverExposeTheFullAddress() {
        UUID dossier = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(useCase.list(any(), any())).thenReturn(new ManageSignatureUseCase.Listing(true, List.of(view(id))));

        var listed = tools.listSignatureRequests(dossier.toString());
        var one = tools.getSignatureRequest(dossier.toString(), id.toString());

        assertThat(listed).hasSize(1);
        assertThat(one.signers().get(0).contact()).doesNotContain("alice.martin").contains("@");
        assertThat(listed.toString()).doesNotContain("alice.martin@example.com");
        assertThatThrownBy(() -> tools.getSignatureRequest(dossier.toString(), UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contactIsHiddenWithoutTheManageRight() {
        // revue adversariale n°11 : même politique que le REST (A2)
        UUID id = UUID.randomUUID();
        var v = view(id);
        when(useCase.list(any(), any())).thenReturn(new ManageSignatureUseCase.Listing(false,
                List.of(new ManageSignatureUseCase.View(v.request(), v.signers(), false))));

        assertThat(tools.listSignatureRequests(UUID.randomUUID().toString()).get(0).signers().get(0).contact())
                .isNull();
    }

    @Test
    void cancelPassesTheDryRunFlagAndTheActor() {
        UUID id = UUID.randomUUID();
        when(useCase.cancel(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(view(id));

        tools.cancelSignatureRequest(id.toString(), true);
        verify(useCase).cancel(any(), any(), org.mockito.ArgumentMatchers.eq(true));
        tools.cancelSignatureRequest(id.toString(), null);
        verify(useCase).cancel(any(), any(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void noToolCanEmitASignatureRequest() {
        List<String> names = Arrays.stream(SignatureRequestsMcpTools.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(McpTool.class))
                .filter(a -> a != null)
                .map(McpTool::name)
                .toList();
        assertThat(names).containsExactlyInAnyOrder(
                "list_signature_requests", "get_signature_request", "cancel_signature_request");
    }
}
