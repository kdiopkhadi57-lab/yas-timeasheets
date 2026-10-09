package sn.yas.itfactory.timesheet.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.yas.itfactory.timesheet.service.DashboardService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.DashboardDto;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /** Réservé aux managers, au Head of Office et à l'Admin ; weeks : profondeur de la période (4 à 52). */
    @GetMapping
    public DashboardDto get(@RequestParam(required = false) Long projectId,
                            @RequestParam(defaultValue = "12") int weeks) {
        return dashboard.build(projectId, weeks);
    }
}
