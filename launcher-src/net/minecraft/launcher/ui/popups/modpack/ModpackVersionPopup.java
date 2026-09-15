package net.minecraft.launcher.ui.popups.modpack;

import net.minecraft.launcher.modpacks.ModpackSearchResult;
import net.minecraft.launcher.modpacks.ModpackVersion;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class ModpackVersionPopup extends JDialog {

    private static final long serialVersionUID = 1L;

    private ModpackVersion selectedVersion;

    private final JList<ModpackVersion> versionList;

    public ModpackVersionPopup(
            Window owner,
            ModpackSearchResult modpack,
            List<ModpackVersion> versions
    ) {

        super(
                owner,
                "Choose Modpack Version",
                ModalityType.APPLICATION_MODAL
        );

        DefaultListModel<ModpackVersion> model =
                new DefaultListModel<>();

        for (ModpackVersion version : versions) {
            model.addElement(version);
        }

        versionList = new JList<>(model);

        versionList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        versionList.setCellRenderer(
                new VersionRenderer()
        );

        versionList.setFixedCellHeight(58);

        createInterface(modpack);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 430);
        setMinimumSize(new Dimension(500, 350));
        setLocationRelativeTo(owner);
    }

    private void createInterface(
            ModpackSearchResult modpack
    ) {

        setLayout(new BorderLayout(8, 8));

        JPanel header =
                new JPanel(new BorderLayout());

        header.setBorder(
                new EmptyBorder(10, 10, 0, 10)
        );

        JLabel title =
                new JLabel(modpack.getName());

        title.setFont(
                title.getFont()
                        .deriveFont(Font.BOLD, 16f)
        );

        JLabel description =
                new JLabel(
                        "Choose the version you want to install."
                );

        JPanel titlePanel = new JPanel();

        titlePanel.setOpaque(false);

        titlePanel.setLayout(
                new BoxLayout(
                        titlePanel,
                        BoxLayout.Y_AXIS
                )
        );

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(3));
        titlePanel.add(description);

        header.add(
                titlePanel,
                BorderLayout.CENTER
        );

        add(header, BorderLayout.NORTH);

        JScrollPane scroll =
                new JScrollPane(versionList);

        scroll.setBorder(
                BorderFactory.createTitledBorder(
                        "Available Versions"
                )
        );

        JPanel center =
                new JPanel(new BorderLayout());

        center.setBorder(
                new EmptyBorder(5, 10, 5, 10)
        );

        center.add(
                scroll,
                BorderLayout.CENTER
        );

        add(center, BorderLayout.CENTER);

        JButton cancelButton =
                new JButton("Cancel");

        JButton installButton =
                new JButton("Install");

        installButton.setEnabled(false);

        versionList.addListSelectionListener(e -> {

            if (!e.getValueIsAdjusting()) {

                installButton.setEnabled(
                        versionList.getSelectedValue()
                                != null
                );
            }
        });

        cancelButton.addActionListener(
                e -> dispose()
        );

        installButton.addActionListener(e -> {

            selectedVersion =
                    versionList.getSelectedValue();

            dispose();
        });

        versionList.addMouseListener(
                new java.awt.event.MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            java.awt.event.MouseEvent e
                    ) {

                        if (
                                e.getClickCount() == 2
                                        && versionList
                                        .getSelectedValue()
                                        != null
                        ) {

                            selectedVersion =
                                    versionList
                                            .getSelectedValue();

                            dispose();
                        }
                    }
                }
        );

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );

        buttons.add(cancelButton);
        buttons.add(installButton);

        add(buttons, BorderLayout.SOUTH);
    }

    public ModpackVersion getSelectedVersion() {
        return selectedVersion;
    }

    public static ModpackVersion showVersionDialog(
            Window owner,
            ModpackSearchResult modpack,
            List<ModpackVersion> versions
    ) {

        ModpackVersionPopup popup =
                new ModpackVersionPopup(
                        owner,
                        modpack,
                        versions
                );

        popup.setVisible(true);

        return popup.getSelectedVersion();
    }

    private static class VersionRenderer
            extends JPanel
            implements ListCellRenderer<ModpackVersion> {

        private static final long serialVersionUID = 1L;

        private final JLabel nameLabel =
                new JLabel();

        private final JLabel detailsLabel =
                new JLabel();

        public VersionRenderer() {

            setLayout(
                    new BorderLayout()
            );

            setBorder(
                    new EmptyBorder(
                            7,
                            8,
                            7,
                            8
                    )
            );

            JPanel text =
                    new JPanel();

            text.setOpaque(false);

            text.setLayout(
                    new BoxLayout(
                            text,
                            BoxLayout.Y_AXIS
                    )
            );

            nameLabel.setFont(
                    nameLabel
                            .getFont()
                            .deriveFont(
                                    Font.BOLD,
                                    13f
                            )
            );

            detailsLabel.setFont(
                    detailsLabel
                            .getFont()
                            .deriveFont(11f)
            );

            text.add(nameLabel);
            text.add(
                    Box.createVerticalStrut(4)
            );
            text.add(detailsLabel);

            add(
                    text,
                    BorderLayout.CENTER
            );
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ModpackVersion> list,
                ModpackVersion value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {

            nameLabel.setText(
                    value.getName()
            );

            String minecraft =
                    value.getMinecraftVersion();

            String loader =
                    formatLoader(
                            value.getLoader()
                    );

            StringBuilder details =
                    new StringBuilder();

            if (
                    minecraft != null
                            && !minecraft.isEmpty()
            ) {

                details.append(
                        "Minecraft "
                );

                details.append(
                        minecraft
                );
            }

            if (
                    loader != null
                            && !loader.isEmpty()
            ) {

                if (details.length() > 0) {
                    details.append("  |  ");
                }

                details.append(loader);
            }

            detailsLabel.setText(
                    details.toString()
            );

            if (isSelected) {

                setBackground(
                        list.getSelectionBackground()
                );

                nameLabel.setForeground(
                        list.getSelectionForeground()
                );

                detailsLabel.setForeground(
                        list.getSelectionForeground()
                );

            } else {

                setBackground(
                        list.getBackground()
                );

                nameLabel.setForeground(
                        list.getForeground()
                );

                detailsLabel.setForeground(
                        UIManager.getColor(
                                "Label.disabledForeground"
                        )
                );
            }

            setOpaque(true);

            return this;
        }

        private String formatLoader(
                String loader
        ) {

            if (
                    loader == null
                            || loader.isEmpty()
            ) {
                return "";
            }

            if (
                    loader.equalsIgnoreCase(
                            "neoforge"
                    )
            ) {
                return "NeoForge";
            }

            if (
                    loader.equalsIgnoreCase(
                            "fabric"
                    )
            ) {
                return "Fabric";
            }

            if (
                    loader.equalsIgnoreCase(
                            "forge"
                    )
            ) {
                return "Forge";
            }

            if (
                    loader.equalsIgnoreCase(
                            "quilt"
                    )
            ) {
                return "Quilt";
            }

            return loader;
        }
    }
}