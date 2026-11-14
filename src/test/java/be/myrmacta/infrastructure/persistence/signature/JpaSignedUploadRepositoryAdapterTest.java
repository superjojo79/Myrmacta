package be.myrmacta.infrastructure.persistence.signature;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TODO §138 (PostgreSQL embarqué) — téléversements acceptés : aller-retour des constats, ordre par rang, les deux
 * unicités par demande, purge du dossier, verrou de ligne sur la demande.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaSignedUploadRepositoryAdapterTest {

    @Autowired
    private SignatureRequestRepository requests;

    @Autowired
    private SignedUploadRepository uploads;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record Seed(SignatureRequest request, List<Signer> signers) {}

    private Seed seed() {
        SignatureRequest base = request(UUID.randomUUID(), SignatureMode.PARALLEL);
        SignatureRequest r = new SignatureRequest(base.id(), base.tenantId(), base.dossierId(),
                "proc-" + System.nanoTime(), "sign", base.sourceElementId(), base.sourceStorageRef(), base.sourceDigest(),
                "eid", null, base.mode(), base.status(), null, false, "QES", base.deadlineAt(), null, 0, NOW, null, "test",
                0L);
        List<Signer> signers = SignatureProgression.start(r.mode(),
                List.of(external(r.id(), 0, true, "a@example.org"), external(r.id(), 1, true, "b@example.org"))).signers();
        requests.insert(r, signers);
        return new Seed(r, signers);
    }

    private static SignedUpload upload(Seed seed, int signerIndex, int sequence, String fingerprint, String ref) {
        SignedUploadEvidence evidence = new SignedUploadEvidence("Alice Martin", "CN=Citizen CA", "0A1B", fingerprint,
                Instant.parse("2026-09-30T09:59:00Z"), NOW, "PAdES-BASELINE-B", "RSA_SHA256", true, true, "OCSP",
                Instant.parse("2026-09-30T09:58:00Z"));
        return new SignedUpload(UUID.randomUUID(), TENANT, seed.request().id(), seed.signers().get(signerIndex).id(),
                sequence, ref, "d".repeat(64), evidence, NOW);
    }

    @Test
    void saveThenReadRoundTripsTheEvidenceInRankOrder() {
        Seed seed = seed();
        SignedUpload second = upload(seed, 1, 2, "f2", "ref-2");
        SignedUpload first = upload(seed, 0, 1, "f1", "ref-1");
        uploads.save(second);
        uploads.save(first);

        assertThat(uploads.findByRequestId(seed.request().id())).containsExactly(first, second);
        assertThat(uploads.findByRequestId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aRankIsUsedOncePerRequest() {
        Seed seed = seed();
        uploads.save(upload(seed, 0, 1, "f1", "ref-1"));

        assertThatThrownBy(() -> uploads.save(upload(seed, 1, 1, "f2", "ref-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(uploads.findByRequestId(seed.request().id())).hasSize(1);
    }

    @Test
    void aCertificateSignsOnlyOncePerRequestButMayServeAnotherRequest() {
        Seed seed = seed();
        uploads.save(upload(seed, 0, 1, "same", "ref-1"));

        assertThatThrownBy(() -> uploads.save(upload(seed, 1, 2, "same", "ref-2")))
                .isInstanceOf(DataIntegrityViolationException.class);

        Seed other = seed();
        uploads.save(upload(other, 0, 1, "same", "ref-3"));
        assertThat(uploads.findByRequestId(other.request().id())).hasSize(1);
    }

    @Test
    void longCertificateTextIsKeptAndBoundedFieldsAreTruncatedInsteadOfFailing() {
        Seed seed = seed();
        SignedUploadEvidence evidence = new SignedUploadEvidence("N".repeat(5000), "I".repeat(5000), "S".repeat(300),
                "f".repeat(64), null, NOW, "F".repeat(200), "A".repeat(200), false, false, "O".repeat(50), null);
        uploads.save(new SignedUpload(UUID.randomUUID(), TENANT, seed.request().id(), seed.signers().get(0).id(), 1,
                "ref", "d".repeat(64), evidence, NOW));

        SignedUploadEvidence read = uploads.findByRequestId(seed.request().id()).getFirst().evidence();
        assertThat(read.signerName()).hasSize(5000);
        assertThat(read.certificateIssuer()).hasSize(5000);
        assertThat(read.certificateSerial()).hasSize(300);
        assertThat(read.signatureFormat()).hasSize(64);
        assertThat(read.signatureAlgorithm()).hasSize(64);
        assertThat(read.revocationSource()).hasSize(16);
        assertThat(read.signingTime()).isNull();
    }

    @Test
    void purgeListsTheBlobsOfTheDossierThenDeletesOnlyItsRows() {
        Seed seed = seed();
        Seed other = seed();
        uploads.save(upload(seed, 0, 1, "f1", "ref-a"));
        uploads.save(upload(seed, 1, 2, "f2", "ref-b"));
        uploads.save(upload(other, 0, 1, "f3", "ref-c"));

        assertThat(uploads.findStorageRefsByDossierId(seed.request().dossierId())).containsExactlyInAnyOrder("ref-a", "ref-b");

        assertThat(uploads.deleteAllByDossierId(seed.request().dossierId())).isEqualTo(2);

        assertThat(uploads.findByRequestId(seed.request().id())).isEmpty();
        assertThat(uploads.findByRequestId(other.request().id())).hasSize(1);
        assertThat(requests.findById(seed.request().id())).isPresent();
    }

    @Test
    void deletingTheRequestsOfADossierRemovesTheirUploadsToo() {
        Seed seed = seed();
        uploads.save(upload(seed, 0, 1, "f1", "ref-a"));

        requests.deleteAllByDossierId(seed.request().dossierId());

        assertThat(uploads.findByRequestId(seed.request().id())).isEmpty();
    }

    @Test
    void lockingTheRequestRowMakesAConcurrentWriterWait() throws Exception {
        Seed seed = seed();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstCommitted = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                assertThat(requests.findByIdForUpdate(seed.request().id())).isPresent();
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                firstCommitted.set(true);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = pool.submit(() -> tx.execute(status -> {
                requests.findByIdForUpdate(seed.request().id());
                return firstCommitted.get();
            }));
            Thread.sleep(500);
            assertThat(second.isDone()).as("the second writer waits for the row lock").isFalse();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
