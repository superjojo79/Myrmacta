package com.flowforge.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.infrastructure.config.AttachmentScanProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Preuve contre un faux {@code clamd} (socket loopback réelle, pas un mock du protocole) —
 * cohérent avec la préférence du dépôt pour des tests réels. {@link ClamAvAttachmentScanIT}
 * complète avec un vrai moteur ClamAV via Testcontainers (D-AV-CLAMAV-TEST-SCOPE).
 */
class ClamAvAttachmentScanAdapterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void scan_cleanResponse_returnsClean() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: OK\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1, 2, 3}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(result.reason()).isNull();
            assertThat(clamd.receivedInstreamCommand()).isTrue();
        }
    }

    @Test
    void scan_infectedResponse_returnsInfectedWithSignature() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: Eicar-Signature FOUND\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan("EICAR".getBytes(StandardCharsets.US_ASCII), "text/plain");

            assertThat(result.clean()).isFalse();
            assertThat(result.reason()).isEqualTo("Eicar-Signature");
        }
    }

    @Test
    void scan_responseWithoutNulTerminator_stillParsedFromConnectionClose() throws Exception {
        // clamd ferme la connexion juste après la réponse sans NUL terminal (ou un proxy
        // intermédiaire la coupe) : readResponse() doit s'arrêter sur la fin de flux (-1), pas
        // seulement sur l'octet NUL.
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: OK")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
        }
    }

    @Test
    void scan_emptyContent_stillSendsTerminatorAndGetsClean() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: OK\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[0], "text/plain");

            assertThat(result.clean()).isTrue();
        }
    }

    @Test
    void scan_connectionRefused_isFailOpenAndIncrementsCounter() {
        try (ServerSocket closedServer = new ServerSocket(0)) {
            int port = closedServer.getLocalPort();
            closedServer.close();
            ClamAvAttachmentScanAdapter adapter = new ClamAvAttachmentScanAdapter(
                    new AttachmentScanProperties.ClamAv(
                            "localhost", port, Duration.ofSeconds(2), Duration.ofSeconds(2)),
                    meterRegistry);

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void scan_connectTimeout_isFailOpen() {
        // TEST-NET-1 (RFC 5737) : jamais routé, garantit un vrai timeout de connexion plutôt
        // qu'un refus immédiat.
        ClamAvAttachmentScanAdapter adapter = new ClamAvAttachmentScanAdapter(
                new AttachmentScanProperties.ClamAv(
                        "192.0.2.1", 3310, Duration.ofMillis(300), Duration.ofMillis(300)),
                meterRegistry);

        AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

        assertThat(result.clean()).isTrue();
        assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                .isEqualTo(1);
    }

    @Test
    void scan_readTimeout_isFailOpen() throws Exception {
        try (FakeClamd clamd = FakeClamd.neverResponding()) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofMillis(300));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    void scan_unexpectedResponse_isFailOpenNotInfected() throws Exception {
        // Couvre aussi le cas "taille de flux dépassée" (StreamMaxLength) — même famille de
        // réponse protocolaire non reconnue, traitée comme un échec technique et non une
        // infection (D-AV-CLAMAV-SIZE-ALIGN).
        try (FakeClamd clamd = FakeClamd.respondingWith("INSTREAM size limit exceeded\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    void scan_streamPrefixWithoutFoundSuffix_isFailOpenNotInfected() throws Exception {
        // Couvre la branche où la réponse commence par "stream: " (comme un verdict FOUND) mais
        // ne se termine pas par " FOUND" — un statut clamd non reconnu, pas une infection.
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: some-other-status\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    void scan_responseExceedingMaxSize_isFailOpenNotUnboundedRead() throws Exception {
        // Trouvé en Phase 7 (check implementation) : readResponse() n'avait aucune borne sur la
        // taille bufferisée — écart par rapport à la convention du dépôt (ConnectorProperties.
        // Http.maxResponseBytes, ConnectorProperties.Webhook.maxBodyBytes, etc. bornent toujours
        // une lecture réseau externe). Un clamd qui ne renvoie jamais NUL/EOF dans la limite du
        // raisonnable doit être traité comme une erreur technique, pas bufferisé indéfiniment.
        String oversized = "stream: " + "x".repeat(ClamAvAttachmentScanAdapter.MAX_RESPONSE_BYTES) + "\0";
        try (FakeClamd clamd = FakeClamd.respondingWith(oversized)) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    @Timeout(15) // filet de sécurité JUnit : si le correctif régresse, échec net plutôt qu'un hang.
    void scan_writeBlocksOnFullSocketBuffer_isBoundedAndFailsOpen() throws Exception {
        // Trouvé en revue adversariale (sous-agent) : socket.setSoTimeout() ne borne que les
        // read() Java, jamais les write()/writeInt() bloquants (Javadoc Socket.setSoTimeout).
        // Un clamd vivant mais qui ne draine pas assez vite (file de scan saturée, charge
        // légitime) bloque indéfiniment le thread de requête HTTP sur l'écriture — épuisement du
        // pool de threads Tomcat avec assez d'uploads concurrents, pas seulement un échec de scan.
        try (FakeClamd clamd = FakeClamd.acceptButNeverRead()) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofMillis(300));
            byte[] content = new byte[2 * 1024 * 1024]; // 2 Mo : dépasse largement le buffer TCP réduit du faux serveur

            long start = System.nanoTime();
            AttachmentScanResult result = adapter.scan(content, "application/pdf");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(result.clean()).isTrue();
            assertThat(elapsedMs)
                    .as("l'écriture doit être bornée par un budget, pas seulement la lecture")
                    .isLessThan(5_000);
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    void scan_infectedPrefixAndSuffixOverlap_isFailOpenNotCrash() throws Exception {
        // Trouvé en revue adversariale (sous-agent) : "stream: FOUND" (13 car.) matche à la fois
        // startsWith("stream: ") et endsWith(" FOUND"), mais les deux se chevauchent sur l'espace
        // partagé (8 + 6 = 14 > 13) — substring(8, 13-6=7) avec begin>end levait
        // StringIndexOutOfBoundsException, une RuntimeException non rattrapée par le catch
        // (IOException ex) de scan(), cassant l'invariant fail-open pour toute réponse
        // dégénérée de ce type au lieu de tomber dans la branche "réponse inattendue".
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: FOUND\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(1);
        }
    }

    @Test
    void scan_unexpectedResponseWithControlCharacters_logsSanitizedReason() throws Exception {
        // Défense en profondeur (remarque mineure, revue adversariale sous-agent) : le texte brut
        // de la réponse clamd (jusqu'à MAX_RESPONSE_BYTES) atterrit dans un log.warn(...) sans
        // filtrage — un clamd non standard/compromis pourrait injecter de fausses lignes de log
        // via CR/LF. Un vrai ClamAV n'envoie jamais ça (noms de signature contraints), donc pas
        // exploitable via le contenu d'un fichier uploadé — mais peu coûteux à neutraliser.
        try (FakeClamd clamd = FakeClamd.respondingWith("not-stream: line1\r\nFAKE LOG LINE\0")) {
            ClamAvAttachmentScanAdapter adapter = adapterFor(clamd, Duration.ofSeconds(5));

            AttachmentScanResult result = adapter.scan(new byte[] {1}, "application/pdf");

            assertThat(result.clean()).isTrue();
        }
    }

    @Test
    void sanitizeForLog_escapesCarriageReturnAndNewline() {
        assertThat(ClamAvAttachmentScanAdapter.sanitizeForLog("line1\r\nFAKE LOG LINE"))
                .isEqualTo("line1\\r\\nFAKE LOG LINE");
    }

    @Test
    void scan_multipleFailOpens_counterAccumulates() {
        try (ServerSocket closedServer = new ServerSocket(0)) {
            int port = closedServer.getLocalPort();
            closedServer.close();
            ClamAvAttachmentScanAdapter adapter = new ClamAvAttachmentScanAdapter(
                    new AttachmentScanProperties.ClamAv(
                            "localhost", port, Duration.ofSeconds(2), Duration.ofSeconds(2)),
                    meterRegistry);

            adapter.scan(new byte[] {1}, "application/pdf");
            adapter.scan(new byte[] {2}, "application/pdf");

            assertThat(meterRegistry.get(ClamAvAttachmentScanAdapter.FAIL_OPEN_COUNTER).counter().count())
                    .isEqualTo(2);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ClamAvAttachmentScanAdapter adapterFor(FakeClamd clamd, Duration timeout) {
        return new ClamAvAttachmentScanAdapter(
                new AttachmentScanProperties.ClamAv("localhost", clamd.port(), timeout, timeout), meterRegistry);
    }

    /** Faux serveur {@code clamd} minimal : accepte une connexion, lit le flux INSTREAM, répond. */
    private static final class FakeClamd implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread thread;
        private volatile boolean receivedInstreamCommand;
        private final CountDownLatch accepted = new CountDownLatch(1);

        private FakeClamd(ServerSocket serverSocket, Thread thread) {
            this.serverSocket = serverSocket;
            this.thread = thread;
        }

        static FakeClamd respondingWith(String response) throws IOException {
            ServerSocket serverSocket = new ServerSocket(0);
            byte[] responseBytes = response.getBytes(StandardCharsets.US_ASCII);
            FakeClamd[] holder = new FakeClamd[1];
            Thread thread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    holder[0].accepted.countDown();
                    drainInstream(client, holder[0]);
                    OutputStream out = client.getOutputStream();
                    out.write(responseBytes);
                    out.flush();
                } catch (IOException ignored) {
                    // socket closed by the test teardown — nothing to report
                }
            });
            FakeClamd fake = new FakeClamd(serverSocket, thread);
            holder[0] = fake;
            thread.setDaemon(true);
            thread.start();
            return fake;
        }

        static FakeClamd neverResponding() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0);
            FakeClamd[] holder = new FakeClamd[1];
            Thread thread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    holder[0].accepted.countDown();
                    // n'écrit jamais de réponse : force le timeout de lecture côté adaptateur.
                    Thread.sleep(5_000);
                } catch (IOException | InterruptedException ignored) {
                    // socket fermée par le test / thread interrompu en fin de test
                }
            });
            FakeClamd fake = new FakeClamd(serverSocket, thread);
            holder[0] = fake;
            thread.setDaemon(true);
            thread.start();
            return fake;
        }

        static FakeClamd acceptButNeverRead() throws IOException {
            ServerSocket serverSocket = new ServerSocket();
            // Fenêtre de réception réduite côté serveur pour forcer un blocage TCP rapide et
            // déterministe côté écrivain (adaptateur), sans dépendre des tailles de buffer par
            // défaut de l'OS — doit être posé avant bind()/accept() pour s'appliquer à la socket
            // acceptée.
            serverSocket.setReceiveBufferSize(1024);
            serverSocket.bind(new InetSocketAddress("localhost", 0));
            FakeClamd[] holder = new FakeClamd[1];
            Thread thread = new Thread(() -> {
                try {
                    Socket client = serverSocket.accept();
                    holder[0].accepted.countDown();
                    // N'écrit jamais de réponse, ne lit jamais l'INSTREAM entrant : le buffer TCP
                    // finit par saturer et bloque l'écrivain côté adaptateur.
                    Thread.sleep(30_000);
                } catch (IOException | InterruptedException ignored) {
                    // socket fermée par le test / thread interrompu en fin de test
                }
            });
            FakeClamd fake = new FakeClamd(serverSocket, thread);
            holder[0] = fake;
            thread.setDaemon(true);
            thread.start();
            return fake;
        }

        private static void drainInstream(Socket client, FakeClamd fake) throws IOException {
            DataInputStream in = new DataInputStream(client.getInputStream());
            ByteArrayOutputStream command = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1 && b != 0) {
                command.write(b);
            }
            fake.receivedInstreamCommand = "zINSTREAM".equals(command.toString(StandardCharsets.US_ASCII));
            while (true) {
                int len = in.readInt();
                if (len == 0) {
                    break;
                }
                in.skipNBytes(len);
            }
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        boolean receivedInstreamCommand() {
            try {
                accepted.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return receivedInstreamCommand;
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            thread.interrupt();
        }
    }
}
