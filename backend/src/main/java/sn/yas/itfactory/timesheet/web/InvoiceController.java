package sn.yas.itfactory.timesheet.web;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.service.InvoiceService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Factures mensuelles des consultants, envoyées en PDF au Head of Office. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping("/mine")
    public List<InvoiceDto> mine() {
        return invoices.mine();
    }

    /** Facture du mois (AAAA-MM), ou proposition pré-remplie si elle n'existe pas encore. */
    @GetMapping("/month/{month}")
    public InvoiceDto forMonth(@PathVariable String month) {
        return invoices.forMonth(month);
    }

    @PutMapping
    public InvoiceDto save(@Valid @RequestBody InvoiceRequest req) {
        return invoices.save(req);
    }

    @PostMapping("/{id}/submit")
    public InvoiceDto submit(@PathVariable Long id) {
        return invoices.submit(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoiceService.PdfFile f = invoices.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('HEAD_OF_OFFICE','ADMIN')")
    public List<InvoiceTeamRowDto> team(@RequestParam String month) {
        return invoices.team(month);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('HEAD_OF_OFFICE','ADMIN')")
    public InvoiceDto approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest req) {
        return invoices.approve(id, req == null ? null : req.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HEAD_OF_OFFICE','ADMIN')")
    public InvoiceDto reject(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return invoices.reject(id, req.comment());
    }

    // ---- Coordonnées et signature imprimées sur la facture

    @GetMapping("/profile")
    public InvoiceProfileDto profile() {
        return invoices.profile();
    }

    @PutMapping("/profile")
    public InvoiceProfileDto updateProfile(@Valid @RequestBody InvoiceProfileRequest req) {
        return invoices.updateProfile(req);
    }

    @GetMapping(value = "/profile/signature", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] signature() {
        return invoices.signature();
    }

    @PutMapping("/profile/signature")
    public InvoiceProfileDto saveSignature(@Valid @RequestBody SignatureRequest req) {
        return invoices.saveSignature(req.dataUrl());
    }

    @DeleteMapping("/profile/signature")
    public InvoiceProfileDto deleteSignature() {
        return invoices.deleteSignature();
    }
}
