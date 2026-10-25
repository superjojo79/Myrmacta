package be.myrmacta.infrastructure.signature;

import java.io.ByteArrayOutputStream;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.openpdf.text.pdf.PdfWriter;

/** PDF de test (OpenPDF) et extraction de texte. */
public final class TestPdfs {

    private TestPdfs() {}

    public static byte[] pdf(int pages, String textPerPage) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            for (int i = 1; i <= pages; i++) {
                if (i > 1) {
                    document.newPage();
                }
                document.add(new Paragraph(textPerPage + " page " + i));
            }
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static int pageCount(byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            int n = reader.getNumberOfPages();
            reader.close();
            return n;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String text(byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i)).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
