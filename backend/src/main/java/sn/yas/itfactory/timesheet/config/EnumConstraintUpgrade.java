package sn.yas.itfactory.timesheet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hibernate fige les valeurs des enums dans le schéma (type ENUM natif en H2, contrainte CHECK en PostgreSQL)
 * et ne les met pas à jour en ddl-auto=update. Une base créée avant l'ajout des rôles Architecte / Lead dev
 * ou du type d'activité « Estimation » refuserait ces valeurs : au démarrage, les colonnes ENUM H2 passent
 * en VARCHAR et les anciennes contraintes CHECK sont supprimées.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EnumConstraintUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumConstraintUpgrade.class);

    /** Table → valeur ajoutée qui doit être acceptée. */
    private static final Map<String, List<String>> NEW_VALUES = Map.of(
            "app_user", List.of("ARCHITECTE", "LEAD_DEV"),
            "task_activity", List.of("ESTIMATION"));

    private final JdbcTemplate jdbc;

    public EnumConstraintUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        widenH2Enums();
        dropChecks();
    }

    /** H2 : colonnes de type ENUM (liste de valeurs figée) → VARCHAR. */
    private void widenH2Enums() {
        try {
            List<Map<String, Object>> cols = jdbc.queryForList(
                    "select table_name, column_name from information_schema.columns "
                            + "where table_schema = current_schema and data_type = 'ENUM'");
            for (Map<String, Object> c : cols) {
                String table = String.valueOf(c.get("table_name"));
                String column = String.valueOf(c.get("column_name"));
                jdbc.execute("alter table \"" + table + "\" alter column \"" + column + "\" varchar(64)");
                log.info("Colonne {}.{} convertie de ENUM en VARCHAR", table, column);
            }
        } catch (RuntimeException e) {
            log.debug("Pas de colonne ENUM à convertir : {}", e.getMessage());
        }
    }

    private void dropChecks() {
        try {
            List<Map<String, Object>> checks = jdbc.queryForList(
                    "select tc.table_name, tc.constraint_name, cc.check_clause from information_schema.table_constraints tc "
                            + "join information_schema.check_constraints cc on cc.constraint_name = tc.constraint_name "
                            + "where tc.constraint_type = 'CHECK'");
            for (Map<String, Object> c : checks) {
                String table = String.valueOf(c.get("table_name")).toLowerCase();
                String clause = String.valueOf(c.get("check_clause"));
                List<String> required = NEW_VALUES.get(table);
                if (required == null || required.stream().allMatch(clause::contains)) {
                    continue;
                }
                if (!clause.contains("'CONSULTANT'") && !clause.contains("'COMMENTAIRE'")) {
                    continue; // contrainte qui ne porte pas sur l'enum
                }
                String name = String.valueOf(c.get("constraint_name"));
                jdbc.execute("alter table " + table + " drop constraint \"" + name.replace("\"", "") + "\"");
                log.info("Contrainte {} supprimée sur {} (nouvelles valeurs d'enum)", name, table);
            }
        } catch (RuntimeException e) {
            log.warn("Vérification des contraintes d'enum impossible : {}", e.getMessage());
        }
    }
}
