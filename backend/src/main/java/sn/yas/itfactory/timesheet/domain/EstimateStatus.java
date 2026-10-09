package sn.yas.itfactory.timesheet.domain;

/**
 * Estimation d'une tâche assignée par le lead dev : le développeur propose une durée,
 * le lead dev l'accorde et la tâche devient active.
 */
public enum EstimateStatus {
    /** Assignée, en attente de l'estimation du développeur. */
    A_ESTIMER,
    /** Durée proposée par le développeur, en attente du lead dev. */
    PROPOSEE,
    /** Estimation accordée : la tâche est active. */
    ACCORDEE
}
