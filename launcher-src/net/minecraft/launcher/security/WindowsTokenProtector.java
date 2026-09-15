package net.minecraft.launcher.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;

public final class WindowsTokenProtector {

    private static final String PREFIX = "dpapi:";

    private WindowsTokenProtector() {
    }

    public static String protect(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        if (!Platform.isWindows()) {
            throw new IllegalStateException(
                    "Secure token storage is only available on Windows"
            );
        }

        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);

        try {
            byte[] encrypted = Crypt32Util.cryptProtectData(plaintext);

            return PREFIX + Base64.getEncoder().encodeToString(encrypted);

        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }

    public static String unprotect(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        if (!value.startsWith(PREFIX)) {
            return value;
        }

        if (!Platform.isWindows()) {
            throw new IllegalStateException(
                    "Cannot decrypt Windows-protected token on this platform"
            );
        }

        byte[] encrypted = Base64.getDecoder().decode(
                value.substring(PREFIX.length())
        );

        byte[] decrypted = null;

        try {
            decrypted = Crypt32Util.cryptUnprotectData(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } finally {
            java.util.Arrays.fill(encrypted, (byte) 0);

            if (decrypted != null) {
                java.util.Arrays.fill(decrypted, (byte) 0);
            }
        }
    }
}