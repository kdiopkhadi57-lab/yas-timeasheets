package sn.yas.itfactory.timesheet.gitlab;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import sn.yas.itfactory.timesheet.config.AppProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RestGitLabApi implements GitLabApi {

    private static final int PER_PAGE = 100;
    /** Garde-fou : 2 000 éléments maximum par appel paginé. */
    private static final int MAX_PAGES = 20;

    private final RestClient http;
    private final String baseUrl;

    public RestGitLabApi(RestClient.Builder builder, AppProperties properties) {
        AppProperties.Gitlab gitlab = properties.gitlab();
        this.baseUrl = gitlab.url().replaceAll("/+$", "") + "/api/v4";
        this.http = builder.defaultHeader("PRIVATE-TOKEN", gitlab.token() == null ? "" : gitlab.token()).build();
    }

    @Override
    public List<Project> projects(List<String> groups) {
        Map<Long, Project> byId = new LinkedHashMap<>();
        if (groups.isEmpty()) {
            pages("/projects", "membership=true&archived=false&simple=false", new ParameterizedTypeReference<List<Project>>() { })
                    .forEach(p -> byId.put(p.id(), p));
        }
        for (String group : groups) {
            pages("/groups/" + enc(group) + "/projects", "include_subgroups=true&archived=false",
                    new ParameterizedTypeReference<List<Project>>() { })
                    .forEach(p -> byId.put(p.id(), p));
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    public List<Commit> commits(long projectId, OffsetDateTime since) {
        return pages("/projects/" + projectId + "/repository/commits", "all=true&since=" + enc(since.toString()),
                new ParameterizedTypeReference<List<Commit>>() { });
    }

    @Override
    public List<MergeRequest> mergeRequests(long projectId, OffsetDateTime updatedAfter) {
        return pages("/projects/" + projectId + "/merge_requests", "state=all&updated_after=" + enc(updatedAfter.toString()),
                new ParameterizedTypeReference<List<MergeRequest>>() { });
    }

    @Override
    public List<Commit> mergeRequestCommits(long projectId, long iid) {
        return pages("/projects/" + projectId + "/merge_requests/" + iid + "/commits", "",
                new ParameterizedTypeReference<List<Commit>>() { });
    }

    @Override
    public List<Branch> branches(long projectId) {
        return pages("/projects/" + projectId + "/repository/branches", "", new ParameterizedTypeReference<List<Branch>>() { });
    }

    @Override
    public List<Commit> compare(long projectId, String from, String to) {
        Comparison c = http.get()
                .uri(URI.create(baseUrl + "/projects/" + projectId + "/repository/compare?from=" + enc(from) + "&to=" + enc(to)))
                .retrieve()
                .body(Comparison.class);
        return c == null || c.commits() == null ? List.of() : c.commits();
    }

    private <T> List<T> pages(String path, String query, ParameterizedTypeReference<List<T>> type) {
        List<T> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String q = (query.isEmpty() ? "" : query + "&") + "per_page=" + PER_PAGE + "&page=" + page;
            List<T> batch = http.get().uri(URI.create(baseUrl + path + "?" + q)).retrieve().body(type);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < PER_PAGE) {
                break;
            }
        }
        return all;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
