package sn.yas.itfactory.timesheet.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Facture d'un consultant au format du modèle YAS (Facture_CONST-IT-C060-09-2026) :
 * coordonnées du consultant, numéro et date, client, ligne « Objet », honoraires bruts / retenue / nets,
 * retenue ordinateur, net à payer et signature.
 */
public final class InvoicePdfWriter {

    /** Données imprimées ; les montants sont en FCFA. */
    public record Data(String consultantName, String phone, String email, String address, String number,
                       LocalDate date, String client, String objectLine, long unitPrice, long gross, long withholding,
                       int withholdingPercent, long net, long computerDeduction, long netToPay, byte[] signaturePng) {
    }

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color GREY_BLOCK = new Color(0xF2, 0xF2, 0xF2);
    private static final Color GREY_HEADER = new Color(0xBF, 0xBF, 0xBF);
    private static final Color LINK = new Color(0x1F, 0x5F, 0xBF);
    private static final Color GRID = new Color(0xE3, 0xE3, 0xE3);

    // Colonnes (points) : Objet | Quantité | Prix unitaire | Montant
    private static final float LEFT = 36;
    private static final float C1 = 340;
    private static final float C2 = 406;
    private static final float C3 = 490;
    private static final float RIGHT = 559;

    private InvoicePdfWriter() {
    }

    /** « Periode du 01 au 30 Septembre 2026 ». */
    public static String periodLabel(LocalDate start, LocalDate end) {
        String month = start.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return "Periode du " + String.format("%02d", start.getDayOfMonth()) + " au " + String.format("%02d", end.getDayOfMonth())
                + " " + Character.toUpperCase(month.charAt(0)) + month.substring(1) + " " + end.getYear();
    }

    /** 800000 → « 800,000 », comme sur le modèle. */
    public static String amount(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    public static byte[] write(Data d) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float top = PDRectangle.A4.getHeight() - 40;

                text(cs, BOLD, 20, C1, top - 18, "FACTURE DE");

                // Bloc consultant (fond gris) et numéro / date
                float blockTop = top - 46;
                fill(cs, GREY_BLOCK, LEFT, blockTop - 112, C1 - LEFT - 4, 112);
                fill(cs, GREY_BLOCK, C1, blockTop - 22, RIGHT - C1, 22);
                float y = blockTop - 16;
                text(cs, BOLD, 11, LEFT + 2, y, d.consultantName());
                text(cs, BOLD, 11, C1 + 2, y, "NUMERO:");
                text(cs, REGULAR, 11, C2 + 4, y, d.number());
                y -= 20;
                text(cs, BOLD, 11, LEFT + 2, y, nz(d.phone()));
                y -= 20;
                if (d.email() != null && !d.email().isBlank()) {
                    cs.setNonStrokingColor(LINK);
                    text(cs, REGULAR, 11, LEFT + 2, y, d.email());
                    float w = width(REGULAR, 11, d.email());
                    line(cs, LINK, LEFT + 2, y - 2, LEFT + 2 + w, y - 2, 0.6f);
                    cs.setNonStrokingColor(Color.BLACK);
                }
                text(cs, BOLD, 11, C1 + 2, y, "DATE:");
                text(cs, REGULAR, 11, C2 + 4, y, d.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                y -= 22;
                text(cs, BOLD, 11, LEFT + 2, y, "adresse");
                y -= 20;
                text(cs, BOLD, 11, LEFT + 2, y, nz(d.address()));

                // Client
                y = blockTop - 150;
                text(cs, BOLD, 15, LEFT + 2, y, "A");
                y -= 24;
                text(cs, BOLD, 11, LEFT + 2, y, d.client());

                // Tableau
                y -= 52;
                float rowH = 18;
                fill(cs, GREY_HEADER, LEFT, y - 5, RIGHT - LEFT, rowH);
                line(cs, Color.BLACK, LEFT, y + rowH - 5, RIGHT, y + rowH - 5, 1.2f);
                line(cs, Color.BLACK, LEFT, y - 5, RIGHT, y - 5, 1.2f);
                for (float x : new float[]{C1, C2, C3}) {
                    line(cs, Color.BLACK, x, y - 5, x, y + rowH - 5, 0.8f);
                }
                text(cs, BOLD, 10, LEFT + 2, y, "OBJET");
                text(cs, BOLD, 10, C1 + 2, y, "QUANTITE");
                text(cs, BOLD, 10, C2 + 2, y, "PRIX UNITAIRE");
                text(cs, BOLD, 10, C3 + 2, y, "MONTANT");
                y -= rowH;
                fill(cs, GREY_BLOCK, LEFT, y - 5, C1 - LEFT, rowH);
                line(cs, Color.BLACK, LEFT, y - 5, RIGHT, y - 5, 0.8f);
                for (float x : new float[]{C1, C2, C3, RIGHT}) {
                    line(cs, Color.BLACK, x, y - 5, x, y + rowH - 5, 0.8f);
                }
                float objSize = fit(REGULAR, 10.5f, C1 - LEFT - 6, d.objectLine());
                text(cs, REGULAR, objSize, LEFT + 2, y, d.objectLine());
                right(cs, REGULAR, 10.5f, C2 - 3, y, "1");
                right(cs, REGULAR, 10.5f, C3 - 3, y, amount(d.unitPrice()));
                right(cs, REGULAR, 10.5f, RIGHT - 3, y, amount(d.unitPrice()));

                // Lignes vides comme sur le modèle
                for (int i = 0; i < 3; i++) {
                    y -= rowH;
                    line(cs, GRID, LEFT, y - 5, RIGHT, y - 5, 0.4f);
                }

                // Totaux
                y -= rowH;
                total(cs, y, "Honoraires bruts", amount(d.gross()), false);
                y -= rowH;
                total(cs, y, "Retenue(" + d.withholdingPercent() + "%)", amount(-d.withholding()), false);
                y -= rowH;
                total(cs, y, "Honoraires nets", amount(d.net()), true);
                y -= rowH;
                total(cs, y, "Retenue ordinateur a reverser a STAM", amount(d.computerDeduction()), false);
                y -= rowH * 2;
                line(cs, Color.BLACK, LEFT, y + rowH - 5, RIGHT, y + rowH - 5, 1f);
                line(cs, Color.BLACK, LEFT, y - 5, RIGHT, y - 5, 1f);
                for (float x : new float[]{C1, C2, C3}) {
                    line(cs, Color.BLACK, x, y - 5, x, y + rowH - 5, 0.8f);
                }
                right(cs, BOLD, 11, C1 - 3, y, "Net a payer");
                right(cs, BOLD, 11, RIGHT - 3, y, amount(d.netToPay()));

                // Signature
                if (d.signaturePng() != null && d.signaturePng().length > 0) {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, d.signaturePng(), "signature");
                    float w = 150;
                    float h = img.getHeight() * w / img.getWidth();
                    if (h > 90) {
                        w = w * 90 / h;
                        h = 90;
                    }
                    cs.drawImage(img, RIGHT - w - 10, y - 40 - h, w, h);
                }
            }
            doc.getDocumentInformation().setTitle("Facture " + d.number());
            doc.getDocumentInformation().setAuthor(d.consultantName());
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void total(PDPageContentStream cs, float y, String label, String value, boolean bold) throws IOException {
        line(cs, GRID, LEFT, y - 5, RIGHT, y - 5, 0.4f);
        right(cs, bold ? BOLD : REGULAR, 11, C1 - 3, y, label);
        right(cs, bold ? BOLD : REGULAR, 11, RIGHT - 3, y, value);
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(font, s));
        cs.endText();
    }

    private static void right(PDPageContentStream cs, PDFont font, float size, float x, float y, String s) throws IOException {
        text(cs, font, size, x - width(font, size, s), y, s);
    }

    private static float width(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(safe(font, s)) / 1000 * size;
    }

    /** Taille de police réduite si la ligne « Objet » est trop longue pour sa colonne. */
    private static float fit(PDFont font, float size, float max, String s) throws IOException {
        float sz = size;
        while (sz > 6 && width(font, sz, s) > max) {
            sz -= 0.5f;
        }
        return sz;
    }

    private static void fill(PDPageContentStream cs, Color c, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static void line(PDPageContentStream cs, Color c, float x1, float y1, float x2, float y2, float width) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
        cs.setStrokingColor(Color.BLACK);
    }

    /** Les polices standard PDF ne codent que le jeu WinAnsi : les autres caractères sont remplacés. */
    private static String safe(PDFont font, String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                out.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                out.append(cp == '’' ? "'" : "?");
            }
        });
        return out.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
