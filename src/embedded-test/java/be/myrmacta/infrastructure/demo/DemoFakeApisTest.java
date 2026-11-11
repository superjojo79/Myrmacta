package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
    void groupsAndCompensationsSucceed_unknownRoutesDoNot() {
        assertThat(apis.respond("PUT /iam/users/u-1/groups/developpeurs", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /iam/users/u-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("DELETE /sirh/employees/E-1", new byte[0]).status()).isEqualTo(204);
        assertThat(apis.respond("GET /sirh/employees", new byte[0]).status()).isEqualTo(404);
    }
}
