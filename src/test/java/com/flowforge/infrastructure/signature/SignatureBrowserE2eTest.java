package com.flowforge.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * TODO §72, S-PW-2/3/4/5 — parcours <strong>réel</strong> de bout en bout : un vrai navigateur
 * (Playwright, {@code frontend/e2e-real}) ouvre le lien reçu par e-mail sur un vrai Spring Boot
 * (H2, vrai Flowable, vrai portail statique) ; aucune API simulée. Le test Java sème les données,
 * pilote le navigateur puis vérifie l'état du process, de la demande et des pièces du dossier.
 *
 * <p>Désactivé par défaut (nécessite Node, les navigateurs Playwright et le portail construit dans
 * {@code target/classes/static/portal}) : {@code -Dsignature.e2e=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.signature.provider=stub",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant_dev",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h"
})
class SignatureBrowserE2eTest extends AbstractSignatureEngineTest {

    private static final Path FRONTEND = Path.of("frontend");
    private static final Pattern LINK = Pattern.compile("(https://[^\\s\"<]+/portal/#signature:[A-Za-z0-9_-]{43})");

    @LocalServerPort
    private int port;

    @BeforeEach
    void requireBrowserEnvironment() {
        assumeTrue(Boolean.getBoolean("signature.e2e"), "-Dsignature.e2e=true requis");
        assumeTrue(Files.exists(FRONTEND.resolve("node_modules/.bin/playwright")), "Playwright non installé");
        assumeTrue(Files.exists(Path.of("target/classes/static/portal/index.html")), "portail non construit");
    }

    /** Les liens d'e-mail portent l'URL publique configurée ; on les rejoue sur le port local. */
    private String linkFor(String recipient) {
        sendInvites.sendPending();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort, atLeastOnce()).send(eq(recipient), anyString(), body.capture());
        Matcher m = LINK.matcher(body.getValue());
        assertThat(m.find()).as("lien de signature dans l'e-mail").isTrue();
        return m.group(1).replace("https://ff.example.org", "http://localhost:" + port);
    }

    private void browser(String link, String mode) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("SIGNATURE_LINK", link);
        env.put("SIGNATURE_MODE", mode);
        ProcessBuilder pb = new ProcessBuilder(FRONTEND.resolve("node_modules/.bin/playwright").toAbsolutePath()
                .toString(), "test", "-c", "playwright.real.config.ts")
                .directory(FRONTEND.toFile())
                .redirectErrorStream(true);
        pb.environment().putAll(env);
        // La sortie est capturée (et non héritée : elle corromprait le flux du processus Surefire).
        Path log = Files.createTempFile("playwright-real-", ".log");
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.MINUTES);
        String output = Files.readString(log);
        assertThat(finished).as("Playwright terminé\n" + output).isTrue();
        assertThat(process.exitValue()).as("Playwright (" + mode + ")\n" + output).isZero();
    }

    private UUID start(String processKey, String mailA, String mailB) {
        UUID dossierId = dossier(Map.of("signerA", mailA, "signerB", mailB)).id();
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossierId.toString());
        variables.put("generatedDocumentId", producedDoc(dossierId));
        runtimeService.startProcessInstanceByKey(processKey, variables);
        return dossierId;
    }

    private SignatureRequest request(UUID dossierId) {
        return signatureRepository.findByDossierId(dossierId).getFirst();
    }

    private String taskName(UUID dossierId) throws Exception {
        String processInstanceId = request(dossierId).processInstanceId();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            var task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
            if (task != null) {
                return task.getName();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("le process n'a pas repris");
    }

    private void assertSignedArtifacts(UUID dossierId) {
        List<DossierElement> marked = elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.metadata().get("signatureRole") != null)
                .toList();
        assertThat(marked).extracting(e -> e.metadata().get("signatureRole"))
                .containsExactlyInAnyOrder("SIGNED_DOCUMENT", "SIGNATURE_PROOF");
        assertThat(marked).allMatch(e -> Boolean.TRUE.equals(e.metadata().get("simulated")));
    }

    @Test
    void optionalSignerDeclinesThenTheRequiredSignerSigns_processResumesSigned() throws Exception {
        // S-PW-3, S-PW-4
        String mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = start("signatureSmoke", mailA, mailB);
        String linkA = linkFor(mailA);
        String linkB = linkFor(mailB);

        browser(linkB, "decline");
        assertThat(request(dossierId).status()).isEqualTo(SignatureStatus.PENDING);
        browser(linkA, "sign");

        assertThat(request(dossierId).status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(taskName(dossierId)).isEqualTo("Signature obtenue");
        assertSignedArtifacts(dossierId);
    }

    @Test
    void requiredSignerDeclines_processRoutesToNotObtained() throws Exception {
        // S-PW-5
        String mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = start("signatureSmoke", mailA, mailB);
        String linkA = linkFor(mailA);
        String linkB = linkFor(mailB);

        browser(linkA, "decline");

        assertThat(request(dossierId).status()).isEqualTo(SignatureStatus.DECLINED);
        assertThat(taskName(dossierId)).isEqualTo("Signature non obtenue");
        browser(linkB, "denied");
    }

    @Test
    void sequentialSigners_secondIsInvitedOnlyAfterTheFirstHasSigned() throws Exception {
        // S-PW-2
        String mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = start("signatureSequentialSmoke", mailA, mailB);
        String linkA = linkFor(mailA);
        org.mockito.Mockito.verify(emailPort, org.mockito.Mockito.never()).send(eq(mailB), anyString(), anyString());

        browser(linkA, "sign");
        assertThat(request(dossierId).status()).isEqualTo(SignatureStatus.PENDING);
        String linkB = linkFor(mailB);
        browser(linkB, "sign");

        assertThat(request(dossierId).status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(taskName(dossierId)).isEqualTo("Signature obtenue");
        assertSignedArtifacts(dossierId);
    }
}
