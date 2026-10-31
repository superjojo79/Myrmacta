package be.myrmacta.domain.user.usecase;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RecordUserLoginUseCaseImpl implements RecordUserLoginUseCase {

    static final int MAX_DISPLAY_NAME_LENGTH = 200;
    static final int MAX_EMAIL_LENGTH = 320;
    /** TODO §114 — groupes d'IdP gardés par connexion, et longueur de chacun (au-delà : écarté). */
    static final int MAX_IDP_GROUPS = 100;
    static final int MAX_IDP_GROUP_CHARS = 256;

    private final String tenantId;
    private final KnownUserRepository repository;
    private final Clock clock;

    public RecordUserLoginUseCaseImpl(String tenantId, KnownUserRepository repository, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void record(String userId, String name, String email, Set<String> roles, Set<String> idpGroups) {
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
                boundedGroups(idpGroups),
                clock.instant()));
    }

    private static Set<String> boundedGroups(Set<String> idpGroups) {
        if (idpGroups == null) {
            return Set.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String group : idpGroups) {
            String clean = blankToNull(group);
            if (clean != null && clean.length() <= MAX_IDP_GROUP_CHARS && clean.chars().noneMatch(Character::isISOControl)) {
                kept.add(clean);
                if (kept.size() == MAX_IDP_GROUPS) {
                    break;
                }
            }
        }
        return kept;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
