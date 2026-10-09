package sn.yas.itfactory.timesheet.domain;

/** Ligne du fil d'activité d'une tâche : un commentaire ou une modification tracée. */
public enum TaskActivityKind {
    CREATION,
    COMMENTAIRE,
    STATUT,
    ASSIGNATION,
    MODIFICATION,
    ESTIMATION
}
