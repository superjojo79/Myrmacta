package be.myrmacta.domain.workflow.model;

/** Un attribut de brique référence une ressource du tenant qui n'existe pas (TODO §73, contrôle au déploiement). */
public class UnknownBlockReferenceException extends RuntimeException {

    private final String value;

    public UnknownBlockReferenceException(String activityId, String parameter, String value) {
        super("BPMN activity " + activityId + ": flowable:" + parameter + "=\"" + value + "\" references a resource "
                + "that does not exist");
        this.value = value;
    }

    /** La valeur référencée (TODO §117 : le plan d'import nomme la ressource que le bundle apporte). */
    public String value() {
        return value;
    }
}
