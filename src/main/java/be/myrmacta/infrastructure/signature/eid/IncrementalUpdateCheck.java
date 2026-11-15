package be.myrmacta.infrastructure.signature.eid;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Intégrité d'un prolongement incrémental, lue dans les tables de références croisées (revue de clôture, HAUT 1 et
 * HAUT 3 ; TODO §138, D-EID-BINDING), sans dépendre du parcours d'objets de DSS (qui saute une clé déjà vue sous la
 * même référence dans le document d'avant, sans regarder celle du document d'après).
 *
 * <p><b>Redéfinitions.</b> Tout numéro d'objet du document d'avant dont l'entrée de table change (objet réécrit, ou
 * déplacé vers d'autres octets) est une redéfinition. Elle est refusée, sauf pour une liste fermée, comparée clé par
 * clé à la version d'avant :
 * <ul>
 *   <li>le catalogue : seules {@code /AcroForm} (règle de l'AcroForm), {@code /DSS}, {@code /Extensions},
 *       {@code /Metadata}, {@code /Version} changent, {@code /OutputIntents} et {@code /Perms} s'ajoutent ;
 *   <li>l'{@code /AcroForm} : {@code /Fields} et les ressources {@code /DR} ne font que s'enrichir d'objets neufs,
 *       {@code /SigFlags} change, {@code /DA} et {@code /NeedAppearances} ne s'ajoutent que s'ils manquaient
 *       ({@code /NeedAppearances} faux seulement) ;
 *   <li>une page : seul {@code /Annots} change, en s'enrichissant d'objets neufs ; de même pour un tableau
 *       {@code /Annots} ou {@code /Fields} indirect ;
 *   <li>un champ de signature vide : reçoit {@code /V}, {@code /AP}, {@code /F}, {@code /Rect}, {@code /MK} ;
 *   <li>ce que l'extension LT/LTA redéfinit : le dictionnaire {@code /DSS} et ses objets, les métadonnées XMP,
 *       le dictionnaire {@code /Info} (non affichés).
 * </ul>
 * Un objet du document d'avant libéré (absent de la table d'après) est refusé.
 *
 * <p><b>Apparence des signatures ajoutées.</b> Tout widget de signature neuf a une aire de {@code /Rect} d'au plus
 * {@code maxWidgetAreaFraction} de la page : une signature visible ordinaire passe, une apparence qui couvrirait la
 * page pour afficher d'autres clauses est refusée (la comparaison visuelle masque les annotations ajoutées).
 */
final class IncrementalUpdateCheck {

    private static final int MAX_DSS_OBJECTS = 20_000;
    private static final COSName DSS = COSName.getPDFName("DSS");
    private static final COSName EXTENSIONS = COSName.getPDFName("Extensions");
    private static final COSName OUTPUT_INTENTS = COSName.getPDFName("OutputIntents");
    private static final COSName SIG_FLAGS = COSName.getPDFName("SigFlags");
    private static final COSName NEED_APPEARANCES = COSName.getPDFName("NeedAppearances");
    private static final COSName SIG = COSName.getPDFName("Sig");
    private static final Set<COSName> CATALOG_CHANGEABLE = Set.of(DSS, EXTENSIONS, COSName.METADATA, COSName.VERSION);
    private static final Set<COSName> CATALOG_ADDABLE = Set.of(OUTPUT_INTENTS);
    private static final Set<COSName> EMPTY_FIELD_FILLABLE = Set.of(COSName.V, COSName.AP, COSName.F, COSName.RECT, COSName.MK);

    private IncrementalUpdateCheck() {}

    /** Motif du refus (noms de clés PDF seulement), ou {@code null} si le prolongement est admis. */
    static String refusal(PDDocument before, PDDocument after, double maxWidgetAreaFraction) {
        Context context = new Context(before, after);
        String redefinition = context.redefinitions();
        if (redefinition != null) {
            return redefinition;
        }
        return context.oversizedWidget(maxWidgetAreaFraction);
    }

    private static final class Context {
        private final COSDocument beforeDocument;
        private final COSDocument afterDocument;
        private final PDDocument after;
        private final Map<COSObjectKey, Long> beforeXref;
        private final Map<COSObjectKey, Long> afterXref;
        private final Map<Long, COSObjectKey> beforeByNumber = new HashMap<>();
        private final Map<Long, COSObjectKey> afterByNumber = new HashMap<>();
        private final COSObjectKey catalogKey;
        private final COSObjectKey infoKey;
        private final COSObjectKey acroFormKey;
        private final COSObjectKey metadataKey;
        private final Set<COSObjectKey> annotsOrFieldsArrays = new HashSet<>();
        private final Set<COSObjectKey> dssObjects = new HashSet<>();
        private final Set<COSObjectKey> reachableFromPagesOrForm = new HashSet<>();

        Context(PDDocument before, PDDocument after) {
            this.beforeDocument = before.getDocument();
            this.afterDocument = after.getDocument();
            this.after = after;
            this.beforeXref = beforeDocument.getXrefTable();
            this.afterXref = afterDocument.getXrefTable();
            beforeXref.keySet().forEach(key -> beforeByNumber.put(key.getNumber(), key));
            afterXref.keySet().forEach(key -> afterByNumber.put(key.getNumber(), key));
            COSDictionary trailer = beforeDocument.getTrailer();
            this.catalogKey = keyOf(trailer.getItem(COSName.ROOT));
            this.infoKey = keyOf(trailer.getItem(COSName.INFO));
            COSDictionary catalog = before.getDocumentCatalog().getCOSObject();
            this.acroFormKey = keyOf(catalog.getItem(COSName.ACRO_FORM));
            this.metadataKey = keyOf(catalog.getItem(COSName.METADATA));
            for (PDPage page : before.getPages()) {
                COSObjectKey annots = keyOf(page.getCOSObject().getItem(COSName.ANNOTS));
                if (annots != null) {
                    annotsOrFieldsArrays.add(annots);
                }
            }
            if (catalog.getDictionaryObject(COSName.ACRO_FORM) instanceof COSDictionary acroForm) {
                COSObjectKey fields = keyOf(acroForm.getItem(COSName.FIELDS));
                if (fields != null) {
                    annotsOrFieldsArrays.add(fields);
                }
            }
            collectDss(catalog.getDictionaryObject(DSS), dssObjects);
            collect(catalog.getItem(COSName.PAGES), reachableFromPagesOrForm);
            collect(catalog.getItem(COSName.ACRO_FORM), reachableFromPagesOrForm);
        }

        /**
         * Contre-revue (MOYEN D) : sous {@code /DSS}, seuls {@code /Certs}, {@code /CRLs}, {@code /OCSPs} et {@code /VRI}
         * (puis {@code /Cert}, {@code /CRL}, {@code /OCSP}, {@code /TS}) sont suivis, flux seulement en feuilles.
         */
        private void collectDss(COSBase dss, Set<COSObjectKey> into) {
            if (!(dss instanceof COSDictionary dictionary) || dss instanceof COSStream) {
                return;
            }
            for (String name : List.of("Certs", "CRLs", "OCSPs")) {
                collectStreams(dictionary.getItem(COSName.getPDFName(name)), into);
            }
            COSBase vriItem = dictionary.getItem(COSName.getPDFName("VRI"));
            addKey(vriItem, into);
            if (deref(vriItem) instanceof COSDictionary vri && !(deref(vriItem) instanceof COSStream)) {
                for (COSBase entryItem : vri.getValues()) {
                    addKey(entryItem, into);
                    if (deref(entryItem) instanceof COSDictionary entry && !(deref(entryItem) instanceof COSStream)) {
                        for (String name : List.of("Cert", "CRL", "OCSP")) {
                            collectStreams(entry.getItem(COSName.getPDFName(name)), into);
                        }
                        if (deref(entry.getItem(COSName.getPDFName("TS"))) instanceof COSStream) {
                            addKey(entry.getItem(COSName.getPDFName("TS")), into);
                        }
                    }
                }
            }
        }

        private void collectStreams(COSBase arrayItem, Set<COSObjectKey> into) {
            addKey(arrayItem, into);
            if (deref(arrayItem) instanceof COSArray array) {
                for (int i = 0; i < array.size(); i++) {
                    if (deref(array.get(i)) instanceof COSStream) {
                        addKey(array.get(i), into);
                    }
                }
            }
        }

        private static void addKey(COSBase value, Set<COSObjectKey> into) {
            COSObjectKey key = keyOf(value);
            if (key != null) {
                into.add(key);
            }
        }

        String redefinitions() {
            for (Map.Entry<Long, COSObjectKey> entry : beforeByNumber.entrySet()) {
                COSObjectKey beforeKey = entry.getValue();
                COSObjectKey afterKey = afterByNumber.get(entry.getKey());
                if (afterKey == null) {
                    return "object of the delivered document freed";
                }
                if (afterKey.equals(beforeKey) && Objects.equals(beforeXref.get(beforeKey), afterXref.get(afterKey))) {
                    continue;
                }
                if (!allowedRedefinition(beforeKey, afterKey)) {
                    return "object of the delivered document redefined (" + describe(beforeKey) + ")";
                }
            }
            return null;
        }

        /** Nature de l'objet, en noms de clés PDF seulement (journal). */
        private String describe(COSObjectKey key) {
            COSBase old = beforeDocument.getObjectFromPool(key).getObject();
            if (key.equals(catalogKey)) {
                return "catalog";
            }
            if (key.equals(acroFormKey)) {
                return "AcroForm";
            }
            if (old instanceof COSDictionary dictionary) {
                return (dictionary instanceof COSStream ? "stream " : "dictionary ") + dictionary.keySet();
            }
            return old == null ? "null" : old.getClass().getSimpleName();
        }

        private boolean allowedRedefinition(COSObjectKey beforeKey, COSObjectKey afterKey) {
            COSBase old = beforeDocument.getObjectFromPool(beforeKey).getObject();
            COSBase now = afterDocument.getObjectFromPool(afterKey).getObject();
            // Contre-revue (MOYEN D) : exceptions par rôle seulement si l'objet n'est pas aussi atteint depuis /Pages ou
            // /AcroForm, et de la nature attendue (XMP : flux ; /Info : dictionnaire sans flux).
            if (!reachableFromPagesOrForm.contains(beforeKey)) {
                if (beforeKey.equals(infoKey) && old instanceof COSDictionary && !(old instanceof COSStream)
                        && now instanceof COSDictionary && !(now instanceof COSStream)) {
                    return true;
                }
                if (beforeKey.equals(metadataKey) && old instanceof COSStream && now instanceof COSStream) {
                    return true;
                }
                if (dssObjects.contains(beforeKey) && (old instanceof COSStream) == (now instanceof COSStream)) {
                    return true;
                }
            }
            // Réécriture à l'identique (les outils réécrivent par exemple les champs de signature déjà remplis) :
            // rien ne change, donc admise.
            if (identical(old, now)) {
                return true;
            }
            if (beforeKey.equals(catalogKey)) {
                return old instanceof COSDictionary o && now instanceof COSDictionary n && catalogAllowed(o, n);
            }
            if (beforeKey.equals(acroFormKey)) {
                return old instanceof COSDictionary o && now instanceof COSDictionary n && acroFormAllowed(o, n);
            }
            if (annotsOrFieldsArrays.contains(beforeKey)) {
                return old instanceof COSArray o && now instanceof COSArray n && onlyNewElementsAdded(o, n);
            }
            if (old instanceof COSDictionary o && now instanceof COSDictionary n) {
                if (COSName.PAGE.equals(o.getCOSName(COSName.TYPE))) {
                    return changedKeysWithin(o, n, Set.of(COSName.ANNOTS)) && arrayGrowsWithNewObjects(o, n, COSName.ANNOTS);
                }
                if (SIG.equals(o.getCOSName(COSName.FT)) && o.getItem(COSName.V) == null) {
                    return changedKeysWithin(o, n, EMPTY_FIELD_FILLABLE) && removedNone(o, n);
                }
            }
            return false;
        }

        private boolean catalogAllowed(COSDictionary old, COSDictionary now) {
            for (COSName key : union(old, now)) {
                COSBase o = old.getItem(key);
                COSBase n = now.getItem(key);
                if (same(o, n) || CATALOG_CHANGEABLE.contains(key)) {
                    continue;
                }
                if (CATALOG_ADDABLE.contains(key) && o == null) {
                    continue;
                }
                if (COSName.ACRO_FORM.equals(key) && n != null) {
                    COSBase oldForm = deref(o);
                    COSBase newForm = deref(n);
                    if (oldForm == null && newForm instanceof COSDictionary) {
                        continue; // première signature : l'AcroForm neuve suit la liste blanche des objets neufs
                    }
                    if (oldForm instanceof COSDictionary of && newForm instanceof COSDictionary nf && acroFormAllowed(of, nf)) {
                        continue;
                    }
                }
                return false;
            }
            return true;
        }

        private boolean acroFormAllowed(COSDictionary old, COSDictionary now) {
            for (COSName key : union(old, now)) {
                COSBase o = old.getItem(key);
                COSBase n = now.getItem(key);
                if (same(o, n) || SIG_FLAGS.equals(key)) {
                    continue;
                }
                if (COSName.FIELDS.equals(key)) {
                    if (deref(o) instanceof COSArray of && deref(n) instanceof COSArray nf && onlyNewElementsAdded(of, nf)) {
                        continue;
                    }
                    if (o == null && deref(n) instanceof COSArray nf && allNew(nf)) {
                        continue;
                    }
                    return false;
                }
                if (COSName.DR.equals(key)) {
                    if (o == null || (deref(o) instanceof COSDictionary od && deref(n) instanceof COSDictionary nd
                            && dictionaryOnlyGains(od, nd, 0))) {
                        continue;
                    }
                    return false;
                }
                if (COSName.DA.equals(key) && o == null) {
                    continue;
                }
                if (NEED_APPEARANCES.equals(key) && o == null && n instanceof COSBoolean b && !b.getValue()) {
                    continue;
                }
                return false;
            }
            return true;
        }

        /** Ressources : chaque entrée d'avant reste identique ; une entrée ajoutée désigne un objet neuf. */
        private boolean dictionaryOnlyGains(COSDictionary old, COSDictionary now, int depth) {
            if (depth > 4) {
                return false;
            }
            for (COSName key : union(old, now)) {
                COSBase o = old.getItem(key);
                COSBase n = now.getItem(key);
                if (same(o, n)) {
                    continue;
                }
                if (o == null) {
                    if (!isNewObjectOrDirect(n)) {
                        return false;
                    }
                    continue;
                }
                if (n instanceof COSDictionary nd && o instanceof COSDictionary od && keyOf(n) == null && keyOf(o) == null
                        && dictionaryOnlyGains(od, nd, depth + 1)) {
                    continue;
                }
                return false;
            }
            return true;
        }

        private boolean arrayGrowsWithNewObjects(COSDictionary old, COSDictionary now, COSName key) {
            COSBase o = old.getItem(key);
            COSBase n = now.getItem(key);
            if (same(o, n)) {
                return true;
            }
            COSBase oldArray = deref(o);
            COSBase newArray = deref(n);
            if (oldArray == null) {
                return newArray instanceof COSArray na && allNew(na);
            }
            return oldArray instanceof COSArray oa && newArray instanceof COSArray na && onlyNewElementsAdded(oa, na);
        }

        /** Le tableau d'après garde tous les éléments d'avant, dans l'ordre, et n'ajoute que des objets neufs. */
        private boolean onlyNewElementsAdded(COSArray old, COSArray now) {
            if (now.size() < old.size()) {
                return false;
            }
            for (int i = 0; i < old.size(); i++) {
                if (!same(old.get(i), now.get(i))) {
                    return false;
                }
            }
            for (int i = old.size(); i < now.size(); i++) {
                if (!isNewObject(now.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean allNew(COSArray array) {
            for (int i = 0; i < array.size(); i++) {
                if (!isNewObject(array.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean isNewObject(COSBase value) {
            COSObjectKey key = keyOf(value);
            return key != null && !beforeByNumber.containsKey(key.getNumber());
        }

        private boolean isNewObjectOrDirect(COSBase value) {
            COSObjectKey key = keyOf(value);
            return key == null || !beforeByNumber.containsKey(key.getNumber());
        }

        private static boolean changedKeysWithin(COSDictionary old, COSDictionary now, Set<COSName> allowed) {
            for (COSName key : union(old, now)) {
                if (!same(old.getItem(key), now.getItem(key)) && !allowed.contains(key)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean removedNone(COSDictionary old, COSDictionary now) {
            for (COSName key : old.keySet()) {
                if (now.getItem(key) == null) {
                    return false;
                }
            }
            return true;
        }

        // --- HAUT 3 : aire des widgets de signature neufs ---

        String oversizedWidget(double maxFraction) {
            for (PDPage page : after.getPages()) {
                PDRectangle box = page.getCropBox();
                double pageArea = (double) box.getWidth() * box.getHeight();
                if (!(page.getCOSObject().getDictionaryObject(COSName.ANNOTS) instanceof COSArray annots)) {
                    continue;
                }
                for (int i = 0; i < annots.size(); i++) {
                    COSBase item = annots.get(i);
                    if (!isNewObjectOrDirect(item) || !(deref(item) instanceof COSDictionary annotation)) {
                        continue;
                    }
                    if (!isSignatureField(annotation)) {
                        continue;
                    }
                    double area = area(annotation.getDictionaryObject(COSName.RECT));
                    if (area < 0 || pageArea <= 0 || area > maxFraction * pageArea) {
                        return "new signature appearance is larger than allowed";
                    }
                }
            }
            return null;
        }

        private static boolean isSignatureField(COSDictionary dictionary) {
            COSDictionary current = dictionary;
            for (int depth = 0; current != null && depth < 16; depth++) {
                COSName type = current.getCOSName(COSName.FT);
                if (type != null) {
                    return SIG.equals(type);
                }
                current = current.getCOSDictionary(COSName.PARENT);
            }
            return false;
        }

        private static double area(COSBase rect) {
            if (!(rect instanceof COSArray array) || array.size() != 4) {
                return -1;
            }
            float[] values = new float[4];
            for (int i = 0; i < 4; i++) {
                if (!(array.getObject(i) instanceof COSNumber number)) {
                    return -1;
                }
                values[i] = number.floatValue();
            }
            return Math.abs((double) (values[2] - values[0]) * (values[3] - values[1]));
        }

        private void collect(COSBase start, Set<COSObjectKey> into) {
            Deque<COSBase> queue = new ArrayDeque<>();
            Set<COSBase> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            if (start != null) {
                queue.add(start);
            }
            while (!queue.isEmpty() && into.size() < MAX_DSS_OBJECTS) {
                COSBase value = queue.poll();
                if (!seen.add(value)) {
                    continue;
                }
                COSObjectKey key = keyOf(value);
                if (key != null) {
                    into.add(key);
                }
                COSBase resolved = deref(value);
                if (resolved instanceof COSDictionary dictionary) {
                    queue.addAll(dictionary.getValues());
                } else if (resolved instanceof COSArray array) {
                    array.forEach(queue::add);
                }
            }
        }
    }

    /** Même contenu : dictionnaires et tableaux comparés par {@link #same}, flux par leurs octets bruts en plus. */
    private static boolean identical(COSBase a, COSBase b) {
        if (a instanceof COSStream sa && b instanceof COSStream sb) {
            if (!sa.keySet().equals(sb.keySet())) {
                return false;
            }
            for (COSName key : sa.keySet()) {
                if (!same(sa.getItem(key), sb.getItem(key))) {
                    return false;
                }
            }
            try (java.io.InputStream ia = sa.createRawInputStream(); java.io.InputStream ib = sb.createRawInputStream()) {
                return Arrays.equals(ia.readAllBytes(), ib.readAllBytes());
            } catch (java.io.IOException unreadable) {
                return false;
            }
        }
        if (a instanceof COSStream || b instanceof COSStream) {
            return false;
        }
        if (a instanceof COSDictionary da && b instanceof COSDictionary db) {
            if (!da.keySet().equals(db.keySet())) {
                return false;
            }
            for (COSName key : da.keySet()) {
                if (!same(da.getItem(key), db.getItem(key))) {
                    return false;
                }
            }
            return true;
        }
        return a != null && b != null && keyOf(a) == null && keyOf(b) == null && same(a, b)
                || (a instanceof COSArray && b instanceof COSArray && sameArray((COSArray) a, (COSArray) b));
    }

    private static boolean sameArray(COSArray a, COSArray b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!same(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Set<COSName> union(COSDictionary a, COSDictionary b) {
        Set<COSName> keys = new HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }

    private static COSObjectKey keyOf(COSBase value) {
        if (value instanceof COSObject object) {
            return object.getKey();
        }
        return value == null ? null : value.getKey();
    }

    private static COSBase deref(COSBase value) {
        return value instanceof COSObject object ? object.getObject() : value;
    }

    /** Même valeur : même référence si l'une est indirecte, sinon même contenu (directs, récursif). */
    private static boolean same(COSBase a, COSBase b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        COSObjectKey ka = keyOf(a);
        COSObjectKey kb = keyOf(b);
        if (ka != null || kb != null) {
            return ka != null && kb != null && ka.getNumber() == kb.getNumber() && ka.getGeneration() == kb.getGeneration();
        }
        if (a instanceof COSDictionary da && b instanceof COSDictionary db) {
            if (a instanceof COSStream || b instanceof COSStream || !da.keySet().equals(db.keySet())) {
                return false;
            }
            for (COSName key : da.keySet()) {
                if (!same(da.getItem(key), db.getItem(key))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof COSArray aa && b instanceof COSArray ab) {
            if (aa.size() != ab.size()) {
                return false;
            }
            for (int i = 0; i < aa.size(); i++) {
                if (!same(aa.get(i), ab.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }
}
