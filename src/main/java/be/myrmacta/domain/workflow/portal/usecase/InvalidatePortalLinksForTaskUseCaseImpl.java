package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.port.in.InvalidatePortalLinksForTaskUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * TODO §68 T-INVAL — une tâche qui se termine hors du portail (complétée en interne, supprimée,
 * process annulé ou purgé) révoque ses liens vivants. La soumission portail passe d'abord le lien
 * en {@code COMPLETED} avant de compléter la tâche : il n'est alors plus vivant et n'est pas
 * révoqué ici.
 */
public final class InvalidatePortalLinksForTaskUseCaseImpl implements InvalidatePortalLinksForTaskUseCase {

    public static final String REVOKED_BY_ENGINE = "flowable-task-ended";

    private final PortalLinkRepository repository;
    private final Clock clock;

    public InvalidatePortalLinksForTaskUseCaseImpl(PortalLinkRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public int onTaskEnded(String taskId) {
        int revoked = 0;
        for (PortalLink live : repository.findLiveByTaskId(taskId)) {
            PortalLink next = live.revoked(clock.instant(), REVOKED_BY_ENGINE, PortalLinkStatusReason.TASK_GONE);
            if (repository.transition(live, next)) {
                revoked++;
            }
        }
        return revoked;
    }
}
