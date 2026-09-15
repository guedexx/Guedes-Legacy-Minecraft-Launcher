package net.minecraft.launcher.modpacks;

import java.util.Collections;
import java.util.List;

public class ModpackSearchResult {

    private final String provider;
    private final String projectId;
    private final String name;
    private final String author;
    private final String description;
    private final String iconUrl;
    private final long downloads;

    private final List<String> minecraftVersions;
    private final List<String> loaders;

    private final String updated;

    public ModpackSearchResult(
            String provider,
            String projectId,
            String name,
            String author,
            String description,
            String iconUrl,
            long downloads,
            List<String> minecraftVersions,
            List<String> loaders,
            String updated
    ) {
        this.provider = provider;
        this.projectId = projectId;
        this.name = name;
        this.author = author;
        this.description = description;
        this.iconUrl = iconUrl;
        this.downloads = downloads;

        this.minecraftVersions =
                minecraftVersions != null
                        ? minecraftVersions
                        : Collections.emptyList();

        this.loaders =
                loaders != null
                        ? loaders
                        : Collections.emptyList();

        this.updated = updated;
    }

    public String getProvider() {
        return provider;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public long getDownloads() {
        return downloads;
    }

    public List<String> getMinecraftVersions() {
        return minecraftVersions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public String getUpdated() {
        return updated;
    }

    @Override
    public String toString() {
        return name;
    }
}