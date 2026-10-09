package sn.yas.itfactory.timesheet.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.service.EntryService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entries")
public class EntryController {

    private final EntryService entries;

    public EntryController(EntryService entries) {
        this.entries = entries;
    }

    @GetMapping
    public List<EntryDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long consultantId) {
        return entries.list(from, to, consultantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryDto create(@Valid @RequestBody EntryRequest req) {
        return entries.create(req);
    }

    @PutMapping("/{id}")
    public EntryDto update(@PathVariable Long id, @Valid @RequestBody EntryRequest req) {
        return entries.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        entries.delete(id);
    }
}
