package net.minecraft.launcher.modpacks;

import java.util.ArrayList;
import java.util.List;

public class CurseForgeProvider implements ModpackProvider {

    @Override
    public String getName() {
        return "CurseForge";
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public List<ModpackSearchResult> search(String query) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<ModpackVersion> getVersions(String projectId) throws Exception {
        return new ArrayList<>();
    }
}