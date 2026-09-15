package net.minecraft.launcher.modpacks;

public class CurseForgeProject {

    private long id;
    private String name;
    private boolean isAvailable;
    private boolean allowModDistribution;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public boolean isAllowModDistribution() {
        return allowModDistribution;
    }
}