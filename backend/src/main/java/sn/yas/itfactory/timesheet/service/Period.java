package sn.yas.itfactory.timesheet.service;

import sn.yas.itfactory.timesheet.domain.ReportType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Période d'un rapport.
 * MENSUEL : du 20 du mois au 19 du mois suivant (format de la fiche de temps YAS).
 * HEBDO : du samedi au vendredi — le week-end qui précède le lundi, puis la semaine jusqu'au vendredi,
 * jour où le rapport est soumis.
 */
public record Period(ReportType type, LocalDate start, LocalDate end) {

    public static final int MONTHLY_START_DAY = 20;
    private static final Locale FR = Locale.FRENCH;

    public static Period of(ReportType type, LocalDate date) {
        return type == ReportType.MENSUEL ? monthly(date) : weekly(date);
    }

    public static Period monthly(LocalDate date) {
        LocalDate start = date.getDayOfMonth() >= MONTHLY_START_DAY
                ? date.withDayOfMonth(MONTHLY_START_DAY)
                : date.minusMonths(1).withDayOfMonth(MONTHLY_START_DAY);
        return new Period(ReportType.MENSUEL, start, start.plusMonths(1).withDayOfMonth(MONTHLY_START_DAY - 1));
    }

    public static Period weekly(LocalDate date) {
        LocalDate saturday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        return new Period(ReportType.HEBDO, saturday, saturday.plusDays(6));
    }

    /** Lundi de la semaine qui démarre à cette date (samedi → lundi suivant) : donne le numéro de semaine. */
    public static LocalDate monday(LocalDate weekStart) {
        return weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Vendredi de la semaine qui démarre à cette date : jour de soumission du rapport hebdo. */
    public static LocalDate friday(LocalDate weekStart) {
        return weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
    }

    public Period previous() {
        return of(type, start.minusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** « Mai - Juin 2026 » pour le mensuel, « Semaine 41 - 2026 » pour l'hebdo. */
    public String label() {
        if (type == ReportType.MENSUEL) {
            String from = capitalize(start.getMonth().getDisplayName(TextStyle.FULL, FR));
            String to = capitalize(end.getMonth().getDisplayName(TextStyle.FULL, FR));
            return from + " - " + to + " " + end.getYear();
        }
        LocalDate monday = monday(start);
        return "Semaine " + monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " - " + monday.get(IsoFields.WEEK_BASED_YEAR);
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(FR) + s.substring(1);
    }
}
