package planetmap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

/**
 * Main application window for the planet map generator.
 */
public class PlanetMapApp extends JFrame {

    private final JLabel mapLabel;
    private final JLabel seedLabel;
    private final PlanetGenerator generator;
    private long currentSeed;
    private BufferedImage currentMap;

    public PlanetMapApp() {
        super("Planet Map Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        generator = new PlanetGenerator(2048, 1024);

        // Map display
        mapLabel = new JLabel();
        mapLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mapLabel.setBackground(Color.BLACK);
        mapLabel.setOpaque(true);

        JScrollPane scrollPane = new JScrollPane(mapLabel);
        scrollPane.getViewport().setBackground(Color.BLACK);
        scrollPane.setBorder(null);

        // Controls panel
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));

        JButton generateButton = new JButton("Generate New Planet");
        generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        generateButton.addActionListener(e -> generateNewMap());

        JLabel seedInputLabel = new JLabel("Seed:");
        JTextField seedField = new JTextField(16);
        seedField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JButton seedButton = new JButton("Use Seed");
        seedButton.addActionListener(e -> {
            try {
                long seed = Long.parseLong(seedField.getText().trim());
                generateMap(seed);
            } catch (NumberFormatException ex) {
                // Try hashing the string as a seed
                generateMap(seedField.getText().trim().hashCode());
            }
        });

        seedLabel = new JLabel("Seed: —");
        seedLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton saveButton = new JButton("Save PNG");
        saveButton.addActionListener(e -> saveImage());

        controls.add(generateButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(seedInputLabel);
        controls.add(seedField);
        controls.add(seedButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(saveButton);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(seedLabel);

        // Layout
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        // Size the window to fit the map with controls
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int winW = Math.min(generator.getWidth() + 20, screenSize.width - 40);
        int winH = Math.min(generator.getHeight() + 80, screenSize.height - 40);
        setPreferredSize(new Dimension(winW, winH));
        pack();
        setLocationRelativeTo(null);

        // Generate initial map
        generateNewMap();
    }

    private void generateNewMap() {
        long seed = System.nanoTime();
        generateMap(seed);
    }

    private void generateMap(long seed) {
        currentSeed = seed;
        seedLabel.setText("Seed: " + seed);

        // Generate in background to keep UI responsive
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return generator.generate(seed);
            }

            @Override
            protected void done() {
                try {
                    currentMap = get();
                    mapLabel.setIcon(new ImageIcon(currentMap));
                    mapLabel.revalidate();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PlanetMapApp.this,
                            "Error generating map: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void saveImage() {
        if (currentMap == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("planet_" + currentSeed + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                javax.imageio.ImageIO.write(currentMap, "PNG", chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Map saved successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        // macOS look and feel tweaks
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
