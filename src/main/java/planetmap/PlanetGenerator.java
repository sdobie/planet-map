package planetmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Generates realistic planet maps using 3D simplex noise sampled on a sphere,
 * with domain warping, separate continentalness layer, and bimodal elevation.
 */
public class PlanetGenerator {

    private final int width;
    private final int height;

    // Biome colors
    private static final Color DEEP_OCEAN        = new Color(15, 40, 100);
    private static final Color OCEAN             = new Color(25, 60, 140);
    private static final Color SHALLOW_WATER     = new Color(50, 95, 165);
    private static final Color SHORE_WATER       = new Color(70, 120, 180);

    private static final Color BEACH             = new Color(220, 210, 160);
    private static final Color SUBTROPICAL_DESERT = new Color(215, 190, 120);
    private static final Color DRY_GRASSLAND     = new Color(175, 175, 80);
    private static final Color GRASSLAND         = new Color(110, 165, 60);
    private static final Color TEMPERATE_FOREST  = new Color(45, 115, 45);
    private static final Color TROPICAL_FOREST   = new Color(20, 90, 30);
    private static final Color BOREAL_FOREST     = new Color(35, 75, 45);
    private static final Color TUNDRA            = new Color(165, 185, 170);
    private static final Color SNOW              = new Color(235, 242, 248);
    private static final Color MOUNTAIN_ROCK     = new Color(95, 85, 75);
    private static final Color MOUNTAIN_HIGH     = new Color(140, 130, 120);
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
        SimplexNoise ridgeNoise     = new SimplexNoise(seed + 5000);
        SimplexNoise mountainNoise  = new SimplexNoise(seed + 8000); // broad mountain regions
        SimplexNoise moistNoise     = new SimplexNoise(seed + 6000);
        SimplexNoise tempNoise      = new SimplexNoise(seed + 7000);

        double[][] elevation = new double[width][height];
        double[][] mountainMap = new double[width][height]; // broad mountain regions for coloring
        double[][] mountainDetail = new double[width][height]; // texture within mountains
        double[][] moisture = new double[width][height];
        double[][] temperature = new double[width][height];

        for (int py = 0; py < height; py++) {
            // Latitude in radians: +PI/2 (north) to -PI/2 (south)
            double lat = Math.PI * (0.5 - (double) py / height);
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);
            double absLat = Math.abs(sinLat);

            for (int px = 0; px < width; px++) {
                // Longitude in radians: 0 to 2*PI
                double lon = 2.0 * Math.PI * px / width;

                // 3D point on the unit sphere
                double sx = cosLat * Math.cos(lon);
                double sy = cosLat * Math.sin(lon);
                double sz = sinLat;

                // === CONTINENTALNESS: very low frequency, smooth, determines land vs ocean ===
                double contFreq = 1.0;
                double cont = continentNoise.fractal(
                        sx * contFreq, sy * contFreq, sz * contFreq,
                        2, 0.4, 2.0);

                // === DOMAIN WARPING: low-frequency only, to break up round shapes ===
                // Level 1: broad warp
                double warpFreq = 0.8;
                double warpAmp = 0.45;
                double wx = warpNoise1.fractal(
                        sx * warpFreq + 5.2, sy * warpFreq + 1.3, sz * warpFreq + 3.7,
                        2, 0.4, 2.0) * warpAmp;
                double wy = warpNoise2.fractal(
                        sx * warpFreq + 9.1, sy * warpFreq + 4.8, sz * warpFreq + 7.2,
                        2, 0.4, 2.0) * warpAmp;
                double wz = warpNoise3.fractal(
                        sx * warpFreq + 2.6, sy * warpFreq + 8.4, sz * warpFreq + 1.9,
                        2, 0.4, 2.0) * warpAmp;

                double wsx = sx + wx;
                double wsy = sy + wy;
                double wsz = sz + wz;

                // Level 2: subtle secondary warp for coastline irregularity
                double warp2Freq = 2.0;
                double warp2Amp = 0.12;
                double w2x = warpNoise1.fractal(
                        wsx * warp2Freq + 20.1, wsy * warp2Freq + 15.3, wsz * warp2Freq + 10.7,
                        2, 0.4, 2.0) * warp2Amp;
                double w2y = warpNoise2.fractal(
                        wsx * warp2Freq + 25.4, wsy * warp2Freq + 18.9, wsz * warp2Freq + 12.1,
                        2, 0.4, 2.0) * warp2Amp;
                double w2z = warpNoise3.fractal(
                        wsx * warp2Freq + 30.8, wsy * warp2Freq + 22.5, wsz * warp2Freq + 14.3,
                        2, 0.4, 2.0) * warp2Amp;

                double wwsx = wsx + w2x;
                double wwsy = wsy + w2y;
                double wwsz = wsz + w2z;

                // === TERRAIN DETAIL: only affects land texture, NOT continent shape ===
                double terrainFreq = 3.5;
                double terrain = detailNoise.fractal(
                        wwsx * terrainFreq, wwsy * terrainFreq, wwsz * terrainFreq,
                        4, 0.4, 2.0);

                // === MOUNTAIN REGIONS: low-frequency blobs marking where mountains are ===
                double mtFreq = 1.8;
                double mt = mountainNoise.fractal(
                        wsx * mtFreq + 50, wsy * mtFreq + 50, wsz * mtFreq + 50,
                        2, 0.4, 2.0);
                // Shift up so ~40% of area can be mountainous, then smooth threshold
                mt = smoothstep(Math.max(0, mt + 0.25));

                // === RIDGE NOISE: subtle elevation variation within mountain regions ===
                double ridgeFreq = 3.0;
                double ridge = ridgeNoise.fractal(
                        wsx * ridgeFreq, wsy * ridgeFreq, wsz * ridgeFreq,
                        3, 0.4, 2.0);
                ridge = Math.max(0, ridge);

                // Mountains only contribute elevation where mountain regions exist
                double mountainElev = mt * (0.8 + ridge * 0.2);

                // === COMBINE: continent shape + terrain detail + mountain elevation ===
                double e = cont * 0.60 + terrain * 0.10 + mountainElev * 0.30;

                elevation[px][py] = e;
                mountainMap[px][py] = mt; // store broad mountain region for coloring
                mountainDetail[px][py] = ridge * 0.5 + terrain * 0.5; // texture for mountain coloring

                // === MOISTURE ===
                double moistFreq = 2.5;
                double m = moistNoise.fractal(
                        wwsx * moistFreq + 40, wwsy * moistFreq + 40, wwsz * moistFreq + 40,
                        5, 0.5, 2.0);
                moisture[px][py] = m;

                // === TEMPERATURE: latitude-based with noise variation ===
                double baseTemp = 1.0 - absLat; // hot at equator, cold at poles
                double tNoise = tempNoise.fractal(
                        sx * 2.0, sy * 2.0, sz * 2.0,
                        3, 0.5, 2.0) * 0.15;
                temperature[px][py] = baseTemp * 0.85 + tNoise + 0.08;
            }
        }

        // Normalize all to [0, 1]
        normalize(elevation);
        normalize(mountainMap);
        normalize(mountainDetail);
        normalize(moisture);

        // === ELEVATION REDISTRIBUTION: create bimodal hypsometric curve ===
        // This is the key to making oceans flat and continents distinct
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                elevation[x][y] = hypsometric(elevation[x][y]);
            }
        }

        // === LATITUDE EFFECTS ===
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));

            for (int px = 0; px < width; px++) {
                // Reduce temperature at high elevations
                if (elevation[px][py] > 0.5) {
                    double landH = (elevation[px][py] - 0.5) / 0.5;
                    temperature[px][py] -= landH * 0.3;
                }

                // Coastal moisture boost
                if (elevation[px][py] > 0.45 && elevation[px][py] < 0.55) {
                    moisture[px][py] = Math.min(1.0, moisture[px][py] + 0.15);
                }

                // Polar moisture reduction
                if (absLat > 0.75) {
                    double polar = (absLat - 0.75) / 0.25;
                    moisture[px][py] *= (1.0 - polar * 0.5);
                }
            }
        }
        normalize(moisture);

        // === RENDER ===
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < height; py++) {
            double lat = Math.PI * (0.5 - (double) py / height);
            double absLat = Math.abs(Math.sin(lat));

            for (int px = 0; px < width; px++) {
                double e = elevation[px][py];
                double mtReg = mountainMap[px][py];
                double mtDet = mountainDetail[px][py];
                double m = moisture[px][py];
                double t = temperature[px][py];

                Color color = getBiomeColor(e, mtReg, mtDet, m, t, absLat);
                image.setRGB(px, py, color.getRGB());
            }
        }

        return image;
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

    private Color getBiomeColor(double elevation, double mountainRegion, double mountainDetail,
                                double moisture, double temperature, double absLat) {
        // Water
        if (elevation < SEA_LEVEL) {
            double depth = (SEA_LEVEL - elevation) / SEA_LEVEL;
            // Polar ice on water
            if (absLat > 0.92 || (absLat > 0.82 && depth < 0.3)) {
                double iceAmount = absLat > 0.92 ? 1.0 : (absLat - 0.82) / 0.10;
                return lerpColor(getWaterColor(depth), ICE, iceAmount);
            }
            return getWaterColor(depth);
        }

        double landHeight = (elevation - SEA_LEVEL) / (1.0 - SEA_LEVEL);
        Color biome = getLandBiome(moisture, temperature, absLat);

        // Beach
        if (landHeight < 0.04) {
            return lerpColor(BEACH, biome, smoothstep(landHeight / 0.04));
        }

        // Snow/ice caps at poles
        if (absLat > 0.90) {
            return lerpColor(TUNDRA, SNOW, smoothstep((absLat - 0.90) / 0.10));
        }

        // Mountain coloring based on broad mountain regions
        double mountainness = smoothstep(mountainRegion);

        // Snow line depends on latitude
        double snowLine = 0.75 - absLat * 0.25;
        if (mountainness > 0.5 && landHeight > snowLine) {
            double snowT = smoothstep((landHeight - snowLine) / (1.0 - snowLine));
            // Textured rock under snow
            Color rock = lerpColor(MOUNTAIN_ROCK, MOUNTAIN_HIGH, mountainDetail);
            Color base = lerpColor(biome, rock, mountainness);
            return lerpColor(base, SNOW, snowT);
        }

        // Textured mountain rock: fully opaque, detail only varies rock color
        if (mountainness > 0.2) {
            // Sharp transition: 0.2 -> 0.5 mountainness = 0% -> 100% rock
            double rockT = Math.min(1.0, (mountainness - 0.2) / 0.3);
            // Detail varies between dark rock and light rock
            Color rock = lerpColor(MOUNTAIN_ROCK, MOUNTAIN_HIGH, mountainDetail);
            return lerpColor(biome, rock, rockT);
        }

        return biome;
    }

    private Color getWaterColor(double depth) {
        if (depth > 0.55) return DEEP_OCEAN;
        if (depth > 0.30) return lerpColor(OCEAN, DEEP_OCEAN, (depth - 0.30) / 0.25);
        if (depth > 0.10) return lerpColor(SHALLOW_WATER, OCEAN, (depth - 0.10) / 0.20);
        return lerpColor(SHORE_WATER, SHALLOW_WATER, depth / 0.10);
    }

    private Color getLandBiome(double moisture, double temperature, double absLat) {
        if (absLat > 0.85) return TUNDRA;
        if (absLat > 0.78) {
            return moisture > 0.4 ? BOREAL_FOREST : TUNDRA;
        }

        if (temperature < 0.2) {
            return moisture > 0.45 ? BOREAL_FOREST : TUNDRA;
        }
        if (temperature < 0.5) {
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
