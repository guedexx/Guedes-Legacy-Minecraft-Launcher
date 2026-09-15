package net.minecraft.launcher.modpacks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TechnicLegacyVersionInstaller {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public String install(
            File minecraftDirectory,
            File instanceDirectory,
            String packId,
            String packVersion,
            String minecraftVersion
    ) throws Exception {

        if (minecraftDirectory == null) {
            throw new IllegalArgumentException(
                    "Minecraft directory cannot be null."
            );
        }

        if (instanceDirectory == null) {
            throw new IllegalArgumentException(
                    "Instance directory cannot be null."
            );
        }

        File modpackJar =
                new File(
                        instanceDirectory,
                        "bin/modpack.jar"
                );

        if (!modpackJar.isFile()) {
            throw new IllegalStateException(
                    "Technic legacy modpack.jar was not found: "
                            + modpackJar.getAbsolutePath()
            );
        }

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Minecraft version cannot be empty."
            );
        }

        String forgeVersion =
                detectForgeVersion(modpackJar);

        if (forgeVersion == null) {
            throw new IllegalStateException(
                    "Could not detect Forge version from modpack.jar."
            );
        }

        String versionId =
                "technic-"
                        + sanitizeId(packId)
                        + "-"
                        + sanitizeId(packVersion);

        installForgeLibrary(
                minecraftDirectory,
                modpackJar,
                forgeVersion
        );

        writeVersionJson(
                minecraftDirectory,
                versionId,
                minecraftVersion,
                forgeVersion
        );

        return versionId;
    }

    private void installForgeLibrary(
            File minecraftDirectory,
            File modpackJar,
            String forgeVersion
    ) throws Exception {

        File forgeDirectory =
                new File(
                        minecraftDirectory,
                        "libraries/net/minecraftforge/minecraftforge/"
                                + forgeVersion
                );

        if (
                !forgeDirectory.exists()
                        && !forgeDirectory.mkdirs()
        ) {
            throw new IllegalStateException(
                    "Could not create Forge library directory."
            );
        }

        File forgeJar =
                new File(
                        forgeDirectory,
                        "minecraftforge-"
                                + forgeVersion
                                + ".jar"
                );

        Files.copy(
                modpackJar.toPath(),
                forgeJar.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void writeVersionJson(
            File minecraftDirectory,
            String versionId,
            String minecraftVersion,
            String forgeVersion
    ) throws Exception {

        File versionDirectory =
                new File(
                        minecraftDirectory,
                        "versions/" + versionId
                );

        if (
                !versionDirectory.exists()
                        && !versionDirectory.mkdirs()
        ) {
            throw new IllegalStateException(
                    "Could not create Minecraft version directory."
            );
        }

        JsonObject root =
                new JsonObject();

        root.addProperty(
                "id",
                versionId
        );

        root.addProperty(
                "inheritsFrom",
                minecraftVersion
        );

        root.addProperty(
                "jar",
                minecraftVersion
        );

        root.addProperty(
                "type",
                "release"
        );

        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ssXXX"
                );

        dateFormat.setTimeZone(
                java.util.TimeZone.getTimeZone("UTC")
        );

        String currentTime =
                dateFormat.format(
                        new java.util.Date()
                );

        root.addProperty(
                "time",
                currentTime
        );

        root.addProperty(
                "releaseTime",
                currentTime
        );

        root.addProperty(
                "minecraftArguments",
                "--username ${auth_player_name} "
                        + "--session ${auth_session} "
                        + "--version ${version_name} "
                        + "--gameDir ${game_directory} "
                        + "--assetsDir ${game_assets} "
                        + "--tweakClass cpw.mods.fml.common.launcher.FMLTweaker"
        );

        root.addProperty(
                "mainClass",
                "net.minecraft.launchwrapper.Launch"
        );

        JsonArray libraries =
                new JsonArray();

        libraries.add(
                library(
                        "net.minecraftforge:minecraftforge:"
                                + forgeVersion
                )
        );

        libraries.add(
                library(
                        "net.minecraft:launchwrapper:1.8"
                )
        );

        libraries.add(
                library(
                        "org.ow2.asm:asm-all:4.1"
                )
        );

        libraries.add(
                library(
                        "org.scala-lang:scala-library:2.10.2",
                        "https://repo1.maven.org/maven2/"
                )
        );

        libraries.add(
                library(
                        "org.scala-lang:scala-compiler:2.10.2",
                        "https://repo1.maven.org/maven2/"
                )
        );

        libraries.add(
                library(
                        "lzma:lzma:0.0.1"
                )
        );

        libraries.add(
                library(
                        "net.sf.jopt-simple:jopt-simple:4.5"
                )
        );

        root.add(
                "libraries",
                libraries
        );

        File versionJson =
                new File(
                        versionDirectory,
                        versionId + ".json"
                );

        try (
                FileWriter writer =
                        new FileWriter(
                                versionJson
                        )
        ) {

            GSON.toJson(
                    root,
                    writer
            );
        }
    }

    private JsonObject library(
            String name
    ) {

        JsonObject library =
                new JsonObject();

        library.addProperty(
                "name",
                name
        );

        return library;
    }

    private JsonObject library(
            String name,
            String url
    ) {

        JsonObject library =
                new JsonObject();

        library.addProperty(
                "name",
                name
        );

        library.addProperty(
                "url",
                url
        );

        return library;
    }

    private String detectForgeVersion(
            File jar
    ) throws Exception {

        try (
                java.util.zip.ZipFile zip =
                        new java.util.zip.ZipFile(
                                jar
                        )
        ) {

            java.util.zip.ZipEntry entry =
                    zip.getEntry(
                            "forgeversion.properties"
                    );

            if (entry == null) {
                return null;
            }

            java.util.Properties properties =
                    new java.util.Properties();

            try (
                    InputStream input =
                            zip.getInputStream(
                                    entry
                            )
            ) {
                properties.load(input);
            }

            String major =
                    properties.getProperty(
                            "forge.major.number"
                    );

            String minor =
                    properties.getProperty(
                            "forge.minor.number"
                    );

            String revision =
                    properties.getProperty(
                            "forge.revision.number"
                    );

            String build =
                    properties.getProperty(
                            "forge.build.number"
                    );

            if (
                    major == null
                            || minor == null
                            || revision == null
                            || build == null
            ) {
                return null;
            }

            return major
                    + "."
                    + minor
                    + "."
                    + revision
                    + "."
                    + build;
        }
    }

    private String sanitizeId(
            String value
    ) {

        if (
                value == null
                        || value.trim().isEmpty()
        ) {
            return "unknown";
        }

        return value
                .trim()
                .toLowerCase()
                .replaceAll(
                        "[^a-z0-9._-]+",
                        "-"
                )
                .replaceAll(
                        "-+",
                        "-"
                );
    }
}