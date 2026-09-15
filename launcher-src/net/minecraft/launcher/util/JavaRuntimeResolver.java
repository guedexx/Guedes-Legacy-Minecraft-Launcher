package net.minecraft.launcher.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.updater.CompleteMinecraftVersion;

public final class JavaRuntimeResolver {

    private JavaRuntimeResolver() {
    }

    public static String resolveJavaExecutable(
            Profile profile,
            CompleteMinecraftVersion minecraftVersion
    ) {

        String version =
                resolveMinecraftVersion(minecraftVersion);

        int requiredMajor =
                requiredJavaMajor(version);

        if (profile != null) {

            String configured =
                    profile.getJavaPath();

            if (
                    configured != null
                            && !configured.trim().isEmpty()
            ) {

                File configuredFile =
                        new File(configured);

                if (
                        configuredFile.isFile()
                                && detectJavaMajor(configuredFile)
                                == requiredMajor
                ) {
                    return configuredFile.getAbsolutePath();
                }
            }
        }

        File found =
                findInstalledJava(requiredMajor);

        if (found != null) {
            return found.getAbsolutePath();
        }

        String currentJavaHome =
                System.getProperty("java.home");

        if (currentJavaHome != null) {

            File currentJava =
                    javaExecutableFromHome(
                            new File(currentJavaHome)
                    );

            if (
                    currentJava != null
                            && detectJavaMajor(currentJava)
                            == requiredMajor
            ) {
                return currentJava.getAbsolutePath();
            }
        }

        return "javaw";
    }

    private static String resolveMinecraftVersion(
            CompleteMinecraftVersion version
    ) {

        if (version == null) {
            return null;
        }

        CompleteMinecraftVersion original =
                version.getSavableVersion();

        if (original != null) {

            String inherited =
                    original.getInheritsFrom();

            if (
                    inherited != null
                            && !inherited.trim().isEmpty()
            ) {
                return inherited.trim();
            }
        }

        return version.getId();
    }

    private static int requiredJavaMajor(
            String minecraftVersion
    ) {

        if (
                minecraftVersion == null
                        || minecraftVersion.trim().isEmpty()
        ) {
            return 21;
        }

        int[] version =
                parseMinecraftVersion(
                        minecraftVersion
                );

        int major = version[0];
        int minor = version[1];
        int patch = version[2];

        if (major > 1) {
            return 21;
        }

        if (minor > 20) {
            return 21;
        }

        if (
                minor == 20
                        && patch >= 5
        ) {
            return 21;
        }

        if (minor >= 18) {
            return 17;
        }

        if (minor == 17) {
            return 16;
        }

        return 8;
    }

    private static int[] parseMinecraftVersion(
            String version
    ) {

        int[] result =
                new int[] {
                        1,
                        21,
                        0
                };

        try {

            String clean =
                    version.trim();

            String[] parts =
                    clean.split("\\.");

            if (parts.length >= 2) {

                result[0] =
                        parseLeadingNumber(
                                parts[0]
                        );

                result[1] =
                        parseLeadingNumber(
                                parts[1]
                        );

                if (parts.length >= 3) {

                    result[2] =
                            parseLeadingNumber(
                                    parts[2]
                            );

                } else {

                    result[2] = 0;
                }
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    private static int parseLeadingNumber(
            String value
    ) {

        if (value == null) {
            return 0;
        }

        StringBuilder number =
                new StringBuilder();

        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            char c =
                    value.charAt(i);

            if (Character.isDigit(c)) {

                number.append(c);

            } else {

                break;
            }
        }

        if (number.length() == 0) {
            return 0;
        }

        return Integer.parseInt(
                number.toString()
        );
    }

    private static File findInstalledJava(
            int requiredMajor
    ) {

        List<File> candidates =
                new ArrayList<File>();

        String javaHome =
                System.getenv("JAVA_HOME");

        if (javaHome != null) {

            addCandidate(
                    candidates,
                    javaExecutableFromHome(
                            new File(javaHome)
                    )
            );
        }

        String currentJavaHome =
                System.getProperty("java.home");

        if (currentJavaHome != null) {

            addCandidate(
                    candidates,
                    javaExecutableFromHome(
                            new File(currentJavaHome)
                    )
            );
        }

        String[] bases =
                new String[] {
                        System.getenv("ProgramFiles"),
                        System.getenv("ProgramFiles(x86)"),
                        "C:\\Program Files",
                        "C:\\Program Files (x86)"
                };

        for (String base : bases) {

            if (base == null) {
                continue;
            }

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Java"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Eclipse Adoptium"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Microsoft"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Zulu"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Amazon Corretto"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "BellSoft"
                    )
            );

            addJavaInstallations(
                    candidates,
                    new File(
                            base,
                            "Semeru"
                    )
            );
        }

        for (File candidate : candidates) {

            if (
                    detectJavaMajor(candidate)
                            == requiredMajor
            ) {
                return candidate;
            }
        }

        return null;
    }

    private static void addCandidate(
            List<File> output,
            File candidate
    ) {

        if (
                candidate != null
                        && candidate.isFile()
                        && !output.contains(candidate)
        ) {
            output.add(candidate);
        }
    }

    private static void addJavaInstallations(
            List<File> output,
            File root
    ) {

        if (
                root == null
                        || !root.isDirectory()
        ) {
            return;
        }

        File[] children =
                root.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {

            if (!child.isDirectory()) {
                continue;
            }

            addCandidate(
                    output,
                    javaExecutableFromHome(
                            child
                    )
            );
        }
    }

    private static File javaExecutableFromHome(
            File javaHome
    ) {

        if (javaHome == null) {
            return null;
        }

        File javaw =
                new File(
                        javaHome,
                        "bin/javaw.exe"
                );

        if (javaw.isFile()) {
            return javaw;
        }

        File java =
                new File(
                        javaHome,
                        "bin/java.exe"
                );

        if (java.isFile()) {
            return java;
        }

        File unixJava =
                new File(
                        javaHome,
                        "bin/java"
                );

        if (unixJava.isFile()) {
            return unixJava;
        }

        File macJava =
                new File(
                        javaHome,
                        "Contents/Home/bin/java"
                );

        if (macJava.isFile()) {
            return macJava;
        }

        return null;
    }

    private static int detectJavaMajor(
            File javaExecutable
    ) {

        Process process = null;

        try {

            process =
                    new ProcessBuilder(
                            javaExecutable
                                    .getAbsolutePath(),
                            "-version"
                    )
                            .redirectErrorStream(true)
                            .start();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );

            String line =
                    reader.readLine();

            process.waitFor();

            if (line == null) {
                return -1;
            }

            int firstQuote =
                    line.indexOf('"');

            int secondQuote =
                    line.indexOf(
                            '"',
                            firstQuote + 1
                    );

            if (
                    firstQuote < 0
                            || secondQuote <= firstQuote
            ) {
                return -1;
            }

            String version =
                    line.substring(
                            firstQuote + 1,
                            secondQuote
                    );

            String[] parts =
                    version.split(
                            "[._+-]"
                    );

            if (parts.length == 0) {
                return -1;
            }

            int major =
                    Integer.parseInt(
                            parts[0]
                    );

            if (
                    major == 1
                            && parts.length > 1
            ) {

                major =
                        Integer.parseInt(
                                parts[1]
                        );
            }

            return major;

        } catch (Exception ignored) {

            return -1;

        } finally {

            if (process != null) {
                process.destroy();
            }
        }
    }
}