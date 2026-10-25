package be.myrmacta.infrastructure.dossier;

import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.infrastructure.config.AttachmentScanProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * D-AV-CLAMAV-PROTO: talks to {@code clamd} over its native {@code INSTREAM} TCP protocol — no
 * third-party client library. D-AV-CLAMAV-FAILMODE: any technical failure (connection refused,
 * timeout, an unrecognized/malformed response — including a "stream size limit exceeded" reply)
 * fails open (accepted as clean) rather than blocking the upload; only a scan that actually
 * completes with an infected verdict is rejected. D-AV-CLAMAV-HEALTH: every fail-open increments
 * {@link #FAIL_OPEN_COUNTER} so a prolonged {@code clamd} outage stays observable, not just
 * logged per request.
 */
public class ClamAvAttachmentScanAdapter implements AttachmentScanPort {

    public static final String FAIL_OPEN_COUNTER = "myrmacta.attachment.scan.fail_open";

    /**
     * Borne haute sur la réponse {@code clamd} bufferisée — un statut clamd tient normalement en
     * quelques dizaines d'octets ; même convention que {@code ConnectorProperties.Http.
     * maxResponseBytes}/{@code Webhook.maxBodyBytes} : toute lecture réseau externe de ce dépôt
     * est bornée, jamais bufferisée indéfiniment (trouvé en Phase 7, check implementation).
     */
    static final int MAX_RESPONSE_BYTES = 4096;

    private static final Logger log = LoggerFactory.getLogger(ClamAvAttachmentScanAdapter.class);
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 8192;
    private static final String OK_RESPONSE = "stream: OK";
    private static final String INFECTED_PREFIX = "stream: ";
    private static final String INFECTED_SUFFIX = " FOUND";

    private final AttachmentScanProperties.ClamAv properties;
    private final Counter failOpenCounter;

    public ClamAvAttachmentScanAdapter(AttachmentScanProperties.ClamAv properties, MeterRegistry meterRegistry) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.failOpenCounter =
                Counter.builder(FAIL_OPEN_COUNTER).register(Objects.requireNonNull(meterRegistry, "meterRegistry"));
    }

    @Override
    public AttachmentScanResult scan(byte[] content, String declaredContentType) {
        try {
            String response = talkToClamd(content).trim();
            if (response.equals(OK_RESPONSE)) {
                return AttachmentScanResult.ofClean();
            }
            if (response.startsWith(INFECTED_PREFIX)
                    && response.endsWith(INFECTED_SUFFIX)
                    && response.length() >= INFECTED_PREFIX.length() + INFECTED_SUFFIX.length()) {
                String signature = response.substring(
                        INFECTED_PREFIX.length(), response.length() - INFECTED_SUFFIX.length());
                return AttachmentScanResult.ofInfected(signature);
            }
            return failOpen("unexpected clamd response: " + response);
        } catch (IOException ex) {
            return failOpen("clamd scan failed: " + ex.getMessage());
        }
    }

    private AttachmentScanResult failOpen(String reason) {
        failOpenCounter.increment();
        log.warn("Attachment scan bypassed (fail-open): {}", sanitizeForLog(reason));
        return AttachmentScanResult.ofClean();
    }

    /**
     * Défense en profondeur (revue adversariale) : {@code reason} peut inclure jusqu'à {@link
     * #MAX_RESPONSE_BYTES} d'octets bruts renvoyés par {@code clamd} — un moteur standard ne les
     * contient jamais, mais neutralise l'injection de fausses lignes de log si {@code clamd}
     * était compromis ou non standard.
     */
    static String sanitizeForLog(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String talkToClamd(byte[] content) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(properties.host(), properties.port()),
                    (int) properties.connectTimeout().toMillis());
            socket.setSoTimeout((int) properties.readTimeout().toMillis());

            // Trouvé en revue adversariale (sous-agent) : setSoTimeout() ne borne que les read()
            // Java (Javadoc Socket.setSoTimeout) — jamais les write()/writeInt() ci-dessous.
            // Un clamd vivant mais qui ne draine pas assez vite (file de scan saturée, charge
            // légitime) bloquait indéfiniment le thread de requête HTTP à l'écriture, avec assez
            // d'uploads concurrents jusqu'à épuiser le pool de threads Tomcat. Un watchdog ferme
            // la socket au-delà du budget — fermer une socket débloque immédiatement un read/
            // write en cours (SocketException), contrairement à Thread.interrupt() qui n'a aucun
            // effet sur un java.io.Socket bloquant (vérifié : un test avec @Timeout basé sur
            // l'interruption ne suffisait pas à arrêter le blocage, seule la fermeture de la
            // socket le fait).
            Thread ioWatchdog = startIoWatchdog(socket, properties.readTimeout());
            try {
                OutputStream rawOut = socket.getOutputStream();
                rawOut.write(INSTREAM_COMMAND);
                DataOutputStream out = new DataOutputStream(rawOut);
                int offset = 0;
                while (offset < content.length) {
                    int len = Math.min(CHUNK_SIZE, content.length - offset);
                    out.writeInt(len);
                    out.write(content, offset, len);
                    offset += len;
                }
                out.writeInt(0);
                out.flush();

                return readResponse(socket.getInputStream());
            } finally {
                ioWatchdog.interrupt();
            }
        }
    }

    private static Thread startIoWatchdog(Socket socket, Duration budget) {
        Thread watchdog = new Thread(
                () -> {
                    try {
                        Thread.sleep(budget.toMillis());
                        socket.close();
                    } catch (InterruptedException expected) {
                        // l'écriture/lecture s'est terminée avant l'expiration du budget —
                        // interrompu par le bloc finally de talkToClamd, rien à faire.
                    } catch (IOException ignored) {
                        // fermeture déjà en cours ailleurs (try-with-resources) — sans conséquence.
                    }
                },
                "clamav-scan-io-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }

    private static String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            if (buffer.size() >= MAX_RESPONSE_BYTES) {
                throw new IOException("clamd response exceeded " + MAX_RESPONSE_BYTES + " bytes");
            }
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}
