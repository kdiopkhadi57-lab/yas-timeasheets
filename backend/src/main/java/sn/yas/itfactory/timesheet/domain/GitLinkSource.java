package sn.yas.itfactory.timesheet.domain;

/** Comment un commit a été relié à une tâche, du plus explicite au moins explicite. */
public enum GitLinkSource {
    /** TS-42 dans le message du commit. */
    MESSAGE,
    /** TS-42 dans le titre ou la branche d'une merge request. */
    MERGE_REQUEST,
    /** TS-42 dans le nom d'une branche sans MR. */
    BRANCHE
}
