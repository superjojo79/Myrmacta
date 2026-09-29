package com.flowforge.domain.signature.model;

import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloc « Demander une signature » tel que déclaré dans un BPMN (D-SIGNATURE-BPMN-MARKER) : un
 * {@code serviceTask} {@code ${signatureRequestTask}} portant des attributs {@code flowable:signature*}
 * et un élément {@code flowable:signer} par signataire. Les sources sont des noms littéraux
 * ({@code dossierField:} / {@code variable:}), jamais évalués en UEL (TODO §69).
 *
 * @param deadlineDays échéance en jours ; {@code null} = défaut tenant
 */
public record SignatureBlockDefinition(
        String activityId,
        PortalRecipientSource document,
        SignatureMode mode,
        Integer deadlineDays,
        List<SignerDefinition> signers) {

    public SignatureBlockDefinition {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(mode, "mode");
        signers = List.copyOf(Objects.requireNonNull(signers, "signers"));
        if (signers.isEmpty()) {
            throw new IllegalArgumentException("a signature block needs at least one signer");
        }
        if (signers.stream().noneMatch(SignerDefinition::required)) {
            throw new IllegalArgumentException("a signature block needs at least one required signer");
        }
        Set<PortalRecipientSource> distinct = signers.stream().map(SignerDefinition::source).collect(Collectors.toSet());
        if (distinct.size() != signers.size()) {
            throw new IllegalArgumentException("signer sources must be distinct");
        }
        if (deadlineDays != null && deadlineDays <= 0) {
            throw new IllegalArgumentException("deadlineDays must be positive");
        }
    }

    public boolean hasExternalSigner() {
        return signers.stream().anyMatch(s -> s.kind() == SignerKind.EXTERNAL);
    }

    /** {@code name} : libellé affiché ; défaut = nom de la source. */
    public record SignerDefinition(PortalRecipientSource source, SignerKind kind, boolean required, String name) {
        public SignerDefinition {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
