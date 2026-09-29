package com.flowforge.domain.signature;

import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.out.SignatureConflictException;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Repository en mémoire fidèle au compare-and-set sur {@code version} (tests de use cases du lot §72). */
public class InMemorySignatureRepository implements SignatureRequestRepository {

    public final Map<UUID, SignatureRequest> requests = new LinkedHashMap<>();
    public final Map<UUID, Signer> signers = new LinkedHashMap<>();

    @Override
    public synchronized void insert(SignatureRequest request, List<Signer> list) {
        boolean clash = request.status() == SignatureStatus.PENDING
                && requests.values().stream().anyMatch(r -> r.status() == SignatureStatus.PENDING
                        && r.processInstanceId().equals(request.processInstanceId())
                        && r.activityId().equals(request.activityId()));
        if (clash) {
            throw new SignatureConflictException("pending exists", null);
        }
        requests.put(request.id(), request);
        list.forEach(s -> signers.put(s.id(), s));
    }

    @Override
    public synchronized Optional<SignatureRequest> findById(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public synchronized Optional<SignatureRequest> findByProviderReference(String providerReference) {
        return requests.values().stream()
                .filter(r -> providerReference != null && providerReference.equals(r.providerReference()))
                .findFirst();
    }

    @Override
    public synchronized Optional<SignatureRequest> findPendingByProcessAndActivity(String process, String activity) {
        return requests.values().stream()
                .filter(r -> r.status() == SignatureStatus.PENDING
                        && r.processInstanceId().equals(process) && r.activityId().equals(activity))
                .findFirst();
    }

    @Override
    public synchronized List<SignatureRequest> findPendingByProcessInstance(String process) {
        return requests.values().stream()
                .filter(r -> r.status() == SignatureStatus.PENDING && r.processInstanceId().equals(process))
                .toList();
    }

    @Override
    public synchronized List<SignatureRequest> findByDossierId(UUID dossierId) {
        return requests.values().stream().filter(r -> r.dossierId().equals(dossierId)).toList();
    }

    @Override
    public synchronized List<Signer> findSigners(UUID requestId) {
        return signers.values().stream()
                .filter(s -> s.requestId().equals(requestId))
                .sorted(Comparator.comparingInt(Signer::position))
                .toList();
    }

    @Override
    public synchronized Optional<Signer> findSignerById(UUID signerId) {
        return Optional.ofNullable(signers.get(signerId));
    }

    @Override
    public synchronized Optional<Signer> findSignerByTokenHash(String tokenHash) {
        return signers.values().stream().filter(s -> tokenHash != null && tokenHash.equals(s.tokenHash())).findFirst();
    }

    @Override
    public synchronized List<Signer> findOpenInternalSigners(String userId) {
        return signers.values().stream()
                .filter(s -> s.kind() == SignerKind.INTERNAL && userId.equals(s.userId()) && s.status().isOpen())
                .toList();
    }

    @Override
    public synchronized List<Signer> findInvitable(int maxAttempts, int limit) {
        return signers.values().stream()
                .filter(s -> s.kind() == SignerKind.EXTERNAL && s.status() == SignerStatus.PENDING
                        && s.inviteSentAt() == null && s.inviteAttempts() < maxAttempts)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<SignatureRequest> findPendingWithDeadlineBefore(Instant now, int limit) {
        return requests.values().stream()
                .filter(r -> r.status() == SignatureStatus.PENDING && !r.deadlineAt().isAfter(now))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<SignatureRequest> findTerminalNotDispatched(int maxAttempts, int limit) {
        return requests.values().stream()
                .filter(r -> r.isDispatchPending() && r.dispatchAttempts() < maxAttempts)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized boolean update(SignatureRequest next, List<Signer> nextSigners) {
        SignatureRequest current = requests.get(next.id());
        if (current == null || current.version() != next.version()) {
            return false;
        }
        for (Signer s : nextSigners) {
            Signer cur = signers.get(s.id());
            if (cur == null || cur.version() != s.version()) {
                return false;
            }
        }
        requests.put(next.id(), next.withVersion(next.version() + 1));
        nextSigners.forEach(s -> signers.put(s.id(), s.withVersion(s.version() + 1)));
        return true;
    }

    @Override
    public synchronized boolean updateSigner(Signer next) {
        Signer cur = signers.get(next.id());
        if (cur == null || cur.version() != next.version()) {
            return false;
        }
        signers.put(next.id(), next.withVersion(next.version() + 1));
        return true;
    }

    @Override
    public synchronized boolean updateRequest(SignatureRequest next) {
        SignatureRequest cur = requests.get(next.id());
        if (cur == null || cur.version() != next.version()) {
            return false;
        }
        requests.put(next.id(), next.withVersion(next.version() + 1));
        return true;
    }

    @Override
    public synchronized int deleteAllByDossierId(UUID dossierId) {
        List<UUID> ids = requests.values().stream()
                .filter(r -> r.dossierId().equals(dossierId))
                .map(SignatureRequest::id)
                .toList();
        signers.values().removeIf(s -> ids.contains(s.requestId()));
        ids.forEach(requests::remove);
        return ids.size();
    }
}
