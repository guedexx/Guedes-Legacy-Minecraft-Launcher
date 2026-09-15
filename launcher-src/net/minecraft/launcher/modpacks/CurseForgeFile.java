package net.minecraft.launcher.modpacks;

public class CurseForgeFile {

    private long id;
    private long modId;
    private String displayName;
    private String fileName;
    private String downloadUrl;
    private long fileLength;

    public long getId() {
        return id;
    }

    public long getModId() {
        return modId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public long getFileLength() {
        return fileLength;
    }
}