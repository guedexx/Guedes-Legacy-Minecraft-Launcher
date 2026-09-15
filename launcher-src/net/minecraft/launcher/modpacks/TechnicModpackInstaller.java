package net.minecraft.launcher.modpacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TechnicModpackInstaller {

    public interface ProgressListener {
        void onProgress(
                String message,
                int current,
                int total
        );
    }

    public static class InstallResult {

        private final File instanceDirectory;
        private final String minecraftVersion;
        private final String forgeVersion;

        public InstallResult(
                File instanceDirectory,
                String minecraftVersion,
                String forgeVersion
        ) {
            this.instanceDirectory = instanceDirectory;
            this.minecraftVersion = minecraftVersion;
            this.forgeVersion = forgeVersion;
        }

        public File getInstanceDirectory() {
            return instanceDirectory;
        }

        public String getMinecraftVersion() {
            return minecraftVersion;
        }

        public String getForgeVersion() {
            return forgeVersion;
        }
    }

    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;

    public void cancel() {

        cancelled = true;

        HttpURLConnection connection =
                activeConnection;

        if (connection != null) {
            connection.disconnect();
        }
    }

    public InstallResult install(
            File minecraftDirectory,
            String packName,
            JsonObject buildInfo,
            ProgressListener listener
    ) throws Exception {

        checkCancelled();

        String minecraftVersion =
                getString(
                        buildInfo,
                        "minecraft"
                );

        String forgeVersion =
                getNullableString(
                        buildInfo,
                        "forge"
                );

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            throw new IllegalStateException(
                    "Solder build does not specify Minecraft version."
            );
        }

        JsonArray mods =
                buildInfo.getAsJsonArray("mods");

        if (mods == null) {
            throw new IllegalStateException(
                    "Solder build does not contain a mods array."
            );
        }

        File instancesDirectory =
                new File(
                        minecraftDirectory,
                        "launcher_instances"
                );

        if (
                !instancesDirectory.exists()
                        && !instancesDirectory.mkdirs()
        ) {
            throw new IllegalStateException(
                    "Could not create instances directory."
            );
        }

        File instanceDirectory =
                createUniqueInstanceDirectory(
                        instancesDirectory,
                        packName
                );

        if (!instanceDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create instance directory."
            );
        }

        File tempDirectory =
                new File(
                        instanceDirectory,
                        ".technic-downloads"
                );

        if (!tempDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create temporary download directory."
            );
        }

        try {

            int total =
                    mods.size();

            int current =
                    0;

            for (JsonElement element : mods) {

                checkCancelled();

                if (
                        element == null
                                || element.isJsonNull()
                                || !element.isJsonObject()
                ) {
                    continue;
                }

                JsonObject mod =
                        element.getAsJsonObject();

                String name =
                        getString(
                                mod,
                                "name"
                        );

                String version =
                        getString(
                                mod,
                                "version"
                        );

                String url =
                        getString(
                                mod,
                                "url"
                        );

                String md5 =
                        getString(
                                mod,
                                "md5"
                        );

                if (
                        url == null
                                || url.trim().isEmpty()
                ) {
                    throw new IllegalStateException(
                            "Solder module "
                                    + name
                                    + " has no download URL."
                    );
                }

                current++;

                if (listener != null) {

                    listener.onProgress(
                            "Downloading "
                                    + name
                                    + " "
                                    + version
                                    + "...",
                            current,
                            total
                    );
                }

                File moduleZip =
                        new File(
                                tempDirectory,
                                current
                                        + "-"
                                        + sanitizeFileName(name)
                                        + ".zip"
                        );

                download(
                        url,
                        moduleZip
                );

                checkCancelled();

                if (
                        md5 != null
                                && !md5.trim().isEmpty()
                ) {

                    String actualMd5 =
                            calculateMd5(
                                    moduleZip
                            );

                    if (
                            !md5.equalsIgnoreCase(
                                    actualMd5
                            )
                    ) {
                        throw new IllegalStateException(
                                "MD5 mismatch for module "
                                        + name
                                        + ". Expected "
                                        + md5
                                        + ", got "
                                        + actualMd5
                        );
                    }
                }

                if (listener != null) {

                    listener.onProgress(
                            "Installing "
                                    + name
                                    + "...",
                            current,
                            total
                    );
                }

                extractZipSafely(
                        moduleZip,
                        instanceDirectory
                );
            }

            deleteRecursively(
                    tempDirectory
            );

            return new InstallResult(
                    instanceDirectory,
                    minecraftVersion,
                    forgeVersion
            );

        } catch (Exception ex) {

            deleteRecursively(
                    instanceDirectory
            );

            throw ex;
        }
    }

    private void download(
            String urlString,
            File destination
    ) throws Exception {

        checkCancelled();

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(
                                urlString
                        ).openConnection();

        activeConnection =
                connection;

        connection.setRequestMethod("GET");

        connection.setConnectTimeout(
                15000
        );

        connection.setReadTimeout(
                60000
        );

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

            activeConnection =
                    null;

            throw new IllegalStateException(
                    "Download returned HTTP "
                            + responseCode
                            + ": "
                            + urlString
            );
        }

        try (
                InputStream input =
                        new BufferedInputStream(
                                connection.getInputStream()
                        );

                BufferedOutputStream output =
                        new BufferedOutputStream(
                                new FileOutputStream(
                                        destination
                                )
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while (
                    (read = input.read(buffer))
                            != -1
            ) {

                checkCancelled();

                output.write(
                        buffer,
                        0,
                        read
                );
            }

        } finally {

            connection.disconnect();

            activeConnection =
                    null;
        }
    }

    private void extractZipSafely(
            File zipFile,
            File destinationDirectory
    ) throws Exception {

        String destinationPath =
                destinationDirectory
                        .getCanonicalPath()
                        + File.separator;

        try (
                ZipInputStream zip =
                        new ZipInputStream(
                                new BufferedInputStream(
                                        new FileInputStream(
                                                zipFile
                                        )
                                )
                        )
        ) {

            ZipEntry entry;

            while (
                    (entry = zip.getNextEntry())
                            != null
            ) {

                checkCancelled();

                File output =
                        new File(
                                destinationDirectory,
                                entry.getName()
                        );

                String outputPath =
                        output.getCanonicalPath();

                if (
                        !outputPath.startsWith(
                                destinationPath
                        )
                ) {
                    throw new SecurityException(
                            "Unsafe ZIP entry: "
                                    + entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    if (
                            !output.exists()
                                    && !output.mkdirs()
                    ) {
                        throw new IllegalStateException(
                                "Could not create directory: "
                                        + output
                        );
                    }

                    continue;
                }

                File parent =
                        output.getParentFile();

                if (
                        parent != null
                                && !parent.exists()
                                && !parent.mkdirs()
                ) {
                    throw new IllegalStateException(
                            "Could not create directory: "
                                    + parent
                    );
                }

                try (
                        BufferedOutputStream fileOutput =
                                new BufferedOutputStream(
                                        new FileOutputStream(
                                                output
                                        )
                                )
                ) {

                    byte[] buffer =
                            new byte[8192];

                    int read;

                    while (
                            (read = zip.read(buffer))
                                    != -1
                    ) {

                        checkCancelled();

                        fileOutput.write(
                                buffer,
                                0,
                                read
                        );
                    }
                }

                zip.closeEntry();
            }
        }
    }

    private String calculateMd5(
            File file
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "MD5"
                );

        try (
                InputStream input =
                        new BufferedInputStream(
                                new FileInputStream(
                                        file
                                )
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while (
                    (read = input.read(buffer))
                            != -1
            ) {

                checkCancelled();

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        byte[] hash =
                digest.digest();

        StringBuilder result =
                new StringBuilder();

        for (byte value : hash) {

            result.append(
                    String.format(
                            Locale.ROOT,
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    private File createUniqueInstanceDirectory(
            File parent,
            String name
    ) {

        String baseName =
                sanitizeFileName(
                        name
                );

        File candidate =
                new File(
                        parent,
                        baseName
                );

        int number =
                2;

        while (candidate.exists()) {

            candidate =
                    new File(
                            parent,
                            baseName
                                    + "-"
                                    + number
                    );

            number++;
        }

        return candidate;
    }

    private String sanitizeFileName(
            String value
    ) {

        if (
                value == null
                        || value.trim().isEmpty()
        ) {
            return "technic-pack";
        }

        return value
                .replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                )
                .trim();
    }

    private String getString(
            JsonObject object,
            String key
    ) {

        if (
                object == null
                        || !object.has(key)
                        || object.get(key).isJsonNull()
        ) {
            return null;
        }

        return object
                .get(key)
                .getAsString();
    }

    private String getNullableString(
            JsonObject object,
            String key
    ) {

        return getString(
                object,
                key
        );
    }

    private void checkCancelled()
            throws InterruptedException {

        if (
                cancelled
                        || Thread.currentThread()
                        .isInterrupted()
        ) {

            throw new InterruptedException(
                    "Installation cancelled"
            );
        }
    }

    private void deleteRecursively(
            File file
    ) {

        if (
                file == null
                        || !file.exists()
        ) {
            return;
        }

        if (file.isDirectory()) {

            File[] children =
                    file.listFiles();

            if (children != null) {

                for (File child : children) {
                    deleteRecursively(
                            child
                    );
                }
            }
        }

        try {
            Files.deleteIfExists(
                    file.toPath()
            );
        } catch (Exception ignored) {
        }
    }

    public InstallResult installDirect(
            File minecraftDirectory,
            String packName,
            ModpackVersion version,
            ProgressListener listener
    ) throws Exception {

        if (
                version.getDownloadUrl() == null
                        || version.getDownloadUrl().trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Technic pack has no direct download URL."
            );
        }

        File instancesDirectory =
                new File(
                        minecraftDirectory,
                        "launcher_instances"
                );

        if (
                !instancesDirectory.exists()
                        && !instancesDirectory.mkdirs()
        ) {
            throw new IOException(
                    "Could not create instances directory: "
                            + instancesDirectory
            );
        }

        File instanceDirectory =
                createUniqueInstanceDirectory(
                        instancesDirectory,
                        sanitizeFileName(packName)
                );

        if (!instanceDirectory.mkdirs()) {
            throw new IOException(
                    "Could not create instance directory: "
                            + instanceDirectory
            );
        }

        File tempZip =
                File.createTempFile(
                        "guedes-technic-",
                        ".zip"
                );

        try {

            notify(
                    listener,
                    "Downloading Technic modpack...",
                    0,
                    1
            );

            downloadToFile(
                    version.getDownloadUrl(),
                    tempZip
            );

            notify(
                    listener,
                    "Extracting Technic modpack...",
                    0,
                    1
            );

            extractZipSafely(
                    tempZip,
                    instanceDirectory
            );

            String minecraftVersion =
                    version.getMinecraftVersion();

            if (
                    minecraftVersion == null
                            || minecraftVersion.trim().isEmpty()
            ) {
                throw new IOException(
                        "Technic API did not provide the Minecraft version "
                                + "for this modpack."
                );
            }

            if (
                    minecraftVersion == null
                            || minecraftVersion.trim().isEmpty()
            ) {
                throw new IOException(
                        "Could not determine Minecraft version "
                                + "for Technic modpack."
                );
            }

            notify(
                    listener,
                    "Technic modpack installed.",
                    1,
                    1
            );

            return new InstallResult(
                    instanceDirectory,
                    minecraftVersion,
                    null
            );

        } catch (Exception e) {

            deleteRecursively(
                    instanceDirectory
            );

            throw e;

        } finally {

            if (tempZip.exists()) {
                tempZip.delete();
            }
        }
    }

    private void notify(
            ProgressListener listener,
            String message,
            int current,
            int total
    ) {
        if (listener != null) {
            listener.onProgress(
                    message,
                    current,
                    total
            );
        }
    }

    private void downloadToFile(
            String url,
            File destination
    ) throws IOException {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(url).openConnection();

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);

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
                        "Technic download returned HTTP "
                                + responseCode
                );
            }

            try (
                    InputStream input =
                            new BufferedInputStream(
                                    connection.getInputStream()
                            );

                    FileOutputStream output =
                            new FileOutputStream(destination)
            ) {

                byte[] buffer =
                        new byte[8192];

                int read;

                while (
                        (read = input.read(buffer))
                                != -1
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
}