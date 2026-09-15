package net.minecraft.launcher.modpacks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ModrinthProvider implements ModpackProvider {

    private static final String API = "https://api.modrinth.com/v2";

    @Override
    public String getName() {
        return "Modrinth";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public List<ModpackSearchResult> search(String query) throws Exception {

        String facets = "[[\"project_type:modpack\"]]";

        String url =
                API
                        + "/search?query="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&facets="
                        + URLEncoder.encode(facets, StandardCharsets.UTF_8)
                        + "&limit=20";

        JsonObject json = getJson(url);

        List<ModpackSearchResult> results = new ArrayList<>();

        JsonArray hits = json.getAsJsonArray("hits");

        for (JsonElement element : hits) {

            JsonObject hit = element.getAsJsonObject();

            String projectId =
                    hit.get("project_id").getAsString();

            String name =
                    hit.get("title").getAsString();

            String author =
                    hit.has("author")
                            ? hit.get("author").getAsString()
                            : "";

            String description =
                    hit.has("description")
                            ? hit.get("description").getAsString()
                            : "";

            String icon =
                    hit.has("icon_url") && !hit.get("icon_url").isJsonNull()
                            ? hit.get("icon_url").getAsString()
                            : null;

            long downloads =
                    hit.has("downloads")
                            ? hit.get("downloads").getAsLong()
                            : 0;

            List<String> minecraftVersions = new ArrayList<>();

            if (hit.has("versions")) {

                JsonArray versions =
                        hit.getAsJsonArray("versions");

                for (JsonElement version : versions) {
                    minecraftVersions.add(
                            version.getAsString()
                    );
                }
            }

            List<String> loaders = new ArrayList<>();

            if (hit.has("categories")) {

                JsonArray categories =
                        hit.getAsJsonArray("categories");

                for (JsonElement categoryElement : categories) {

                    String category =
                            categoryElement
                                    .getAsString()
                                    .toLowerCase();

                    if (
                            category.equals("forge")
                                    || category.equals("fabric")
                                    || category.equals("neoforge")
                                    || category.equals("quilt")
                                    || category.equals("liteloader")
                    ) {

                        loaders.add(category);
                    }
                }
            }

            String updated =
                    hit.has("date_modified")
                            ? hit.get("date_modified").getAsString()
                            : null;

            results.add(
                    new ModpackSearchResult(
                            getName(),
                            projectId,
                            name,
                            author,
                            description,
                            icon,
                            downloads,
                            minecraftVersions,
                            loaders,
                            updated
                    )
            );
        }

        return results;
    }

    @Override
    public List<ModpackVersion> getVersions(String projectId) throws Exception {

        String url =
                API
                        + "/project/"
                        + URLEncoder.encode(projectId, StandardCharsets.UTF_8)
                        + "/version?include_changelog=false";

        JsonArray versions = getJsonArray(url);

        List<ModpackVersion> results = new ArrayList<>();

        for (JsonElement element : versions) {

            JsonObject version = element.getAsJsonObject();

            String versionId =
                    version.get("id").getAsString();

            String name =
                    version.has("name")
                            ? version.get("name").getAsString()
                            : versionId;

            String minecraftVersion = "";

            if (version.has("game_versions")) {

                JsonArray gameVersions =
                        version.getAsJsonArray("game_versions");

                if (!gameVersions.isEmpty()) {
                    minecraftVersion =
                            gameVersions.get(0).getAsString();
                }
            }

            String loader = "";

            if (version.has("loaders")) {

                JsonArray loaders =
                        version.getAsJsonArray("loaders");

                if (!loaders.isEmpty()) {
                    loader =
                            loaders.get(0).getAsString();
                }
            }

            String downloadUrl = null;

            if (version.has("files")) {

                JsonArray files =
                        version.getAsJsonArray("files");

                JsonObject selectedFile = null;

                for (JsonElement fileElement : files) {

                    JsonObject file =
                            fileElement.getAsJsonObject();

                    if (
                            file.has("primary")
                                    && file.get("primary").getAsBoolean()
                    ) {

                        selectedFile = file;
                        break;
                    }
                }

                if (selectedFile == null && !files.isEmpty()) {

                    selectedFile =
                            files.get(0).getAsJsonObject();
                }

                if (selectedFile != null) {

                    downloadUrl =
                            selectedFile
                                    .get("url")
                                    .getAsString();
                }
            }

            if (downloadUrl != null) {

                results.add(
                        new ModpackVersion(
                                getName(),
                                projectId,
                                versionId,
                                name,
                                minecraftVersion,
                                loader,
                                downloadUrl
                        )
                );
            }
        }

        return results;
    }

    private JsonObject getJson(String address) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return JsonParser
                    .parseString(response.toString())
                    .getAsJsonObject();

        } finally {

            connection.disconnect();
        }
    }

    private JsonArray getJsonArray(String address) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            StringBuilder response =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return JsonParser
                    .parseString(response.toString())
                    .getAsJsonArray();

        } finally {

            connection.disconnect();
        }
    }
}