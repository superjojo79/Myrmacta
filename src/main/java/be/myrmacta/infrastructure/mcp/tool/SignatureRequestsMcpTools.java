package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.port.in.ManageSignatureUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §72 (D-SIGNATURE-MCP) — lecture et annulation des demandes de signature. Volontairement
 * <b>aucun tool d'émission</b> : une signature engage juridiquement, elle ne part que d'un bloc BPMN
 * déployé. Les adresses sont toujours masquées (jamais l'adresse complète).
 */
@Component
@ConditionalOnExpression("'${app.signature.provider:none}' != 'none'")
public class SignatureRequestsMcpTools {

    private final ManageSignatureUseCase useCase;
    private final McpAccessContextFactory accessContextFactory;

    public SignatureRequestsMcpTools(ManageSignatureUseCase useCase, McpAccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "list_signature_requests",
            description = "List the electronic-signature requests of a dossier (status, mode, signers with masked "
                    + "contact). Requires VIEW on the dossier type.")
    @Transactional(readOnly = true)
    public List<RequestView> listSignatureRequests(
            @McpToolParam(required = true, description = "Dossier id") String dossierId) {
        return useCase.list(UUID.fromString(dossierId), accessContextFactory.currentActor()).requests().stream()
                .map(RequestView::from)
                .toList();
    }

    @McpTool(
            name = "get_signature_request",
            description = "Get one signature request of a dossier with its signers (masked contact).")
    @Transactional(readOnly = true)
    public RequestView getSignatureRequest(
            @McpToolParam(required = true, description = "Dossier id") String dossierId,
            @McpToolParam(required = true, description = "Signature request id") String requestId) {
        UUID id = UUID.fromString(requestId);
        return useCase.list(UUID.fromString(dossierId), accessContextFactory.currentActor()).requests().stream()
                .filter(v -> v.request().id().equals(id))
                .findFirst()
                .map(RequestView::from)
                .orElseThrow(() -> new IllegalArgumentException("Signature request not found"));
    }

    @McpTool(
            name = "cancel_signature_request",
            description = "Cancel a pending signature request (propagated to the provider). dryRun=true checks "
                    + "rights and state without any effect. Requires EDIT on the dossier type.")
    @Transactional
    public RequestView cancelSignatureRequest(
            @McpToolParam(required = true, description = "Signature request id") String requestId,
            @McpToolParam(required = false, description = "Check only, no effect; default false") Boolean dryRun) {
        return RequestView.from(useCase.cancel(
                UUID.fromString(requestId), accessContextFactory.currentActor(), Boolean.TRUE.equals(dryRun)));
    }

    public record RequestView(
            UUID id,
            String status,
            String statusReason,
            String mode,
            boolean simulated,
            Instant deadlineAt,
            List<SignerView> signers) {
        static RequestView from(ManageSignatureUseCase.View view) {
            SignatureRequest r = view.request();
            return new RequestView(r.id(), r.status().name(), r.statusReason(), r.mode().name(), r.simulated(),
                    r.deadlineAt(), view.signers().stream().map(s -> SignerView.from(s, view.canManage())).toList());
        }
    }

    /** {@code contact} : adresse masquée ou identifiant — jamais l'adresse complète ; absent sans EDIT (comme le REST). */
    public record SignerView(
            UUID id, int position, String kind, boolean required, String displayName, String contact, String status) {
        static SignerView from(Signer s, boolean canManage) {
            return new SignerView(s.id(), s.position(), s.kind().name(), s.required(), s.displayName(),
                    !canManage ? null : (s.email() != null ? s.maskedEmail() : s.userId()), s.status().name());
        }
    }
}
