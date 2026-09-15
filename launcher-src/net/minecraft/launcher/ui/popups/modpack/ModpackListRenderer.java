package net.minecraft.launcher.ui.popups.modpack;

import net.minecraft.launcher.modpacks.ModpackSearchResult;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;

import java.text.NumberFormat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModpackListRenderer
        extends JPanel
        implements ListCellRenderer<ModpackSearchResult> {

    private static final long serialVersionUID = 1L;

    private static final Map<String, ImageIcon> ICON_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, Boolean> LOADING_ICONS =
            new ConcurrentHashMap<>();

    private static final ExecutorService ICON_EXECUTOR =
            Executors.newFixedThreadPool(6);

    private final JLabel iconLabel =
            new JLabel();

    private final JLabel nameLabel =
            new JLabel();

    private final JLabel authorLabel =
            new JLabel();

    private final JLabel descriptionLabel =
            new JLabel();

    private final JLabel infoLabel =
            new JLabel();

    public ModpackListRenderer() {

        setLayout(new BorderLayout(10, 5));

        setBorder(
                new EmptyBorder(
                        8,
                        8,
                        8,
                        8
                )
        );

        iconLabel.setPreferredSize(
                new Dimension(64, 64)
        );

        iconLabel.setMinimumSize(
                new Dimension(64, 64)
        );

        iconLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        iconLabel.setVerticalAlignment(
                SwingConstants.CENTER
        );

        add(iconLabel, BorderLayout.WEST);

        JPanel textPanel =
                new JPanel();

        textPanel.setOpaque(false);

        textPanel.setLayout(
                new BoxLayout(
                        textPanel,
                        BoxLayout.Y_AXIS
                )
        );

        nameLabel.setFont(
                nameLabel
                        .getFont()
                        .deriveFont(Font.BOLD, 14f)
        );

        authorLabel.setFont(
                authorLabel
                        .getFont()
                        .deriveFont(11f)
        );

        descriptionLabel.setFont(
                descriptionLabel
                        .getFont()
                        .deriveFont(11f)
        );

        infoLabel.setFont(
                infoLabel
                        .getFont()
                        .deriveFont(10f)
        );

        textPanel.add(nameLabel);
        textPanel.add(authorLabel);
        textPanel.add(
                Box.createVerticalStrut(3)
        );
        textPanel.add(descriptionLabel);
        textPanel.add(
                Box.createVerticalStrut(4)
        );
        textPanel.add(infoLabel);

        add(textPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends ModpackSearchResult> list,
            ModpackSearchResult value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
    ) {

        if (value == null) {
            return this;
        }

        nameLabel.setText(
                value.getName()
        );

        authorLabel.setText(
                "by " + value.getAuthor()
        );

        descriptionLabel.setText(
                createDescription(
                        value.getDescription()
                )
        );

        infoLabel.setText(
                createInfoText(value)
        );

        loadIcon(
                list,
                value.getIconUrl(),
                value.getProvider()
        );

        if (isSelected) {

            setBackground(
                    list.getSelectionBackground()
            );

            nameLabel.setForeground(
                    list.getSelectionForeground()
            );

            authorLabel.setForeground(
                    list.getSelectionForeground()
            );

            descriptionLabel.setForeground(
                    list.getSelectionForeground()
            );

            infoLabel.setForeground(
                    list.getSelectionForeground()
            );

        } else {

            setBackground(
                    list.getBackground()
            );

            nameLabel.setForeground(
                    list.getForeground()
            );

            authorLabel.setForeground(
                    UIManager.getColor(
                            "Label.foreground"
                    )
            );

            descriptionLabel.setForeground(
                    UIManager.getColor(
                            "Label.foreground"
                    )
            );

            infoLabel.setForeground(
                    UIManager.getColor(
                            "Label.disabledForeground"
                    )
            );
        }

        setOpaque(true);

        return this;
    }

    private String createDescription(
            String description
    ) {

        if (
                description == null
                        || description.trim().isEmpty()
        ) {

            return " ";
        }

        description =
                description.trim();

        if (description.length() > 100) {

            description =
                    description.substring(
                            0,
                            97
                    )
                            + "...";
        }

        return "<html>"
                + escapeHtml(description)
                + "</html>";
    }

    private String createInfoText(
            ModpackSearchResult result
    ) {

        StringBuilder builder =
                new StringBuilder();

        String minecraft =
                formatMinecraftVersions(
                        result.getMinecraftVersions()
                );

        if (!minecraft.isEmpty()) {

            builder.append("Minecraft ");
            builder.append(minecraft);
        }

        String loaders =
                formatLoaders(
                        result.getLoaders()
                );

        if (!loaders.isEmpty()) {

            if (builder.length() > 0) {
                builder.append("  |  ");
            }

            builder.append(loaders);
        }

        if (builder.length() > 0) {
            builder.append("  |  ");
        }

        builder.append(
                NumberFormat
                        .getIntegerInstance(Locale.US)
                        .format(result.getDownloads())
        );

        builder.append(" downloads");

        String updated =
                formatUpdated(
                        result.getUpdated()
                );

        if (!updated.isEmpty()) {

            builder.append("  |  Updated ");
            builder.append(updated);
        }

        return builder.toString();
    }

    private String formatMinecraftVersions(
            List<String> versions
    ) {

        if (
                versions == null
                        || versions.isEmpty()
        ) {

            return "";
        }

        StringBuilder builder =
                new StringBuilder();

        int amount =
                Math.min(3, versions.size());

        for (int i = 0; i < amount; i++) {

            if (i > 0) {
                builder.append(", ");
            }

            builder.append(
                    versions.get(i)
            );
        }

        if (versions.size() > amount) {
            builder.append("...");
        }

        return builder.toString();
    }

    private String formatLoaders(
            List<String> loaders
    ) {

        if (
                loaders == null
                        || loaders.isEmpty()
        ) {

            return "";
        }

        StringBuilder builder =
                new StringBuilder();

        for (String loader : loaders) {

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(
                    capitalize(loader)
            );
        }

        return builder.toString();
    }

    private String capitalize(
            String value
    ) {

        if (
                value == null
                        || value.isEmpty()
        ) {

            return "";
        }

        return Character
                .toUpperCase(
                        value.charAt(0)
                )
                + value.substring(1);
    }

    private String formatUpdated(
            String updated
    ) {

        if (
                updated == null
                        || updated.isEmpty()
        ) {

            return "";
        }

        if (updated.length() >= 10) {
            return updated.substring(0, 10);
        }

        return updated;
    }

    private String escapeHtml(
            String text
    ) {

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void loadIcon(
            JList<?> list,
            String iconUrl,
            String provider) {

        iconLabel.setIcon(null);
        iconLabel.setText("");

        if (
                iconUrl == null
                        || iconUrl.trim().isEmpty()
        ) {

            if ("Technic".equalsIgnoreCase(provider)) {
                setTechnicDefaultIcon();
            } else {
                iconLabel.setText("No icon");
            }

            return;
        }

        ImageIcon cached =
                ICON_CACHE.get(iconUrl);

        if (cached != null) {
            iconLabel.setIcon(cached);
            return;
        }

        iconLabel.setText("...");

        if (
                LOADING_ICONS.putIfAbsent(
                        iconUrl,
                        true
                ) != null
        ) {
            return;
        }

        ICON_EXECUTOR.submit(() -> {

            try {

                URL url = new URL(iconUrl);

                HttpURLConnection connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("GET");

                connection.setRequestProperty(
                        "User-Agent",
                        "Guedes-Legacy-Launcher/1.6.94"
                );

                connection.setRequestProperty(
                        "Accept",
                        "image/png,image/jpeg,image/webp,image/*"
                );

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                try {

                    if (
                            connection.getResponseCode()
                                    != HttpURLConnection.HTTP_OK
                    ) {
                        return;
                    }

                    BufferedImage original =
                            ImageIO.read(
                                    connection.getInputStream()
                            );

                    if (original == null) {
                        return;
                    }

                    Image resized =
                            original.getScaledInstance(
                                    64,
                                    64,
                                    Image.SCALE_SMOOTH
                            );

                    ImageIcon icon =
                            new ImageIcon(resized);

                    ICON_CACHE.put(
                            iconUrl,
                            icon
                    );

                } finally {
                    connection.disconnect();
                }

            } catch (Exception ex) {

                ex.printStackTrace();

            } finally {

                LOADING_ICONS.remove(iconUrl);

                SwingUtilities.invokeLater(() -> {

                    list.repaint();

                });
            }
        });
    }

    private void setTechnicDefaultIcon() {

        try {

            URL resource =
                    ModpackListRenderer.class.getResource(
                            "/technic_default_icon.png"
                    );

            if (resource == null) {
                iconLabel.setText("No icon");

                System.err.println(
                        "[MODPACK ICON] technic_default_icon.png not found."
                );

                return;
            }

            BufferedImage original =
                    ImageIO.read(resource);

            if (original == null) {
                iconLabel.setText("No icon");
                return;
            }

            Image resized =
                    original.getScaledInstance(
                            64,
                            64,
                            Image.SCALE_SMOOTH
                    );

            iconLabel.setIcon(
                    new ImageIcon(resized)
            );

            iconLabel.setText("");

        } catch (Exception ex) {

            ex.printStackTrace();
            iconLabel.setText("No icon");
        }
    }
}