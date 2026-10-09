package sn.yas.itfactory.timesheet.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import sn.yas.itfactory.timesheet.domain.ReportStatus;
import sn.yas.itfactory.timesheet.domain.ReportType;
import sn.yas.itfactory.timesheet.service.TimesheetService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Génère la fiche de temps au format YAS IT-FACTORY
 * (« Prenom NOM - C099 - Fiche de temps Mai - Juin 2026.xlsx ») :
 * en-tête Nom / Matricule / Profil / Période, puis une ligne par jour
 * Date | Projet | Business Unit | Liste des tâches | Commentaire, et le cadre de signature.
 */
@Component
public class TimesheetExcelWriter {

    private static final byte[] HEADER_BLUE = {(byte) 0xFF, (byte) 0x4B, (byte) 0xAC, (byte) 0xC6};
    private static final byte[] WEEKEND_GREY = {(byte) 0xFF, (byte) 0xF2, (byte) 0xF2, (byte) 0xF2};
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH)
            .withZone(ZoneId.of("Africa/Dakar"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] TASK_STATUS_LABELS = {"À faire", "En cours", "Bloquée", "Terminée"};

    /** Largeurs de colonnes du modèle (en caractères). */
    private static final double[] WIDTHS = {27.43, 20, 16, 25, 30};
    private static final int FIRST_DAY_ROW = 7; // ligne 8 dans Excel

    public String fileName(TimesheetDto ts) {
        UserDto c = ts.consultant();
        String kind = ts.type() == ReportType.MENSUEL ? "Fiche de temps " : "Rapport hebdo ";
        String name = c.firstName() + " " + c.lastName().toUpperCase(Locale.FRENCH) + " - " + c.matricule() + " - "
                + kind + ts.periodLabel() + ".xlsx";
        return name.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    public byte[] write(TimesheetDto ts, List<TaskDto> tasks) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            writeTimesheet(wb, s, ts);
            if (tasks != null && !tasks.isEmpty()) {
                writeTasks(wb, s, tasks);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTimesheet(XSSFWorkbook wb, Styles s, TimesheetDto ts) {
        Sheet sh = wb.createSheet(ts.type() == ReportType.MENSUEL ? "Fiche temps Mensuel" : "Fiche temps Hebdo");
        for (int i = 0; i < WIDTHS.length; i++) {
            sh.setColumnWidth(i, (int) Math.round(WIDTHS[i] * 256));
        }
        UserDto c = ts.consultant();

        Row r0 = row(sh, 0, 18.6f);
        cell(r0, 0, "Fiche de temps", s.title);
        merge(sh, 0, 0, 0, 4);

        labelRow(sh, s, 1, "Prénom(s) & Nom", c.firstName() + " " + c.lastName().toUpperCase(Locale.FRENCH));
        labelRow(sh, s, 2, "Matricule", c.matricule());
        labelRow(sh, s, 3, "Profil", c.profil() == null ? "" : c.profil());

        Row r4 = row(sh, 4, 15.6f);
        cell(r4, 0, "Période", s.label);
        cell(r4, 1, "DU", s.centered);
        Cell from = r4.createCell(2);
        from.setCellValue(ts.periodStart());
        from.setCellStyle(s.headerDate);
        cell(r4, 3, "AU", s.centered);
        Cell to = r4.createCell(4);
        to.setCellValue(ts.periodEnd());
        to.setCellStyle(s.headerDate);

        Row head = row(sh, 6, 15f);
        String[] titles = {"Date", "Projet", "Business Unit", "Liste des tâches", "Commentaire"};
        for (int i = 0; i < titles.length; i++) {
            cell(head, i, titles[i], s.columnHeader);
        }

        int rowIdx = FIRST_DAY_ROW;
        for (DayDto day : ts.days()) {
            Row r = sh.createRow(rowIdx++);
            String projects = String.join("\n", day.projects());
            String units = String.join("\n", day.businessUnits());
            String taskText = day.tasks().isEmpty() ? "" : TimesheetService.formatTasks(day.tasks());
            String comments = String.join("\n", day.comments());

            Cell date = r.createCell(0);
            date.setCellValue(day.date());
            date.setCellStyle(day.weekend() ? s.dateWeekend : s.date);
            cell(r, 1, projects, day.weekend() ? s.centeredWeekend : s.centeredWrap);
            cell(r, 2, units, day.weekend() ? s.centeredWeekend : s.centeredWrap);
            cell(r, 3, taskText, day.weekend() ? s.wrapWeekend : s.wrap);
            cell(r, 4, comments, day.weekend() ? s.wrapWeekend : s.wrap);

            int lines = Math.max(1, Math.max(lineCount(taskText, WIDTHS[3]),
                    Math.max(lineCount(comments, WIDTHS[4]), lineCount(projects, WIDTHS[1]))));
            r.setHeightInPoints(Math.max(15f, lines * 15f));
        }

        // Cadre de signature : 4 lignes fusionnées, comme dans le modèle.
        int sig = rowIdx + 1;
        for (int i = 0; i < 4; i++) {
            Row r = row(sh, sig + i, 15f);
            for (int col = 0; col < 5; col++) {
                r.createCell(col).setCellStyle(col == 0 ? s.signatureLabel : s.signatureBox);
            }
        }
        sh.getRow(sig).getCell(0).setCellValue("Signature Consultant");
        sh.getRow(sig).getCell(1).setCellValue(signatureText(ts));
        merge(sh, sig, sig + 3, 0, 0);
        merge(sh, sig, sig + 3, 1, 4);

        sh.createFreezePane(0, FIRST_DAY_ROW);
        PrintSetup ps = sh.getPrintSetup();
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sh.setFitToPage(true);
        ps.setFitWidth((short) 1);
        ps.setFitHeight((short) 0);
        sh.setRepeatingRows(new CellRangeAddress(6, 6, 0, 4));
    }

    private String signatureText(TimesheetDto ts) {
        ReportDto r = ts.report();
        if (r == null || r.submittedAt() == null || r.status() == ReportStatus.BROUILLON) {
            return "";
        }
        String text = "Soumis électroniquement par " + ts.consultant().fullName() + " le " + STAMP.format(r.submittedAt());
        if (r.status() == ReportStatus.VALIDE && r.reviewedAt() != null) {
            text += "\nValidé par " + r.reviewerName() + " le " + STAMP.format(r.reviewedAt());
        }
        return text;
    }

    private void writeTasks(XSSFWorkbook wb, Styles s, List<TaskDto> tasks) {
        Sheet sh = wb.createSheet("Suivi des tâches");
        double[] widths = {45, 20, 14, 14, 14};
        for (int i = 0; i < widths.length; i++) {
            sh.setColumnWidth(i, (int) Math.round(widths[i] * 256));
        }
        Row head = sh.createRow(0);
        String[] titles = {"Tâche", "Projet", "Statut", "Échéance", "Terminée le"};
        for (int i = 0; i < titles.length; i++) {
            cell(head, i, titles[i], s.columnHeader);
        }
        int i = 1;
        for (TaskDto t : tasks) {
            Row r = sh.createRow(i++);
            cell(r, 0, t.title(), s.wrap);
            cell(r, 1, t.projectName(), s.wrap);
            cell(r, 2, TASK_STATUS_LABELS[t.status().ordinal()], s.wrap);
            cell(r, 3, t.dueDate() == null ? "" : DAY.format(t.dueDate()), s.wrap);
            cell(r, 4, t.completedAt() == null ? "" : DAY.format(t.completedAt().atZone(ZoneId.of("Africa/Dakar"))), s.wrap);
        }
        sh.setAutoFilter(new CellRangeAddress(0, Math.max(0, i - 1), 0, 4));
    }

    /** Estimation du nombre de lignes affichées dans une cellule à retour automatique. */
    static int lineCount(String text, double width) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int perLine = Math.max(1, (int) (width * 1.05));
        int lines = 0;
        for (String part : text.split("\n", -1)) {
            lines += Math.max(1, (int) Math.ceil(part.length() / (double) perLine));
        }
        return lines;
    }

    private void labelRow(Sheet sh, Styles s, int idx, String label, String value) {
        Row r = row(sh, idx, 15.6f);
        cell(r, 0, label, s.label);
        cell(r, 1, value, s.centered);
        for (int col = 2; col < 5; col++) {
            r.createCell(col).setCellStyle(s.centered);
        }
        merge(sh, idx, idx, 1, 4);
    }

    private static Row row(Sheet sh, int idx, float height) {
        Row r = sh.getRow(idx) == null ? sh.createRow(idx) : sh.getRow(idx);
        r.setHeightInPoints(height);
        return r;
    }

    private static void cell(Row r, int col, String value, CellStyle style) {
        Cell c = r.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void merge(Sheet sh, int r1, int r2, int c1, int c2) {
        CellRangeAddress range = new CellRangeAddress(r1, r2, c1, c2);
        sh.addMergedRegion(range);
        RegionUtil.setBorderTop(BorderStyle.THIN, range, sh);
        RegionUtil.setBorderBottom(BorderStyle.THIN, range, sh);
        RegionUtil.setBorderLeft(BorderStyle.THIN, range, sh);
        RegionUtil.setBorderRight(BorderStyle.THIN, range, sh);
    }

    private static final class Styles {
        final CellStyle title, label, centered, headerDate, columnHeader, date, dateWeekend, centeredWrap,
                centeredWeekend, wrap, wrapWeekend, signatureLabel, signatureBox;

        Styles(XSSFWorkbook wb) {
            short dateFmt = wb.createDataFormat().getFormat("dd/mm/yyyy");
            XSSFFont f11 = font(wb, 11, false);
            XSSFFont f12 = font(wb, 12, false);
            XSSFFont bold11 = font(wb, 11, true);

            title = base(wb, font(wb, 14, true));
            title.setAlignment(HorizontalAlignment.CENTER);

            label = base(wb, f12);
            label.setAlignment(HorizontalAlignment.LEFT);

            centered = base(wb, f12);
            centered.setAlignment(HorizontalAlignment.CENTER);
            centered.setVerticalAlignment(VerticalAlignment.CENTER);

            headerDate = base(wb, f12);
            headerDate.setAlignment(HorizontalAlignment.CENTER);
            headerDate.setVerticalAlignment(VerticalAlignment.CENTER);
            headerDate.setDataFormat(dateFmt);

            columnHeader = base(wb, bold11);
            ((XSSFCellStyle) columnHeader).setFillForegroundColor(new XSSFColor(HEADER_BLUE, null));
            columnHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            date = base(wb, f11);
            date.setDataFormat(dateFmt);
            date.setAlignment(HorizontalAlignment.RIGHT);
            date.setVerticalAlignment(VerticalAlignment.BOTTOM);
            dateWeekend = weekend(wb, date);

            centeredWrap = base(wb, f11);
            centeredWrap.setAlignment(HorizontalAlignment.CENTER);
            centeredWrap.setVerticalAlignment(VerticalAlignment.CENTER);
            centeredWrap.setWrapText(true);
            centeredWeekend = weekend(wb, centeredWrap);

            wrap = base(wb, f11);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);
            wrap.setWrapText(true);
            wrapWeekend = weekend(wb, wrap);

            signatureLabel = base(wb, bold11);
            signatureLabel.setVerticalAlignment(VerticalAlignment.CENTER);

            signatureBox = base(wb, f11);
            signatureBox.setAlignment(HorizontalAlignment.CENTER);
            signatureBox.setVerticalAlignment(VerticalAlignment.CENTER);
            signatureBox.setWrapText(true);
        }

        private static XSSFFont font(XSSFWorkbook wb, int size, boolean bold) {
            XSSFFont f = wb.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            return f;
        }

        private static CellStyle base(XSSFWorkbook wb, Font font) {
            CellStyle st = wb.createCellStyle();
            st.setFont(font);
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
            return st;
        }

        private static CellStyle weekend(XSSFWorkbook wb, CellStyle from) {
            XSSFCellStyle st = wb.createCellStyle();
            st.cloneStyleFrom(from);
            st.setFillForegroundColor(new XSSFColor(WEEKEND_GREY, null));
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return st;
        }
    }
}
