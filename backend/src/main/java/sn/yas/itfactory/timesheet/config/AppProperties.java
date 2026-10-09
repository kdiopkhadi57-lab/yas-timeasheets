package sn.yas.itfactory.timesheet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Mail mail, Cors cors, String frontendUrl, Gitlab gitlab, Documents documents,
                            Invoice invoice) {

    public record Jwt(String secret, int expirationHours) {
    }

    /** enabled=false : les e-mails sont seulement journalisés (pratique en développement). */
    public record Mail(boolean enabled, String from) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Factures des consultants : client facturé, préfixe du numéro (CONST-IT-C060-09-2026)
     * et taux de retenue appliqué aux honoraires bruts.
     */
    public record Invoice(String client, String prefix, Double withholdingRate) {
    }

    /** Dossier où sont stockés les documents des projets (un sous-dossier par projet). */
    public record Documents(String dir) {
    }

    /**
     * Liaison GitLab. Sans jeton, la synchronisation est désactivée.
     * groups vide : tous les dépôts dont le propriétaire du jeton est membre.
     */
    public record Gitlab(String url, String token, List<String> groups, String taskPrefix, int initialDays) {

        public boolean enabled() {
            return token != null && !token.isBlank();
        }
    }
}
