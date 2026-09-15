package net.minecraft.launcher.modpacks;

import java.util.List;

public interface ModpackProvider {

    String getName();

    List<ModpackSearchResult> search(String query) throws Exception;

    List<ModpackVersion> getVersions(String projectId) throws Exception;
}