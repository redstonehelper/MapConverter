import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import javax.imageio.ImageIO;

public class MapConverter {

	// constants
	public static final int WATER_COLOR_ID = 11;
	public static final int VARIATION_DARKER = 0;
	public static final int VARIATION_NORMAL = 1;
	public static final int VARIATION_LIGHTER = 2;
	private static final int numberOfBaseColors = 58;
	public static final String BLOCK_STATE_GLASS = "minecraft:glass";
	public static final String BLOCK_STATE_WATER = "minecraft:water[level=0]";

	private static final String version = "0.0.16";
	private static final String REDDIT_URL = "https://www.reddit.com/r/Minecraft/comments/gu1npm";

	public static enum Mode {
		STAIRCASE, FLAT, GROUNDED, ASCENDING, DESCENDING
	}

	// config options
	private static boolean allowWater = true;
	private static boolean dithering = true;
	private static Mode mode = Mode.STAIRCASE;

	private static String pathToOutputFolder;
	private static String pathToInputImage;

	// color and block data for all base colors, 3 shades
	private static int[][][] baseColorsRGB = new int[numberOfBaseColors][3][3];
	private static int[][][] baseColorsLAB = new int[numberOfBaseColors][3][3];
	private static boolean[][] allowColor = new boolean[numberOfBaseColors][3];
	private static String bestBlockState[] = new String[numberOfBaseColors];
	private static boolean bestBlockNeedsSupport[] = new boolean[numberOfBaseColors];
	private static String supportBlockState = null;

	public static void main(String[] args) {
		System.out.println("Parsing arguments and config.txt...");
		parseArguments(args);
		parseConfig();
		System.out.println("Initializing color data, saving color palette preview...");
		initColorData();
		saveColorPaletteImage(pathToOutputFolder + "colorPalette.png");

		String ditherString = dithering ? "" : " without dithering";
		System.out.println(
				"Loading image, reducing colors (" + mode + " mode" + ditherString + "), saving output preview...");
		// load image
		BufferedImage workingImage = loadImage(pathToInputImage);
		// extend image
		workingImage = extendImage(workingImage);
		// reduce colors
		int[][][] colorIndices = getMapColorMatrix(workingImage);
		// save output preview
		saveImage(workingImage, pathToOutputFolder + "completeImage.png");

		System.out.println("Writing structure files...");
		// cut up matrix into square sections
		for (int i = 0; i < workingImage.getWidth() / 128; i++) {
			for (int j = 0; j < workingImage.getHeight() / 128; j++) {
				// copy relevant parts of color matrix
				int[][][] subMatrix = new int[128][128][2];
				for (int x = 0; x < 128; x++) {
					for (int y = 0; y < 128; y++) {
						subMatrix[x][y] = colorIndices[i * 128 + x][j * 128 + y];
					}
				}
				// generate terrain
				TerrainSquare terrain = new TerrainSquare(subMatrix, bestBlockState, bestBlockNeedsSupport,
						supportBlockState, mode);
				terrain.createTerrain();

				// count blocks before adding glass floor
				System.out.println("Block counts for section " + i + "." + j + ":");
				HashMap<String, Integer> blockCounts = terrain.countBlocks();
				for (String blockState : blockCounts.keySet()) {
					System.out.println("\t" + blockState + ": " + blockCounts.get(blockState));
				}

				terrain.addGlassFloor();

				// save as structure file
				terrain.saveToStructure(pathToOutputFolder + "structure/section." + i + "." + j + ".nbt");
			}
		}
		System.out.println("Done! Place the .nbt files in <world>/generated/minecraft/structures.");
	}

	// returns array indexed by [x][y][i], i being 0=colorIndex and 1=variation
	// modifies image if dithering is enabled
	private static int[][][] getMapColorMatrix(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[][][] mapMatrix = new int[width][height][2];
		int totalPixels = width * height;
		int counter = 0;
		int percentOld = 0;

		// cache mapping rgb color to color id
		HashMap<Integer, int[]> colorMap = new HashMap<Integer, int[]>();
		// Floyd-Steinberg dithering
		int[][] ditheringMatrix = { { 1, 0, 7 }, { -1, 1, 3 }, { 0, 1, 5 }, { 1, 1, 1 } };
		double ditheringScaleFactor = 16.0;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// Find closest color for every pixel
				int bestMatch[] = { 0, 0 };
				int originalRGBint = image.getRGB(x, y);
				int[] originalRGB = getRGBtriple(originalRGBint);
				if (colorMap.containsKey(originalRGBint)) {
					bestMatch = colorMap.get(originalRGBint);
				} else {
					bestMatch = findClosestBaseColor(originalRGBint);
					colorMap.put(originalRGBint, bestMatch);
				}
				int rgbClosest = getRGBint(baseColorsRGB[bestMatch[0]][bestMatch[1]]);
				// save color
				image.setRGB(x, y, rgbClosest);
				mapMatrix[x][y] = bestMatch;

				// Dithering: diffuse error
				if (dithering) {
					int[] matchRGB = getRGBtriple(rgbClosest);
					double[] differenceRGB = new double[3];
					for (int channel = 0; channel < 3; channel++) {
						differenceRGB[channel] = originalRGB[channel] - matchRGB[channel];
					}
					for (int index = 0; index < ditheringMatrix.length; index++) {
						int xOffset = ditheringMatrix[index][0];
						int yOffset = ditheringMatrix[index][1];
						if (x + xOffset < width && x + xOffset >= 0 && y + yOffset < height && y + yOffset >= 0) {
							int[] nextRGB = getRGBtriple(image.getRGB(x + xOffset, y + yOffset));
							for (int channel = 0; channel < 3; channel++) {
								nextRGB[channel] = (int) Math.min(Math.max((nextRGB[channel]
										+ differenceRGB[channel] * ditheringMatrix[index][2] / ditheringScaleFactor),
										0), 255);
							}
							image.setRGB(x + xOffset, y + yOffset, getRGBint(nextRGB));
						}
					}
				}

				// Percent counter
				counter++;
				int percent = counter * 100 / totalPixels;
				if (percent % 5 == 0 && percent != percentOld) {
					if (percent == 5) {
						System.out.print("  ");
					}
					System.out.print(percent + "% ");
					percentOld = percent;
					if (percent == 100) {
						System.out.println();
					}
				}
			}
		}
		return mapMatrix;
	}

	// returns {colorIndex, variation}, usable as array indices
	private static int[] findClosestBaseColor(int rgb) {
		double smallestDifference = Double.MAX_VALUE;
		int bestMatch[] = { 0, 0 };
		int[] imageRGB = getRGBtriple(rgb);
		for (int colorID = 0; colorID < numberOfBaseColors; colorID++) {
			for (int variation = 0; variation < 3; variation++) {
				if (allowColor[colorID][variation]) {
					double difference = colorDifferenceRGBLAB(imageRGB, baseColorsLAB[colorID][variation]);
					if (difference < smallestDifference) {
						bestMatch[0] = colorID;
						bestMatch[1] = variation;
						smallestDifference = difference;
					}
				}
			}
		}
		return bestMatch;
	}

	// http://en.wikipedia.org/wiki/Color_difference#CIE76
	// Except it's squared since we only care about relative difference
	// This should be enough for our limited purposes
	private static double colorDifferenceRGBLAB(int[] rgb, int[] lab) {
		int[] rgbConvertedToLab = rgb2lab(rgb);
		return euclideanDistanceSquared(rgbConvertedToLab[0], rgbConvertedToLab[1], rgbConvertedToLab[2], lab[0],
				lab[1], lab[2]);
	}

	private static double euclideanDistanceSquared(int x1, int x2, int x3, int y1, int y2, int y3) {
		return (x1 - y1) * (x1 - y1) + (x2 - y2) * (x2 - y2) + (x3 - y3) * (x3 - y3);
	}

	// Pad image so we get full 128*128 sections
	private static BufferedImage extendImage(BufferedImage image) {
		int width = (int) (128 * Math.ceil((double) image.getWidth() / 128));
		int height = (int) (128 * Math.ceil((double) image.getHeight() / 128));

		BufferedImage extendedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		extendedImage.getGraphics().drawImage(image, 0, 0, null);
		return extendedImage;
	}

	private static void parseArguments(String[] args) {
		for (String s : args) {
			if (s.equals("-h") || s.equals("--help") || s.equals("-help") || s.equals("--h")) {
				printHelpAndQuit();
			}
			if (s.equals("--about")) {
				printAboutAndQuit();
			}
		}

		if (args.length > 0) {
			pathToInputImage = args[0];
		} else {
			System.out.println("Invalid arguments. Use --help for help.");
			System.exit(0);
		}

		boolean foundOutputArgument = false;
		if (args.length > 1) {
			if (!args[1].startsWith("-")) {
				pathToOutputFolder = args[1];
				foundOutputArgument = true;
			}
		}
		if (!foundOutputArgument) {
			int lastSlashIndex = pathToInputImage.lastIndexOf('/');
			// This also works if firstDashIndex == -1
			pathToOutputFolder = pathToInputImage.substring(0, lastSlashIndex + 1) + "out/";
		}
		if (!pathToOutputFolder.endsWith("/")) {
			pathToOutputFolder += "/";
		}
		File structureDir = new File(pathToOutputFolder + "structure");
		structureDir.mkdirs(); // Also creates parent folder

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--nowater")) {
				allowWater = false;
			}
			if (args[i].equals("--nodither")) {
				dithering = false;
			}
			if (args[i].equals("--mode")) {
				if (args.length > i + 1) {
					for (Mode potentialMode : Mode.values()) {
						if (potentialMode.toString().toLowerCase().equals(args[i + 1].toLowerCase())) {
							mode = potentialMode;
						}
					}
				}
			}
		}
	}

	private static void printHelpAndQuit() {
		System.out.println("Usage: java -jar MapConverter.jar infile [outpath] [options]");
		System.out.println("\tIf not specified, outpath will be \"out\" in the same folder as infile");
		System.out.println("\tWarning: Contents of outpath folder may be overwritten.");

		System.out.println("\nOptions:");
		System.out.println("-h or --help\t\tPrint this help text");
		System.out.println("--about\t\t\tPrint info");
		System.out.println("--nodither\t\tDisable dithering");
		System.out.println("--nowater\t\tDisable Water");

		System.out.println("--mode <mode>\t\tTerrain modes. Options for <mode>:");
		System.out.println("\t\t\t\tSTAIRCASE, FLAT, GROUNDED, ASCENDING, DESCENDING");
		System.out.println("\t\t\t\t(Full color range only available for STAIRCASE and GROUNDED)");

		System.out.println("For more help see " + REDDIT_URL);

		System.exit(0);
	}

	private static void printAboutAndQuit() {
		System.out.println("Made by /u/redstonehelper on reddit: " + REDDIT_URL);
		System.out.println("Requires JNBT 1.1: jnbt.sourceforge.net");
		System.out.println("Version " + version);

		System.exit(0);
	}

	private static void initColorData() {
		// Borrowed from bcy.class in 1.12-pre7.jar or cxe.class in 1.16.1
		int[] baseColors = { 8368696, 16247203, 13092807, 16711680, 10526975, 10987431, 31744, 16777215, 10791096,
				9923917, 7368816, 4210943, 9402184, 16776437, 14188339, 11685080, 6724056, 15066419, 8375321, 15892389,
				5000268, 10066329, 5013401, 8339378, 3361970, 6704179, 6717235, 10040115, 1644825, 16445005, 6085589,
				4882687, 55610, 8476209, 7340544, 13742497, 10441252, 9787244, 7367818, 12223780, 6780213, 10505550,
				3746083, 8874850, 5725276, 8014168, 4996700, 4993571, 5001770, 9321518, 2430480, 12398641, 9715553,
				6035741, 1474182, 3837580, 5647422, 1356933 };
		// Weirdly, the colors are slightly different between the wiki, the
		// output vanilla code creates and the game. Whatever.

		// Calculate map base colors in RGB and LAB
		for (int colorID = 0; colorID < numberOfBaseColors; colorID++) {
			int[] scales = { 180, 220, 255 }; // darker, normal, lighter
			for (int variation = 0; variation < 3; variation++) {
				int scale = scales[variation];
				int[] tripleRGB = getRGBtriple(baseColors[colorID]);
				tripleRGB[0] = tripleRGB[0] * scale / 255;
				tripleRGB[1] = tripleRGB[1] * scale / 255;
				tripleRGB[2] = tripleRGB[2] * scale / 255;
				int[] tripleLAB = rgb2lab(tripleRGB);
				baseColorsRGB[colorID][variation] = tripleRGB;
				baseColorsLAB[colorID][variation] = tripleLAB;
			}
		}

		// enable all colors at first, some later disabled by config/parameters
		for (int colorID = 0; colorID < numberOfBaseColors; colorID++) {
			for (int variation = 0; variation < 3; variation++) {
				allowColor[colorID][variation] = true;
			}
		}

		// disable colors for which no block is configured
		for (int i = 0; i < numberOfBaseColors; i++) {
			if (bestBlockState[i] == null) {
				allowColor[i][VARIATION_DARKER] = false;
				allowColor[i][VARIATION_NORMAL] = false;
				allowColor[i][VARIATION_LIGHTER] = false;
			}
		}

		if (mode == Mode.FLAT) {
			// only allow flat shading
			for (int i = 0; i < numberOfBaseColors; i++) {
				allowColor[i][VARIATION_DARKER] = false;
				allowColor[i][VARIATION_LIGHTER] = false;
			}
			// Special case for water
			allowColor[11][VARIATION_DARKER] = false;
			allowColor[11][VARIATION_NORMAL] = false;
			allowColor[11][VARIATION_LIGHTER] = true;
		} else if (mode == Mode.ASCENDING) {
			// don't allow descending, i.e. lightest color
			for (int i = 0; i < numberOfBaseColors; i++) {
				allowColor[i][VARIATION_LIGHTER] = false;
			}
			// Special case for water: never changes height.
			allowColor[11][VARIATION_LIGHTER] = true;
		} else if (mode == Mode.DESCENDING) {
			// don't allow ascending, i.e. darkest color
			for (int i = 0; i < numberOfBaseColors; i++) {
				allowColor[i][VARIATION_DARKER] = false;
			}
			// Special case for water: never changes height.
			allowColor[11][VARIATION_DARKER] = true;
		}

		// water flag override
		if (!allowWater) {
			allowColor[11][VARIATION_DARKER] = false;
			allowColor[11][VARIATION_NORMAL] = false;
			allowColor[11][VARIATION_LIGHTER] = false;
		}
	}

	// parse config.txt and populate block states
	private static void parseConfig() {
		File file = new File("config.txt");
		if (!file.exists()) {
			System.out.println("Missing config.txt, exiting...");
			System.exit(0);
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = br.readLine();
			while (line != null) {
				if (line.startsWith("support: ")) {
					supportBlockState = line.substring(9);
				} else if (line.startsWith("needSupport: ")) {
					String[] idsNeedingSupport = line.substring(13).replace(" ", "").split(",");
					for (String idNeedingSupport : idsNeedingSupport) {
						int indexNeedingSupport = Integer.parseInt(idNeedingSupport) - 1;
						bestBlockNeedsSupport[indexNeedingSupport] = true;
					}
				} else {
					int colorIndex = Integer.parseInt(line.substring(0, 2)) - 1;
					if (colorIndex == WATER_COLOR_ID) {
						System.out.println("Water blocks can't be changed!");
					} else {
						String blockState = line.substring(4);
						bestBlockState[colorIndex] = blockState;
					}
				}
				line = br.readLine();
			}
			br.close();
		} catch (Exception e) {
			System.out.println("Exception reading config.txt, exiting...");
			e.printStackTrace();
			System.exit(0);
		}

		// hardcoded water block state
		bestBlockState[11] = BLOCK_STATE_WATER;
	}

	private static void saveColorPaletteImage(String path) {
		int stripeHeight = 4;
		int width = 128;
		int allowedColors = 0;
		for (int i = 0; i < allowColor.length; i++) {
			for (int j = 0; j < allowColor[i].length; j++) {
				if (allowColor[i][j]) {
					allowedColors++;
				}
			}
		}
		int height = allowedColors * stripeHeight;
		int stripes = 0;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int colorID = 0; colorID < numberOfBaseColors; colorID++) {
			for (int variation = 0; variation < allowColor[colorID].length; variation++) {
				if (allowColor[colorID][variation]) {
					for (int stripeOffset = 0; stripeOffset < stripeHeight; stripeOffset++) {
						for (int x = 0; x < width; x++) {
							int rgb = getRGBint(baseColorsRGB[colorID][variation][0],
									baseColorsRGB[colorID][variation][1], baseColorsRGB[colorID][variation][2]);
							image.setRGB(x, stripeHeight * stripes + stripeOffset, rgb);
						}
					}
					stripes++;
				}
			}
		}
		saveImage(image, path);
	}

	private static BufferedImage loadImage(String path) {
		BufferedImage img = null;
		File file = new File(path);
		try {
			img = ImageIO.read(file);
		} catch (IOException e) {
			System.out.println("Something went wrong:");
			e.printStackTrace();
			System.exit(0);
		}

		// ensure proper image type
		BufferedImage copiedImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
		copiedImage.getGraphics().drawImage(img, 0, 0, null);
		return copiedImage;
	}

	private static void saveImage(BufferedImage img, String path) {
		File file = new File(path);
		try {
			ImageIO.write(img, "png", file);
		} catch (IOException e) {
			System.out.println("Something went wrong:");
			e.printStackTrace();
			System.exit(0);
		}
	}

	private static int getRGBint(int[] rgbTriple) {
		return getRGBint(rgbTriple[0], rgbTriple[1], rgbTriple[2]);
	}

	private static int getRGBint(int r, int g, int b) {
		return 256 * 256 * r + 256 * g + b;
	}

	private static int[] getRGBtriple(int rgb) {
		int[] array = new int[3];
		array[0] = (rgb >> 16) & 0xFF;
		array[1] = (rgb >> 8) & 0xFF;
		array[2] = rgb & 0xFF;
		return array;
	}

	// https://en.wikipedia.org/wiki/CIELAB_color_space
	// http://www.brucelindbloom.com/index.html?Eqn_RGB_to_XYZ.html
	// http://www.brucelindbloom.com/index.html?Eqn_XYZ_to_Lab.html
	// https://de.wikipedia.org/wiki/Lab-Farbraum#Umrechnung_von_XYZ_zu_Lab
	// https://observablehq.com/@mbostock/lab-and-rgb
	private static int[] rgb2lab(int[] rgb) {
		double[] values = new double[3];
		for (int i = 0; i < 3; i++) {
			double V = rgb[i] / 255.0;
			double v = Math.pow(V, 2.2);
			if (V <= 0.04045) {
				v = V / 12.92;
			} else {
				v = Math.pow((V + 0.055) / 1.055, 2.4);
			}
			values[i] = v;
		}
		double[] XYZ = new double[3];
		XYZ[0] = 0.4360747 * values[0] + 0.3850649 * values[1] + 0.1430804 * values[2];
		XYZ[1] = 0.2225045 * values[0] + 0.7168786 * values[1] + 0.0606169 * values[2];
		XYZ[2] = 0.0139322 * values[0] + 0.0971045 * values[1] + 0.7141733 * values[2];

		XYZ[0] = XYZ[0] / 0.96422;
		XYZ[1] = XYZ[1] / 1.0;
		XYZ[2] = XYZ[2] / 0.82521;
		double[] fVals = new double[3];
		for (int i = 0; i < 3; i++) {
			double f;
			double val = XYZ[i];
			double valr = val;
			if (valr > (216.0 / 24389.0)) {
				f = Math.pow(valr, 1.0 / 3.0);
			} else {
				f = ((24389.0 / 27.0) * valr + 16.0) / 116.0;
			}
			fVals[i] = f;
		}
		// lab values, moved into [0,255]
		int[] lab = new int[3];
		lab[0] = (int) (2.55 * (116 * fVals[1] - 16));
		lab[1] = 128 + (int) (500 * (fVals[0] - fVals[1]));
		lab[2] = 128 + (int) (200 * (fVals[1] - fVals[2]));
		return lab;
	}

}
