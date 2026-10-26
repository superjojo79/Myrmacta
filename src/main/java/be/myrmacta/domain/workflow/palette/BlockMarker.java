package be.myrmacta.domain.workflow.palette;

/**
 * Motif XML qui reconnaît une brique (D-PALETTE-RECOGNITION) et sert à la créer. Champs non utilisés à {@code null}
 * ou {@code false}. {@code async} : la palette crée l'élément avec {@code flowable:async="true"} (TODO §106,
 * D-PRECHECK-BLOCK : une brique dont le déploiement exige l'asynchrone) ; ce n'est pas un critère de reconnaissance.
 */
public record BlockMarker(String delegate, String serviceTaskType, String formKeyPrefix, boolean portalRecipient,
        boolean async) {

    public static BlockMarker delegate(String delegate) {
        return new BlockMarker(delegate, null, null, false, false);
    }

    /** Brique toujours asynchrone (son appel ne tient pas dans la transaction d'une complétion de tâche). */
    public static BlockMarker asyncDelegate(String delegate) {
        return new BlockMarker(delegate, null, null, false, true);
    }

    public static BlockMarker dmn() {
        return new BlockMarker(null, "dmn", null, false, false);
    }

    public static BlockMarker userQuestion() {
        return new BlockMarker(null, null, "user-question:", false, false);
    }

    public static BlockMarker externalTask() {
        return new BlockMarker(null, null, "user-question:", true, false);
    }
}
