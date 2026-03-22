package planetmap;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Main application window for the planet map generator.
 */
public class PlanetMapApp extends JFrame {

    private final JLabel mapLabel;
    private final JLabel seedLabel;
    private final PlanetGenerator generator;
    private long currentSeed;
    private BufferedImage currentFlatMap;
    private BufferedImage currentDisplay;
    private boolean sphereView = true;
    private double rotationDeg = 0;
    private long starSeed;

    public PlanetMapApp() {
        super("Planet Map Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        generator = new PlanetGenerator(2048, 1024);
        starSeed = System.nanoTime();

        // Map display
        mapLabel = new JLabel();
        mapLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mapLabel.setBackground(Color.BLACK);
        mapLabel.setOpaque(true);

        JScrollPane scrollPane = new JScrollPane(mapLabel);
        scrollPane.getViewport().setBackground(Color.BLACK);
        scrollPane.setBorder(null);

        // Controls panel
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));

        JButton generateButton = new JButton("Generate New Planet");
        generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        generateButton.addActionListener(e -> generateNewMap());

        JToggleButton viewToggle = new JToggleButton("Sphere View", true);
        viewToggle.addActionListener(e -> {
            sphereView = viewToggle.isSelected();
            viewToggle.setText(sphereView ? "Sphere View" : "Flat View");
            updateDisplay();
        });

        JButton rotLeftBtn = new JButton("\u25C0 Rotate");
        rotLeftBtn.addActionListener(e -> {
            rotationDeg = (rotationDeg - 30 + 360) % 360;
            updateDisplay();
        });

        JButton rotRightBtn = new JButton("Rotate \u25B6");
        rotRightBtn.addActionListener(e -> {
            rotationDeg = (rotationDeg + 30) % 360;
            updateDisplay();
        });

        JLabel seedInputLabel = new JLabel("Seed:");
        JTextField seedField = new JTextField(14);
        seedField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JButton seedButton = new JButton("Use Seed");
        seedButton.addActionListener(e -> {
            try {
                long seed = Long.parseLong(seedField.getText().trim());
                generateMap(seed);
            } catch (NumberFormatException ex) {
                generateMap(seedField.getText().trim().hashCode());
            }
        });

        seedLabel = new JLabel("Seed: \u2014");
        seedLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton saveButton = new JButton("Save PNG");
        saveButton.addActionListener(e -> saveImage());

        controls.add(generateButton);
        controls.add(viewToggle);
        controls.add(rotLeftBtn);
        controls.add(rotRightBtn);
        controls.add(Box.createHorizontalStrut(10));
        controls.add(seedInputLabel);
        controls.add(seedField);
        controls.add(seedButton);
        controls.add(saveButton);
        controls.add(seedLabel);

        // Layout
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        // Size window for sphere view (square + controls)
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int sphereSize = Math.min(1024, screenSize.height - 120);
        setPreferredSize(new Dimension(sphereSize + 20, sphereSize + 70));
        pack();
        setLocationRelativeTo(null);

        generateNewMap();
    }

    private void generateNewMap() {
        long seed = System.nanoTime();
        generateMap(seed);
    }

    private void generateMap(long seed) {
        currentSeed = seed;
        starSeed = seed + 99999;
        seedLabel.setText("Seed: " + seed);

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return generator.generate(seed);
            }

            @Override
            protected void done() {
                try {
                    currentFlatMap = get();
                    updateDisplay();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PlanetMapApp.this,
                            "Error generating map: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateDisplay() {
        if (currentFlatMap == null) return;

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                if (sphereView) {
                    int sphereSize = Math.min(1024, Math.min(getWidth() - 20, getHeight() - 70));
                    sphereSize = Math.max(256, sphereSize);
                    return SphereRenderer.render(currentFlatMap, sphereSize, rotationDeg, starSeed);
                } else {
                    return currentFlatMap;
                }
            }

            @Override
            protected void done() {
                try {
                    currentDisplay = get();
                    mapLabel.setIcon(new ImageIcon(currentDisplay));
                    mapLabel.revalidate();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void saveImage() {
        if (currentDisplay == null) return;

        JFileChooser chooser = new JFileChooser();
        String suffix = sphereView ? "_sphere" : "_flat";
        chooser.setSelectedFile(new java.io.File("planet_" + currentSeed + suffix + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                javax.imageio.ImageIO.write(currentDisplay, "PNG", chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Map saved successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Planet Map Generator");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            new PlanetMapApp().setVisible(true);
        });
    }
}
