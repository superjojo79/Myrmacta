package be.myrmacta.infrastructure.signature.eid;

import eu.europa.esig.dss.enumerations.PdfObjectModificationType;
import eu.europa.esig.dss.pdf.PdfArray;
import eu.europa.esig.dss.pdf.PdfDict;
import eu.europa.esig.dss.pdf.PdfObject;
import eu.europa.esig.dss.pdf.modifications.ObjectModification;
import eu.europa.esig.dss.pdf.modifications.PdfObjectModifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ce qu'un téléversement a le droit de changer dans le document remis (TODO §138, D-EID-BINDING), appliqué aux
 * différences d'objets trouvées par DSS ({@code DefaultPdfObjectModificationsFinder}).
 *
 * <p>Les catégories de DSS ne suffisent pas : sa catégorie « signature ou remplissage de formulaire » admet le
 * remplissage de n'importe quel champ, la modification d'une annotation existante, l'ajout d'un arbre {@code /Names}
 * (pièces jointes, JavaScript) ou un tableau {@code /Annots} neuf quel que soit son contenu, et il classe la création
 * de l'{@code /AcroForm} d'une première signature en « changement indéfini ». La règle d'ici est une liste blanche :
 *
 * <ul>
 *   <li>Partout : les changements d'extension que DSS reconnaît (dictionnaire {@code /DSS}, horodatage de document,
 *       métadonnées XMP, version, {@code /Extensions}).
 *   <li>Dans la révision de la signature seulement ({@link Phase#SIGNATURE}) : création d'un champ de signature (ou de
 *       son widget), d'un tableau {@code /Annots} ou {@code /Fields} qui ne contient que des champs de signature, de
 *       l'{@code /AcroForm} réduite à ses clés de signature, remplissage d'un champ de signature vide existant, clés
 *       {@code /SigFlags}, {@code /DA}, {@code /DR} de l'{@code /AcroForm}, création de {@code /OutputIntents} (profil de
 *       couleur d'une apparence visible) ou de {@code /Perms}.
 *   <li>Un champ de signature créé ne porte ni action ({@code /A}) ni action additionnelle ({@code /AA}).
 * </ul>
 *
 * Tout le reste est refusé, y compris la modification de la valeur d'un champ de signature déjà rempli.
 */
final class SignatureModificationPolicy {

    /** Où se situe la comparaison. */
    enum Phase {
        /** Du document remis à la fin de la révision signée : la signature et ses objets. */
        SIGNATURE,
        /** De la révision signée au fichier final : extensions seulement (LT, LTA). */
        EXTENSION
    }

    private static final Set<String> ACROFORM_SIGNATURE_KEYS = Set.of("Fields", "SigFlags", "DA", "DR", "NeedAppearances", "Type");
    /**
     * Revue de clôture (HAUT 3) : clés admises d'un champ ou widget de signature ajouté, liste fermée. Rien qui joue un
     * média, joigne un fichier ou déclenche une action ({@code /A}, {@code /AA}, {@code /FS}, {@code /RichMedia},
     * {@code /Movie}, {@code /Sound}, {@code /Contents}…).
     */
    private static final Set<String> SIGNATURE_FIELD_KEYS = Set.of("Type", "Subtype", "FT", "T", "TU", "TM", "V", "F",
            "Ff", "P", "Rect", "AP", "AS", "MK", "DA", "Q", "Parent", "Kids", "Lock", "SV", "StructParent", "NM", "M",
            "Border", "BS", "C");
    private static final int MAX_PARENT_WALK = 16;

    private SignatureModificationPolicy() {}

    /** Les changements refusés (vide : tout est admis). Le libellé de chacun ne porte que des noms de clés PDF. */
    static List<String> refused(PdfObjectModifications modifications, Phase phase) {
        List<String> refused = new ArrayList<>();
        // Extensions (DSS, DocTimeStamp, métadonnées) : admises dans les deux phases.
        List<ObjectModification> others = new ArrayList<>();
        others.addAll(modifications.getFormFillInAndSignatureCreationChanges());
        others.addAll(modifications.getAnnotCreationChanges());
        others.addAll(modifications.getUndefinedChanges());
        for (ObjectModification modification : others) {
            if (phase == Phase.EXTENSION || !isSignatureChange(modification)) {
                refused.add(modification.getActionType() + " " + modification.getObjectTree());
            }
        }
        return refused;
    }

    static boolean isSignatureChange(ObjectModification modification) {
        List<String> chain = modification.getObjectTree().getKeyChain();
        String last = modification.getObjectTree().getLastKey();
        String parent = chain.size() > 1 ? chain.get(chain.size() - 2) : null;
        PdfObjectModificationType action = modification.getActionType();
        PdfObject finalObject = modification.getFinalObject();
        PdfObject originalObject = modification.getOriginalObject();
        boolean creation = action == PdfObjectModificationType.CREATION
                || (action == PdfObjectModificationType.MODIFICATION && originalObject == null);

        // /AcroForm créée par la première signature : seulement ses clés de signature, seulement des champs de signature.
        if (creation && "AcroForm".equals(last) && "Catalog".equals(parent) && finalObject instanceof PdfDict acroForm) {
            return isSignatureAcroForm(acroForm);
        }
        // Clés de signature d'une /AcroForm existante : /SigFlags change ; sous /DR et pour /DA, seulement des
        // créations (revue de clôture, BAS), jamais la modification ou la suppression d'une ressource existante.
        if (chain.contains("AcroForm") && !chain.contains("Fields")) {
            if ("SigFlags".equals(last) && !chain.contains("DR")) {
                return true;
            }
            if (chain.contains("DR") || "DA".equals(last)) {
                return action == PdfObjectModificationType.CREATION;
            }
        }
        // Tableau /Annots ou /Fields créé : seulement des champs de signature.
        if (creation && isOneOf(last, "Annots", "Fields") && finalObject instanceof PdfArray array) {
            return containsOnlySignatureFields(array);
        }
        // Champ ou widget de signature ajouté dans un /Annots ou /Fields existant.
        if (creation && isOneOf(last, "Annots", "Fields", "Kids") && finalObject instanceof PdfDict field) {
            return isNewSignatureField(field);
        }
        // Remplissage d'un champ de signature vide existant : /V posé (jamais remplacé), apparence créée.
        if (creation && (chain.contains("Annots") || chain.contains("Fields")) && withinSignatureField(finalObject)) {
            return !"V".equals(last) || isSignatureValue(finalObject);
        }
        if (action == PdfObjectModificationType.MODIFICATION && (chain.contains("Annots") || chain.contains("Fields"))
                && !"V".equals(last) && chain.contains("AP") && withinSignatureField(finalObject)
                && withinEmptySignatureField(originalObject)) {
            return true;
        }
        // Profil de couleur d'une apparence visible, verrou de certification : création seulement.
        // /Perms (certification /DocMDP) n'est plus admis : il ferait refuser tous les signataires suivants (BAS F).
        if (creation && "Catalog".equals(parent) && isOneOf(last, "OutputIntents")) {
            return true;
        }
        return false;
    }

    private static boolean isSignatureAcroForm(PdfDict acroForm) {
        for (String key : acroForm.list()) {
            if (!ACROFORM_SIGNATURE_KEYS.contains(key)) {
                return false;
            }
        }
        PdfArray fields = acroForm.getAsArray("Fields");
        return fields != null && containsOnlySignatureFields(fields);
    }

    private static boolean containsOnlySignatureFields(PdfArray array) {
        for (int i = 0; i < array.size(); i++) {
            PdfDict field = array.getAsDict(i);
            if (field == null || !isNewSignatureField(field)) {
                return false;
            }
        }
        return true;
    }

    /** Champ (ou widget d'un champ) de signature, sans action, dont la valeur est absente ou une signature. */
    private static boolean isNewSignatureField(PdfDict field) {
        if (!isSignatureField(field)) {
            return false;
        }
        for (String key : field.list()) {
            if (!SIGNATURE_FIELD_KEYS.contains(key)) {
                return false;
            }
        }
        // Une annotation n'est un champ de signature que sous forme de widget (pas de tampon, texte libre, pièce jointe).
        String subtype = field.getNameValue("Subtype");
        if (subtype != null && !"Widget".equals(subtype)) {
            return false;
        }
        if (subtype == null && field.getObject("Rect") != null) {
            return false;
        }
        PdfObject value = field.getObject("V");
        return value == null || isSignatureValue(value);
    }

    private static boolean isSignatureField(PdfDict field) {
        PdfDict current = field;
        for (int depth = 0; current != null && depth < MAX_PARENT_WALK; depth++) {
            String type = current.getNameValue("FT");
            if (type != null) {
                return "Sig".equals(type);
            }
            current = current.getAsDict("Parent");
        }
        return false;
    }

    private static boolean isSignatureValue(PdfObject value) {
        if (!(value instanceof PdfDict dict)) {
            return false;
        }
        String type = dict.getNameValue("Type");
        return "Sig".equals(type) || "DocTimeStamp".equals(type);
    }

    /** L'objet est contenu (par ses parents DSS) dans un champ de signature sans action. */
    private static boolean withinSignatureField(PdfObject object) {
        PdfObject current = object;
        for (int depth = 0; current != null && depth < MAX_PARENT_WALK; depth++) {
            if (current instanceof PdfDict dict && dict.getNameValue("FT") != null) {
                return isNewSignatureField(dict);
            }
            if (current instanceof PdfDict dict && "Widget".equals(dict.getNameValue("Subtype"))
                    && dict.getAsDict("Parent") != null) {
                return isNewSignatureField(dict);
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean withinEmptySignatureField(PdfObject object) {
        PdfObject current = object;
        for (int depth = 0; current != null && depth < MAX_PARENT_WALK; depth++) {
            if (current instanceof PdfDict dict && dict.getNameValue("FT") != null) {
                return "Sig".equals(dict.getNameValue("FT")) && dict.getObject("V") == null;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean isOneOf(String key, String... values) {
        if (key == null) {
            return false;
        }
        for (String value : values) {
            if (value.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
