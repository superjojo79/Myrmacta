package com.flowforge.infrastructure.web.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationInboxApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SendNotificationUseCase sendNotificationUseCase;

    @Test
    void list_returnsOnlyCurrentRecipientsNotifications() throws Exception {
        // IT-NOTIF-1
        String me = "alice-" + UUID.randomUUID();
        String someoneElse = "bob-" + UUID.randomUUID();
        send(me, "Mine");
        send(someoneElse, "Not mine");

        mockMvc.perform(get("/api/notifications").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].payload.title").value("Mine"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void markRead_ownNotification_returns204AndPersistsReadState() throws Exception {
        // IT-NOTIF-3
        String me = "carol-" + UUID.randomUUID();
        send(me, "To read");
        String id = firstNotificationId(me);

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(user(me)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications").with(user(me)))
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void markRead_belongsToAnotherRecipient_returns404NotForbidden() throws Exception {
        // IT-NOTIF-3 — IDOR: never leak existence via 403
        String owner = "dave-" + UUID.randomUUID();
        String stranger = "erin-" + UUID.randomUUID();
        send(owner, "Owner's");
        String id = firstNotificationId(owner);

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(user(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_unknownId_returns404() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", UUID.randomUUID())
                        .with(user("frank-" + UUID.randomUUID()))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void readAll_marksAllOfCurrentRecipientsUnreadNotifications() throws Exception {
        // IT-NOTIF-4
        String me = "grace-" + UUID.randomUUID();
        send(me, "One");
        send(me, "Two");

        mockMvc.perform(post("/api/notifications/read-all").with(user(me)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications").with(user(me)))
                .andExpect(jsonPath("$.content[0].read").value(true))
                .andExpect(jsonPath("$.content[1].read").value(true));
    }

    @Test
    void list_withoutAuthentication_returns401() throws Exception {
        // IT-NOTIF-5
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCount_reflectsServerStateNotClientEstimate() throws Exception {
        // TODO §66 F3 (Phase 10 adversarial review) — server-truth badge count, unaffected by
        // pagination and consistent with what markRead actually persisted.
        String me = "henri-" + UUID.randomUUID();
        send(me, "One");
        send(me, "Two");
        send(me, "Three");
        String firstId = firstNotificationId(me);

        mockMvc.perform(get("/api/notifications/unread-count").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        mockMvc.perform(patch("/api/notifications/{id}/read", firstId).with(user(me)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/unread-count").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void unreadCount_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    private void send(String userId, String title) {
        sendNotificationUseCase.send(
                new NotificationEvent(
                        List.of(NotificationChannel.BROWSER),
                        null,
                        null,
                        null,
                        userId,
                        null,
                        Map.of("type", "TEST", "title", title, "body", "body")),
                TENANT_ID);
    }

    private String firstNotificationId(String userId) throws Exception {
        String body = mockMvc.perform(get("/api/notifications").with(user(userId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(body)
                .get("content")
                .get(0)
                .get("id")
                .asText();
    }

    private static RequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID));
    }
}
