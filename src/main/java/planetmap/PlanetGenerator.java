package planetmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Generates realistic planet maps using 3D simplex noise sampled on a sphere,
 * with domain warping, hillshading for 3D relief, and bimodal elevation.
 */
public class PlanetGenerator {

    private final int width;
    private final int height;

    // Water colors
    private static final Color DEEP_OCEAN        = new Color(15, 35, 90);
    private static final Color OCEAN             = new Color(25, 55, 130);
    private static final Color SHALLOW_WATER     = new Color(45, 85, 155);
    private static final Color SHORE_WATER       = new Color(65, 110, 170);

    // Land colors — olive/tan palette matching realistic planet renders
    private static final Color BEACH             = new Color(210, 200, 150);
    private static final Color SUBTROPICAL_DESERT = new Color(195, 175, 110);
    private static final Color DRY_GRASSLAND     = new Color(170, 165, 85);
    private static final Color GRASSLAND         = new Color(140, 155, 65);
    private static final Color TEMPERATE_FOREST  = new Color(75, 115, 50);
    private static final Color TROPICAL_FOREST   = new Color(50, 100, 40);
    private static final Color BOREAL_FOREST     = new Color(55, 80, 45);
    private static final Color TUNDRA            = new Color(160, 170, 150);
    private static final Color SNOW              = new Color(235, 242, 248);
    private static final Color ICE               = new Color(210, 225, 240);

    public PlanetGenerator() {
        this(1024, 512);
    }

    public PlanetGenerator(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public BufferedImage generate() {
        return generate(new Random().nextLong());
    }

    public BufferedImage generate(long seed) {
        // Independent noise generators for each role
        SimplexNoise continentNoise = new SimplexNoise(seed);
        SimplexNoise warpNoise1     = new SimplexNoise(seed + 1000);
        SimplexNoise warpNoise2     = new SimplexNoise(seed + 2000);
        SimplexNoise warpNoise3     = new SimplexNoise(seed + 3000);
        SimplexNoise detailNoise    = new SimplexNoise(seed + 4000);
        SimplexNoise moistNoise     = new SimplexNoise(seed + 6000);
        SimplexNoise tempNoise      = new SimplexNoise(seed + 7000);
        SimplexNoise mountainNoise  = new SimplexNoise(seed + 8000);

        double[][] elevation = new double[width][height];
        double[][] moisture = new double[width][height];
        double[][] temperature = new double[width][height];

        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);
            double absLat = Math.abs(sinLat);

            for (int px = 0; px < width; px++) {
                double lon = 2.0 * Math.PI * px / width;

                // 3D point on the unit sphere
                double sx = cosLat * Math.cos(lon);
                double sy = cosLat * Math.sin(lon);
                double sz = sinLat;

                // === CONTINENTALNESS: very low frequency, smooth ===
                double cont = continentNoise.fractal(sx, sy, sz, 2, 0.4, 2.0);

                // === DOMAIN WARPING ===
                double warpFreq = 0.8;
                double warpAmp = 0.45;
                double wx = warpNoise1.fractal(sx * warpFreq + 5.2, sy * warpFreq + 1.3, sz * warpFreq + 3.7, 2, 0.4, 2.0) * warpAmp;
                double wy = warpNoise2.fractal(sx * warpFreq + 9.1, sy * warpFreq + 4.8, sz * warpFreq + 7.2, 2, 0.4, 2.0) * warpAmp;
                double wz = warpNoise3.fractal(sx * warpFreq + 2.6, sy * warpFreq + 8.4, sz * warpFreq + 1.9, 2, 0.4, 2.0) * warpAmp;

                double wsx = sx + wx, wsy = sy + wy, wsz = sz + wz;

                // Level 2: coastline irregularity
                double w2Amp = 0.12;
                double w2x = warpNoise1.fractal(wsx * 2 + 20.1, wsy * 2 + 15.3, wsz * 2 + 10.7, 2, 0.4, 2.0) * w2Amp;
                double w2y = warpNoise2.fractal(wsx * 2 + 25.4, wsy * 2 + 18.9, wsz * 2 + 12.1, 2, 0.4, 2.0) * w2Amp;
                double w2z = warpNoise3.fractal(wsx * 2 + 30.8, wsy * 2 + 22.5, wsz * 2 + 14.3, 2, 0.4, 2.0) * w2Amp;

                double wwsx = wsx + w2x, wwsy = wsy + w2y, wwsz = wsz + w2z;

                // === TERRAIN DETAIL ===
                double terrain = detailNoise.fractal(wwsx * 3.5, wwsy * 3.5, wwsz * 3.5, 4, 0.4, 2.0);

                // === MOUNTAIN REGIONS ===
                double mt = mountainNoise.fractal(wsx * 1.8 + 50, wsy * 1.8 + 50, wsz * 1.8 + 50, 2, 0.4, 2.0);
                mt = smoothstep(Math.max(0, mt + 0.25));

                double mountainElev = mt * 0.9;

                // === COMBINE ===
                double e = cont * 0.55 + terrain * 0.10 + mountainElev * 0.35;
                elevation[px][py] = e;

                // === MOISTURE ===
                moisture[px][py] = moistNoise.fractal(wwsx * 2.5 + 40, wwsy * 2.5 + 40, wwsz * 2.5 + 40, 5, 0.5, 2.0);

                // === TEMPERATURE: latitude-based with significant noise to break horizontal bands ===
                double baseTemp = 1.0 - absLat;
                double tNoise = tempNoise.fractal(sx * 2, sy * 2, sz * 2, 3, 0.5, 2.0) * 0.25;
                temperature[px][py] = baseTemp * 0.75 + tNoise + 0.12;
            }
        }

        normalize(elevation);
        normalize(moisture);

        // Hypsometric curve
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                elevation[x][y] = hypsometric(elevation[x][y]);
            }
        }

        // Latitude effects
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));
            for (int px = 0; px < width; px++) {
                if (elevation[px][py] > 0.5) {
                    double landH = (elevation[px][py] - 0.5) / 0.5;
                    temperature[px][py] -= landH * 0.3;
                }
                if (elevation[px][py] > 0.45 && elevation[px][py] < 0.55) {
                    moisture[px][py] = Math.min(1.0, moisture[px][py] + 0.15);
                }
                if (absLat > 0.75) {
                    double polar = (absLat - 0.75) / 0.25;
                    moisture[px][py] *= (1.0 - polar * 0.5);
                }
            }
        }
        normalize(moisture);

        // === COMPUTE HILLSHADING ===
        // Light direction: upper-left (northwest), angled down at 45 degrees
        double lightAzimuth = Math.toRadians(315); // NW
        double lightAltitude = Math.toRadians(35);
        double lx = Math.cos(lightAltitude) * Math.cos(lightAzimuth);
        double ly = Math.cos(lightAltitude) * Math.sin(lightAzimuth);
        double lz = Math.sin(lightAltitude);

        double[][] hillshade = new double[width][height];
        double zFactor = 4.0; // exaggeration factor for relief

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                // Compute gradient using central differences, wrapping horizontally
                int xl = (px - 1 + width) % width;
                int xr = (px + 1) % width;
                int yt = Math.max(0, py - 1);
                int yb = Math.min(height - 1, py + 1);

                double dzdx = (elevation[xr][py] - elevation[xl][py]) * zFactor;
                double dzdy = (elevation[px][yt] - elevation[px][yb]) * zFactor;

                // Surface normal
                double nx = -dzdx;
                double ny = -dzdy;
                double nz = 1.0;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;

                // Lambertian shading
                double shade = nx * lx + ny * ly + nz * lz;
                shade = Math.max(0, shade);
                hillshade[px][py] = shade;
            }
        }

        // Normalize hillshade to [0, 1]
        double hsMin = Double.MAX_VALUE, hsMax = -Double.MAX_VALUE;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (hillshade[x][y] < hsMin) hsMin = hillshade[x][y];
                if (hillshade[x][y] > hsMax) hsMax = hillshade[x][y];
            }
        }
        double hsRange = hsMax - hsMin;
        if (hsRange > 0) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    hillshade[x][y] = (hillshade[x][y] - hsMin) / hsRange;
                }
            }
        }

        // === RENDER ===
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));

            for (int px = 0; px < width; px++) {
                double e = elevation[px][py];
                double m = moisture[px][py];
                double t = temperature[px][py];
                double hs = hillshade[px][py];

                Color color = getBiomeColor(e, m, t, absLat);
                // Apply hillshading: multiply color by shade factor
                // Range from 0.4 (deep shadow) to 1.3 (bright highlight)
                double shadeFactor = 0.4 + hs * 0.9;
                color = applyShading(color, shadeFactor);
                image.setRGB(px, py, color.getRGB());
            }
        }

        return image;
    }

    /**
     * Apply a brightness multiplier to a color, clamping to [0, 255].
     */
    private static Color applyShading(Color c, double factor) {
        int r = Math.min(255, Math.max(0, (int) (c.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int) (c.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int) (c.getBlue() * factor)));
        return new Color(r, g, b);
    }

    /**
     * Remaps normalized elevation [0,1] through a curve that produces
     * a bimodal distribution: flat ocean floors and distinct continental shelves.
     */
    private double hypsometric(double e) {
        // Piecewise linear spline approximating Earth's hypsometric curve
        // Input [0,1] -> Output [0,1] with the "sea level" crossing around 0.5
        if (e < 0.30) {
            // Deep ocean floor: mostly flat
            return lerp(0.0, 0.20, e / 0.30);
        } else if (e < 0.42) {
            // Ocean to shelf transition
            return lerp(0.20, 0.38, (e - 0.30) / 0.12);
        } else if (e < 0.50) {
            // Continental shelf / coastline: steep transition
            return lerp(0.38, 0.52, (e - 0.42) / 0.08);
        } else if (e < 0.65) {
            // Lowlands: relatively flat
            return lerp(0.52, 0.62, (e - 0.50) / 0.15);
        } else if (e < 0.80) {
            // Highlands
            return lerp(0.62, 0.78, (e - 0.65) / 0.15);
        } else {
            // Mountains
            return lerp(0.78, 1.0, (e - 0.80) / 0.20);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static final double SEA_LEVEL = 0.50;

    private Color getBiomeColor(double elevation, double moisture, double temperature, double absLat) {
        // Water
        if (elevation < SEA_LEVEL) {
            double depth = (SEA_LEVEL - elevation) / SEA_LEVEL;
            // Polar ice — use temperature instead of hard latitude
            if (temperature < 0.05) {
                return lerpColor(getWaterColor(depth), ICE, smoothstep((0.05 - temperature) / 0.05));
            }
            if (temperature < 0.15 && depth < 0.3) {
                double iceAmount = smoothstep((0.15 - temperature) / 0.10);
                return lerpColor(getWaterColor(depth), ICE, iceAmount * 0.7);
            }
            return getWaterColor(depth);
        }

        double landHeight = (elevation - SEA_LEVEL) / (1.0 - SEA_LEVEL);
        Color biome = getLandBiome(moisture, temperature);

        // Beach
        if (landHeight < 0.04) {
            return lerpColor(BEACH, biome, smoothstep(landHeight / 0.04));
        }

        // Snow/ice at very cold temperatures
        if (temperature < 0.08) {
            double snowT = smoothstep((0.08 - temperature) / 0.08);
            return lerpColor(biome, SNOW, snowT);
        }

        // High altitude snow — threshold based on temperature
        double snowLine = 0.60 + temperature * 0.35;
        if (landHeight > snowLine) {
            double snowT = smoothstep((landHeight - snowLine) / (1.0 - snowLine));
            return lerpColor(biome, SNOW, snowT * 0.8);
        }

        return biome;
    }

    private Color getWaterColor(double depth) {
        if (depth > 0.55) return DEEP_OCEAN;
        if (depth > 0.30) return lerpColor(OCEAN, DEEP_OCEAN, (depth - 0.30) / 0.25);
        if (depth > 0.10) return lerpColor(SHALLOW_WATER, OCEAN, (depth - 0.10) / 0.20);
        return lerpColor(SHORE_WATER, SHALLOW_WATER, depth / 0.10);
    }

    private Color getLandBiome(double moisture, double temperature) {
        // All biome selection purely temperature + moisture based (no latitude thresholds)
        if (temperature < 0.15) {
            return moisture > 0.45 ? BOREAL_FOREST : TUNDRA;
        }
        if (temperature < 0.35) {
            if (moisture > 0.55) return BOREAL_FOREST;
            if (moisture > 0.35) return GRASSLAND;
            return DRY_GRASSLAND;
        }
        if (temperature < 0.55) {
            if (moisture > 0.6) return TEMPERATE_FOREST;
            if (moisture > 0.35) return GRASSLAND;
            return DRY_GRASSLAND;
        }

        // Hot / tropical
        if (moisture > 0.60) return TROPICAL_FOREST;
        if (moisture > 0.40) return GRASSLAND;
        if (moisture > 0.25) return DRY_GRASSLAND;
        return SUBTROPICAL_DESERT;
    }

    private void normalize(double[][] data) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (data[x][y] < min) min = data[x][y];
                if (data[x][y] > max) max = data[x][y];
            }
        }
        double range = max - min;
        if (range == 0) return;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                data[x][y] = (data[x][y] - min) / range;
            }
        }
    }

    private static Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }
}
