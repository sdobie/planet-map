package planetmap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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
    private double tiltDeg = 0;
    private long starSeed;
    private int dragStartX, dragStartY;
    private double dragStartRot, dragStartTilt;
    private boolean isDragging = false;
    private double zoomLevel = 1.0;

    // Render throttling
    private volatile boolean isRendering = false;
    private volatile boolean renderPending = false;
    private volatile boolean isGenerating = false;

    public PlanetMapApp() {
        super("Planet Map Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        generator = new PlanetGenerator(4096, 2048);
        starSeed = System.nanoTime();

        // Map display
        mapLabel = new JLabel();
        mapLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mapLabel.setBackground(Color.BLACK);
        mapLabel.setOpaque(true);

        // Mouse drag to rotate the sphere
        mapLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (sphereView && !isGenerating) {
                    isDragging = true;
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                    dragStartRot = rotationDeg;
                    dragStartTilt = tiltDeg;
                    mapLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging) {
                    isDragging = false;
                    mapLabel.setCursor(Cursor.getDefaultCursor());
                    // Full-res render on release
                    updateDisplay();
                }
            }
        });

        mapLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging && sphereView) {
                    int dx = e.getX() - dragStartX;
                    int dy = e.getY() - dragStartY;
                    rotationDeg = (dragStartRot + dx * 0.4 + 360) % 360;
                    tiltDeg = Math.max(-80, Math.min(80, dragStartTilt + dy * 0.4));
                    updateDisplay();
                }
            }
        });

        // Scroll wheel / trackpad pinch to zoom
        mapLabel.addMouseWheelListener(e -> {
            if (sphereView) {
                double scrollAmount = e.getPreciseWheelRotation();
                // Negative = zoom in, positive = zoom out
                zoomLevel *= Math.pow(1.05, -scrollAmount);
                zoomLevel = Math.max(1.0, Math.min(4.0, zoomLevel));
                updateDisplay();
            }
        });

        JScrollPane scrollPane = new JScrollPane(mapLabel);
        scrollPane.getViewport().setBackground(Color.BLACK);
        scrollPane.setBorder(null);

        // Controls panel
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));

        JButton generateButton = new JButton("Generate New Planet");
        generateButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        generateButton.addActionListener(e -> {
            if (!isGenerating) {
                generateNewMap();
            }
        });

        JToggleButton viewToggle = new JToggleButton("Sphere View", true);
        viewToggle.addActionListener(e -> {
            sphereView = viewToggle.isSelected();
            viewToggle.setText(sphereView ? "Sphere View" : "Flat View");
            updateDisplay();
        });

        JLabel seedInputLabel = new JLabel("Seed:");
        JTextField seedField = new JTextField(14);
        seedField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JButton seedButton = new JButton("Use Seed");
        seedButton.addActionListener(e -> {
            if (!isGenerating) {
                try {
                    long seed = Long.parseLong(seedField.getText().trim());
                    generateMap(seed);
                } catch (NumberFormatException ex) {
                    generateMap(seedField.getText().trim().hashCode());
                }
            }
        });

        seedLabel = new JLabel("Seed: \u2014");
        seedLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton saveButton = new JButton("Save PNG");
        saveButton.addActionListener(e -> saveImage());

        JButton resetViewBtn = new JButton("Reset View");
        resetViewBtn.addActionListener(e -> {
            rotationDeg = 0;
            tiltDeg = 0;
            zoomLevel = 1.0;
            updateDisplay();
        });

        controls.add(generateButton);
        controls.add(viewToggle);
        controls.add(resetViewBtn);
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
        isGenerating = true;
        currentSeed = seed;
        starSeed = seed + 99999;
        seedLabel.setText("Seed: " + seed + " (generating...)");

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                return generator.generate(seed);
            }

            @Override
            protected void done() {
                try {
                    currentFlatMap = get();
                    isGenerating = false;
                    seedLabel.setText("Seed: " + seed);
                    updateDisplay();
                } catch (Exception ex) {
                    isGenerating = false;
                    JOptionPane.showMessageDialog(PlanetMapApp.this,
                            "Error generating map: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void updateDisplay() {
        if (currentFlatMap == null || isGenerating) return;

        // If already rendering, just mark that we need another render
        if (isRendering) {
            renderPending = true;
            return;
        }

        isRendering = true;
        renderPending = false;

        // Capture current rotation state for this render
        final double rot = rotationDeg;
        final double tilt = tiltDeg;
        final boolean sphere = sphereView;
        final boolean dragging = isDragging;
        final double zm = zoomLevel;

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                if (sphere) {
                    int fullSize = Math.min(1024, Math.min(getWidth() - 20, getHeight() - 70));
                    fullSize = Math.max(256, fullSize);
                    // Render at half resolution while dragging for smoother interaction
                    if (dragging) {
                        int halfSize = fullSize / 2;
                        BufferedImage small = SphereRenderer.render(currentFlatMap, halfSize, rot, tilt, zm, starSeed);
                        // Scale up to full size
                        BufferedImage scaled = new BufferedImage(fullSize, fullSize, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2 = scaled.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(small, 0, 0, fullSize, fullSize, null);
                        g2.dispose();
                        return scaled;
                    }
                    return SphereRenderer.render(currentFlatMap, fullSize, rot, tilt, zm, starSeed);
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
                isRendering = false;
                // If rotation changed during render, do another render
                if (renderPending) {
                    updateDisplay();
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
