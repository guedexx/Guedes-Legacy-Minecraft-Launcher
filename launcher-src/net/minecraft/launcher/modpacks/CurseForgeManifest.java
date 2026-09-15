package net.minecraft.launcher.modpacks;

import java.util.List;

public class CurseForgeManifest {

    private Minecraft minecraft;
    private String manifestType;
    private int manifestVersion;
    private String name;
    private String version;
    private String author;
    private List<ManifestFile> files;
    private String overrides;

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public String getManifestType() {
        return manifestType;
    }

    public int getManifestVersion() {
        return manifestVersion;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public List<ManifestFile> getFiles() {
        return files;
    }

    public String getOverrides() {
        return overrides;
    }

    public static class Minecraft {

        private String version;
        private List<ModLoader> modLoaders;

        public String getVersion() {
            return version;
        }

        public List<ModLoader> getModLoaders() {
            return modLoaders;
        }
    }

    public static class ModLoader {

        private String id;
        private boolean primary;

        public String getId() {
            return id;
        }

        public boolean isPrimary() {
            return primary;
        }
    }

    public static class ManifestFile {

        private long projectID;
        private long fileID;
        private boolean required;

        public long getProjectID() {
            return projectID;
        }

        public long getFileID() {
            return fileID;
        }

        public boolean isRequired() {
            return required;
        }
    }
}