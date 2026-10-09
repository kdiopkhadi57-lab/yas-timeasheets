package sn.yas.itfactory.timesheet.domain;

public enum ReportStatus {
    BROUILLON,
    SOUMIS,
    VALIDE,
    REJETE;

    /** Un rapport soumis ou validé verrouille les saisies de sa période. */
    public boolean locksEntries() {
        return this == SOUMIS || this == VALIDE;
    }
}
