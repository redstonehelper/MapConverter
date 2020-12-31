import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;

public class TerrainSlice {

	// terrain data, indexed by z-coordinate, offset 1 (it's 130 high, not 128)
	private ArrayList<PlacedBlock>[] blocks = new ArrayList[130];

	// config/input data
	private int[][] colorVector;
	private String bestBlockState[];
	private boolean[] bestBlockNeedsSupport;
	private String supportBlockState;

	public TerrainSlice(int[][] colorVector, String bestBlockState[], boolean[] bestBlockNeedsSupport,
			String supportBlockState) {
		this.colorVector = colorVector;
		this.bestBlockState = bestBlockState;
		this.bestBlockNeedsSupport = bestBlockNeedsSupport;
		this.supportBlockState = supportBlockState;
		for (int i = 0; i < 130; i++) {
			blocks[i] = new ArrayList<PlacedBlock>();
		}
	}

	public void countBlocks(HashMap<String, Integer> counts) {
		for (int z = 0; z < 130; z++) {
			for (PlacedBlock block : blocks[z]) {
				String blockState = block.getBlockState();
				if (counts.containsKey(blockState)) {
					counts.put(blockState, counts.get(blockState) + 1);
				} else {
					counts.put(blockState, 1);
				}
			}
		}
	}

	public void createBasicTerrain() {
		// pay special attention to:
		// an extra block necessary for correct shading of north-most block
		// water columns not just being a single block in height
		// water columns requiring glass to prevent them from spreading
		// an offset of 1 in x-direction because squares are surrounded by glass

		// give height 0 to first block, then in/decrease height for shading
		int currentHeight = 0;
		for (int z = 127; z >= 0; z--) {
			int blockStateIndex = colorVector[z][0];
			int shadingType = colorVector[z][1];
			String blockState = bestBlockState[blockStateIndex];

			ArrayList<PlacedBlock> blockList = blocks[z + 1];

			if (!blockState.equals(MapConverter.BLOCK_STATE_WATER)) {
				// normal block
				PlacedBlock block = new PlacedBlock(blockState, currentHeight);
				blockList.add(block);
				if (bestBlockNeedsSupport[blockStateIndex]) {
					PlacedBlock supportBlock = new PlacedBlock(supportBlockState, currentHeight - 1);
					blockList.add(supportBlock);
				}

				// update height for next block. not necessary for water.
				if (shadingType == MapConverter.VARIATION_DARKER) {
					currentHeight++;
				} else if (shadingType == MapConverter.VARIATION_LIGHTER) {
					currentHeight--;
				}
			} else {
				// water: depending on shade we need different depth
				int waterDepth = 0;
				switch (shadingType) {
				case MapConverter.VARIATION_LIGHTER:
					waterDepth = 1;
					break;
				case MapConverter.VARIATION_NORMAL:
					waterDepth = 5;
					break;
				case MapConverter.VARIATION_DARKER:
					waterDepth = 10;
					break;
				}

				// place water
				for (int i = 0; i < waterDepth; i++) {
					PlacedBlock waterBlock = new PlacedBlock(MapConverter.BLOCK_STATE_WATER, currentHeight - i);
					blockList.add(waterBlock);
				}
				// place glass block underneath to stop spills, glass around the
				// sides to be added later...
				PlacedBlock glassBlock = new PlacedBlock(MapConverter.BLOCK_STATE_GLASS, currentHeight - waterDepth);
				blockList.add(glassBlock);
			}
		}
		// extra block at the end for proper shading of last block on map
		PlacedBlock shadingSupportBlock = new PlacedBlock(supportBlockState, currentHeight);
		blocks[0].add(shadingSupportBlock);

		// inspect all blocks and move everything up if negative height occurs
		int minHeight = 0;
		for (int z = 0; z < 130; z++) {
			for (PlacedBlock block : blocks[z]) {
				if (block.getHeight() < minHeight) {
					minHeight = block.getHeight();
				}
			}
		}
		if (minHeight < 0) {
			for (int z = 0; z < 130; z++) {
				for (PlacedBlock block : blocks[z]) {
					block.setHeight(block.getHeight() - minHeight);
				}
			}
		}
	}

	public void groundTerrain() {
		// greedy approach: find lowest point. traverse from there both ways.
		// when a peak is found, explore the rest and see how far down it can
		// move. repeat to both ends of map.
		groundTerrain(0, 129); // don't forget last block for shading!
	}

	// startZ inclusive, endZ exclusive
	private void groundTerrain(int startZ, int endZ) {
		if (startZ >= endZ) {
			return;
		}

		// find lowest block
		int lowestHeightZ = -1;
		int lowestHeight = Integer.MAX_VALUE;
		for (int z = startZ; z < endZ; z++) {
			int height = lowestHeightAt(z);
			if (height < lowestHeight) {
				lowestHeight = height;
				lowestHeightZ = z;
			}
		}
		// explore in both directions, but stay within [startZ, endZ[
		int currentHeight = lowestHeight;

		int includeLowZ = lowestHeightZ; // inclusive
		int includeHighZ = lowestHeightZ + 1; // exclusive
		// explore towards positive z (south)
		for (int z = lowestHeightZ + 1; z < endZ; z++) {
			PlacedBlock currentBlock = getHighestBlockAt(z);
			// cut before we find water because water doesn't care about shading
			if (currentBlock.getBlockState().equals(MapConverter.BLOCK_STATE_WATER)) {
				break;
			}
			// cut before blocks go down again
			if (currentBlock.getHeight() < currentHeight) {
				break;
			}
			// include this block
			currentHeight = currentBlock.getHeight();
			includeHighZ = z + 1;
		}
		// explore towards negative z (north)
		currentHeight = lowestHeight;
		for (int z = lowestHeightZ - 1; z >= startZ; z--) {
			PlacedBlock currentBlock = getHighestBlockAt(z);
			PlacedBlock previousBlock = getHighestBlockAt(z + 1);
			// cut after we find water because water doesn't care about shading
			if (previousBlock.getBlockState().equals(MapConverter.BLOCK_STATE_WATER)) {
				break;
			}
			// cut before blocks go down again
			if (currentBlock.getHeight() < currentHeight) {
				break;
			}
			// include this block
			currentHeight = currentBlock.getHeight();
			includeLowZ = z;
		}

		// move explored blocks down
		changeHeight(includeLowZ, includeHighZ, -lowestHeight);

		// recursively ground the of [startZ, endZ[
		groundTerrain(startZ, includeLowZ);
		groundTerrain(includeHighZ, endZ);
	}

	private PlacedBlock getHighestBlockAt(int z) {
		int height = highestHeightAt(z);
		for (PlacedBlock block : blocks[z]) {
			if (block.getHeight() == height) {
				return block;
			}
		}
		return null;
	}

	private int highestHeightAt(int z) {
		int highestHeight = Integer.MIN_VALUE;
		for (PlacedBlock block : blocks[z]) {
			if (block.getHeight() > highestHeight) {
				highestHeight = block.getHeight();
			}
		}
		return highestHeight;
	}

	private int lowestHeightAt(int z) {
		int lowestHeight = Integer.MAX_VALUE;
		for (PlacedBlock block : blocks[z]) {
			if (block.getHeight() < lowestHeight) {
				lowestHeight = block.getHeight();
			}
		}
		return lowestHeight;
	}

	public void surroundAllWater(TerrainSlice neighbor1, TerrainSlice neighbor2) {
		// for every water block, place a glass block on all 4 sides if no block
		// exists in that position
		for (int z = 1; z < 129; z++) {
			for (PlacedBlock block : blocks[z]) {
				if (block.getBlockState().equals(MapConverter.BLOCK_STATE_WATER)) {
					int height = block.getHeight();
					this.addGlassBlockOrDoNothing(z - 1, height);
					this.addGlassBlockOrDoNothing(z + 1, height);
					neighbor1.addGlassBlockOrDoNothing(z, height);
					neighbor2.addGlassBlockOrDoNothing(z, height);
				}
			}
		}
	}

	// places a glass block if no block exists in this position yet
	public void addGlassBlockOrDoNothing(int z, int height) {
		for (PlacedBlock block : blocks[z]) {
			if (block.getHeight() == height) {
				return; // block space already occupied by some other block
			}
		}
		// block space not occupied yet, place glass block
		PlacedBlock glass = new PlacedBlock(MapConverter.BLOCK_STATE_GLASS, height);
		blocks[z].add(glass);
	}

	public void addGlassFloor() {
		lift();
		for (int i = 0; i < 130; i++) {
			// insert glass at bottom
			PlacedBlock glassBlock = new PlacedBlock(MapConverter.BLOCK_STATE_GLASS, 0);
			blocks[i].add(glassBlock);
		}
	}

	// move everything up by one
	public void lift() {
		changeHeight(0, 130, 1);
	}

	// startZ inclusive, endZ exclusive
	private void changeHeight(int minZ, int maxZ, int heightOffset) {
		for (int i = minZ; i < maxZ; i++) {
			for (PlacedBlock block : blocks[i]) {
				block.setHeight(block.getHeight() + heightOffset);
			}
		}
	}

	public int getHeight() {
		// find heighest block height
		boolean containsBlocks = false;
		int maxHeight = 0;
		for (int z = 0; z < 130; z++) {
			for (PlacedBlock block : blocks[z]) {
				containsBlocks = true;
				if (block.getHeight() > maxHeight) {
					maxHeight = block.getHeight();
				}
			}
		}
		if (containsBlocks) {
			return maxHeight + 1;
		} else {
			return 0;
		}
	}

	public void writeAllBlocks(StructureWriter writer, int x) {
		for (int z = 0; z < 130; z++) {
			for (PlacedBlock block : blocks[z]) {
				writer.placeBlock(block.getBlockState(), x, block.getHeight(), z);
			}
		}
	}

	// simple Pair class for placed blocks
	private class PlacedBlock extends AbstractMap.SimpleEntry<String, Integer> {
		private static final long serialVersionUID = -7408005377908173098L;

		public PlacedBlock(String blockState, int height) {
			super(blockState, height);
		}

		public void setHeight(int height) {
			setValue(height);
		}

		public int getHeight() {
			return getValue();
		}

		public String getBlockState() {
			return getKey();
		}
	}

}
