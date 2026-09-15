package net.minecraft.launcher.modpacks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CurseForgeApiClient {

    private static final String API_BASE =
            "https://api.curseforge.com/v1";

    private final Gson gson = new Gson();
    private final String apiKey;

    public CurseForgeApiClient(String apiKey) {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "CurseForge API key was not configured."
            );
        }

        this.apiKey = apiKey.trim();
    }

    public static CurseForgeApiClient fromEnvironment() {

        String apiKey =
                System.getenv("CURSEFORGE_API_KEY");

        System.out.println(
                "[CURSEFORGE API] Key present: "
                        + (apiKey != null && !apiKey.trim().isEmpty())
        );

        if (apiKey != null) {
            System.out.println(
                    "[CURSEFORGE API] Key length: "
                            + apiKey.trim().length()
            );
        }

        return new CurseForgeApiClient(apiKey);
    }

    public CurseForgeFile getFile(
            long projectId,
            long fileId
    ) throws IOException {

        JsonObject response =
                getJson(
                        "/mods/"
                                + projectId
                                + "/files/"
                                + fileId
                );

        JsonObject data =
                response.getAsJsonObject("data");

        if (data == null) {
            throw new IOException(
                    "CurseForge returned no file data for "
                            + projectId
                            + "/"
                            + fileId
            );
        }

        return gson.fromJson(
                data,
                CurseForgeFile.class
        );
    }

    public String getDownloadUrl(
            long projectId,
            long fileId
    ) throws IOException {

        JsonObject response =
                getJson(
                        "/mods/"
                                + projectId
                                + "/files/"
                                + fileId
                                + "/download-url"
                );

        if (
                !response.has("data")
                        || response.get("data").isJsonNull()
        ) {
            return null;
        }

        return response
                .get("data")
                .getAsString();
    }

    public CurseForgeProject getProject(
            long projectId
    ) throws IOException {

        JsonObject response =
                getJson(
                        "/mods/" + projectId
                );

        JsonObject data =
                response.getAsJsonObject("data");

        if (data == null) {
            throw new IOException(
                    "CurseForge returned no project data for "
                            + projectId
            );
        }

        return gson.fromJson(
                data,
                CurseForgeProject.class
        );
    }

    private JsonObject getJson(
            String path
    ) throws IOException {

        HttpURLConnection connection = null;

        try {

            URL url =
                    new URL(API_BASE + path);

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("GET");

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "x-api-key",
                    apiKey
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "Guedes-Legacy-Launcher/1.6.94"
            );

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            int status =
                    connection.getResponseCode();

            InputStream input =
                    status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String body = readAll(input);

            if (status < 200 || status >= 300) {

                throw new IOException(
                        "CurseForge API returned HTTP "
                                + status
                                + ": "
                                + body
                );
            }

            return gson.fromJson(
                    body,
                    JsonObject.class
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(
            InputStream input
    ) throws IOException {

        if (input == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        try (
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
                result.append(line);
            }
        }

        return result.toString();
    }
}