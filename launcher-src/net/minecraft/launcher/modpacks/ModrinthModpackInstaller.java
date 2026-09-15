package net.minecraft.launcher.modpacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.security.MessageDigest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModrinthModpackInstaller {

    private volatile boolean cancelled = false;

    private volatile HttpURLConnection activeConnection;

    public interface ProgressListener {
        void onProgress(
                String message,
                int current,
                int total
        );
    }

    public void cancel() {

        cancelled = true;

        HttpURLConnection connection =
                activeConnection;

        if (connection != null) {
            connection.disconnect();
        }
    }

    private void checkCancelled()
            throws InterruptedException {

        if (
                cancelled
                        || Thread.currentThread().isInterrupted()
        ) {

            throw new InterruptedException(
                    "Installation cancelled"
            );
        }
    }

    public static class InstallResult {

        private final File instanceDirectory;
        private final String minecraftVersion;
        private final String loader;
        private final String loaderVersion;

        public InstallResult(
                File instanceDirectory,
                String minecraftVersion,
                String loader,
                String loaderVersion
        ) {
            this.instanceDirectory = instanceDirectory;
            this.minecraftVersion = minecraftVersion;
            this.loader = loader;
            this.loaderVersion = loaderVersion;
        }

        public File getInstanceDirectory() {
            return instanceDirectory;
        }

        public String getMinecraftVersion() {
            return minecraftVersion;
        }

        public String getLoader() {
            return loader;
        }

        public String getLoaderVersion() {
            return loaderVersion;
        }
    }

    public InstallResult install(
            File minecraftDirectory,
            ModpackSearchResult modpack,
            ModpackVersion version,
            ProgressListener listener
    ) throws Exception {

        File instancesDirectory =
                new File(
                        minecraftDirectory,
                        "launcher_instances"
                );

        if (!instancesDirectory.exists()
                && !instancesDirectory.mkdirs()) {

            throw new IllegalStateException(
                    "Could not create instances directory: "
                            + instancesDirectory
            );
        }

        String folderName =
                sanitizeFileName(
                        modpack.getName()
                );

        File instanceDirectory =
                createUniqueInstanceDirectory(
                        instancesDirectory,
                        folderName
                );

        if (!instanceDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create instance directory: "
                            + instanceDirectory
            );
        }

        File tempMrpack =
                File.createTempFile(
                        "guedes-modrinth-",
                        ".mrpack"
                );

        try {

            notify(
                    listener,
                    "Downloading modpack...",
                    0,
                    1
            );

            downloadToFile(
                    version.getDownloadUrl(),
                    tempMrpack
            );

            notify(
                    listener,
                    "Reading modpack index...",
                    0,
                    1
            );

            try (
                    ZipFile zip =
                            new ZipFile(tempMrpack)
            ) {

                ZipEntry indexEntry =
                        zip.getEntry(
                                "modrinth.index.json"
                        );

                if (indexEntry == null) {
                    throw new IllegalStateException(
                            "This file does not contain modrinth.index.json"
                    );
                }

                JsonObject index;

                try (
                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(
                                                zip.getInputStream(
                                                        indexEntry
                                                ),
                                                StandardCharsets.UTF_8
                                        )
                                )
                ) {

                    index =
                            JsonParser
                                    .parseReader(reader)
                                    .getAsJsonObject();
                }

                validateIndex(index);

                JsonObject dependencies =
                        index.getAsJsonObject(
                                "dependencies"
                        );

                String minecraftVersion =
                        getString(
                                dependencies,
                                "minecraft"
                        );

                String loader = null;
                String loaderVersion = null;

                if (dependencies.has("neoforge")) {

                    loader = "neoforge";
                    loaderVersion =
                            dependencies
                                    .get("neoforge")
                                    .getAsString();

                } else if (
                        dependencies.has("forge")
                ) {

                    loader = "forge";
                    loaderVersion =
                            dependencies
                                    .get("forge")
                                    .getAsString();

                } else if (
                        dependencies.has("fabric-loader")
                ) {

                    loader = "fabric";
                    loaderVersion =
                            dependencies
                                    .get("fabric-loader")
                                    .getAsString();

                } else if (
                        dependencies.has("quilt-loader")
                ) {

                    loader = "quilt";
                    loaderVersion =
                            dependencies
                                    .get("quilt-loader")
                                    .getAsString();
                }

                JsonArray files =
                        index.getAsJsonArray(
                                "files"
                        );

                int total =
                        files.size();

                int current = 0;

                for (
                        JsonElement element :
                        files
                ) {
                    checkCancelled();

                    current++;

                    JsonObject file =
                            element
                                    .getAsJsonObject();

                    if (!shouldInstallOnClient(file)) {

                        notify(
                                listener,
                                "Skipping server-only file...",
                                current,
                                total
                        );

                        continue;
                    }

                    String relativePath =
                            file.get("path")
                                    .getAsString();

                    File destination =
                            resolveSafePath(
                                    instanceDirectory,
                                    relativePath
                            );

                    File parent =
                            destination.getParentFile();

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

                    JsonArray downloads =
                            file.getAsJsonArray(
                                    "downloads"
                            );

                    if (downloads == null
                            || downloads.isEmpty()) {

                        throw new IllegalStateException(
                                "No download URLs for "
                                        + relativePath
                        );
                    }

                    String url =
                            downloads
                                    .get(0)
                                    .getAsString();

                    notify(
                            listener,
                            "Downloading "
                                    + relativePath,
                            current,
                            total
                    );

                    downloadToFile(
                            url,
                            destination
                    );

                    validateHashes(
                            destination,
                            file.getAsJsonObject(
                                    "hashes"
                            )
                    );
                }

                notify(
                        listener,
                        "Applying overrides...",
                        total,
                        total
                );

                checkCancelled();

                extractOverrides(
                        zip,
                        "overrides/",
                        instanceDirectory
                );

                checkCancelled();

                extractOverrides(
                        zip,
                        "client-overrides/",
                        instanceDirectory
                );

                writeInstanceMetadata(
                        instanceDirectory,
                        modpack,
                        version,
                        minecraftVersion,
                        loader,
                        loaderVersion
                );

                notify(
                        listener,
                        "Modpack files installed.",
                        total,
                        total
                );

                return new InstallResult(
                        instanceDirectory,
                        minecraftVersion,
                        loader,
                        loaderVersion
                );
            }

        } catch (Exception e) {

            deleteRecursively(
                    instanceDirectory
            );

            throw e;

        } finally {

            if (tempMrpack.exists()) {
                tempMrpack.delete();
            }
        }
    }

    private void validateIndex(
            JsonObject index
    ) {

        if (!index.has("formatVersion")) {
            throw new IllegalArgumentException(
                    "Missing formatVersion"
            );
        }

        if (
                index.get("formatVersion")
                        .getAsInt() != 1
        ) {

            throw new IllegalArgumentException(
                    "Unsupported .mrpack formatVersion: "
                            + index.get(
                            "formatVersion"
                    )
            );
        }

        if (
                !index.has("game")
                        || !"minecraft".equalsIgnoreCase(
                        index.get("game")
                                .getAsString()
                )
        ) {

            throw new IllegalArgumentException(
                    "This modpack is not for Minecraft."
            );
        }

        if (!index.has("files")) {
            throw new IllegalArgumentException(
                    "Missing files array"
            );
        }

        if (!index.has("dependencies")) {
            throw new IllegalArgumentException(
                    "Missing dependencies"
            );
        }

        JsonObject dependencies =
                index.getAsJsonObject(
                        "dependencies"
                );

        if (!dependencies.has("minecraft")) {
            throw new IllegalArgumentException(
                    "Missing Minecraft dependency"
            );
        }
    }

    private boolean shouldInstallOnClient(
            JsonObject file
    ) {

        if (!file.has("env")) {
            return true;
        }

        JsonObject env =
                file.getAsJsonObject(
                        "env"
                );

        if (
                env == null
                        || !env.has("client")
        ) {
            return true;
        }

        String client =
                env.get("client")
                        .getAsString();

        return !"unsupported"
                .equalsIgnoreCase(client);
    }

    private void validateHashes(
            File file,
            JsonObject hashes
    ) throws Exception {

        if (hashes == null) {
            throw new IllegalArgumentException(
                    "Missing hashes for "
                            + file.getName()
            );
        }

        if (hashes.has("sha512")) {

            String expected =
                    hashes.get("sha512")
                            .getAsString();

            String actual =
                    calculateHash(
                            file,
                            "SHA-512"
                    );

            if (!expected.equalsIgnoreCase(actual)) {

                throw new IllegalStateException(
                        "SHA-512 mismatch for "
                                + file.getName()
                );
            }

        } else if (hashes.has("sha1")) {

            String expected =
                    hashes.get("sha1")
                            .getAsString();

            String actual =
                    calculateHash(
                            file,
                            "SHA-1"
                    );

            if (!expected.equalsIgnoreCase(actual)) {

                throw new IllegalStateException(
                        "SHA-1 mismatch for "
                                + file.getName()
                );
            }

        } else {

            throw new IllegalArgumentException(
                    "No supported hash for "
                            + file.getName()
            );
        }
    }

    private String calculateHash(
            File file,
            String algorithm
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        algorithm
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

        StringBuilder builder =
                new StringBuilder();

        for (
                byte value :
                digest.digest()
        ) {

            builder.append(
                    String.format(
                            Locale.ROOT,
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return builder.toString();
    }

    private void extractOverrides(
            ZipFile zip,
            String prefix,
            File instanceDirectory
    ) throws Exception {

        zip.stream()
                .filter(
                        entry ->
                                !entry.isDirectory()
                                        && entry.getName()
                                        .startsWith(prefix)
                )
                .forEach(entry -> {

                    try {

                        String relative =
                                entry.getName()
                                        .substring(
                                                prefix.length()
                                        );

                        if (relative.isEmpty()) {
                            return;
                        }

                        File destination =
                                resolveSafePath(
                                        instanceDirectory,
                                        relative
                                );

                        File parent =
                                destination
                                        .getParentFile();

                        if (
                                parent != null
                                        && !parent.exists()
                                        && !parent.mkdirs()
                        ) {

                            throw new IllegalStateException(
                                    "Could not create "
                                            + parent
                            );
                        }

                        try (
                                InputStream input =
                                        zip.getInputStream(
                                                entry
                                        )
                        ) {

                            Files.copy(
                                    input,
                                    destination.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                        }

                    } catch (Exception ex) {

                        throw new RuntimeException(
                                ex
                        );
                    }
                });
    }

    private File resolveSafePath(
            File root,
            String relativePath
    ) throws Exception {

        if (
                relativePath == null
                        || relativePath.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Empty file path"
            );
        }

        String normalized =
                relativePath
                        .replace('\\', '/');

        if (
                normalized.startsWith("/")
                        || normalized.startsWith("\\")
                        || normalized.matches(
                        "^[A-Za-z]:.*"
                )
                        || normalized.contains("../")
                        || normalized.equals("..")
        ) {

            throw new SecurityException(
                    "Unsafe path in modpack: "
                            + relativePath
            );
        }

        File destination =
                new File(
                        root,
                        normalized
                );

        String rootPath =
                root.getCanonicalPath()
                        + File.separator;

        String destinationPath =
                destination.getCanonicalPath();

        if (!destinationPath.startsWith(rootPath)) {

            throw new SecurityException(
                    "Path escapes instance directory: "
                            + relativePath
            );
        }

        return destination;
    }

    private void downloadToFile(
            String address,
            File destination
    ) throws Exception {

        URL url =
                new URL(address);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        activeConnection = connection;

        connection.setInstanceFollowRedirects(true);

        connection.setRequestProperty(
                "User-Agent",
                "Guedes-Legacy-Launcher/1.6.94"
        );

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        checkCancelled();

        int status =
                connection.getResponseCode();

        if (
                status < 200
                        || status >= 300
        ) {

            connection.disconnect();

            throw new IllegalStateException(
                    "HTTP "
                            + status
                            + " downloading "
                            + address
            );
        }

        File parent =
                destination.getParentFile();

        if (
                parent != null
                        && !parent.exists()
                        && !parent.mkdirs()
        ) {

            connection.disconnect();

            throw new IllegalStateException(
                    "Could not create "
                            + parent
            );
        }

        try (
                InputStream input =
                        new BufferedInputStream(
                                connection
                                        .getInputStream()
                        );

                FileOutputStream output =
                        new FileOutputStream(
                                destination
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

            activeConnection = null;
            connection.disconnect();
        }
    }

    private void writeInstanceMetadata(
            File instanceDirectory,
            ModpackSearchResult modpack,
            ModpackVersion version,
            String minecraftVersion,
            String loader,
            String loaderVersion
    ) throws Exception {

        JsonObject metadata =
                new JsonObject();

        metadata.addProperty(
                "provider",
                "modrinth"
        );

        metadata.addProperty(
                "projectId",
                modpack.getProjectId()
        );

        metadata.addProperty(
                "versionId",
                version.getVersionId()
        );

        metadata.addProperty(
                "name",
                modpack.getName()
        );

        metadata.addProperty(
                "versionName",
                version.getName()
        );

        metadata.addProperty(
                "minecraftVersion",
                minecraftVersion
        );

        if (loader != null) {

            metadata.addProperty(
                    "loader",
                    loader
            );
        }

        if (loaderVersion != null) {

            metadata.addProperty(
                    "loaderVersion",
                    loaderVersion
            );
        }

        File metadataFile =
                new File(
                        instanceDirectory,
                        ".guedes-instance.json"
                );

        try (
                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(
                                                metadataFile
                                        ),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            writer.write(
                    metadata.toString()
            );
        }
    }

    private File createUniqueInstanceDirectory(
            File parent,
            String baseName
    ) {

        File candidate =
                new File(
                        parent,
                        baseName
                );

        if (!candidate.exists()) {
            return candidate;
        }

        int number = 2;

        while (true) {

            candidate =
                    new File(
                            parent,
                            baseName
                                    + " "
                                    + number
                    );

            if (!candidate.exists()) {
                return candidate;
            }

            number++;
        }
    }

    private String sanitizeFileName(
            String name
    ) {

        String sanitized =
                name.replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                );

        sanitized =
                sanitized.trim();

        if (sanitized.isEmpty()) {
            return "Modpack";
        }

        return sanitized;
    }

    private String getString(
            JsonObject object,
            String key
    ) {

        if (
                object == null
                        || !object.has(key)
                        || object.get(key)
                        .isJsonNull()
        ) {

            return null;
        }

        return object.get(key)
                .getAsString();
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
                    deleteRecursively(child);
                }
            }
        }

        file.delete();
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
}