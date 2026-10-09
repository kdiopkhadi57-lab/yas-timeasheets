package sn.yas.itfactory.timesheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class TimesheetApplication {

    public static void main(String[] args) {
        // Les périodes (vendredi, 20 → 19) se calculent à l'heure de Dakar, quel que soit le serveur.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dakar"));
        SpringApplication.run(TimesheetApplication.class, args);
    }
}
