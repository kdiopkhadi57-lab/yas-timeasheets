package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Component;
import sn.yas.itfactory.timesheet.config.AppProperties;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Référence courte d'une tâche (TS-42) à mettre dans le nom de branche, le titre de MR ou le message de commit.
 * La casse est ignorée : feature/ts-42-export-excel fonctionne aussi.
 */
@Component
public class TaskRefs {

    private final String prefix;
    private final Pattern pattern;

    public TaskRefs(AppProperties properties) {
        String p = properties.gitlab() == null ? null : properties.gitlab().taskPrefix();
        this.prefix = p == null || p.isBlank() ? "TS" : p.trim().toUpperCase();
        this.pattern = Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(prefix) + "-(\\d{1,9})(?![0-9])");
    }

    public String prefix() {
        return prefix;
    }

    public String of(Long taskId) {
        return prefix + "-" + taskId;
    }

    /** Première référence trouvée dans les textes, dans l'ordre donné. */
    public Optional<Long> find(String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                return Optional.of(Long.parseLong(m.group(1)));
            }
        }
        return Optional.empty();
    }

    /** Retire la référence d'un titre de commit, pour en faire une ligne de fiche de temps lisible. */
    public String strip(String text) {
        return pattern.matcher(text).replaceAll("").replaceAll("^[\\s:\\-\\[\\]()#]+", "").trim();
    }
}
