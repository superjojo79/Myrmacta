package be.myrmacta.domain.workflow.model;

/** Un attribut de brique référence une ressource du tenant qui n'existe pas (TODO §73, contrôle au déploiement). */
public class UnknownBlockReferenceException extends RuntimeException {

    public UnknownBlockReferenceException(String activityId, String parameter, String value) {
        super("BPMN activity " + activityId + ": flowable:" + parameter + "=\"" + value + "\" references a resource "
                + "that does not exist");
    }
}
