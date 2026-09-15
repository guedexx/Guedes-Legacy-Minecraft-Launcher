package net.minecraft.launcher.ui.popups.modpack;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.*;

import com.google.gson.JsonObject;
import com.mojang.launcher.UserInterface;
import com.mojang.launcher.updater.VersionSyncInfo;
import com.mojang.launcher.updater.download.DownloadJob;
import com.mojang.launcher.updater.download.DownloadListener;
import com.mojang.launcher.versions.Version;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.SwingUserInterface;
import net.minecraft.launcher.modpacks.*;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.profile.ProfileManager;
import net.minecraft.launcher.updater.MinecraftVersionManager;

public class InstallModpackPopup extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Launcher launcher;

    private final JComboBox<ModpackProvider> providerBox =
            new JComboBox<ModpackProvider>();

    private final JTextField searchField = new JTextField();

    private final JButton searchButton = new JButton("Search");

    private final JButton importCurseForgeButton =
            new JButton("Import ZIP");

    private final DefaultListModel<ModpackSearchResult> resultModel =
            new DefaultListModel<ModpackSearchResult>();

    private final JList<ModpackSearchResult> resultList =
            new JList<ModpackSearchResult>(resultModel);

    private final JButton installButton = new JButton("Install");

    private final JButton cancelButton = new JButton("Cancel");

    private final JLabel statusLabel = new JLabel(" ");

    private volatile boolean installCancelled = false;

    private volatile ModrinthModpackInstaller activeInstaller;

    private Thread installThread;

    public InstallModpackPopup(Launcher launcher) {

        super(
                (Window) launcher.getFrame(),
                "Install Modpack",
                ModalityType.APPLICATION_MODAL
        );

        this.launcher = launcher;

        providerBox.addItem(new ModrinthProvider());
        providerBox.addItem(new CurseForgeProvider());
        providerBox.addItem(new TechnicProvider());

        resultList.setCellRenderer(
                new ModpackListRenderer()
        );

        resultList.setFixedCellHeight(105);

        installButton.setEnabled(false);

        createInterface();
        registerListeners();

        setMinimumSize(
                new Dimension(720, 520)
        );

        setSize(
                new Dimension(720, 520)
        );

        setLocationRelativeTo(launcher.getFrame());
    }

    private void createModpackProfile(
            String profileName,
            File gameDir,
            String versionId
    ) throws Exception {

        ProfileManager profileManager =
                launcher.getProfileManager();

        String uniqueName = profileName;
        int number = 2;

        while (profileManager.getProfiles().containsKey(uniqueName)) {
            uniqueName = profileName + " " + number;
            number++;
        }

        Profile profile =
                new Profile(uniqueName);

        profile.setGameDir(gameDir);
        profile.setLastVersionId(versionId);

        profileManager
                .getProfiles()
                .put(uniqueName, profile);

        profileManager.setSelectedProfile(uniqueName);

        profileManager.saveProfiles();
        profileManager.fireRefreshEvent();
    }

    private void createTechnicProfile(
            String profileName,
            File gameDir,
            String versionId
    ) throws Exception {

        ProfileManager profileManager =
                launcher.getProfileManager();

        String uniqueName = profileName;
        int number = 2;

        while (
                profileManager
                        .getProfiles()
                        .containsKey(uniqueName)
        ) {
            uniqueName =
                    profileName
                            + " "
                            + number;

            number++;
        }

        Profile profile =
                new Profile(uniqueName);

        profile.setGameDir(gameDir);

        profile.setLastVersionId(
                versionId
        );

        profile.setJavaArgs(
                "-Xmx1G -Xmn128M "
                        + "-Dfml.ignoreInvalidMinecraftCertificates=true"
        );

        profileManager
                .getProfiles()
                .put(
                        uniqueName,
                        profile
                );

        profileManager.setSelectedProfile(
                uniqueName
        );

        profileManager.saveProfiles();
        profileManager.fireRefreshEvent();
    }

    private File minecraftLauncherDirectory() {

        String appData =
                System.getenv(
                        "APPDATA"
                );

        if (
                appData != null
                        && !appData.trim().isEmpty()
        ) {

            return new File(
                    appData,
                    ".minecraft"
            );
        }

        return new File(
                System.getProperty(
                        "user.home"
                ),
                ".minecraft"
        );
    }

    private void ensureMinecraftVersionInstalled(
            String minecraftVersion
    ) throws Exception {

        setLauncherProgress(
                "Checking Minecraft " + minecraftVersion + "...",
                0,
                0
        );

        MinecraftVersionManager versionManager =
                (MinecraftVersionManager)
                        launcher
                                .getLauncher()
                                .getVersionManager();

        versionManager.refreshVersions();

        VersionSyncInfo syncInfo =
                versionManager.getVersionSyncInfo(
                        minecraftVersion
                );

        if (
                syncInfo == null
                        || !syncInfo.isOnRemote()
        ) {
            throw new IllegalStateException(
                    "Minecraft version "
                            + minecraftVersion
                            + " was not found on the remote version list."
            );
        }

        File minecraftJar =
                new File(
                        minecraftLauncherDirectory(),
                        "versions/"
                                + minecraftVersion
                                + "/"
                                + minecraftVersion
                                + ".jar"
                );

        if (
                syncInfo.isInstalled()
                        && syncInfo.isUpToDate()
                        && minecraftJar.isFile()
        ) {

            System.out.println(
                    "[TECHNIC] Minecraft "
                            + minecraftVersion
                            + " is already installed."
            );

            return;
        }

        setLauncherProgress(
                "Downloading Minecraft "
                        + minecraftVersion
                        + "...",
                0,
                0
        );

        CountDownLatch finished =
                new CountDownLatch(1);

        DownloadJob job =
                new DownloadJob(
                        "Minecraft "
                                + minecraftVersion
                                + " for Technic",
                        false,
                        new DownloadListener() {

                            @Override
                            public void onDownloadJobFinished(
                                    DownloadJob job
                            ) {

                                finished.countDown();
                            }

                            @Override
                            public void onDownloadJobProgressChanged(
                                    DownloadJob job
                            ) {

                                setLauncherProgress(
                                        "Downloading Minecraft "
                                                + minecraftVersion
                                                + "...",
                                        job.getSuccessful(),
                                        job.getAllFiles().size()
                                );
                            }
                        }
                );

        versionManager.downloadVersion(
                syncInfo,
                job
        );

        job.startDownloading(
                versionManager.getExecutorService()
        );

        finished.await();

        if (job.getFailures() > 0) {

            throw new IllegalStateException(
                    "Could not download Minecraft "
                            + minecraftVersion
                            + ": "
                            + job.getFailures()
                            + " file(s) failed."
            );
        }

        if (!minecraftJar.isFile()) {

            throw new IllegalStateException(
                    "Minecraft "
                            + minecraftVersion
                            + " finished downloading, "
                            + "but its client JAR was not created."
            );
        }

        Version version =
                versionManager.getLatestVersion(
                        syncInfo
                );

        versionManager.installVersion(
                version
        );

        versionManager.refreshVersions();

        System.out.println(
                "[TECHNIC] Minecraft "
                        + minecraftVersion
                        + " installed successfully."
        );
    }

    private void createInterface() {

        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new BorderLayout(5, 5));

        JPanel providerPanel =
                new JPanel(new BorderLayout(5, 5));

        providerPanel.add(
                new JLabel("Source:"),
                BorderLayout.WEST
        );

        providerPanel.add(
                providerBox,
                BorderLayout.CENTER
        );

        top.add(providerPanel, BorderLayout.NORTH);

        JPanel searchPanel =
                new JPanel(new BorderLayout(5, 5));

        searchPanel.add(
                new JLabel("Search:"),
                BorderLayout.WEST
        );

        searchPanel.add(
                searchField,
                BorderLayout.CENTER
        );

        JPanel searchButtons =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        searchButtons.add(importCurseForgeButton);
        searchButtons.add(searchButton);

        searchPanel.add(
                searchButtons,
                BorderLayout.EAST
        );

        top.add(searchPanel, BorderLayout.SOUTH);

        top.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 0, 10)
        );

        add(top, BorderLayout.NORTH);

        JScrollPane scroll =
                new JScrollPane(resultList);

        scroll.setBorder(
                BorderFactory.createTitledBorder("Modpacks")
        );

        JPanel center = new JPanel(new BorderLayout());

        center.setBorder(
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        );

        center.add(scroll, BorderLayout.CENTER);
        center.add(statusLabel, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);

        JPanel buttons =
                new JPanel(new FlowLayout(FlowLayout.RIGHT));

        buttons.add(cancelButton);
        buttons.add(installButton);

        add(buttons, BorderLayout.SOUTH);
    }

    private SwingUserInterface getSwingUserInterface() {

        UserInterface ui =
                launcher
                        .getLauncher()
                        .getUserInterface();

        if (ui instanceof SwingUserInterface) {
            return (SwingUserInterface) ui;
        }

        return null;
    }

    private void setLauncherProgress(
            String message,
            int current,
            int total
    ) {

        SwingUserInterface ui =
                getSwingUserInterface();

        if (ui != null) {

            ui.setModpackProgress(
                    message,
                    current,
                    total
            );
        }
    }

    private void hideLauncherProgress() {

        SwingUserInterface ui =
                getSwingUserInterface();

        if (ui != null) {
            ui.hideModpackProgress();
        }
    }

    private void finishLauncherInstallationState() {

        SwingUserInterface ui =
                getSwingUserInterface();

        if (ui != null) {
            ui.finishModpackInstallation();
        }
    }

    private void registerListeners() {

        importCurseForgeButton.addActionListener(e -> {

            JFileChooser chooser = new JFileChooser();

            chooser.setDialogTitle(
                    "Select CurseForge Modpack ZIP"
            );

            chooser.setFileSelectionMode(
                    JFileChooser.FILES_ONLY
            );

            int result =
                    chooser.showOpenDialog(
                            InstallModpackPopup.this
                    );

            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File zip =
                    chooser.getSelectedFile();

            try {

                CurseForgeModpackInstaller installer =
                        new CurseForgeModpackInstaller();

                CurseForgeManifest manifest =
                        installer.readManifest(zip);

                CurseForgeManifest.ManifestFile first =
                        manifest.getFiles().get(0);

                CurseForgeApiClient api =
                        CurseForgeApiClient.fromEnvironment();

                CurseForgeProject project =
                        api.getProject(
                                first.getProjectID()
                        );

                CurseForgeFile file =
                        api.getFile(
                                first.getProjectID(),
                                first.getFileID()
                        );

                System.out.println(
                        "[CURSEFORGE API] Project: "
                                + project.getName()
                );

                System.out.println(
                        "[CURSEFORGE API] Available: "
                                + project.isAvailable()
                );

                System.out.println(
                        "[CURSEFORGE API] Distribution: "
                                + project.isAllowModDistribution()
                );

                System.out.println(
                        "[CURSEFORGE API] File: "
                                + file.getFileName()
                );

                System.out.println(
                        "[CURSEFORGE API] Download URL present: "
                                + (
                                file.getDownloadUrl() != null
                                        && !file.getDownloadUrl().isEmpty()
                        )
                );

                System.out.println(
                        "[CURSEFORGE] Pack: "
                                + manifest.getName()
                );

                System.out.println(
                        "[CURSEFORGE] Minecraft: "
                                + manifest
                                .getMinecraft()
                                .getVersion()
                );

                System.out.println(
                        "[CURSEFORGE] Files: "
                                + manifest
                                .getFiles()
                                .size()
                );

                if (
                        manifest
                                .getMinecraft()
                                .getModLoaders() != null
                ) {

                    for (
                            CurseForgeManifest.ModLoader loader :
                            manifest
                                    .getMinecraft()
                                    .getModLoaders()
                    ) {

                        System.out.println(
                                "[CURSEFORGE] Loader: "
                                        + loader.getId()
                                        + " primary="
                                        + loader.isPrimary()
                        );
                    }
                }

                statusLabel.setText(
                        "Loaded CurseForge pack: "
                                + manifest.getName()
                );

                JOptionPane.showMessageDialog(
                        InstallModpackPopup.this,
                        "CurseForge modpack detected!\n\n"
                                + "Name: "
                                + manifest.getName()
                                + "\nMinecraft: "
                                + manifest
                                .getMinecraft()
                                .getVersion()
                                + "\nFiles: "
                                + manifest
                                .getFiles()
                                .size(),
                        "CurseForge Import",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {

                ex.printStackTrace();

                JOptionPane.showMessageDialog(
                        InstallModpackPopup.this,
                        "Could not read CurseForge modpack:\n"
                                + ex.getMessage(),
                        "CurseForge Import Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        searchButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });

        searchField.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });

        cancelButton.addActionListener(e -> {

            if (
                    installThread != null
                            && installThread.isAlive()
            ) {

                int choice =
                        JOptionPane.showConfirmDialog(
                                InstallModpackPopup.this,
                                "Cancel the modpack installation?",
                                "Cancel Installation",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                        );

                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }

                installCancelled = true;

                if (activeInstaller != null) {
                    activeInstaller.cancel();
                }

                installThread.interrupt();

                statusLabel.setText(
                        "Cancelling installation..."
                );

                setLauncherProgress(
                        "Cancelling installation...",
                        0,
                        0
                );

                cancelButton.setEnabled(false);

                return;
            }

            dispose();
        });

        resultList.addListSelectionListener(e -> {

            if (!e.getValueIsAdjusting()) {

                installButton.setEnabled(
                        resultList.getSelectedValue() != null
                );
            }
        });

        installButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                final ModpackSearchResult selected =
                        resultList.getSelectedValue();

                final ModpackProvider provider =
                        (ModpackProvider) providerBox.getSelectedItem();

                if (selected == null || provider == null) {
                    return;
                }

                installButton.setEnabled(false);

                statusLabel.setText(
                        "Loading versions for "
                                + selected.getName()
                                + "..."
                );

                Thread versionThread = new Thread(new Runnable() {

                    @Override
                    public void run() {

                        try {

                            final List<ModpackVersion> versions =
                                    provider.getVersions(
                                            selected.getProjectId()
                                    );

                            SwingUtilities.invokeLater(new Runnable() {

                                @Override
                                public void run() {

                                    if (versions.isEmpty()) {

                                        JOptionPane.showMessageDialog(
                                                InstallModpackPopup.this,
                                                "No versions were found for this modpack.",
                                                "Install Modpack",
                                                JOptionPane.WARNING_MESSAGE
                                        );

                                        statusLabel.setText(
                                                "No versions found."
                                        );

                                        installButton.setEnabled(true);
                                        return;
                                    }

                                    ModpackVersion chosen =
                                            ModpackVersionPopup.showVersionDialog(
                                                    InstallModpackPopup.this,
                                                    selected,
                                                    versions
                                            );

                                    if (chosen != null) {

                                        statusLabel.setText(
                                                "Installing "
                                                        + selected.getName()
                                                        + "..."
                                        );

                                        installButton.setEnabled(false);
                                        searchButton.setEnabled(false);
                                        providerBox.setEnabled(false);
                                        searchField.setEnabled(false);

                                        installCancelled = false;

                                        installThread =
                                                new Thread(() -> {

                                                    try {

                                                        if ("Technic".equalsIgnoreCase(chosen.getProvider())) {

                                                            TechnicProvider technicProvider =
                                                                    (TechnicProvider) provider;

                                                            TechnicModpackInstaller technicInstaller =
                                                                    new TechnicModpackInstaller();

                                                            TechnicModpackInstaller.InstallResult technicResult;

                                                            if (
                                                                    chosen.getDownloadUrl() != null
                                                                            && !chosen.getDownloadUrl().trim().isEmpty()
                                                            ) {

                                                                technicResult =
                                                                        technicInstaller.installDirect(
                                                                                minecraftLauncherDirectory(),
                                                                                selected.getName(),
                                                                                chosen,
                                                                                (message, current, total) -> {
                                                                                    setLauncherProgress(
                                                                                            message,
                                                                                            current,
                                                                                            total
                                                                                    );

                                                                                    SwingUtilities.invokeLater(() -> {
                                                                                        statusLabel.setText(
                                                                                                message
                                                                                                        + "  "
                                                                                                        + current
                                                                                                        + "/"
                                                                                                        + total
                                                                                        );
                                                                                    });
                                                                                }
                                                                        );

                                                            } else {

                                                                JsonObject buildInfo =
                                                                        technicProvider.getSolderBuildInfo(
                                                                                technicProvider.getSolderBaseUrl(
                                                                                        selected.getProjectId()
                                                                                ),
                                                                                selected.getProjectId(),
                                                                                chosen.getVersionId()
                                                                        );

                                                                technicResult =
                                                                        technicInstaller.install(
                                                                                minecraftLauncherDirectory(),
                                                                                selected.getName(),
                                                                                buildInfo,
                                                                                (message, current, total) -> {
                                                                                    setLauncherProgress(
                                                                                            message,
                                                                                            current,
                                                                                            total
                                                                                    );

                                                                                    SwingUtilities.invokeLater(() -> {
                                                                                        statusLabel.setText(
                                                                                                message
                                                                                                        + "  "
                                                                                                        + current
                                                                                                        + "/"
                                                                                                        + total
                                                                                        );
                                                                                    });
                                                                                }
                                                                        );
                                                            }

                                                            setLauncherProgress(
                                                                    "Preparing Minecraft "
                                                                            + technicResult.getMinecraftVersion()
                                                                            + "...",
                                                                    0,
                                                                    0
                                                            );

                                                            ensureMinecraftVersionInstalled(
                                                                    technicResult.getMinecraftVersion()
                                                            );

                                                            setLauncherProgress(
                                                                    "Creating Technic version...",
                                                                    0,
                                                                    0
                                                            );

                                                            TechnicLegacyVersionInstaller legacyInstaller =
                                                                    new TechnicLegacyVersionInstaller();

                                                            String installedVersionId =
                                                                    legacyInstaller.install(
                                                                            minecraftLauncherDirectory(),
                                                                            technicResult.getInstanceDirectory(),
                                                                            selected.getProjectId(),
                                                                            chosen.getVersionId(),
                                                                            technicResult.getMinecraftVersion()
                                                                    );

                                                            setLauncherProgress(
                                                                    "Refreshing Minecraft versions...",
                                                                    0,
                                                                    0
                                                            );

                                                            launcher
                                                                    .getLauncher()
                                                                    .getVersionManager()
                                                                    .refreshVersions();

                                                            setLauncherProgress(
                                                                    "Creating Technic profile...",
                                                                    0,
                                                                    0
                                                            );

                                                            createTechnicProfile(
                                                                    selected.getName(),
                                                                    technicResult.getInstanceDirectory(),
                                                                    installedVersionId
                                                            );

                                                            SwingUtilities.invokeLater(() -> {

                                                                hideLauncherProgress();

                                                                finishLauncherInstallationState();

                                                                JOptionPane.showMessageDialog(
                                                                        launcher.getFrame(),
                                                                        "Technic modpack installed successfully!\n\n"
                                                                                + "Instance:\n"
                                                                                + technicResult
                                                                                .getInstanceDirectory()
                                                                                .getAbsolutePath()
                                                                                + "\n\nMinecraft: "
                                                                                + technicResult.getMinecraftVersion()
                                                                                + "\nVersion: "
                                                                                + installedVersionId,
                                                                        "Install Modpack",
                                                                        JOptionPane.INFORMATION_MESSAGE
                                                                );

                                                                installThread = null;
                                                                cancelButton.setEnabled(true);

                                                                finishSearch();
                                                            });

                                                            return;
                                                        }

                                                        activeInstaller =
                                                                new ModrinthModpackInstaller();

                                                        ModrinthModpackInstaller.InstallResult result =
                                                                activeInstaller.install(
                                                                        minecraftLauncherDirectory(),
                                                                        selected,
                                                                        chosen,
                                                                        (message, current, total) -> {

                                                                            setLauncherProgress(
                                                                                    message,
                                                                                    current,
                                                                                    total
                                                                            );

                                                                            SwingUtilities.invokeLater(() -> {

                                                                                statusLabel.setText(
                                                                                        message
                                                                                                + "  "
                                                                                                + current
                                                                                                + "/"
                                                                                                + total
                                                                                );
                                                                            });
                                                                        }
                                                                );

                                                        ModLoaderInstaller loaderInstaller =
                                                                new ModLoaderInstaller();

                                                        String installedVersionId =
                                                                loaderInstaller.install(
                                                                        minecraftLauncherDirectory(),
                                                                        result.getMinecraftVersion(),
                                                                        result.getLoader(),
                                                                        result.getLoaderVersion(),
                                                                        (message, current, total) -> {

                                                                            setLauncherProgress(
                                                                                    message,
                                                                                    current,
                                                                                    total
                                                                            );

                                                                            SwingUtilities.invokeLater(() -> {
                                                                                statusLabel.setText(message);
                                                                            });
                                                                        }
                                                                );

                                                        setLauncherProgress(
                                                                "Refreshing Minecraft versions...",
                                                                0,
                                                                0
                                                        );

                                                        launcher
                                                                .getLauncher()
                                                                .getVersionManager()
                                                                .refreshVersions();

                                                        setLauncherProgress(
                                                                "Creating modpack profile...",
                                                                0,
                                                                0
                                                        );

                                                        createModpackProfile(
                                                                selected.getName(),
                                                                result.getInstanceDirectory(),
                                                                installedVersionId
                                                        );

                                                        SwingUtilities.invokeLater(() -> {

                                                            setLauncherProgress(
                                                                    "Installed " + selected.getName(),
                                                                    1,
                                                                    1
                                                            );

                                                            finishLauncherInstallationState();

                                                            JOptionPane.showMessageDialog(
                                                                    launcher.getFrame(),

                                                                    "Modpack files installed successfully!\n\n"
                                                                            + "Instance:\n"
                                                                            + result
                                                                            .getInstanceDirectory()
                                                                            .getAbsolutePath()
                                                                            + "\n\nMinecraft: "
                                                                            + result
                                                                            .getMinecraftVersion()
                                                                            + "\nLoader: "
                                                                            + result
                                                                            .getLoader()
                                                                            + " "
                                                                            + result
                                                                            .getLoaderVersion(),

                                                                    "Install Modpack",
                                                                    JOptionPane.INFORMATION_MESSAGE
                                                            );

                                                            Timer timer =
                                                                    new Timer(
                                                                            2000,
                                                                            e -> hideLauncherProgress()
                                                                    );

                                                            timer.setRepeats(false);
                                                            timer.start();

                                                            activeInstaller = null;
                                                            installThread = null;

                                                            finishSearch();
                                                        });

                                                    } catch (Exception ex) {

                                                        if (
                                                                installCancelled
                                                                        || ex instanceof InterruptedException
                                                        ) {

                                                            SwingUtilities.invokeLater(() -> {

                                                                hideLauncherProgress();

                                                                finishLauncherInstallationState();

                                                                activeInstaller = null;
                                                                installThread = null;
                                                            });

                                                            return;
                                                        }

                                                        ex.printStackTrace();

                                                        SwingUtilities.invokeLater(() -> {

                                                            hideLauncherProgress();

                                                            finishLauncherInstallationState();

                                                            JOptionPane.showMessageDialog(
                                                                    launcher.getFrame(),
                                                                    "Could not install modpack:\n"
                                                                            + ex.getMessage(),
                                                                    "Installation Error",
                                                                    JOptionPane.ERROR_MESSAGE
                                                            );

                                                            activeInstaller = null;
                                                            installThread = null;
                                                        });
                                                    }
                                                });

                                        SwingUserInterface ui =
                                                getSwingUserInterface();

                                        if (ui != null) {

                                            ui.beginModpackInstallation(() -> {

                                                installCancelled = true;

                                                if (activeInstaller != null) {
                                                    activeInstaller.cancel();
                                                }

                                                Thread thread =
                                                        installThread;

                                                if (thread != null) {
                                                    thread.interrupt();
                                                }

                                                setLauncherProgress(
                                                        "Cancelling installation...",
                                                        0,
                                                        0
                                                );
                                            });
                                        }

                                        dispose();

                                        installThread.setName(
                                                "Modpack Installer"
                                        );

                                        installThread.setDaemon(true);
                                        installThread.start();
                                    }

                                    statusLabel.setText(" ");

                                    installButton.setEnabled(
                                            resultList.getSelectedValue() != null
                                    );
                                }
                            });

                        } catch (final Exception ex) {

                            SwingUtilities.invokeLater(new Runnable() {

                                @Override
                                public void run() {

                                    JOptionPane.showMessageDialog(
                                            InstallModpackPopup.this,
                                            "Could not load modpack versions:\n"
                                                    + ex.getMessage(),
                                            "Version Error",
                                            JOptionPane.ERROR_MESSAGE
                                    );

                                    statusLabel.setText(
                                            "Could not load versions."
                                    );

                                    installButton.setEnabled(true);
                                }
                            });
                        }
                    }
                });
                versionThread.setName("Modpack Versions");
                versionThread.setDaemon(true);
                versionThread.start();
            }
        });
    }

    private void search() {

        final ModpackProvider provider =
                (ModpackProvider) providerBox.getSelectedItem();

        final String query = searchField.getText().trim();

        if (provider == null) {
            return;
        }

        if (query.isEmpty()) {
            return;
        }

        resultModel.clear();

        searchButton.setEnabled(false);
        providerBox.setEnabled(false);
        searchField.setEnabled(false);
        installButton.setEnabled(false);

        statusLabel.setText(
                "Searching " + provider.getName() + "..."
        );

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    final List<ModpackSearchResult> results =
                            provider.search(query);

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {

                            for (ModpackSearchResult result : results) {
                                resultModel.addElement(result);
                            }

                            statusLabel.setText(
                                    results.size()
                                            + " modpack(s) found."
                            );

                            finishSearch();
                        }
                    });

                } catch (final Exception ex) {

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {

                            statusLabel.setText(
                                    "Search failed."
                            );

                            JOptionPane.showMessageDialog(
                                    InstallModpackPopup.this,
                                    "Could not search "
                                            + provider.getName()
                                            + ":\n"
                                            + ex.getMessage(),
                                    "Search Error",
                                    JOptionPane.ERROR_MESSAGE
                            );

                            finishSearch();
                        }
                    });
                }
            }
        });

        thread.setName("Modpack Search");
        thread.setDaemon(true);
        thread.start();
    }

    private void finishSearch() {

        searchButton.setEnabled(true);
        providerBox.setEnabled(true);
        searchField.setEnabled(true);

        installButton.setEnabled(
                resultList.getSelectedValue() != null
        );
    }

    public static void showInstallModpackDialog(
            Launcher launcher
    ) {

        InstallModpackPopup popup =
                new InstallModpackPopup(launcher);

        popup.setVisible(true);
    }
}