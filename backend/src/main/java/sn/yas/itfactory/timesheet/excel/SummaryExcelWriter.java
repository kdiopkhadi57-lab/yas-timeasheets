package sn.yas.itfactory.timesheet.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import sn.yas.itfactory.timesheet.domain.ReportStatus;
import sn.yas.itfactory.timesheet.web.dto.Dtos.TeamStatusDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Synthèse d'équipe (PM) ou globale (Head of Office) : un consultant par ligne. */
@Component
public class SummaryExcelWriter {

    public byte[] write(String title, List<TeamStatusDto> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Synthèse");
            CellStyle bold = wb.createCellStyle();
            Font bf = wb.createFont();
            bf.setBold(true);
            bf.setFontHeightInPoints((short) 14);
            bold.setFont(bf);

            XSSFCellStyle header = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            header.setFont(hf);
            header.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0x4B, (byte) 0xAC, (byte) 0xC6}, null));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            Row t = sh.createRow(0);
            Cell tc = t.createCell(0);
            tc.setCellValue(title);
            tc.setCellStyle(bold);

            String[] cols = {"Consultant", "Matricule", "Profil", "Manager", "Jours travaillés", "Tâches saisies", "Statut", "Commentaire"};
            int[] widths = {28, 12, 24, 24, 16, 15, 14, 50};
            Row h = sh.createRow(2);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
                sh.setColumnWidth(i, widths[i] * 256);
            }
            int idx = 3;
            for (TeamStatusDto r : rows) {
                Row row = sh.createRow(idx++);
                row.createCell(0).setCellValue(r.consultant().fullName());
                row.createCell(1).setCellValue(r.consultant().matricule());
                row.createCell(2).setCellValue(nz(r.consultant().profil()));
                row.createCell(3).setCellValue(nz(r.consultant().pmName()));
                row.createCell(4).setCellValue(r.workedDays());
                row.createCell(5).setCellValue(r.entryCount());
                row.createCell(6).setCellValue(statusLabel(r.report() == null ? null : r.report().status()));
                row.createCell(7).setCellValue(r.report() == null ? "" : nz(r.report().consultantComment()));
            }
            sh.setAutoFilter(new CellRangeAddress(2, Math.max(2, idx - 1), 0, cols.length - 1));
            sh.createFreezePane(0, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String statusLabel(ReportStatus s) {
        if (s == null) {
            return "Non soumis";
        }
        return switch (s) {
            case BROUILLON -> "Non soumis";
            case SOUMIS -> "Soumis";
            case VALIDE -> "Validé";
            case REJETE -> "À corriger";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
