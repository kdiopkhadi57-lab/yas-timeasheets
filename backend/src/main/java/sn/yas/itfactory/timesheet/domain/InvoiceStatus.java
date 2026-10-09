package sn.yas.itfactory.timesheet.domain;

public enum InvoiceStatus {
    /** Saisie par le consultant, pas encore envoyée. */
    BROUILLON,
    /** Envoyée en PDF au Head of Office. */
    SOUMISE,
    VALIDEE,
    /** Renvoyée au consultant avec un commentaire : modifiable à nouveau. */
    REJETEE;

    public boolean editable() {
        return this == BROUILLON || this == REJETEE;
    }
}
