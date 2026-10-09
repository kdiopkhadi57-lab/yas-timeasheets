package sn.yas.itfactory.timesheet.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.domain.ReportType;
import sn.yas.itfactory.timesheet.service.ReportService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Fiches de temps. type=HEBDO (semaine, envoyée au PM) ou MENSUEL (20 → 19, envoyée au Head of Office) ;
 * date = n'importe quel jour de la période voulue (aujourd'hui par défaut).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/timesheet")
    public TimesheetDto timesheet(@RequestParam ReportType type,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(required = false) Long consultantId) {
        return reports.timesheet(type, orToday(date), consultantId);
    }

    @PostMapping("/submit")
    public TimesheetDto submit(@RequestParam ReportType type,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestBody(required = false) SubmitRequest req) {
        return reports.submit(type, orToday(date), req == null ? null : req.comment());
    }

    @PostMapping("/withdraw")
    public TimesheetDto withdraw(@RequestParam ReportType type,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.withdraw(type, orToday(date));
    }

    @PostMapping("/{id}/approve")
    public ReportDto approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest req) {
        return reports.approve(id, req == null ? null : req.comment());
    }

    @PostMapping("/{id}/reject")
    public ReportDto reject(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return reports.reject(id, req.comment());
    }

    @GetMapping("/team")
    public List<TeamStatusDto> team(@RequestParam ReportType type,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.teamStatus(type, orToday(date));
    }

    /** Fiches soumises reçues par le manager ou le Head of Office et en attente de validation (cloche de notification). */
    @GetMapping("/inbox")
    public List<ReportDto> inbox() {
        return reports.inbox();
    }

    @GetMapping("/history")
    public List<ReportDto> history(@RequestParam(required = false) Long consultantId) {
        return reports.history(consultantId);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@RequestParam ReportType type,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @RequestParam(required = false) Long consultantId) {
        ReportService.ExcelFile f = reports.excelFor(type, orToday(date), consultantId);
        return download(f, XLSX);
    }

    @GetMapping("/team/zip")
    public ResponseEntity<byte[]> teamZip(@RequestParam ReportType type,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return download(reports.teamZip(type, orToday(date)), MediaType.parseMediaType("application/zip"));
    }

    private static ResponseEntity<byte[]> download(ReportService.ExcelFile f, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    private static LocalDate orToday(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }
}
