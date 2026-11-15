package be.myrmacta.infrastructure.signature.eid;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.apache.pdfbox.cos.COSObjectKey;

/**
 * Lecture stricte, par Myrmacta, des sections de références croisées ajoutées après le document remis (contre-revue,
 * HAUT B ; TODO §138, D-EID-BINDING). PDFBox est tolérant : il ignore les entrées libres, garde côte à côte deux
 * générations d'un même numéro et reconstruit une table illisible en balayant le fichier. Un lecteur conforme, lui,
 * traiterait un objet déclaré libre comme supprimé : la clause disparaîtrait à l'écran sans que PDFBox, DSS ni les
 * contrôles ne le voient.
 *
 * <p>Sont lus : tables classiques, flux de références ({@code /Type /XRef}, Flate, prédicteurs PNG), table hybride
 * ({@code /XRefStm}), chaînage {@code /Prev} jusqu'à la dernière section du document remis. Sont refusés : une section
 * illisible ou incohérente, une chaîne qui ne ramène pas au document remis, une entrée libre autre que l'objet 0, un
 * numéro du document remis dont la génération change, une entrée en service qui pointe dans les octets du document
 * remis, une entrée compressée qui vise un flux d'objets du document remis, un {@code /Encrypt}, un {@code /Root}
 * différent de celui du document remis, et tout désaccord avec la table que PDFBox a construite (signe d'une
 * reconstruction tolérante).
 */
final class AddedXrefSections {

    private static final int MAX_SECTIONS = 32;
    private static final int MAX_ENTRIES = 200_000;
    private static final Pattern STARTXREF = Pattern.compile("startxref\\s+(\\d+)");
    private static final Pattern ROOT = Pattern.compile("/Root\\s+(\\d+)\\s+(\\d+)\\s+R");
    private static final Pattern PREV = Pattern.compile("/Prev\\s+(\\d+)");
    private static final Pattern XREF_STM = Pattern.compile("/XRefStm\\s+(\\d+)");
    private static final Pattern OBJECT_HEADER = Pattern.compile("\\G\\s*(\\d+)\\s+(\\d+)\\s+obj\\s*");

    private AddedXrefSections() {}

    /** Une entrée lue : {@code type} 0 libre, 1 en service ({@code field2} = position), 2 compressée ({@code field2} = flux). */
    private record Entry(long number, int type, long field2, long field3) {}

    /**
     * @param baseXref table construite par PDFBox pour le document remis
     * @param finalXref table construite par PDFBox pour le fichier final
     * @return motif du refus, ou {@code null}
     */
    static String refusal(byte[] base, byte[] signed, Map<COSObjectKey, Long> baseXref, Map<COSObjectKey, Long> finalXref) {
        try {
            return check(base, signed, baseXref, finalXref);
        } catch (RuntimeException unreadable) {
            return "added cross-reference section is not readable";
        }
    }

    private static String check(byte[] base, byte[] signed, Map<COSObjectKey, Long> baseXref, Map<COSObjectKey, Long> finalXref) {
        long baseStart = lastStartxref(base, 0);
        long finalStart = lastStartxref(signed, base.length);
        if (baseStart < 0 || finalStart < 0) {
            return "cross-reference offset missing";
        }
        Map<Long, Integer> baseGenerations = new HashMap<>();
        baseXref.keySet().forEach(key -> baseGenerations.put(key.getNumber(), key.getGeneration()));
        Map<Long, Entry> newest = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        String text = new String(signed, StandardCharsets.ISO_8859_1);
        long offset = finalStart;
        boolean first = true;
        int sections = 0;
        while (offset != baseStart) {
            if (offset < base.length || offset >= signed.length || !visited.add(offset) || ++sections > MAX_SECTIONS) {
                return "cross-reference chain does not lead back to the delivered document";
            }
            Section section = parse(text, signed, (int) offset);
            String refusal = entriesRefusal(section, base.length, baseGenerations, newest);
            if (refusal == null && section.xrefStm() >= 0) {
                if (section.xrefStm() < base.length || section.xrefStm() >= signed.length) {
                    return "hybrid cross-reference stream outside the added revisions";
                }
                Section hybrid = parse(text, signed, (int) section.xrefStm());
                refusal = entriesRefusal(hybrid, base.length, baseGenerations, newest);
            }
            if (refusal != null) {
                return refusal;
            }
            if (section.encrypted()) {
                return "added revision declares encryption";
            }
            if (first && !section.root().equals(rootOf(text, base.length))) {
                return "catalog of the final file is not the catalog of the delivered document";
            }
            first = false;
            if (section.prev() < 0) {
                return "cross-reference chain does not lead back to the delivered document";
            }
            offset = section.prev();
        }
        // Accord avec la table de PDFBox : une reconstruction tolérante (balayage du fichier) s'y verrait.
        for (Entry entry : newest.values()) {
            if (entry.type() == 1) {
                Long pdfBox = finalXref.get(new COSObjectKey(entry.number(), (int) entry.field3()));
                if (pdfBox == null || pdfBox != entry.field2()) {
                    return "cross-reference table differs from the parsed one";
                }
            }
        }
        for (Map.Entry<COSObjectKey, Long> pdfBox : finalXref.entrySet()) {
            long number = pdfBox.getKey().getNumber();
            Long position = pdfBox.getValue();
            boolean added = position != null && position >= base.length;
            boolean known = baseXref.containsKey(pdfBox.getKey()) && position != null && position.equals(baseXref.get(pdfBox.getKey()));
            if (added && !newest.containsKey(number)) {
                return "object found outside the parsed cross-reference sections";
            }
            if (!added && !known && position != null && position >= 0 && !newest.containsKey(number)) {
                return "cross-reference table differs from the parsed one";
            }
        }
        return null;
    }

    private static String entriesRefusal(Section section, int baseLength, Map<Long, Integer> baseGenerations,
            Map<Long, Entry> newest) {
        for (Entry entry : section.entries()) {
            boolean inBase = baseGenerations.containsKey(entry.number());
            switch (entry.type()) {
                case 0 -> {
                    if (entry.number() != 0) {
                        return "added revision frees an object";
                    }
                }
                case 1 -> {
                    if (entry.field2() < baseLength) {
                        return "added entry points into the delivered document";
                    }
                    if (inBase && baseGenerations.get(entry.number()) != (int) entry.field3()) {
                        return "added revision changes the generation of an object";
                    }
                }
                case 2 -> {
                    if (baseGenerations.containsKey(entry.field2())) {
                        return "added entry points into an object stream of the delivered document";
                    }
                    if (inBase && baseGenerations.get(entry.number()) != 0) {
                        return "added revision changes the generation of an object";
                    }
                }
                default -> {
                    // Type inconnu : ignoré par la norme (référence nulle) ; refusé ici, dans le doute.
                    return "added entry of unknown type";
                }
            }
            if (entry.type() != 0) {
                newest.putIfAbsent(entry.number(), entry);
            }
        }
        return null;
    }

    private record Section(java.util.List<Entry> entries, long prev, long xrefStm, String root, boolean encrypted) {}

    private static Section parse(String text, byte[] data, int offset) {
        int position = skipWhitespace(text, offset);
        if (text.startsWith("xref", position)) {
            return parseTable(text, position + 4);
        }
        return parseStream(text, data, position);
    }

    private static Section parseTable(String text, int position) {
        java.util.List<Entry> entries = new java.util.ArrayList<>();
        int[] cursor = {position};
        while (true) {
            int before = skipWhitespace(text, cursor[0]);
            if (text.startsWith("trailer", before)) {
                cursor[0] = before + 7;
                break;
            }
            String token = token(text, cursor);
            long start = Long.parseLong(token);
            int count = Integer.parseInt(token(text, cursor));
            if (count < 0 || entries.size() + count > MAX_ENTRIES) {
                throw new IllegalStateException("too many entries");
            }
            for (int i = 0; i < count; i++) {
                String field = token(text, cursor);
                String generation = token(text, cursor);
                String kind = token(text, cursor);
                if (field.length() != 10 || generation.length() != 5 || !(kind.equals("n") || kind.equals("f"))) {
                    throw new IllegalStateException("malformed entry");
                }
                entries.add(new Entry(start + i, kind.equals("n") ? 1 : 0, Long.parseLong(field), Integer.parseInt(generation)));
            }
        }
        String dictionary = dictionary(text, skipWhitespace(text, cursor[0]));
        return new Section(entries, longOf(PREV, dictionary), longOf(XREF_STM, dictionary), root(dictionary),
                dictionary.contains("/Encrypt"));
    }

    private static Section parseStream(String text, byte[] data, int position) {
        Matcher header = OBJECT_HEADER.matcher(text);
        header.region(position, text.length());
        if (!header.find()) {
            throw new IllegalStateException("no cross-reference section");
        }
        int dictionaryStart = header.end();
        String dictionary = dictionary(text, dictionaryStart);
        if (!dictionary.matches("(?s).*/Type\\s*/XRef\\b.*")) {
            throw new IllegalStateException("not a cross-reference stream");
        }
        int afterDictionary = dictionaryStart + dictionary.length();
        int streamKeyword = text.indexOf("stream", afterDictionary);
        if (streamKeyword < 0 || !text.substring(afterDictionary, streamKeyword).isBlank()) {
            throw new IllegalStateException("no stream");
        }
        int dataStart = streamKeyword + 6;
        if (text.startsWith("\r\n", dataStart)) {
            dataStart += 2;
        } else if (text.startsWith("\n", dataStart) || text.startsWith("\r", dataStart)) {
            dataStart += 1;
        }
        Matcher lengthMatcher = Pattern.compile("/Length\\s+(\\d+)(\\s+\\d+\\s+R)?").matcher(dictionary);
        int dataEnd;
        if (lengthMatcher.find() && lengthMatcher.group(2) == null) {
            dataEnd = dataStart + Integer.parseInt(lengthMatcher.group(1));
        } else {
            dataEnd = text.indexOf("endstream", dataStart);
        }
        if (dataEnd < dataStart || dataEnd > data.length) {
            throw new IllegalStateException("stream bounds");
        }
        byte[] raw = java.util.Arrays.copyOfRange(data, dataStart, dataEnd);
        byte[] decoded = decode(raw, dictionary);
        int[] widths = ints(arrayOf(dictionary, "W"));
        if (widths.length != 3) {
            throw new IllegalStateException("W");
        }
        long size = longOf(Pattern.compile("/Size\\s+(\\d+)"), dictionary);
        int[] index = ints(arrayOf(dictionary, "Index"));
        if (index.length == 0) {
            index = new int[] {0, (int) size};
        }
        int rowLength = widths[0] + widths[1] + widths[2];
        java.util.List<Entry> entries = new java.util.ArrayList<>();
        int at = 0;
        for (int pair = 0; pair + 1 < index.length; pair += 2) {
            for (int i = 0; i < index[pair + 1]; i++) {
                if (at + rowLength > decoded.length || entries.size() > MAX_ENTRIES) {
                    throw new IllegalStateException("short cross-reference stream");
                }
                long type = widths[0] == 0 ? 1 : number(decoded, at, widths[0]);
                long second = number(decoded, at + widths[0], widths[1]);
                long third = number(decoded, at + widths[0] + widths[1], widths[2]);
                entries.add(new Entry(index[pair] + i, (int) type, second, third));
                at += rowLength;
            }
        }
        return new Section(entries, longOf(PREV, dictionary), -1, root(dictionary), dictionary.contains("/Encrypt"));
    }

    private static byte[] decode(byte[] raw, String dictionary) {
        byte[] data = raw;
        if (dictionary.contains("/Filter")) {
            if (!dictionary.matches("(?s).*/Filter\\s*(\\[\\s*)?/FlateDecode\\s*(\\])?.*")) {
                throw new IllegalStateException("unsupported filter");
            }
            data = inflate(raw);
        }
        Matcher predictor = Pattern.compile("/Predictor\\s+(\\d+)").matcher(dictionary);
        if (predictor.find() && Integer.parseInt(predictor.group(1)) >= 10) {
            Matcher columns = Pattern.compile("/Columns\\s+(\\d+)").matcher(dictionary);
            int width = columns.find() ? Integer.parseInt(columns.group(1)) : 1;
            data = unpredict(data, width);
        }
        return data;
    }

    private static byte[] inflate(byte[] raw) {
        Inflater inflater = new Inflater();
        inflater.setInput(raw);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
                if (out.size() > 16 * 1024 * 1024) {
                    throw new IllegalStateException("cross-reference stream too large");
                }
            }
        } catch (DataFormatException ex) {
            throw new IllegalStateException("bad deflate data", ex);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    /** Prédicteurs PNG (RFC 2083), un octet par pixel. */
    private static byte[] unpredict(byte[] data, int columns) {
        int row = columns + 1;
        if (data.length % row != 0) {
            throw new IllegalStateException("predictor rows");
        }
        byte[] out = new byte[data.length / row * columns];
        byte[] previous = new byte[columns];
        for (int r = 0; r < data.length / row; r++) {
            int filter = data[r * row] & 0xff;
            byte[] current = new byte[columns];
            for (int c = 0; c < columns; c++) {
                int x = data[r * row + 1 + c] & 0xff;
                int left = c > 0 ? current[c - 1] & 0xff : 0;
                int up = previous[c] & 0xff;
                int upLeft = c > 0 ? previous[c - 1] & 0xff : 0;
                int value = switch (filter) {
                    case 0 -> x;
                    case 1 -> x + left;
                    case 2 -> x + up;
                    case 3 -> x + (left + up) / 2;
                    case 4 -> x + paeth(left, up, upLeft);
                    default -> throw new IllegalStateException("predictor filter");
                };
                current[c] = (byte) value;
            }
            System.arraycopy(current, 0, out, r * columns, columns);
            previous = current;
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private static long number(byte[] data, int at, int width) {
        long value = 0;
        for (int i = 0; i < width; i++) {
            value = (value << 8) | (data[at + i] & 0xff);
        }
        return value;
    }

    private static String arrayOf(String dictionary, String key) {
        Matcher matcher = Pattern.compile("/" + key + "\\s*\\[([^\\]]*)\\]").matcher(dictionary);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static int[] ints(String values) {
        String trimmed = values.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        String[] parts = trimmed.split("\\s+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }

    private static long longOf(Pattern pattern, String dictionary) {
        Matcher matcher = pattern.matcher(dictionary);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    private static String root(String dictionary) {
        Matcher matcher = ROOT.matcher(dictionary);
        return matcher.find() ? matcher.group(1) + " " + matcher.group(2) : "";
    }

    /** {@code /Root} de la dernière section du document remis. */
    private static String rootOf(String text, int baseLength) {
        String base = text.substring(0, baseLength);
        long start = lastStartxref(base.getBytes(StandardCharsets.ISO_8859_1), 0);
        Section section = parse(base, base.getBytes(StandardCharsets.ISO_8859_1), (int) start);
        return section.root();
    }

    /** Texte du dictionnaire qui commence à {@code start} ({@code <<} … {@code >>}, imbrications comprises). */
    private static String dictionary(String text, int start) {
        if (!text.startsWith("<<", start)) {
            throw new IllegalStateException("dictionary expected");
        }
        int depth = 0;
        for (int i = start; i < text.length() - 1 && i < start + 1_000_000; i++) {
            if (text.charAt(i) == '<' && text.charAt(i + 1) == '<') {
                depth++;
                i++;
            } else if (text.charAt(i) == '>' && text.charAt(i + 1) == '>') {
                depth--;
                i++;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new IllegalStateException("unterminated dictionary");
    }

    private static long lastStartxref(byte[] data, int from) {
        String text = new String(data, StandardCharsets.ISO_8859_1);
        Matcher matcher = STARTXREF.matcher(text);
        long value = -1;
        while (matcher.find()) {
            if (matcher.start() >= from) {
                value = Long.parseLong(matcher.group(1));
            }
        }
        return value;
    }

    private static int skipWhitespace(String text, int position) {
        int i = position;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String token(String text, int[] cursor) {
        int start = skipWhitespace(text, cursor[0]);
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        if (end == start) {
            throw new IllegalStateException("unexpected end");
        }
        cursor[0] = end;
        return text.substring(start, end);
    }
}
