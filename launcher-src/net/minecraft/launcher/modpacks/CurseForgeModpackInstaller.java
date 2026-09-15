package net.minecraft.launcher.modpacks;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CurseForgeModpackInstaller {

    private final Gson gson = new Gson();

    public CurseForgeManifest readManifest(
            File modpackZip
    ) throws IOException {

        if (
                modpackZip == null
                        || !modpackZip.isFile()
        ) {
            throw new FileNotFoundException(
                    "CurseForge modpack ZIP was not found."
            );
        }

        try (ZipFile zip = new ZipFile(modpackZip)) {

            ZipEntry manifestEntry =
                    zip.getEntry("manifest.json");

            if (manifestEntry == null) {
                throw new IOException(
                        "This ZIP does not contain manifest.json."
                );
            }

            try (
                    InputStream input =
                            zip.getInputStream(manifestEntry);

                    Reader reader =
                            new InputStreamReader(
                                    input,
                                    StandardCharsets.UTF_8
                            )
            ) {

                CurseForgeManifest manifest =
                        gson.fromJson(
                                reader,
                                CurseForgeManifest.class
                        );

                validateManifest(manifest);

                return manifest;
            }
        }
    }

    private void validateManifest(
            CurseForgeManifest manifest
    ) throws IOException {

        if (manifest == null) {
            throw new IOException(
                    "Invalid CurseForge manifest."
            );
        }

        if (manifest.getMinecraft() == null) {
            throw new IOException(
                    "CurseForge manifest does not specify Minecraft."
            );
        }

        if (
                manifest.getMinecraft().getVersion() == null
                        || manifest
                        .getMinecraft()
                        .getVersion()
                        .trim()
                        .isEmpty()
        ) {
            throw new IOException(
                    "CurseForge manifest does not specify a Minecraft version."
            );
        }

        if (manifest.getFiles() == null) {
            throw new IOException(
                    "CurseForge manifest does not contain a file list."
            );
        }
    }
}