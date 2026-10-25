package be.myrmacta.domain.notification.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotificationPayloadJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void email_escapesQuotesAndBackslashes() throws Exception {
        String json = NotificationPayloadJson.email("a\"b", "sub\\ject", "line\n2");
        JsonNode node = MAPPER.readTree(json);
        assertThat(node.get("to").asText()).isEqualTo("a\"b");
        assertThat(node.get("subject").asText()).isEqualTo("sub\\ject");
        assertThat(node.get("body").asText()).isEqualTo("line\n2");
    }

    @Test
    void email_withAttachment_carriesTheReferenceAndNoAttachmentKeyOtherwise() throws Exception {
        JsonNode node = MAPPER.readTree(NotificationPayloadJson.email("a@b.c", "S", "B",
                new EmailAttachment("t/doc\"1.pdf", "Décision.pdf", "application/pdf")));
        assertThat(node.get("attachment").get("storageRef").asText()).isEqualTo("t/doc\"1.pdf");
        assertThat(node.get("attachment").get("fileName").asText()).isEqualTo("Décision.pdf");
        assertThat(node.get("attachment").get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(MAPPER.readTree(NotificationPayloadJson.email("a@b.c", "S", "B")).has("attachment")).isFalse();
    }

    @Test
    void webhook_serializesNestedPayloadTypes() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("flag", true);
        nested.put("count", 2);
        nested.put("empty", null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nested", nested);
        payload.put("tags", List.of("a", "b"));

        JsonNode node = MAPPER.readTree(NotificationPayloadJson.webhook(id, payload));
        assertThat(node.get("webhookConfigId").asText()).isEqualTo(id.toString());
        assertThat(node.get("payload").get("nested").get("flag").asBoolean()).isTrue();
        assertThat(node.get("payload").get("nested").get("count").asInt()).isEqualTo(2);
        assertThat(node.get("payload").get("nested").get("empty").isNull()).isTrue();
        assertThat(node.get("payload").get("tags").get(0).asText()).isEqualTo("a");
    }

    @Test
    void browser_nullPayload_becomesEmptyObject() throws Exception {
        UUID inboxId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        JsonNode node = MAPPER.readTree(NotificationPayloadJson.browser("user-1", null, inboxId));
        assertThat(node.get("userId").asText()).isEqualTo("user-1");
        assertThat(node.get("inboxId").asText()).isEqualTo(inboxId.toString());
        assertThat(node.get("payload").isObject()).isTrue();
        assertThat(node.get("payload").isEmpty()).isTrue();
    }
}
