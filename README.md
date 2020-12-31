# MapConverter

MapConverter is a tool for generating structures in Minecraft that give the appearance of an image when viewed through an in-game map.

![](https://i.imgur.com/uhOfszD.png)

[Click here for a full album](https://imgur.com/a/eqvsV5E)

## Features

* Outputs structure files which can be placed in-game using structure blocks
* You can choose which blocks are used by the generator by editing `config.txt`
* Terrain mode options: staircase, flat, ascending, descending, grounded
* Provides a count for each type of block used, so you can make sure you have the resources to build it in your survival worlds

## Usage

Basic usage:

```
java -jar MapConverter.jar /path/to/someImage.png
```

It is also recommended to read through the help text:

```
java -jar MapConverter.jar --help
```

## Usage Notes

* Image width and height are not changed. You will get black borders if your image's width and height aren't multiples of 128. Do scaling and cropping on your own.
* Structure files have a 130x130 blocks footprint, but maps are 128x128. You'll have to load the structure 1 block away diagonally from the map area. See also [this album](https://imgur.com/a/eqvsV5E).
* [Dithering](http://en.wikipedia.org/wiki/Dither) is enabled by default, disable it if it produces unfavorable results.
* A color palette is created every time you run the tool, it shows which colors can be used with the current settings. This is helpful if you want to manually edit an image.
* Maps created by placing blocks in the world cannot use 25% of the available colors. If you are simply looking to create maps based on images, there are better tools out there. One such tool I've seen thrown around is [this one](http://mc-map.djfun.de/).
* Contents of the output folder may be overwritten.
* Disabling a color will remove its 3 shades from the color palette.
* Terrain is now saved as structure files (.nbt files), if you need a different format let me know and I'll see what I can do.

To disable a color, delete the corresponding line from `config.txt`. To change a color's block, put the desired block state in its place in `config.txt`. I recommend having a look at the [wiki's list of map colors](https://minecraft.gamepedia.com/Map_item_format) if you're looking for alternative blocks for a specific color. If a block needs a support block, add its color number (the one at the start of the line) to `needSupport` (comma-separated list of color numbers) in `config.txt`. You can change the support block in there as well. Blocks use the block state format used for `/setblock`, but providing block data is not supported. I can look into adding that if anybody needs it. If you use a block water can destroy you might end up with structures where water is adjacent to that block - you'll have to manually fix these.
