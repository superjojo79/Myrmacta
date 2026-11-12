package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

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
    void groupsAndCompensationsSucceed_unknownRoutesDoNot() {
        assertThat(apis.respond("PUT /iam/users/u-1/groups/developpeurs", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /iam/users/u-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /sirh/employees/E-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("GET /sirh/employees", new byte[0]).status()).isEqualTo(404);
    }
}
