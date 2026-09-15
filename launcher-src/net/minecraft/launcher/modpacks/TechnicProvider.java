package net.minecraft.launcher.modpacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TechnicProvider implements ModpackProvider {

    @Override
    public String getName() {
        return "Technic";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public List<ModpackSearchResult> search(String query) throws Exception {

        List<ModpackSearchResult> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String encodedQuery =
                URLEncoder.encode(
                        query.trim(),
                        StandardCharsets.UTF_8
                );

        int build = getCurrentTechnicBuild();

        System.out.println(
                "[TECHNIC] Using launcher build: " + build
        );

        String endpoint =
                "https://api.technicpack.net/search"
                        + "?q="
                        + encodedQuery
                        + "&build="
                        + build;

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        int responseCode =
                connection.getResponseCode();

        if (
                responseCode < 200
                        || responseCode >= 300
        ) {
            connection.disconnect();

            throw new RuntimeException(
                    "Technic API returned HTTP "
                            + responseCode
            );
        }

        StringBuilder jsonBuilder =
                new StringBuilder();

        try (
                InputStream input =
                        connection.getInputStream();

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

        } finally {
            connection.disconnect();
        }

        JsonObject root =
                JsonParser
                        .parseString(
                                jsonBuilder.toString()
                        )
                        .getAsJsonObject();

        if (
                !root.has("modpacks")
                        || !root.get("modpacks").isJsonArray()
        ) {
            return results;
        }

        for (JsonElement element : root.getAsJsonArray("modpacks")) {

            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject pack = element.getAsJsonObject();

            String slug =
                    getString(
                            pack,
                            "slug",
                            ""
                    );

            if (slug.isEmpty()) {
                continue;
            }

            String name =
                    getString(
                            pack,
                            "name",
                            slug
                    );

            JsonObject details =
                    getPackInfo(
                            slug,
                            build
                    );

            String author =
                    getString(
                            details,
                            "user",
                            ""
                    );

            String displayName =
                    getString(
                            details,
                            "displayName",
                            name
                    );

            String description =
                    getString(
                            details,
                            "description",
                            ""
                    );

            String iconUrl =
                    getNestedUrl(
                            details,
                            "icon"
                    );

            System.out.println(
                    "[TECHNIC] "
                            + displayName
                            + " icon = "
                            + iconUrl
            );

            long downloads =
                    getLong(
                            details,
                            "runs",
                            0L
                    );

            String updated =
                    getString(
                            details,
                            "updated",
                            ""
                    );

            ModpackSearchResult result =
                    new ModpackSearchResult(
                            "Technic",
                            slug,
                            displayName,
                            author,
                            description,
                            iconUrl,
                            downloads,
                            new ArrayList<>(),
                            new ArrayList<>(),
                            updated
                    );

            results.add(result);
        }

        return results;
    }

    @Override
    public List<ModpackVersion> getVersions(String projectId) throws Exception {

        List<ModpackVersion> versions = new ArrayList<>();

        if (projectId == null || projectId.trim().isEmpty()) {
            return versions;
        }

        int build = getCurrentTechnicBuild();

        String encodedSlug =
                URLEncoder.encode(
                        projectId.trim(),
                        StandardCharsets.UTF_8
                );

        String endpoint =
                "https://api.technicpack.net/modpack/"
                        + encodedSlug
                        + "?build="
                        + build;

        System.out.println(
                "[TECHNIC] Loading pack info: " + endpoint
        );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {

            connection.disconnect();

            throw new RuntimeException(
                    "Technic API returned HTTP "
                            + responseCode
            );
        }

        StringBuilder jsonBuilder =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

        } finally {
            connection.disconnect();
        }

        JsonObject pack =
                JsonParser
                        .parseString(
                                jsonBuilder.toString()
                        )
                        .getAsJsonObject();

        System.out.println(
                "[TECHNIC] Pack info: " + pack
        );

        String name =
                getString(
                        pack,
                        "displayName",
                        projectId
                );

        String version =
                getString(
                        pack,
                        "version",
                        "recommended"
                );

        String minecraft =
                getString(
                        pack,
                        "minecraft",
                        ""
                );

        String downloadUrl =
                getString(
                        pack,
                        "url",
                        null
                );

        String solder =
                getString(
                        pack,
                        "solder",
                        ""
                );

        if (solder != null && !solder.trim().isEmpty()) {
            return getSolderVersions(
                    solder,
                    projectId,
                    name
            );
        }

        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            throw new RuntimeException(
                    "Technic pack has no download URL."
            );
        }

        ModpackVersion result =
                new ModpackVersion(
                        "Technic",
                        projectId,
                        version,
                        name + " " + version,
                        minecraft,
                        "",
                        downloadUrl
                );

        versions.add(result);

        return versions;
    }

    private String getString(
            JsonObject object,
            String key,
            String defaultValue
    ) {

        if (
                object.has(key)
                        && !object.get(key).isJsonNull()
        ) {
            try {
                return object
                        .get(key)
                        .getAsString();
            } catch (Exception ignored) {
            }
        }

        return defaultValue;
    }

    private int getCurrentTechnicBuild() throws Exception {

        URL url = new URL(
                "https://api.technicpack.net/launcher/version/stable4"
        );

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        try {
            int responseCode = connection.getResponseCode();

            if (responseCode < 200 || responseCode >= 300) {
                throw new RuntimeException(
                        "Could not get Technic launcher build: HTTP "
                                + responseCode
                );
            }

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

                JsonObject root =
                        JsonParser.parseString(
                                response.toString()
                        ).getAsJsonObject();

                System.out.println(
                        "[TECHNIC] stable4 response: " + root
                );

                if (root.has("build")) {
                    return root.get("build").getAsInt();
                }

                if (root.has("version")) {
                    return root.get("version").getAsInt();
                }

                throw new RuntimeException(
                        "Technic stable4 response contains no build number: "
                                + root
                );
            }

        } finally {
            connection.disconnect();
        }
    }

    public String getSolderBaseUrl(
            String projectId
    ) throws Exception {

        if (
                projectId == null
                        || projectId.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Technic project ID cannot be empty."
            );
        }

        int build =
                getCurrentTechnicBuild();

        String encodedSlug =
                URLEncoder.encode(
                        projectId.trim(),
                        StandardCharsets.UTF_8
                );

        String endpoint =
                "https://api.technicpack.net/modpack/"
                        + encodedSlug
                        + "?build="
                        + build;

        System.out.println(
                "[TECHNIC] Loading Solder URL: "
                        + endpoint
        );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint)
                                .openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        try {

            int responseCode =
                    connection.getResponseCode();

            if (
                    responseCode < 200
                            || responseCode >= 300
            ) {
                throw new RuntimeException(
                        "Technic API returned HTTP "
                                + responseCode
                );
            }

            StringBuilder jsonBuilder =
                    new StringBuilder();

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            connection.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while (
                        (line = reader.readLine())
                                != null
                ) {
                    jsonBuilder.append(line);
                }
            }

            JsonObject pack =
                    JsonParser
                            .parseString(
                                    jsonBuilder.toString()
                            )
                            .getAsJsonObject();

            String solder =
                    getString(
                            pack,
                            "solder",
                            null
                    );

            if (
                    solder == null
                            || solder.trim().isEmpty()
            ) {
                throw new RuntimeException(
                        "Technic modpack does not use Solder."
                );
            }

            return solder;

        } finally {
            connection.disconnect();
        }
    }

    private List<ModpackVersion> getSolderVersions(
            String solderBaseUrl,
            String projectId,
            String displayName
    ) throws Exception {

        List<ModpackVersion> versions = new ArrayList<>();

        String base =
                solderBaseUrl.endsWith("/")
                        ? solderBaseUrl
                        : solderBaseUrl + "/";

        String endpoint =
                base
                        + "modpack/"
                        + URLEncoder.encode(
                        projectId,
                        StandardCharsets.UTF_8
                );

        System.out.println(
                "[TECHNIC] Loading Solder pack: "
                        + endpoint
        );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();

            throw new RuntimeException(
                    "Solder API returned HTTP "
                            + responseCode
            );
        }

        StringBuilder jsonBuilder =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

        } finally {
            connection.disconnect();
        }

        JsonObject root =
                JsonParser
                        .parseString(
                                jsonBuilder.toString()
                        )
                        .getAsJsonObject();

        System.out.println(
                "[TECHNIC] Solder pack info: "
                        + root
        );

        String recommended =
                getString(
                        root,
                        "recommended",
                        ""
                );

        String latest =
                getString(
                        root,
                        "latest",
                        ""
                );

        if (
                !root.has("builds")
                        || !root.get("builds").isJsonArray()
        ) {
            return versions;
        }

        JsonArray builds = root.getAsJsonArray("builds");

        List<String> buildNames = new ArrayList<>();

        for (JsonElement element : builds) {

            if (element == null || element.isJsonNull()) {
                continue;
            }

            buildNames.add(
                    element.getAsString()
            );
        }

        buildNames.sort(
                (a, b) -> compareTechnicVersions(b, a)
        );

        for (String build : buildNames) {

            String label =
                    displayName
                            + " "
                            + build;

            if (build.equals(recommended)) {
                label += " (Recommended)";
            } else if (build.equals(latest)) {
                label += " (Latest)";
            }

            versions.add(
                    new ModpackVersion(
                            "Technic",
                            projectId,
                            build,
                            label,
                            "",
                            "",
                            null
                    )
            );
        }

        return versions;
    }

    private int compareTechnicVersions(String a, String b) {

        List<String> partsA = splitVersion(a);
        List<String> partsB = splitVersion(b);

        int max = Math.max(partsA.size(), partsB.size());

        for (int i = 0; i < max; i++) {

            String partA =
                    i < partsA.size()
                            ? partsA.get(i)
                            : "";

            String partB =
                    i < partsB.size()
                            ? partsB.get(i)
                            : "";

            boolean numberA =
                    partA.matches("\\d+");

            boolean numberB =
                    partB.matches("\\d+");

            int comparison;

            if (numberA && numberB) {

                comparison =
                        Integer.compare(
                                Integer.parseInt(partA),
                                Integer.parseInt(partB)
                        );

            } else {

                comparison =
                        partA.compareToIgnoreCase(partB);
            }

            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private List<String> splitVersion(String version) {

        List<String> parts = new ArrayList<>();

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile("\\d+|[A-Za-z]+")
                        .matcher(version);

        while (matcher.find()) {
            parts.add(matcher.group());
        }

        return parts;
    }

    public JsonObject getSolderBuildInfo(
            String solderBaseUrl,
            String projectId,
            String build
    ) throws Exception {

        String base =
                solderBaseUrl.endsWith("/")
                        ? solderBaseUrl
                        : solderBaseUrl + "/";

        String endpoint =
                base
                        + "modpack/"
                        + URLEncoder.encode(
                        projectId,
                        StandardCharsets.UTF_8
                )
                        + "/"
                        + URLEncoder.encode(
                        build,
                        StandardCharsets.UTF_8
                );

        System.out.println(
                "[TECHNIC] Loading Solder build: "
                        + endpoint
        );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        int responseCode =
                connection.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            connection.disconnect();

            throw new RuntimeException(
                    "Solder build API returned HTTP "
                            + responseCode
            );
        }

        StringBuilder jsonBuilder =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

        } finally {
            connection.disconnect();
        }

        JsonObject root =
                JsonParser
                        .parseString(
                                jsonBuilder.toString()
                        )
                        .getAsJsonObject();

        System.out.println(
                "[TECHNIC] Solder build info: "
                        + root
        );

        return root;
    }

    private JsonObject getPackInfo(
            String slug,
            int build
    ) throws Exception {

        String endpoint =
                "https://api.technicpack.net/modpack/"
                        + URLEncoder.encode(
                        slug,
                        StandardCharsets.UTF_8
                )
                        + "?build="
                        + build;

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        int responseCode =
                connection.getResponseCode();

        if (
                responseCode < 200
                        || responseCode >= 300
        ) {
            connection.disconnect();

            throw new RuntimeException(
                    "Technic API returned HTTP "
                            + responseCode
            );
        }

        StringBuilder json =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

        } finally {
            connection.disconnect();
        }

        return JsonParser
                .parseString(json.toString())
                .getAsJsonObject();
    }

    private String getNestedUrl(
            JsonObject object,
            String key
    ) {

        if (
                object == null
                        || !object.has(key)
                        || !object.get(key).isJsonObject()
        ) {
            return null;
        }

        JsonObject nested =
                object.getAsJsonObject(key);

        return getString(
                nested,
                "url",
                null
        );
    }

    private long getLong(
            JsonObject object,
            String key,
            long defaultValue
    ) {

        if (
                object != null
                        && object.has(key)
                        && !object.get(key).isJsonNull()
        ) {
            try {
                return object
                        .get(key)
                        .getAsLong();
            } catch (Exception ignored) {
            }
        }

        return defaultValue;
    }
}