package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §127 (D-DEMO-FAKE-APIS) — réponses des faux SIRH, IAM et ITSM de la démo. */
class DemoFakeApisTest {

    private final DemoFakeApis apis = new DemoFakeApis(0, JsonMapper.builder().build());

    @Test
    void eachCreationGetsANewIdentifier() {
        String first = apis.respond("POST /sirh/employees", new byte[0]).body();
        String second = apis.respond("POST /sirh/employees", new byte[0]).body();

        assertThat(first).startsWith("{\"id\":\"E-");
        assertThat(second).isNotEqualTo(first);
        assertThat(apis.respond("POST /itsm/tickets", new byte[0]).body()).startsWith("{\"number\":\"RITM");
    }

    @Test
    void theIamAccountKeepsTheLoginTheProcessAskedFor() {
        DemoFakeApis.Response response = apis.respond("POST /iam/users",
                "{\"username\":\"evandenbroeck\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).contains("\"username\":\"evandenbroeck\"").contains("\"id\":\"u-");
    }

    @Test
    void anUnreadableBodyStillGetsALogin() {
        assertThat(apis.respond("POST /iam/users", "pas du json".getBytes(StandardCharsets.UTF_8)).body())
                .contains("\"username\":\"user");
    }

    @Test
    void theSirhDropsTheConnection_forAnAddressInTheOutageDomain() {
        assertThat(apis.respond("POST /sirh/employees",
                "{\"personalEmail\":\"nadia.claes@example.invalid\"}".getBytes(StandardCharsets.UTF_8)))
                .isSameAs(DemoFakeApis.Response.DROP);
        assertThat(apis.respond("POST /sirh/employees",
                "{\"personalEmail\":\"elise.vdb@example.org\"}".getBytes(StandardCharsets.UTF_8)).status())
                .isEqualTo(201);
    }

    @Test
    void startListensOnlyOnTheLoopbackAddress() {
        apis.start();
        try {
            assertThat(apis.listenAddress().getAddress().isLoopbackAddress()).isTrue();
        } finally {
            apis.stop();
        }
    }

    @Test
    void aBodyBeyondTheLimitIsNotRead_andTheReceivedListDoesNotGrowWithoutBound() throws Exception {
        apis.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String head = "{\"username\":\"";
            String tail = "kept\"}";
            byte[] overflow = new byte[DemoFakeApis.MAX_BODY_BYTES + tail.length()];
            Arrays.fill(overflow, (byte) 'x');
            System.arraycopy(head.getBytes(StandardCharsets.UTF_8), 0, overflow, 0, head.length());
            System.arraycopy(tail.getBytes(StandardCharsets.UTF_8), 0, overflow, DemoFakeApis.MAX_BODY_BYTES, tail.length());
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(apis.baseUrl() + "/iam/users"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(overflow)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).doesNotContain("kept");

            for (int i = 0; i < DemoFakeApis.MAX_RECEIVED + 20; i++) {
                client.send(HttpRequest.newBuilder(URI.create(apis.baseUrl() + "/nope")).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
            }
            assertThat(apis.received()).hasSize(DemoFakeApis.MAX_RECEIVED);
        } finally {
            apis.stop();
        }
    }

    @Test
    void theRestOfAnOversizedBodyIsDrained_soTheNextKeepAliveRequestIsIntact() throws Exception {
        apis.start();
        try (Socket socket = new Socket(apis.listenAddress().getAddress(), apis.listenAddress().getPort())) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] overflow = new byte[DemoFakeApis.MAX_BODY_BYTES + 64];
            Arrays.fill(overflow, (byte) 'x');
            String head = "POST /iam/users HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + overflow.length
                    + "\r\nConnection: keep-alive\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(overflow);
            out.flush();
            readHttpResponse(in);

            out.write("GET /sirh/ping HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String second = readHttpResponse(in);

            assertThat(second).startsWith("HTTP/1.1 404");
            assertThat(apis.received()).contains("POST /iam/users", "GET /sirh/ping");
        } finally {
            apis.stop();
        }
    }

    private static String readHttpResponse(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        byte[] end = {'\r', '\n', '\r', '\n'};
        int matched = 0;
        int current;
        while ((current = in.read()) >= 0) {
            raw.write(current);
            matched = current == end[matched] ? matched + 1 : (current == '\r' ? 1 : 0);
            if (matched == end.length) {
                break;
            }
        }
        String headers = raw.toString(StandardCharsets.UTF_8);
        int length = 0;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        return headers + new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    @Test
    void groupsAndCompensationsSucceed_unknownRoutesDoNot() {
        assertThat(apis.respond("PUT /iam/users/u-1/groups/developpeurs", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /iam/users/u-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /sirh/employees/E-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("GET /sirh/employees", new byte[0]).status()).isEqualTo(404);
    }

    // TODO §136 (D-DEMO-FAKE-FAULTS, issue #57) — pannes commandées, pour filmer « Réessayer » et « Abandonner ».

    @Test
    void anArmedFault_answersInErrorTheGivenNumberOfTimes_thenTheServiceSucceedsAgain() {
        apis.arm("iam", 503, 2);

        DemoFakeApis.Response first = apis.respond("POST /iam/users", new byte[0]);
        assertThat(first.status()).isEqualTo(503);
        assertThat(first.body()).contains("\"step\":\"iam\"");
        assertThat(apis.respond("POST /iam/users", new byte[0]).status()).isEqualTo(503);
        assertThat(apis.respond("POST /iam/users", new byte[0]).status()).isEqualTo(201);
    }

    @Test
    void aFaultOnlyHitsItsStep() {
        apis.arm("itsm", 500, 1);

        assertThat(apis.respond("POST /sirh/employees", new byte[0]).status()).isEqualTo(201);
        assertThat(apis.respond("POST /iam/users", new byte[0]).status()).isEqualTo(201);
        assertThat(apis.respond("PUT /iam/users/u-1/groups/developpeurs", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("POST /itsm/tickets", new byte[0]).status()).isEqualTo(500);
    }

    @Test
    void eachStepCanBeArmed_andCompensationsNeverFail() {
        apis.arm("sirh", 502, 1);
        apis.arm("groups", 409, 1);
        apis.arm("iam", 503, 1);

        assertThat(apis.respond("POST /sirh/employees", new byte[0]).status()).isEqualTo(502);
        assertThat(apis.respond("PUT /iam/users/u-1/groups/developpeurs", new byte[0]).status()).isEqualTo(409);
        assertThat(apis.respond("DELETE /iam/users/u-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /sirh/employees/E-1", new byte[0]).status()).isEqualTo(204);
    }

    @Test
    void clearingFaults_restoresSuccess() {
        apis.arm("sirh", 503, 5);
        apis.clearFaults();

        assertThat(apis.respond("POST /sirh/employees", new byte[0]).status()).isEqualTo(201);
    }

    @Test
    void aFaultOutsideTheBounds_isRefused() {
        assertThatThrownBy(() -> apis.arm("ldap", 503, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apis.arm("iam", 399, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apis.arm("iam", 600, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apis.arm("iam", 503, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apis.arm("iam", 503, DemoFakeApis.MAX_FAULT_TIMES + 1))
                .isInstanceOf(IllegalArgumentException.class);
        apis.arm("iam", 400, DemoFakeApis.MAX_FAULT_TIMES);
        apis.arm("iam", 599, 1);
        assertThat(apis.respond("POST /iam/users", new byte[0]).status()).isEqualTo(599);
    }

    @Test
    void theControlRoute_armsAndClearsFaults_withoutBeingRecordedAsAServiceCall() throws Exception {
        apis.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI faults = URI.create(apis.baseUrl() + "/_demo/faults");
            HttpResponse<String> armed = client.send(HttpRequest.newBuilder(faults)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"step\":\"iam\",\"status\":503,\"times\":1}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(armed.statusCode()).isEqualTo(204);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(apis.baseUrl() + "/iam/users"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode()).isEqualTo(503);

            HttpResponse<String> refused = client.send(HttpRequest.newBuilder(faults)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"step\":\"iam\",\"status\":200,\"times\":1}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(refused.statusCode()).isEqualTo(400);
            assertThat(client.send(HttpRequest.newBuilder(faults)
                    .POST(HttpRequest.BodyPublishers.ofString("pas du json")).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode()).isEqualTo(400);

            client.send(HttpRequest.newBuilder(faults)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"step\":\"itsm\",\"status\":500,\"times\":3}")).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(client.send(HttpRequest.newBuilder(faults).DELETE().build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode()).isEqualTo(204);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(apis.baseUrl() + "/itsm/tickets"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode()).isEqualTo(201);
            assertThat(client.send(HttpRequest.newBuilder(faults).GET().build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode()).isEqualTo(405);

            assertThat(apis.received()).containsExactly("POST /iam/users", "POST /itsm/tickets");
        } finally {
            apis.stop();
        }
    }
}
