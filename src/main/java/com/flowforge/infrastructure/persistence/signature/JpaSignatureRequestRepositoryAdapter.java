package com.flowforge.infrastructure.persistence.signature;

import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.out.SignatureConflictException;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
class JpaSignatureRequestRepositoryAdapter implements SignatureRequestRepository {

    private static final EnumSet<SignerStatus> OPEN = EnumSet.of(SignerStatus.PENDING, SignerStatus.WAITING);

    private final SpringDataSignatureRequestRepository requests;
    private final SpringDataSignerRepository signers;

    JpaSignatureRequestRepositoryAdapter(SpringDataSignatureRequestRepository requests, SpringDataSignerRepository signers) {
        this.requests = requests;
        this.signers = signers;
    }

    @Override
    @Transactional
    public void insert(SignatureRequest request, List<Signer> signerList) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(signerList, "signers");
        try {
            requests.saveAndFlush(SignatureRequestJpaEntity.fromDomain(request));
        } catch (DataIntegrityViolationException ex) {
            // ux_signature_request_pending : rejeu du delegate pour le même (process, activité).
            throw new SignatureConflictException(
                    "a PENDING signature request already exists for " + request.processInstanceId() + "/"
                            + request.activityId(),
                    ex);
        }
        signers.saveAll(signerList.stream().map(SignerJpaEntity::fromDomain).toList());
        signers.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignatureRequest> findById(UUID id) {
        return requests.findById(id).map(SignatureRequestJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignatureRequest> findByProviderReference(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return Optional.empty();
        }
        return requests.findFirstByProviderReference(providerReference).map(SignatureRequestJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignatureRequest> findPendingByProcessAndActivity(String processInstanceId, String activityId) {
        return requests.findFirstByProcessInstanceIdAndActivityIdAndStatus(
                        processInstanceId, activityId, SignatureStatus.PENDING)
                .map(SignatureRequestJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignatureRequest> findPendingByProcessInstance(String processInstanceId) {
        return requests.findByProcessInstanceIdAndStatus(processInstanceId, SignatureStatus.PENDING).stream()
                .map(SignatureRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignatureRequest> findByDossierId(UUID dossierId) {
        return requests.findByDossierIdOrderByCreatedAtDesc(dossierId).stream()
                .map(SignatureRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signer> findSigners(UUID requestId) {
        return signers.findByRequestIdOrderByPositionAsc(requestId).stream()
                .map(SignerJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Signer> findSignerById(UUID signerId) {
        return signers.findById(signerId).map(SignerJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Signer> findSignerByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return signers.findByTokenHash(tokenHash).map(SignerJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signer> findOpenInternalSigners(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return signers.findByKindAndUserIdAndStatusInOrderByIdDesc(SignerKind.INTERNAL, userId, OPEN).stream()
                .map(SignerJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signer> findInvitable(int maxAttempts, int limit) {
        return signers.findInvitable(SignerKind.EXTERNAL, SignerStatus.PENDING, maxAttempts, PageRequest.of(0, limit))
                .stream()
                .map(SignerJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignatureRequest> findPendingWithDeadlineBefore(Instant now, int limit) {
        return requests.findByStatusAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
                        SignatureStatus.PENDING, now, PageRequest.of(0, limit))
                .stream()
                .map(SignatureRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignatureRequest> findTerminalNotDispatched(int maxAttempts, int limit) {
        return requests.findTerminalNotDispatched(SignatureStatus.PENDING, maxAttempts, PageRequest.of(0, limit))
                .stream()
                .map(SignatureRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean update(SignatureRequest next, List<Signer> nextSigners) {
        if (!casRequest(next)) {
            return false;
        }
        for (Signer signer : nextSigners) {
            if (!casSigner(signer)) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean updateSigner(Signer next) {
        return casSigner(next);
    }

    @Override
    @Transactional
    public boolean updateRequest(SignatureRequest next) {
        return casRequest(next);
    }

    @Override
    @Transactional
    public int deleteAllByDossierId(UUID dossierId) {
        Objects.requireNonNull(dossierId, "dossierId");
        signers.deleteByDossierId(dossierId);
        return requests.deleteByDossierId(dossierId);
    }

    private boolean casRequest(SignatureRequest r) {
        return requests.cas(r.id(), r.version(), r.status(), r.statusReason(), r.providerReference(),
                        r.completedAt(), r.outcomeDispatchedAt(), r.dispatchAttempts(), r.simulated(), r.assuranceLevel())
                == 1;
    }

    private boolean casSigner(Signer s) {
        return signers.cas(s.id(), s.version(), s.tokenHash(), s.status(), s.inviteSentAt(), s.inviteAttempts(),
                        s.decidedAt(), s.declineReason())
                == 1;
    }
}
