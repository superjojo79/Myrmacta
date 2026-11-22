package be.myrmacta.infrastructure.signature.eid;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Parcours du graphe FINAL depuis le catalogue (contre-revue, HAUT A, HAUT C, MOYEN D, MOYEN E, BAS F ; TODO §138,
 * D-EID-BINDING). Un objet neuf (numéro absent du document d'avant) n'est admis que s'il est atteint par un chemin
 * autorisé :
 * <ul>
 *   <li>{@code /Annots} d'une page ou {@code /Fields} (et {@code /Kids}) vers <b>le</b> widget de la nouvelle signature
 *       (sa valeur {@code /V} porte la signature dont le {@code /ByteRange} finit à la révision signée), ou vers un
 *       widget d'horodatage de document sans apparence visible (aire nulle) ; sous ce widget, {@code /V}, {@code /AP}
 *       et les ressources de l'apparence ;
 *   <li>{@code /DSS} et seulement ses clés {@code /Certs}, {@code /CRLs}, {@code /OCSPs}, {@code /VRI} (et, sous
 *       {@code /VRI}, {@code /Cert}, {@code /CRL}, {@code /OCSP}, {@code /TS}, {@code /TU}), flux en feuilles ; un
 *       {@code /DSS} qui porte une autre clé est refusé ;
 *   <li>{@code /Metadata} (flux XMP), {@code /Extensions}, {@code /OutputIntents} absents d'avant, {@code /DR} de
 *       l'{@code /AcroForm}, une {@code /AcroForm} neuve, le dictionnaire {@code /Info} (sans flux).
 * </ul>
 * Tout objet neuf atteint autrement ({@code /Pages}, {@code /Kids} de l'arbre des pages, {@code /Contents},
 * {@code /Resources} d'une page, {@code /Names}, {@code /OpenAction}, {@code /AA}, {@code /OCProperties}…) est refusé,
 * de même que plus d'un widget pour la nouvelle signature, un champ neuf sans signature, la création de
 * {@code /Perms}, un {@code /Root} différent.
 *
 * <p>MOYEN E : la zone du {@code /Rect} du widget de la nouvelle signature, s'il est visible, doit être vide dans le
 * rendu de la page du document d'avant (une signature ne se pose pas sur le texte remis).
 */
final class FinalGraphWalk {

    private static final int MAX_VISITS = 500_000;
    private static final COSName DSS = COSName.getPDFName("DSS");
    private static final COSName VRI = COSName.getPDFName("VRI");
    private static final COSName PERMS = COSName.getPDFName("Perms");
    private static final COSName EXTENSIONS = COSName.getPDFName("Extensions");
    private static final COSName OUTPUT_INTENTS = COSName.getPDFName("OutputIntents");
    private static final COSName SIG = COSName.getPDFName("Sig");
    private static final COSName DOC_TIME_STAMP = COSName.getPDFName("DocTimeStamp");
    private static final Set<String> DSS_KEYS = Set.of("Type", "Certs", "CRLs", "OCSPs", "VRI");
    private static final Set<String> VRI_ENTRY_KEYS = Set.of("Type", "Cert", "CRL", "OCSP", "TS", "TU");

    private enum Ctx { CATALOG, NORMAL, ACROFORM, FIELD_LIST, FIELD, SIGNATURE_SUBTREE, FREE, DSS, VRI, VRI_ENTRY,
        STREAM_LIST, STREAM_LEAF, METADATA, INFO }

    private record Visit(COSBase value, Ctx ctx, String path) {}

    private final PDDocument before;
    private final PDDocument after;
    private final Set<Long> beforeNumbers = new HashSet<>();
    private final long newSignatureEnd;
    private final Map<COSDictionary, Integer> pageIndexes = new java.util.IdentityHashMap<>();
    private final Set<Long> signatureWidgets = new HashSet<>();
    private COSDictionary signatureWidget;
    private int signatureWidgetPage = -1;

    private FinalGraphWalk(PDDocument before, PDDocument after, long newSignatureEnd) {
        this.before = before;
        this.after = after;
        this.newSignatureEnd = newSignatureEnd;
        before.getDocument().getXrefTable().keySet().forEach(key -> beforeNumbers.add(key.getNumber()));
        int index = 0;
        for (PDPage page : after.getPages()) {
            pageIndexes.put(page.getCOSObject(), index++);
        }
    }

    /**
     * @param newSignatureEnd fin de la révision couverte par la nouvelle signature ; négatif quand le document d'avant
     *     porte déjà la nouvelle signature (seuls des horodatages de document peuvent alors être ajoutés)
     * @return motif du refus (noms de clés PDF seulement), ou {@code null}
     */
    static String refusal(PDDocument before, PDDocument after, long newSignatureEnd) {
        FinalGraphWalk walk = new FinalGraphWalk(before, after, newSignatureEnd);
        String refusal = walk.walk();
        if (refusal == null) {
            refusal = walk.blankUnderSignature();
        }
        return refusal;
    }

    private String walk() {
        COSDictionary beforeTrailer = before.getDocument().getTrailer();
        COSDictionary afterTrailer = after.getDocument().getTrailer();
        if (!sameKey(beforeTrailer.getItem(COSName.ROOT), afterTrailer.getItem(COSName.ROOT))) {
            return "catalog of the final file is not the catalog of the delivered document";
        }
        if (afterTrailer.getItem(COSName.ENCRYPT) != null) {
            return "final file declares encryption";
        }
        COSDictionary beforeCatalog = before.getDocumentCatalog().getCOSObject();
        COSDictionary afterCatalog = after.getDocumentCatalog().getCOSObject();
        // BAS F : un téléversement ne pose pas de verrou de certification (/DocMDP), qui ferait refuser les suivants.
        if (afterCatalog.getItem(PERMS) != null && beforeCatalog.getItem(PERMS) == null) {
            return "added certification permissions (/Perms)";
        }
        Deque<Visit> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(new Visit(afterTrailer.getItem(COSName.ROOT), Ctx.CATALOG, "/Root"));
        if (afterTrailer.getItem(COSName.INFO) != null) {
            queue.add(new Visit(afterTrailer.getItem(COSName.INFO), Ctx.INFO, "/Info"));
        }
        int visits = 0;
        while (!queue.isEmpty()) {
            if (++visits > MAX_VISITS) {
                return "document graph too large";
            }
            Visit visit = queue.poll();
            COSObjectKey key = keyOf(visit.value());
            if (key != null && !seen.add(key.getNumber() + ":" + key.getGeneration() + ":" + visit.ctx())) {
                continue;
            }
            COSBase value = deref(visit.value());
            if (value == null) {
                continue;
            }
            boolean isNew = key != null && !beforeNumbers.contains(key.getNumber());
            String refusal = visit(value, visit.ctx(), isNew, key, visit.path(), queue, beforeCatalog);
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }

    private String visit(COSBase value, Ctx ctx, boolean isNew, COSObjectKey key, String path, Deque<Visit> queue,
            COSDictionary beforeCatalog) {
        switch (ctx) {
            case CATALOG -> {
                COSDictionary catalog = (COSDictionary) value;
                for (Map.Entry<COSName, COSBase> entry : catalog.entrySet()) {
                    COSName name = entry.getKey();
                    Ctx child;
                    if (COSName.ACRO_FORM.equals(name)) {
                        child = Ctx.ACROFORM;
                    } else if (DSS.equals(name)) {
                        child = Ctx.DSS;
                    } else if (COSName.METADATA.equals(name)) {
                        child = Ctx.METADATA;
                    } else if (EXTENSIONS.equals(name)) {
                        child = Ctx.FREE;
                    } else if (OUTPUT_INTENTS.equals(name) && beforeCatalog.getItem(OUTPUT_INTENTS) == null) {
                        child = Ctx.FREE;
                    } else {
                        child = Ctx.NORMAL;
                    }
                    queue.add(new Visit(entry.getValue(), child, "/" + name.getName()));
                }
                return null;
            }
            case ACROFORM -> {
                if (!(value instanceof COSDictionary form)) {
                    return "malformed /AcroForm";
                }
                for (Map.Entry<COSName, COSBase> entry : form.entrySet()) {
                    Ctx child = COSName.FIELDS.equals(entry.getKey()) ? Ctx.FIELD_LIST
                            : COSName.DR.equals(entry.getKey()) ? Ctx.FREE : Ctx.NORMAL;
                    queue.add(new Visit(entry.getValue(), child, path + "/" + entry.getKey().getName()));
                }
                return null;
            }
            case FIELD_LIST -> {
                if (!(value instanceof COSArray array)) {
                    return "malformed field list";
                }
                for (int i = 0; i < array.size(); i++) {
                    queue.add(new Visit(array.get(i), Ctx.FIELD, path));
                }
                return null;
            }
            case FIELD -> {
                return field(value, isNew, key, path, queue);
            }
            case DSS -> {
                if (!(value instanceof COSDictionary dss) || value instanceof COSStream) {
                    return "malformed /DSS";
                }
                for (Map.Entry<COSName, COSBase> entry : dss.entrySet()) {
                    String name = entry.getKey().getName();
                    if (!DSS_KEYS.contains(name)) {
                        return "/DSS carries a key outside /Certs, /CRLs, /OCSPs, /VRI";
                    }
                    if (!"Type".equals(name)) {
                        queue.add(new Visit(entry.getValue(), VRI.equals(entry.getKey()) ? Ctx.VRI : Ctx.STREAM_LIST, path + "/" + name));
                    }
                }
                return null;
            }
            case VRI -> {
                if (!(value instanceof COSDictionary vri) || value instanceof COSStream) {
                    return "malformed /VRI";
                }
                for (COSBase entry : vri.getValues()) {
                    queue.add(new Visit(entry, Ctx.VRI_ENTRY, path));
                }
                return null;
            }
            case VRI_ENTRY -> {
                if (!(value instanceof COSDictionary entry) || value instanceof COSStream) {
                    return "malformed /VRI entry";
                }
                for (Map.Entry<COSName, COSBase> item : entry.entrySet()) {
                    String name = item.getKey().getName();
                    if (!VRI_ENTRY_KEYS.contains(name)) {
                        return "/VRI entry carries an unexpected key";
                    }
                    if (name.equals("Cert") || name.equals("CRL") || name.equals("OCSP")) {
                        queue.add(new Visit(item.getValue(), Ctx.STREAM_LIST, path));
                    } else if (name.equals("TS")) {
                        queue.add(new Visit(item.getValue(), Ctx.STREAM_LEAF, path));
                    }
                }
                return null;
            }
            case STREAM_LIST -> {
                if (!(value instanceof COSArray array)) {
                    return "malformed /DSS array";
                }
                for (int i = 0; i < array.size(); i++) {
                    queue.add(new Visit(array.get(i), Ctx.STREAM_LEAF, path));
                }
                return null;
            }
            case STREAM_LEAF -> {
                return value instanceof COSStream ? null : "/DSS entry is not a stream";
            }
            case METADATA -> {
                return value instanceof COSStream stream && COSName.METADATA.equals(stream.getCOSName(COSName.TYPE))
                        ? null : "/Metadata is not an XMP stream";
            }
            case INFO -> {
                if (!(value instanceof COSDictionary) || value instanceof COSStream) {
                    return "/Info is not a plain dictionary";
                }
                children(value, Ctx.FREE, path, queue);
                return null;
            }
            case FREE, SIGNATURE_SUBTREE -> {
                children(value, ctx, path, queue);
                return null;
            }
            default -> {
                // NORMAL : aucun objet neuf.
                if (isNew) {
                    return "new object reached by " + path;
                }
                if (value instanceof COSDictionary dictionary && pageIndexes.containsKey(dictionary)) {
                    for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                        boolean annots = COSName.ANNOTS.equals(entry.getKey());
                        queue.add(new Visit(entry.getValue(), annots ? Ctx.FIELD_LIST : Ctx.NORMAL,
                                path + "/" + entry.getKey().getName()));
                    }
                    return null;
                }
                children(value, Ctx.NORMAL, path, queue);
                return null;
            }
        }
    }

    /** Un champ ou une annotation dans {@code /Fields}, {@code /Kids} ou {@code /Annots}. */
    private String field(COSBase value, boolean isNew, COSObjectKey key, String path, Deque<Visit> queue) {
        if (!(value instanceof COSDictionary dictionary) || value instanceof COSStream) {
            return isNew ? "new object reached by " + path : null;
        }
        COSDictionary signature = signatureValue(dictionary);
        boolean filledNow = !isNew && signature != null && wasEmptyBefore(key);
        if (!isNew && !filledNow) {
            // Champ ou annotation du document d'avant, inchangé dans son rôle : rien de neuf sous lui.
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                Ctx child = COSName.KIDS.equals(entry.getKey()) ? Ctx.FIELD : Ctx.NORMAL;
                queue.add(new Visit(entry.getValue(), child, path + "/" + entry.getKey().getName()));
            }
            return null;
        }
        if (signature == null) {
            return "new field or annotation without the new signature (" + path + ")";
        }
        boolean timestamp = DOC_TIME_STAMP.equals(signature.getCOSName(COSName.TYPE));
        boolean newSignature = SIG.equals(signature.getCOSName(COSName.TYPE)) && newSignatureEnd >= 0
                && byteRangeEnd(signature) == newSignatureEnd;
        double area = area(dictionary.getDictionaryObject(COSName.RECT));
        boolean widget = dictionary.getItem(COSName.RECT) != null || COSName.WIDGET.equals(dictionary.getCOSName(COSName.SUBTYPE));
        if (timestamp) {
            if (widget && area != 0) {
                return "document timestamp with a visible appearance";
            }
        } else if (!newSignature) {
            return "new field or annotation that does not carry the new signature (" + path + ")";
        } else if (widget) {
            if (key != null && signatureWidgets.add(key.getNumber()) && signatureWidgets.size() > 1) {
                return "more than one widget for the new signature";
            }
            if (signatureWidget == null || signatureWidget != dictionary) {
                if (signatureWidget != null) {
                    return "more than one widget for the new signature";
                }
                signatureWidget = dictionary;
                signatureWidgetPage = pageOf(dictionary);
            }
        }
        for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
            COSName name = entry.getKey();
            Ctx child;
            if (COSName.V.equals(name) || COSName.AP.equals(name)) {
                child = Ctx.SIGNATURE_SUBTREE;
            } else if (COSName.KIDS.equals(name)) {
                child = Ctx.FIELD;
            } else if (COSName.PARENT.equals(name)) {
                child = Ctx.FIELD;
            } else {
                child = Ctx.NORMAL;
            }
            queue.add(new Visit(entry.getValue(), child, path + "/" + name.getName()));
        }
        return null;
    }

    /** Valeur {@code /V} du champ (en remontant {@code /Parent}), si c'est un dictionnaire de signature. */
    private static COSDictionary signatureValue(COSDictionary dictionary) {
        COSDictionary current = dictionary;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current.getDictionaryObject(COSName.V) instanceof COSDictionary value && !(value instanceof COSStream)) {
                return value;
            }
            if (current.getItem(COSName.FT) != null) {
                return null;
            }
            current = current.getCOSDictionary(COSName.PARENT);
        }
        return null;
    }

    private boolean wasEmptyBefore(COSObjectKey key) {
        if (key == null) {
            return false;
        }
        COSObject old = before.getDocument().getObjectFromPool(key);
        return old != null && old.getObject() instanceof COSDictionary dictionary
                && SIG.equals(dictionary.getCOSName(COSName.FT)) && dictionary.getItem(COSName.V) == null;
    }

    private int pageOf(COSDictionary widget) {
        if (widget.getDictionaryObject(COSName.P) instanceof COSDictionary page && pageIndexes.containsKey(page)) {
            return pageIndexes.get(page);
        }
        for (Map.Entry<COSDictionary, Integer> page : pageIndexes.entrySet()) {
            if (page.getKey().getDictionaryObject(COSName.ANNOTS) instanceof COSArray annots) {
                for (int i = 0; i < annots.size(); i++) {
                    if (deref(annots.get(i)) == widget) {
                        return page.getValue();
                    }
                }
            }
        }
        return -1;
    }

    /** MOYEN E : la zone du widget visible de la nouvelle signature est vide dans le rendu du document d'avant. */
    private String blankUnderSignature() {
        if (signatureWidget == null) {
            return null;
        }
        double[] rect = rect(signatureWidget.getDictionaryObject(COSName.RECT));
        if (rect == null || (rect[2] - rect[0]) * (rect[3] - rect[1]) == 0) {
            return null;
        }
        if (signatureWidgetPage < 0 || signatureWidgetPage >= before.getNumberOfPages()) {
            return "signature appearance on an unknown page";
        }
        PDPage page = before.getPage(signatureWidgetPage);
        if (page.getRotation() % 360 != 0) {
            return "visible signature on a rotated page cannot be checked";
        }
        try {
            return BlankZone.isBlank(before, signatureWidgetPage, rect)
                    ? null : "visible signature placed over content of the delivered document";
        } catch (IOException | RuntimeException unreadable) {
            return "page under the visible signature cannot be rendered";
        }
    }

    private static void children(COSBase value, Ctx ctx, String path, Deque<Visit> queue) {
        if (value instanceof COSDictionary dictionary) {
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                queue.add(new Visit(entry.getValue(), ctx, path + "/" + entry.getKey().getName()));
            }
        } else if (value instanceof COSArray array) {
            for (int i = 0; i < array.size(); i++) {
                queue.add(new Visit(array.get(i), ctx, path));
            }
        }
    }

    private static long byteRangeEnd(COSDictionary signature) {
        if (!(signature.getDictionaryObject(COSName.BYTERANGE) instanceof COSArray range) || range.size() != 4
                || !(range.getObject(2) instanceof COSNumber offset) || !(range.getObject(3) instanceof COSNumber length)) {
            return -2;
        }
        return offset.longValue() + length.longValue();
    }

    private static double area(COSBase rect) {
        double[] values = rect(rect);
        return values == null ? 0 : Math.abs((values[2] - values[0]) * (values[3] - values[1]));
    }

    /** {@code [llx lly urx ury]} normalisé, ou {@code null}. */
    private static double[] rect(COSBase rect) {
        if (!(rect instanceof COSArray array) || array.size() != 4) {
            return null;
        }
        double[] v = new double[4];
        for (int i = 0; i < 4; i++) {
            if (!(array.getObject(i) instanceof COSNumber number)) {
                return null;
            }
            v[i] = number.floatValue();
        }
        return new double[] {Math.min(v[0], v[2]), Math.min(v[1], v[3]), Math.max(v[0], v[2]), Math.max(v[1], v[3])};
    }

    private static boolean sameKey(COSBase a, COSBase b) {
        COSObjectKey ka = keyOf(a);
        COSObjectKey kb = keyOf(b);
        return ka != null && kb != null && ka.getNumber() == kb.getNumber() && ka.getGeneration() == kb.getGeneration();
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
}
