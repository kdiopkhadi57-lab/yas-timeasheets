package sn.yas.itfactory.timesheet.domain;

/** Nature d'un document de projet déposé par l'architecte. */
public enum DocumentCategory {
    /** Termes de référence. */
    TDR,
    /** Compte rendu ou support de réunion. */
    REUNION,
    SPECIFICATION,
    ARCHITECTURE,
    CODE,
    AUTRE
}
