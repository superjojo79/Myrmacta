package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.infrastructure.config.AttachmentScanProperties;
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

    public static final String FAIL_OPEN_COUNTER = "flowforge.attachment.scan.fail_open";

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
            if (response.startsWith(INFECTED_PREFIX) && response.endsWith(INFECTED_SUFFIX)) {
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
        log.warn("Attachment scan bypassed (fail-open): {}", reason);
        return AttachmentScanResult.ofClean();
    }

    private String talkToClamd(byte[] content) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(properties.host(), properties.port()),
                    (int) properties.connectTimeout().toMillis());
            socket.setSoTimeout((int) properties.readTimeout().toMillis());

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
        }
    }

    private static String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}
