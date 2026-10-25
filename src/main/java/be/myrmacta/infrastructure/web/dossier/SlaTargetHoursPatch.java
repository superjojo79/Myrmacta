package be.myrmacta.infrastructure.web.dossier;

final class SlaTargetHoursPatch {

    private SlaTargetHoursPatch() {}

    static Integer resolve(Integer existing, Integer requested, Boolean clearFlag) {
        if (Boolean.TRUE.equals(clearFlag) && requested != null) {
            throw new IllegalArgumentException("clearSlaTargetHours cannot be combined with slaTargetHours");
        }
        if (Boolean.TRUE.equals(clearFlag)) {
            return null;
        }
        return requested == null ? existing : requested;
    }
}
