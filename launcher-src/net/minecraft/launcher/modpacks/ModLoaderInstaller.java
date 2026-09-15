package net.minecraft.launcher.modpacks;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ModLoaderInstaller {

    public interface ProgressListener {
        void onProgress(
                String message,
                int current,
                int total
        );
    }

    public String install(
            File minecraftDirectory,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            ProgressListener listener
    ) throws Exception {

        if (
                loader == null
                        || loader.trim().isEmpty()
        ) {
            return minecraftVersion;
        }

        switch (loader.toLowerCase()) {

            case "fabric":
                return installFabric(
                        minecraftDirectory,
                        minecraftVersion,
                        loaderVersion,
                        listener
                );

            case "forge":
                return installForge(
                        minecraftDirectory,
                        minecraftVersion,
                        loaderVersion,
                        listener
                );

            case "neoforge":
                return installNeoForge(
                        minecraftDirectory,
                        minecraftVersion,
                        loaderVersion,
                        listener
                );

            case "quilt":
                return installQuilt(
                        minecraftDirectory,
                        minecraftVersion,
                        loaderVersion,
                        listener
                );

            default:
                throw new IllegalArgumentException(
                        "Unsupported mod loader: " + loader
                );
        }
    }

    private String installNeoForge(
            File minecraftDirectory,
            String minecraftVersion,
            String loaderVersion,
            ProgressListener listener
    ) throws Exception {

        if (loaderVersion == null || loaderVersion.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "NeoForge version was not specified."
            );
        }

        listener.onProgress(
                "Downloading NeoForge installer...",
                0,
                0
        );

        String installerUrl =
                "https://maven.neoforged.net/releases/"
                        + "net/neoforged/neoforge/"
                        + loaderVersion
                        + "/neoforge-"
                        + loaderVersion
                        + "-installer.jar";

        File tempInstaller =
                File.createTempFile(
                        "neoforge-" + loaderVersion + "-",
                        "-installer.jar"
                );

        try {

            downloadFile(
                    installerUrl,
                    tempInstaller
            );

            listener.onProgress(
                    "Installing NeoForge...",
                    0,
                    0
            );

            String javaExecutable =
                    System.getProperty("java.home")
                            + File.separator
                            + "bin"
                            + File.separator
                            + (isWindows() ? "java.exe" : "java");

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            javaExecutable,
                            "-jar",
                            tempInstaller.getAbsolutePath(),
                            "--installClient",
                            minecraftDirectory.getAbsolutePath()
                    );

            processBuilder.redirectErrorStream(true);

            Process process =
                    processBuilder.start();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(
                        "[NeoForge Installer] " + line
                );
            }

            int exitCode =
                    process.waitFor();

            if (exitCode != 0) {
                throw new IOException(
                        "NeoForge installer exited with code "
                                + exitCode
                );
            }

            listener.onProgress(
                    "Detecting NeoForge version...",
                    0,
                    0
            );

            String versionId =
                    findNeoForgeVersionId(
                            minecraftDirectory,
                            loaderVersion
                    );

            if (versionId == null) {
                throw new IOException(
                        "NeoForge installation finished, "
                                + "but no installed version was found."
                );
            }

            return versionId;

        } finally {

            if (tempInstaller.exists()) {
                tempInstaller.delete();
            }
        }
    }

    private String installFabric(
            File minecraftDirectory,
            String minecraftVersion,
            String loaderVersion,
            ProgressListener listener
    ) throws Exception {

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Missing Minecraft version."
            );
        }

        if (
                loaderVersion == null
                        || loaderVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Missing Fabric Loader version."
            );
        }

        if (listener != null) {
            listener.onProgress(
                    "Downloading Fabric Loader profile...",
                    0,
                    2
            );
        }

        String endpoint =
                "https://meta.fabricmc.net/v2/versions/loader/"
                        + encodePath(minecraftVersion)
                        + "/"
                        + encodePath(loaderVersion)
                        + "/profile/json";

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

        int responseCode =
                connection.getResponseCode();

        if (
                responseCode < 200
                        || responseCode >= 300
        ) {

            connection.disconnect();

            throw new RuntimeException(
                    "Fabric Meta returned HTTP "
                            + responseCode
            );
        }

        String json;

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

            StringBuilder builder =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {
                builder
                        .append(line)
                        .append('\n');
            }

            json = builder.toString();

        } finally {
            connection.disconnect();
        }

        JsonObject profile =
                new JsonParser()
                        .parse(json)
                        .getAsJsonObject();

        if (
                !profile.has("id")
                        || profile
                        .get("id")
                        .getAsString()
                        .trim()
                        .isEmpty()
        ) {
            throw new RuntimeException(
                    "Fabric profile does not contain a version id."
            );
        }

        String versionId =
                profile
                        .get("id")
                        .getAsString();

        if (listener != null) {
            listener.onProgress(
                    "Installing Fabric Loader...",
                    1,
                    2
            );
        }

        File versionsDirectory =
                new File(
                        minecraftDirectory,
                        "versions"
                );

        File versionDirectory =
                new File(
                        versionsDirectory,
                        versionId
                );

        if (
                !versionDirectory.exists()
                        && !versionDirectory.mkdirs()
        ) {
            throw new RuntimeException(
                    "Could not create version directory: "
                            + versionDirectory
                            .getAbsolutePath()
            );
        }

        File versionJson =
                new File(
                        versionDirectory,
                        versionId + ".json"
                );

        Files.write(
                versionJson.toPath(),
                json.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        if (listener != null) {
            listener.onProgress(
                    "Fabric Loader installed.",
                    2,
                    2
            );
        }

        return versionId;
    }

    private String installForge(
            File minecraftDirectory,
            String minecraftVersion,
            String loaderVersion,
            ProgressListener listener
    ) throws Exception {

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Minecraft version is required for Forge"
            );
        }

        if (
                loaderVersion == null
                        || loaderVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Forge version is required"
            );
        }

        String forgeVersion =
                minecraftVersion
                        + "-"
                        + loaderVersion;

        String installerUrl =
                "https://maven.minecraftforge.net/"
                        + "net/minecraftforge/forge/"
                        + forgeVersion
                        + "/forge-"
                        + forgeVersion
                        + "-installer.jar";

        if (listener != null) {
            listener.onProgress(
                    "Downloading Forge installer...",
                    0,
                    0
            );
        }

        File tempInstaller =
                File.createTempFile(
                        "forge-installer-",
                        ".jar"
                );

        try {

            downloadFile(
                    installerUrl,
                    tempInstaller
            );

            if (listener != null) {
                listener.onProgress(
                        "Installing Forge...",
                        0,
                        0
                );
            }

            ForgeInstallRun firstRun =
                    runForgeInstaller(
                            tempInstaller,
                            minecraftDirectory
                    );

            if (firstRun.getExitCode() != 0) {

                List<String> failedLibraries =
                        findFailedForgeLibraries(
                                firstRun.getOutput()
                        );

                if (!failedLibraries.isEmpty()) {

                    System.out.println(
                            "[FORGE] "
                                    + failedLibraries.size()
                                    + " biblioteca(s) falharam."
                    );

                    boolean downloadedSomething =
                            false;

                    for (String coordinate : failedLibraries) {

                        if (
                                ensureMavenLibrary(
                                        minecraftDirectory,
                                        coordinate
                                )
                        ) {
                            downloadedSomething = true;
                        }
                    }

                    if (downloadedSomething) {

                        if (listener != null) {
                            listener.onProgress(
                                    "Retrying Forge installation...",
                                    0,
                                    0
                            );
                        }

                        System.out.println(
                                "[FORGE] Tentando instalação novamente..."
                        );

                        ForgeInstallRun secondRun =
                                runForgeInstaller(
                                        tempInstaller,
                                        minecraftDirectory
                                );

                        if (secondRun.getExitCode() != 0) {

                            throw new IOException(
                                    "Forge installer exited with code "
                                            + secondRun.getExitCode()
                            );
                        }

                    } else {

                        throw new IOException(
                                "Forge installer exited with code "
                                        + firstRun.getExitCode()
                                        + " and its failed libraries "
                                        + "could not be recovered"
                        );
                    }

                } else {

                    throw new IOException(
                            "Forge installer exited with code "
                                    + firstRun.getExitCode()
                    );
                }
            }

            if (listener != null) {
                listener.onProgress(
                        "Detecting Forge version...",
                        0,
                        0
                );
            }

            String installedVersion =
                    findInstalledForgeVersion(
                            minecraftDirectory,
                            minecraftVersion,
                            loaderVersion
                    );

            if (installedVersion == null) {

                throw new IOException(
                        "Forge installation finished, "
                                + "but no installed Forge version was found"
                );
            }

            return installedVersion;

        } finally {

            if (
                    tempInstaller.exists()
            ) {
                tempInstaller.delete();
            }
        }
    }

    private String installQuilt(
            File minecraftDirectory,
            String minecraftVersion,
            String loaderVersion,
            ProgressListener listener
    ) throws Exception {

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Missing Minecraft version."
            );
        }

        if (
                loaderVersion == null
                        || loaderVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Missing Quilt Loader version."
            );
        }

        if (listener != null) {
            listener.onProgress(
                    "Downloading Quilt Loader profile...",
                    0,
                    2
            );
        }

        String endpoint =
                "https://meta.quiltmc.org/v3/versions/loader/"
                        + encodePath(minecraftVersion)
                        + "/"
                        + encodePath(loaderVersion)
                        + "/profile/json";

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

        int responseCode =
                connection.getResponseCode();

        if (
                responseCode < 200
                        || responseCode >= 300
        ) {

            connection.disconnect();

            throw new RuntimeException(
                    "Quilt Meta returned HTTP "
                            + responseCode
            );
        }

        String json;

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

            StringBuilder builder =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                builder
                        .append(line)
                        .append('\n');
            }

            json =
                    builder.toString();

        } finally {

            connection.disconnect();
        }

        JsonObject profile =
                new JsonParser()
                        .parse(json)
                        .getAsJsonObject();

        if (
                !profile.has("id")
                        || profile
                        .get("id")
                        .getAsString()
                        .trim()
                        .isEmpty()
        ) {

            throw new RuntimeException(
                    "Quilt profile does not contain a version id."
            );
        }

        String versionId =
                profile
                        .get("id")
                        .getAsString();

        if (listener != null) {
            listener.onProgress(
                    "Installing Quilt Loader...",
                    1,
                    2
            );
        }

        File versionsDirectory =
                new File(
                        minecraftDirectory,
                        "versions"
                );

        File versionDirectory =
                new File(
                        versionsDirectory,
                        versionId
                );

        if (
                !versionDirectory.exists()
                        && !versionDirectory.mkdirs()
        ) {

            throw new RuntimeException(
                    "Could not create Quilt version directory: "
                            + versionDirectory.getAbsolutePath()
            );
        }

        File versionJson =
                new File(
                        versionDirectory,
                        versionId + ".json"
                );

        Files.write(
                versionJson.toPath(),
                json.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        if (listener != null) {
            listener.onProgress(
                    "Quilt Loader installed.",
                    2,
                    2
            );
        }

        return versionId;
    }

    private String findInstalledForgeVersion(
            File minecraftDirectory,
            String minecraftVersion,
            String loaderVersion
    ) {

        File versionsDirectory =
                new File(
                        minecraftDirectory,
                        "versions"
                );

        if (!versionsDirectory.isDirectory()) {
            return null;
        }

        File[] children =
                versionsDirectory.listFiles();

        if (children == null) {
            return null;
        }

        String expectedPart =
                minecraftVersion
                        + "-forge-"
                        + loaderVersion;

        for (File child : children) {

            if (!child.isDirectory()) {
                continue;
            }

            String name =
                    child.getName();

            if (
                    name.equalsIgnoreCase(
                            expectedPart
                    )
                            || (
                            name.toLowerCase()
                                    .contains("forge")
                                    && name.contains(
                                    minecraftVersion
                            )
                                    && name.contains(
                                    loaderVersion
                            )
                    )
            ) {

                File json =
                        new File(
                                child,
                                name + ".json"
                        );

                if (json.isFile()) {
                    return name;
                }
            }
        }

        return null;
    }

    private boolean isWindows() {

        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win");
    }

    private String findNeoForgeVersionId(
            File minecraftDirectory,
            String loaderVersion
    ) {

        File versionsDirectory =
                new File(
                        minecraftDirectory,
                        "versions"
                );

        File[] directories =
                versionsDirectory.listFiles();

        if (directories == null) {
            return null;
        }

        String bestMatch = null;

        for (File directory : directories) {

            if (!directory.isDirectory()) {
                continue;
            }

            String name =
                    directory.getName();

            String lower =
                    name.toLowerCase();

            if (
                    lower.contains("neoforge")
                            && lower.contains(
                            loaderVersion.toLowerCase()
                    )
            ) {

                File json =
                        new File(
                                directory,
                                name + ".json"
                        );

                if (json.isFile()) {
                    bestMatch = name;
                }
            }
        }

        return bestMatch;
    }

    private void downloadFile(
            String url,
            File destination
    ) throws IOException {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(url).openConnection();

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
                throw new IOException(
                        "HTTP "
                                + responseCode
                                + " while downloading "
                                + url
                );
            }

            try (
                    java.io.InputStream input =
                            connection.getInputStream();

                    java.io.FileOutputStream output =
                            new java.io.FileOutputStream(
                                    destination
                            )
            ) {

                byte[] buffer =
                        new byte[8192];

                int read;

                while (
                        (read = input.read(buffer)) != -1
                ) {
                    output.write(
                            buffer,
                            0,
                            read
                    );
                }
            }

        } finally {

            connection.disconnect();
        }
    }

    private boolean ensureMavenLibrary(
            File minecraftDirectory,
            String coordinate
    ) {

        try {

            String[] parts =
                    coordinate.split(":");

            if (parts.length != 3) {
                return false;
            }

            String group =
                    parts[0];

            String artifact =
                    parts[1];

            String version =
                    parts[2];

            String groupPath =
                    group.replace('.', '/');

            File target =
                    new File(
                            minecraftDirectory,
                            "libraries/"
                                    + groupPath
                                    + "/"
                                    + artifact
                                    + "/"
                                    + version
                                    + "/"
                                    + artifact
                                    + "-"
                                    + version
                                    + ".jar"
                    );

            if (
                    target.isFile()
                            && target.length() > 0
            ) {
                return true;
            }

            File parent =
                    target.getParentFile();

            if (
                    !parent.isDirectory()
                            && !parent.mkdirs()
            ) {
                return false;
            }

            /*
             * Primeiro Maven Central oficial.
             */
            String[] repositories =
                    new String[] {
                            "https://repo.maven.apache.org/maven2/",
                            "https://repo1.maven.org/maven2/"
                    };

            for (String repository : repositories) {

                String url =
                        repository
                                + groupPath
                                + "/"
                                + artifact
                                + "/"
                                + version
                                + "/"
                                + artifact
                                + "-"
                                + version
                                + ".jar";

                try {

                    System.out.println(
                            "[FORGE] Tentando fallback Maven: "
                                    + coordinate
                    );

                    System.out.println(
                            "[FORGE] " + url
                    );

                    downloadFile(
                            url,
                            target
                    );

                    if (
                            target.isFile()
                                    && target.length() > 0
                    ) {

                        System.out.println(
                                "[FORGE] Fallback baixado: "
                                        + coordinate
                        );

                        return true;
                    }

                } catch (Exception exception) {

                    if (target.exists()) {
                        target.delete();
                    }

                    System.out.println(
                            "[FORGE] Repositório falhou para "
                                    + coordinate
                                    + ": "
                                    + exception.getMessage()
                    );
                }
            }

        } catch (Exception exception) {

            System.out.println(
                    "[FORGE] Não foi possível resolver "
                            + coordinate
                            + ": "
                            + exception.getMessage()
            );
        }

        return false;
    }

    private ForgeInstallRun runForgeInstaller(
            File installer,
            File minecraftDirectory
    ) throws Exception {

        String javaExecutable =
                new File(
                        System.getProperty("java.home"),
                        "bin/java.exe"
                ).getAbsolutePath();

        if (!new File(javaExecutable).isFile()) {
            javaExecutable =
                    new File(
                            System.getProperty("java.home"),
                            "bin/java"
                    ).getAbsolutePath();
        }

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        javaExecutable,
                        "-jar",
                        installer.getAbsolutePath(),
                        "--installClient",
                        minecraftDirectory.getAbsolutePath()
                );

        processBuilder.redirectErrorStream(true);

        Process process =
                processBuilder.start();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                process.getInputStream()
                        )
                );

        List<String> output =
                new ArrayList<String>();

        String line;

        while ((line = reader.readLine()) != null) {

            output.add(line);

            System.out.println(
                    "[FORGE] " + line
            );
        }

        int exitCode =
                process.waitFor();

        return new ForgeInstallRun(
                exitCode,
                output
        );
    }

    private List<String> findFailedForgeLibraries(
            List<String> output
    ) {

        List<String> result =
                new ArrayList<String>();

        boolean readingFailures =
                false;

        for (String line : output) {

            if (line == null) {
                continue;
            }

            String trimmed =
                    line.trim();

            if (
                    trimmed.contains(
                            "These libraries failed to download"
                    )
            ) {
                readingFailures = true;
                continue;
            }

            if (!readingFailures) {
                continue;
            }

            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts =
                    trimmed.split(":");

            if (parts.length == 3) {

                if (
                        !parts[0].isEmpty()
                                && !parts[1].isEmpty()
                                && !parts[2].isEmpty()
                ) {
                    result.add(trimmed);
                }
            }
        }

        return result;
    }

    private String encodePath(
            String value
    ) {

        return value
                .replace(
                        "%",
                        "%25"
                )
                .replace(
                        " ",
                        "%20"
                )
                .replace(
                        "/",
                        "%2F"
                )
                .replace(
                        "#",
                        "%23"
                );
    }

    private static final class ForgeInstallRun {

        private final int exitCode;
        private final List<String> output;

        private ForgeInstallRun(
                int exitCode,
                List<String> output
        ) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getExitCode() {
            return exitCode;
        }

        public List<String> getOutput() {
            return output;
        }
    }
}