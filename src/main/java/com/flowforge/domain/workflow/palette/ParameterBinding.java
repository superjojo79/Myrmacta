package com.flowforge.domain.workflow.palette;

/** Comment la valeur d'un paramètre s'écrit dans le XML (D-PALETTE-BINDING). */
public enum ParameterBinding {
    /** {@code flowable:<nom>="valeur"} sur l'activité. */
    ATTRIBUTE,
    /** {@code <flowable:field name=…><flowable:string>…} (clé de la table DMN). */
    FIELD_ELEMENT,
    /** {@code flowable:formKey="user-question:<valeur>"}. */
    FORM_KEY_SUFFIX,
    /** {@code <flowable:signer source= kind= required= name=/>} répétés. */
    SIGNER_ELEMENTS
}
