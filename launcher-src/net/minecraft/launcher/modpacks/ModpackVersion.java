package net.minecraft.launcher.modpacks;

public class ModpackVersion {

    private final String provider;
    private final String projectId;
    private final String versionId;
    private final String name;
    private final String minecraftVersion;
    private final String loader;
    private final String downloadUrl;

    public ModpackVersion(
            String provider,
            String projectId,
            String versionId,
            String name,
            String minecraftVersion,
            String loader,
            String downloadUrl
    ) {
        this.provider = provider;
        this.projectId = projectId;
        this.versionId = versionId;
        this.name = name;
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
        this.downloadUrl = downloadUrl;
    }

    public String getProvider() {
        return provider;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getName() {
        return name;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getLoader() {
        return loader;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public String toString() {
        return name;
    }
}