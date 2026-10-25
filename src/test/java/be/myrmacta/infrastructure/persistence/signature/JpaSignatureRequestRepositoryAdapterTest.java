package be.myrmacta.infrastructure.persistence.signature;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.out.SignatureConflictException;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** T1.5 / S-EMIT-5 / S-EMIT-10 (PostgreSQL embarqué) : persistance des demandes de signature (TODO §72). */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaSignatureRequestRepositoryAdapterTest {

    @Autowired
    private SignatureRequestRepository repository;

    private static String proc() {
        return "proc-" + System.nanoTime();
    }

    private static SignatureRequest requestFor(String process, String activity) {
        SignatureRequest base = request(UUID.randomUUID(), SignatureMode.PARALLEL);
        return new SignatureRequest(base.id(), base.tenantId(), base.dossierId(), process, activity,
                base.sourceElementId(), base.sourceStorageRef(), base.sourceDigest(), base.providerCode(), null,
                base.mode(), base.status(), null, true, null, base.deadlineAt(), null, 0, NOW, null, "test", 0L);
    }

    private List<Signer> twoSigners(SignatureRequest r) {
        return SignatureProgression.start(
                        r.mode(),
                        List.of(external(r.id(), 0, true, "a@example.org"), internal(r.id(), 1, false, "bob")))
                .signers();
    }

    @Test
    void insertThenFind_roundTrips() {
        SignatureRequest r = requestFor(proc(), "sign");
        List<Signer> signers = twoSigners(r);
        repository.insert(r, signers);

        assertThat(repository.findById(r.id())).contains(r);
        assertThat(repository.findSigners(r.id())).containsExactlyElementsOf(signers);
        assertThat(repository.findByDossierId(r.dossierId())).containsExactly(r);
    }

    @Test
    void secondPendingRequestForSameProcessAndActivityIsRejected() {
        String process = proc();
        SignatureRequest first = requestFor(process, "sign");
        repository.insert(first, twoSigners(first));
        SignatureRequest replay = requestFor(process, "sign");

        assertThatThrownBy(() -> repository.insert(replay, twoSigners(replay)))
                .isInstanceOf(SignatureConflictException.class);
        assertThat(repository.findPendingByProcessAndActivity(process, "sign")).contains(first);
        assertThat(repository.findById(replay.id())).isEmpty();
    }

    @Test
    void anotherActivityOrATerminalRequestDoesNotConflict() {
        String process = proc();
        SignatureRequest first = requestFor(process, "sign");
        repository.insert(first, twoSigners(first));
        SignatureRequest other = requestFor(process, "sign2");
        repository.insert(other, twoSigners(other));

        repository.updateRequest(first.closed(SignatureOutcome.CANCELLED, "TEST", NOW));
        SignatureRequest again = requestFor(process, "sign");
        repository.insert(again, twoSigners(again));

        assertThat(repository.findPendingByProcessInstance(process)).extracting(SignatureRequest::id)
                .containsExactlyInAnyOrder(other.id(), again.id());
    }

    @Test
    void updateIsCompareAndSetOnVersion() {
        SignatureRequest r = requestFor(proc(), "sign");
        repository.insert(r, twoSigners(r));
        SignatureRequest closed = r.closed(SignatureOutcome.SIGNED, null, NOW);

        assertThat(repository.updateRequest(closed)).isTrue();
        assertThat(repository.updateRequest(closed)).isFalse();

        SignatureRequest reloaded = repository.findById(r.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(reloaded.version()).isEqualTo(1L);
    }

    @Test
    void aggregateUpdateIsAtomic() {
        SignatureRequest r = requestFor(proc(), "sign");
        List<Signer> signers = twoSigners(r);
        repository.insert(r, signers);
        // un autre appel a déjà modifié le second signataire
        assertThat(repository.updateSigner(signers.get(1).withStatus(SignerStatus.REVOKED))).isTrue();

        SignatureRequest closed = r.closed(SignatureOutcome.SIGNED, null, NOW);
        boolean applied = repository.update(closed, List.of(signers.get(0).withStatus(SignerStatus.SIGNED),
                signers.get(1).withStatus(SignerStatus.SIGNED)));

        assertThat(applied).isFalse();
        assertThat(repository.findById(r.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
        assertThat(repository.findSignerById(signers.get(0).id()).orElseThrow().status())
                .isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void lookupsByTokenUserInviteAndDeadline() {
        SignatureRequest r = requestFor(proc(), "sign");
        List<Signer> signers = twoSigners(r);
        repository.insert(r, signers);
        Signer ext = signers.get(0);
        String hash = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);

        assertThat(repository.findInvitable(5, 100)).extracting(Signer::id).contains(ext.id());
        assertThat(repository.updateSigner(ext.withToken(hash).invited(NOW))).isTrue();
        assertThat(repository.findSignerByTokenHash(hash)).map(Signer::id).contains(ext.id());
        assertThat(repository.findInvitable(5, 100)).extracting(Signer::id).doesNotContain(ext.id());
        assertThat(repository.findOpenInternalSigners("bob")).extracting(Signer::id).contains(signers.get(1).id());
        assertThat(repository.findOpenInternalSigners("nobody")).isEmpty();
        assertThat(repository.findPendingWithDeadlineBefore(NOW.plusSeconds(86_401), 500)).extracting(SignatureRequest::id)
                .contains(r.id());
        assertThat(repository.findPendingWithDeadlineBefore(NOW, 500)).extracting(SignatureRequest::id)
                .doesNotContain(r.id());
    }

    @Test
    void terminalRequestsAreDispatchedOnce() {
        SignatureRequest r = requestFor(proc(), "sign");
        repository.insert(r, twoSigners(r));
        assertThat(repository.findTerminalNotDispatched(20, 500)).extracting(SignatureRequest::id).doesNotContain(r.id());

        SignatureRequest closed = r.closed(SignatureOutcome.DECLINED, "REQUIRED_DECLINED", NOW);
        repository.updateRequest(closed);
        assertThat(repository.findTerminalNotDispatched(20, 500)).extracting(SignatureRequest::id).contains(r.id());

        SignatureRequest reloaded = repository.findById(r.id()).orElseThrow();
        assertThat(repository.updateRequest(reloaded.dispatched(NOW))).isTrue();
        assertThat(repository.findTerminalNotDispatched(20, 500)).extracting(SignatureRequest::id).doesNotContain(r.id());
    }

    @Test
    void deleteAllByDossierIdRemovesRequestsAndSigners() {
        SignatureRequest r = requestFor(proc(), "sign");
        List<Signer> signers = twoSigners(r);
        repository.insert(r, signers);

        assertThat(repository.deleteAllByDossierId(r.dossierId())).isEqualTo(1);
        assertThat(repository.findById(r.id())).isEmpty();
        assertThat(repository.findSignerById(signers.get(0).id())).isEmpty();
    }
}
