import java.util.HashMap;

public class TerrainSquare {

	// the terrain
	TerrainSlice[] slices = new TerrainSlice[130];

	private int[][][] colorMatrix;
	private String bestBlockState[];
	private boolean[] bestBlockNeedsSupport;
	private String supportBlockState;
	private MapConverter.Mode mode;

	public TerrainSquare(int[][][] colorMatrix, String bestBlockState[], boolean[] bestBlockNeedsSupport,
			String supportBlockState, MapConverter.Mode mode) {
		this.colorMatrix = colorMatrix;
		this.bestBlockState = bestBlockState;
		this.bestBlockNeedsSupport = bestBlockNeedsSupport;
		this.supportBlockState = supportBlockState;
		this.mode = mode;
	}

	public HashMap<String, Integer> countBlocks() {
		HashMap<String, Integer> counts = new HashMap<String, Integer>();
		for (TerrainSlice slice : slices) {
			slice.countBlocks(counts);
		}
		return counts;
	}

	// create terrain independently for every column, i.e. x-coordinate.
	public void createTerrain() {
		// first and last slices containing only glass floor
		slices[0] = new TerrainSlice(null, null, null, null);
		slices[129] = new TerrainSlice(null, null, null, null);
		// normal slices with proper terrain, initially without water cylinders
		for (int x = 0; x < 128; x++) {
			slices[x + 1] = new TerrainSlice(colorMatrix[x], bestBlockState, bestBlockNeedsSupport, supportBlockState);
			slices[x + 1].createBasicTerrain();
		}

		if (mode == MapConverter.Mode.FLAT) {
			// if water required, adjust height to be uniform across whole map
			for (int x = 0; x < 128; x++) {
				if (slices[x + 1].getHeight() == 2) {
					// there is water, lift slices without water to same level
					for (TerrainSlice slice : slices) {
						if (slice.getHeight() == 1) {
							slice.lift();
						}
					}
					break;
				}
			}
		} else if (mode == MapConverter.Mode.GROUNDED) {
			// cut up staircases so it's only v-shapes on the ground
			for (int x = 0; x < 128; x++) {
				slices[x + 1].groundTerrain();
			}
		}

		// add water cylinders
		for (int x = 0; x < 128; x++) {
			slices[x + 1].surroundAllWater(slices[x], slices[x + 2]);
		}
	}

	public void addGlassFloor() {
		for (int i = 0; i < 130; i++) {
			slices[i].addGlassFloor();
		}
	}

	public void saveToStructure(String fileName) {
		// get height
		int height = 0;
		for (TerrainSlice slice : slices) {
			int sliceHeight = slice.getHeight();
			if (sliceHeight > height) {
				height = sliceHeight;
			}
		}

		// write blocks
		StructureWriter writer = new StructureWriter(130, height, 130);
		for (int x = 0; x < 130; x++) {
			slices[x].writeAllBlocks(writer, x);
		}

		// write structure
		writer.writeToFile(fileName);
	}

}
