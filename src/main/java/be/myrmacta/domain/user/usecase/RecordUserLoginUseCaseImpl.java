package be.myrmacta.domain.user.usecase;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;

public final class RecordUserLoginUseCaseImpl implements RecordUserLoginUseCase {

    static final int MAX_DISPLAY_NAME_LENGTH = 200;
    static final int MAX_EMAIL_LENGTH = 320;

    private final String tenantId;
    private final KnownUserRepository repository;
    private final Clock clock;

    public RecordUserLoginUseCaseImpl(String tenantId, KnownUserRepository repository, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void record(String userId, String name, String email, Set<String> roles) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        String cleanEmail = truncate(blankToNull(email), MAX_EMAIL_LENGTH);
        String displayName = blankToNull(name);
        if (displayName == null) {
            displayName = cleanEmail != null ? cleanEmail : userId;
        }
        repository.upsert(new KnownUser(
                tenantId,
                userId,
                truncate(displayName, MAX_DISPLAY_NAME_LENGTH),
                cleanEmail,
                roles,
                clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
