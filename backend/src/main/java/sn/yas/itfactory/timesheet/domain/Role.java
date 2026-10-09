package sn.yas.itfactory.timesheet.domain;

public enum Role {
    CONSULTANT,
    /** Architecte : dépose les documents des projets dont il est l'architecte. */
    ARCHITECTE,
    /** Lead dev : assigne les tâches aux développeurs de ses projets et valide leurs estimations. */
    LEAD_DEV,
    PM,
    HEAD_OF_OFFICE,
    ADMIN;

    /**
     * Pas de tâches assignées pour l'instant aux développeurs et au lead dev : ils saisissent leur journée après le daily
     * (même règle que usesTasks côté frontend). Leur fiche Excel n'a donc pas d'onglet « Suivi des tâches ».
     */
    public boolean usesTasks() {
        return this != CONSULTANT && this != LEAD_DEV;
    }

    /** Remplit une fiche de temps et un rapport hebdo (suivi par un PM). */
    public boolean fillsTimesheet() {
        return this == CONSULTANT || this == ARCHITECTE || this == LEAD_DEV || this == PM;
    }
}
