package sn.yas.itfactory.timesheet;

import org.junit.jupiter.api.Test;
import sn.yas.itfactory.timesheet.service.Period;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTest {

    @Test
    void monthlyPeriodRunsFromThe20thToThe19th() {
        Period p = Period.monthly(LocalDate.of(2026, 6, 3));
        assertThat(p.start()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(p.end()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(p.label()).isEqualTo("Mai - Juin 2026");
    }

    @Test
    void the20thStartsANewPeriod() {
        Period p = Period.monthly(LocalDate.of(2026, 8, 20));
        assertThat(p.start()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(p.end()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(p.label()).isEqualTo("Août - Septembre 2026");
    }

    @Test
    void decemberPeriodEndsInJanuaryOfNextYear() {
        Period p = Period.monthly(LocalDate.of(2027, 1, 5));
        assertThat(p.start()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(p.end()).isEqualTo(LocalDate.of(2027, 1, 19));
        assertThat(p.label()).isEqualTo("Décembre - Janvier 2027");
        assertThat(p.previous().start()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void weeklyPeriodRunsFromThePreviousSaturdayToFriday() {
        Period p = Period.weekly(LocalDate.of(2026, 10, 9)); // vendredi
        assertThat(p.start()).isEqualTo(LocalDate.of(2026, 10, 3)); // samedi précédent
        assertThat(p.end()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(p.label()).isEqualTo("Semaine 41 - 2026");
        // Le week-end qui précède le lundi appartient à la semaine qui suit.
        assertThat(Period.weekly(LocalDate.of(2026, 10, 4)).start()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(Period.weekly(LocalDate.of(2026, 10, 10)).start()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(Period.weekly(LocalDate.of(2026, 10, 10)).label()).isEqualTo("Semaine 42 - 2026");
    }
}
