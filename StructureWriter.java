import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.ListTag;
import org.jnbt.NBTOutputStream;
import org.jnbt.StringTag;
import org.jnbt.Tag;

public class StructureWriter {

	// https://minecraft.gamepedia.com/Structure_block_file_format

	private int xSize;
	private int ySize;
	private int zSize;

	// the list of placed blocks
	private ArrayList<Tag> blockList = new ArrayList<Tag>();
	// the list of block states used, in the form of tags
	private ArrayList<Tag> blockStateList = new ArrayList<Tag>();

	// map "minecraft:id[prop=true]" block state form to index of blockStateList
	private HashMap<String, Integer> blockStateCache = new HashMap<String, Integer>();

	public StructureWriter(int xSize, int ySize, int zSize) {
		this.xSize = xSize;
		this.ySize = ySize;
		this.zSize = zSize;
	}

	public void placeBlock(String blockState, int x, int y, int z) {
		int stateIndex = getBlockStateIndex(blockState);

		HashMap<String, Tag> block = new HashMap<String, Tag>();
		block.put("state", new IntTag("state", stateIndex));
		ArrayList<Tag> posList = new ArrayList<Tag>();
		posList.add(new IntTag("", x));
		posList.add(new IntTag("", y));
		posList.add(new IntTag("", z));
		ListTag posListTag = new ListTag("pos", IntTag.class, posList);
		block.put("pos", posListTag);
		CompoundTag blockCompoundTag = new CompoundTag("", block);

		blockList.add(blockCompoundTag);
	}

	// blockState has form "minecraft:id[prop=true,prop2=3]"
	private int getBlockStateIndex(String blockState) {
		if (!blockStateCache.containsKey(blockState)) {
			// parse block state
			String blockId = blockState;
			HashMap<String, String> parsedProperties = new HashMap<String, String>();
			if (blockState.contains("[")) {
				// parse properties
				blockId = blockState.substring(0, blockState.indexOf("["));
				String propertiesString = blockState.substring(blockState.indexOf("[") + 1, blockState.length() - 1);
				String[] propertyPairs = propertiesString.split(",");
				for (String propertyPair : propertyPairs) {
					int splitIndex = propertyPair.indexOf("=");
					String key = propertyPair.substring(0, splitIndex);
					String value = propertyPair.substring(splitIndex + 1, propertyPair.length());
					parsedProperties.put(key, value);
				}
			}

			// construct block state tag
			HashMap<String, Tag> blockStateContents = new HashMap<String, Tag>();
			blockStateContents.put("Name", new StringTag("Name", blockId));
			if (parsedProperties.size() > 0) {
				HashMap<String, Tag> blockStateProperties = new HashMap<String, Tag>();
				for (String key : parsedProperties.keySet()) {
					String value = parsedProperties.get(key);
					blockStateProperties.put(key, new StringTag(key, value));
				}
				CompoundTag blockStatePropertiesCompoundTag = new CompoundTag("Properties", blockStateProperties);
				blockStateContents.put("", blockStatePropertiesCompoundTag);
			}
			CompoundTag blockStateCompoundTag = new CompoundTag("", blockStateContents);

			// save into palette
			blockStateList.add(blockStateCompoundTag);

			// cache index
			blockStateCache.put(blockState, blockStateList.size() - 1);
		}
		// return index from cache
		return blockStateCache.get(blockState);
	}

	public void writeToFile(String fileName) {
		// hack: set size to a legal size to trick structure blocks into loading
		// the file even from the UI, and not just when triggered by redstone
		// xSize = 1;
		// ySize = 1;
		// zSize = 1;
		// Disabled because it doesn't work with all tools and mods.

		// construct structure file
		ArrayList<Tag> sizeList = new ArrayList<Tag>();
		sizeList.add(new IntTag("", xSize));
		sizeList.add(new IntTag("", ySize));
		sizeList.add(new IntTag("", zSize));
		ListTag sizeTag = new ListTag("size", IntTag.class, sizeList);

		ListTag blocksTag = new ListTag("blocks", CompoundTag.class, blockList);
		IntTag dataVersionTag = new IntTag("DataVersion", 2567); // 1.16
		ListTag paletteTag = new ListTag("palette", CompoundTag.class, blockStateList);

		HashMap<String, Tag> structureContents = new HashMap<String, Tag>();
		structureContents.put("DataVersion", dataVersionTag);
		structureContents.put("size", sizeTag);
		structureContents.put("palette", paletteTag);
		structureContents.put("blocks", blocksTag);
		CompoundTag structure = new CompoundTag("", structureContents);

		// save structure file
		try {
			FileOutputStream fos = new FileOutputStream(new File(fileName));
			NBTOutputStream nos = new NBTOutputStream(fos);
			nos.writeTag(structure);
			nos.close();
		} catch (Exception e) {
			System.out.println("Something went wrong:");
			e.printStackTrace();
			System.exit(0);
		}
	}

}
