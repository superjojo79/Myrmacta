package be.myrmacta.infrastructure.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.infrastructure.config.NotificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationInboxRetentionJobTest {

    @Test
    void purge_delegatesToUserNotificationRepositoryWithRetentionCutoff() {
        // UT-NOTIF-17/18 (purge boundary itself is covered at the repository layer)
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        when(repository.purgeOlderThan(any())).thenReturn(3);
        NotificationProperties properties =
                new NotificationProperties(null, null, null, new NotificationProperties.Inbox(Duration.ofDays(30), Duration.ofDays(1)));

        NotificationInboxRetentionJob job = new NotificationInboxRetentionJob(repository, properties);

        job.purge();

        verify(repository).purgeOlderThan(any());
    }
}
